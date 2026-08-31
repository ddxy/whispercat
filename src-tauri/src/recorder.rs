use anyhow::{anyhow, Context, Result};
use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use hound::{WavSpec, WavWriter};
use std::fs::File;
use std::io::{BufReader, BufWriter, Read};
use std::path::PathBuf;
use std::process::{Child, Command, Stdio};
use std::sync::mpsc;
use std::sync::{Arc, Mutex};
use std::thread::JoinHandle;
use std::time::{SystemTime, UNIX_EPOCH};

type SharedWriter = Arc<Mutex<Option<WavWriter<BufWriter<File>>>>>;

/// cpal::Stream is not Send on some platforms. The recorder therefore lives
/// in its own thread and is controlled exclusively through channel commands.
pub struct Recorder {
    stream: cpal::Stream,
    writer: SharedWriter,
    session_dir: PathBuf,
    mic_path: PathBuf,
    system_capture: Option<SystemAudioCapture>,
    mic_gain: f32,
    system_audio_gain: f32,
}

enum SystemAudioCapture {
    #[cfg(target_os = "linux")]
    Linux {
        child: Child,
        reader: JoinHandle<Result<()>>,
        raw_path: PathBuf,
        spec: WavSpec,
    },
    #[cfg(target_os = "windows")]
    Windows {
        stream: cpal::Stream,
        writer: SharedWriter,
        path: PathBuf,
        spec: WavSpec,
    },
}

#[derive(Debug, serde::Serialize, serde::Deserialize)]
struct SystemAudioTrack {
    path: PathBuf,
    raw_pcm: bool,
    sample_rate: u32,
    channels: u16,
}

#[derive(Debug, serde::Serialize, serde::Deserialize)]
pub struct Recording {
    session_dir: PathBuf,
    mic_path: PathBuf,
    system_track: Option<SystemAudioTrack>,
    mic_gain: f32,
    system_audio_gain: f32,
}

pub struct FinalizedRecording {
    chunks: Vec<PathBuf>,
}

impl SystemAudioCapture {
    fn start(source: Option<&str>, mic_spec: WavSpec, session_dir: &std::path::Path) -> Result<Self> {
        #[cfg(target_os = "linux")]
        {
            let raw_path = session_dir.join("system.raw");
            let mut command = Command::new("parec");
            command
                .arg("--raw")
                .arg("--format=s16le")
                .arg(format!("--rate={}", mic_spec.sample_rate))
                .arg(format!("--channels={}", mic_spec.channels))
                .stdout(Stdio::piped())
                .stderr(Stdio::piped());
            if let Some(source) = source.filter(|source| !source.trim().is_empty()) {
                command.arg("--device").arg(source);
            } else {
                command.arg("--device").arg("@DEFAULT_MONITOR@");
            }

            let mut child = command
                .spawn()
                .context("Unable to start parec for system-audio recording")?;
            let stdout = child
                .stdout
                .take()
                .ok_or_else(|| anyhow!("Unable to read system-audio stream"))?;
            let raw_file = File::create(&raw_path)
                .with_context(|| format!("Unable to create {}", raw_path.display()))?;
            let reader = std::thread::spawn(move || {
                let mut stdout = BufReader::new(stdout);
                let mut raw_file = BufWriter::new(raw_file);
                let mut buffer = [0u8; 16 * 1024];
                loop {
                    let count = stdout.read(&mut buffer)?;
                    if count == 0 {
                        break;
                    }
                    std::io::Write::write_all(&mut raw_file, &buffer[..count])?;
                }
                std::io::Write::flush(&mut raw_file)?;
                Ok(())
            });
            return Ok(Self::Linux {
                child,
                reader,
                raw_path,
                spec: mic_spec,
            });
        }

        #[cfg(target_os = "windows")]
        {
            let host = cpal::default_host();
            let device = match source.filter(|source| !source.is_empty()) {
                Some(source) => host
                    .output_devices()?
                    .find(|device| device.name().map(|name| name == source).unwrap_or(false))
                    .ok_or_else(|| anyhow!("System-audio device '{source}' not found"))?,
                None => host
                    .default_output_device()
                    .ok_or_else(|| anyhow!("No default output device found"))?,
            };
            let supported = device.default_output_config()?;
            let spec = WavSpec {
                channels: supported.channels(),
                sample_rate: supported.sample_rate().0,
                bits_per_sample: 16,
                sample_format: hound::SampleFormat::Int,
            };
            let path = session_dir.join("system.wav");
            let writer: SharedWriter = Arc::new(Mutex::new(Some(WavWriter::create(&path, spec)?)));
            let stream = build_stream(&device, &supported, writer.clone())?;
            stream.play()?;
            return Ok(Self::Windows {
                stream,
                writer,
                path,
                spec,
            });
        }

        #[cfg(not(any(target_os = "linux", target_os = "windows")))]
        {
            let _ = (source, mic_spec, session_dir);
            Err(anyhow!(
                "System-audio recording is not supported on this platform"
            ))
        }
    }

    fn stop(self) -> Result<SystemAudioTrack> {
        match self {
            #[cfg(target_os = "linux")]
            Self::Linux {
                mut child,
                reader,
                raw_path,
                spec,
            } => {
                let _ = child.kill();
                let _ = child.wait();
                reader
                    .join()
                    .map_err(|_| anyhow!("System-audio reader thread panicked"))??;
                Ok(SystemAudioTrack {
                    path: raw_path,
                    raw_pcm: true,
                    sample_rate: spec.sample_rate,
                    channels: spec.channels,
                })
            }
            #[cfg(target_os = "windows")]
            Self::Windows {
                stream,
                writer,
                path,
                spec,
            } => {
                drop(stream);
                if let Some(writer) = writer.lock().map_err(|error| anyhow!("{error}"))?.take() {
                    writer.finalize()?;
                }
                Ok(SystemAudioTrack {
                    path,
                    raw_pcm: false,
                    sample_rate: spec.sample_rate,
                    channels: spec.channels,
                })
            }
        }
    }

    fn discard(self) -> Result<()> {
        match self {
            #[cfg(target_os = "linux")]
            Self::Linux {
                mut child,
                reader,
                raw_path,
                ..
            } => {
                let _ = child.kill();
                let _ = child.wait();
                let result = reader
                    .join()
                    .map_err(|_| anyhow!("System-audio reader thread panicked"))
                    .and_then(|result| result);
                let _ = std::fs::remove_file(raw_path);
                result
            }
            #[cfg(target_os = "windows")]
            Self::Windows {
                stream,
                writer,
                path,
                ..
            } => {
                drop(stream);
                let result = match writer.lock() {
                    Ok(mut writer) => match writer.take() {
                        Some(writer) => writer.finalize().map_err(Into::into),
                        None => Ok(()),
                    },
                    Err(error) => Err(anyhow!("{error}")),
                };
                let _ = std::fs::remove_file(path);
                result
            }
        }
    }
}

impl Recorder {
    fn start(
        mic_name: Option<&str>,
        system_audio_enabled: bool,
        system_audio_source: Option<&str>,
        mic_gain: f32,
        system_audio_gain: f32,
    ) -> Result<Self> {
        let host = cpal::default_host();
        let device = match mic_name {
            Some(name) if !name.is_empty() => host
                .input_devices()?
                .find(|device| {
                    device
                        .name()
                        .map(|device_name| device_name.contains(name))
                        .unwrap_or(false)
                })
                .ok_or_else(|| anyhow!("Microphone '{name}' not found"))?,
            _ => host
                .default_input_device()
                .ok_or_else(|| anyhow!("No default input device found"))?,
        };
        let supported = device.default_input_config()?;
        let device_name = device.name().unwrap_or_else(|_| "?".to_string());
        tracing::info!(
            "Recording from '{}' — {} Hz / {} channel(s) / {:?}",
            device_name,
            supported.sample_rate().0,
            supported.channels(),
            supported.sample_format()
        );

        let spec = WavSpec {
            channels: supported.channels(),
            sample_rate: supported.sample_rate().0,
            bits_per_sample: 16,
            sample_format: hound::SampleFormat::Int,
        };
        let timestamp = SystemTime::now().duration_since(UNIX_EPOCH)?.as_millis();
        let session_dir = recording_root().join(format!("{timestamp}_{}", uuid::Uuid::new_v4()));
        std::fs::create_dir_all(&session_dir)
            .with_context(|| format!("Unable to create recording directory {}", session_dir.display()))?;
        let mic_path = session_dir.join("mic.wav");
        let writer = match WavWriter::create(&mic_path, spec) {
            Ok(writer) => Arc::new(Mutex::new(Some(writer))),
            Err(error) => {
                let _ = std::fs::remove_dir_all(&session_dir);
                return Err(error.into());
            }
        };
        let stream = match build_stream(&device, &supported, writer.clone()) {
            Ok(stream) => stream,
            Err(error) => {
                if let Some(writer) = writer.lock().map_err(|e| anyhow!("{e}"))?.take() {
                    let _ = writer.finalize();
                }
                let _ = std::fs::remove_dir_all(&session_dir);
                return Err(error);
            }
        };
        if let Err(error) = stream.play() {
            drop(stream);
            if let Some(writer) = writer.lock().map_err(|e| anyhow!("{e}"))?.take() {
                let _ = writer.finalize();
            }
            let _ = std::fs::remove_dir_all(&session_dir);
            return Err(error.into());
        }

        let system_capture = if system_audio_enabled {
            match SystemAudioCapture::start(system_audio_source, spec, &session_dir) {
                Ok(capture) => Some(capture),
                Err(error) => {
                    drop(stream);
                    if let Some(writer) = writer.lock().map_err(|e| anyhow!("{e}"))?.take() {
                        writer.finalize()?;
                    }
                    let _ = std::fs::remove_dir_all(&session_dir);
                    return Err(error);
                }
            }
        } else {
            None
        };

        Ok(Self {
            stream,
            writer,
            session_dir,
            mic_path,
            system_capture,
            mic_gain: mic_gain.clamp(0.0, 2.0),
            system_audio_gain: system_audio_gain.clamp(0.0, 2.0),
        })
    }

    fn stop(self) -> Result<Recording> {
        let Recorder {
            stream,
            writer,
            session_dir,
            mic_path,
            system_capture,
            mic_gain,
            system_audio_gain,
        } = self;
        drop(stream);
        if let Some(writer) = writer.lock().map_err(|e| anyhow!("{e}"))?.take() {
            writer.finalize()?;
        }

        let system_track = system_capture.map(SystemAudioCapture::stop).transpose()?;
        Ok(Recording {
            session_dir,
            mic_path,
            system_track,
            mic_gain,
            system_audio_gain,
        })
    }

    fn discard(self) -> Result<()> {
        let Recorder {
            stream,
            writer,
            session_dir,
            mic_path: _,
            system_capture,
            ..
        } = self;
        drop(stream);

        let mut error = match writer.lock() {
            Ok(mut writer) => writer
                .take()
                .and_then(|writer| writer.finalize().err().map(Into::into)),
            Err(error) => Some(anyhow!("{error}")),
        };
        if let Some(capture) = system_capture {
            if let Err(capture_error) = capture.discard() {
                error.get_or_insert(capture_error);
            }
        }
        if let Err(remove_error) = std::fs::remove_dir_all(&session_dir) {
            if remove_error.kind() != std::io::ErrorKind::NotFound {
                error.get_or_insert(remove_error.into());
            }
        }

        if let Some(error) = error {
            return Err(error);
        }
        tracing::info!("Recording discarded");
        Ok(())
    }
}

const OUTPUT_SAMPLE_RATE: u32 = 16_000;
const OUTPUT_BITRATE: &str = "64k";
const CHUNK_SECONDS: u32 = 600;

/// Mixes recorded tracks and emits compact, API-safe MP3 chunks without loading
/// complete recordings into memory. ffmpeg also handles format differences between tracks.
pub fn finalize(recording: Recording) -> Result<FinalizedRecording> {
    let chunk_dir = recording.session_dir.join("chunks");
    let result = (|| {
        std::fs::create_dir_all(&chunk_dir)
            .with_context(|| format!("Unable to create {}", chunk_dir.display()))?;
        let chunk_pattern = chunk_dir.join("chunk_%03d.mp3");
        let mut command = Command::new("ffmpeg");
        command
            .arg("-hide_banner")
            .arg("-loglevel")
            .arg("error")
            .arg("-y");
        command.arg("-i").arg(&recording.mic_path);

        if let Some(system) = &recording.system_track {
            if system.raw_pcm {
                command
                    .args(["-f", "s16le", "-ar"])
                    .arg(system.sample_rate.to_string())
                    .args(["-ac"])
                    .arg(system.channels.to_string());
            }
            command.arg("-i").arg(&system.path);
            command.arg("-filter_complex").arg(format!(
                "[0:a]volume={}[mic];[1:a]volume={}[system];[mic][system]amix=inputs=2:duration=longest:normalize=0",
                recording.mic_gain.clamp(0.0, 2.0),
                recording.system_audio_gain.clamp(0.0, 2.0),
            ));
        } else {
            command
                .arg("-filter:a")
                .arg(format!("volume={}", recording.mic_gain.clamp(0.0, 2.0)));
        }

        let output = command
            .arg("-ar")
            .arg(OUTPUT_SAMPLE_RATE.to_string())
            .arg("-ac")
            .arg("1")
            .arg("-c:a")
            .arg("libmp3lame")
            .arg("-b:a")
            .arg(OUTPUT_BITRATE)
            .arg("-f")
            .arg("segment")
            .arg("-segment_time")
            .arg(CHUNK_SECONDS.to_string())
            .arg("-reset_timestamps")
            .arg("1")
            .arg(&chunk_pattern)
            .output()
            .context("Unable to start ffmpeg. Install ffmpeg to finalize recordings.")?;
        if !output.status.success() {
            return Err(anyhow!(
                "ffmpeg audio finalization failed: {}",
                String::from_utf8_lossy(&output.stderr).trim()
            ));
        }

        let mut chunks: Vec<_> = std::fs::read_dir(&chunk_dir)?
            .filter_map(|entry| entry.ok().map(|entry| entry.path()))
            .filter(|path| path.extension().is_some_and(|extension| extension == "mp3"))
            .collect();
        chunks.sort();
        if chunks.is_empty() {
            return Err(anyhow!("ffmpeg produced no audio chunks"));
        }
        Ok(FinalizedRecording {
            chunks,
        })
    })();

    result
}

impl Recording {
    pub fn session_dir(&self) -> &std::path::Path {
        &self.session_dir
    }
}

impl FinalizedRecording {
    pub fn chunks(&self) -> &[PathBuf] {
        &self.chunks
    }
}

pub fn recording_root() -> PathBuf {
    std::env::temp_dir().join("whispercat")
}

fn build_stream(
    device: &cpal::Device,
    supported: &cpal::SupportedStreamConfig,
    writer: SharedWriter,
) -> Result<cpal::Stream> {
    let error = |error| tracing::error!("Audio-stream error: {error}");
    let config: cpal::StreamConfig = supported.config();
    let stream = match supported.sample_format() {
        cpal::SampleFormat::F32 => {
            let writer = writer;
            device.build_input_stream(
                &config,
                move |data: &[f32], _| {
                    if let Some(writer) = writer.lock().unwrap().as_mut() {
                        for &sample in data {
                            let value = (sample.clamp(-1.0, 1.0) * i16::MAX as f32) as i16;
                            let _ = writer.write_sample(value);
                        }
                    }
                },
                error,
                None,
            )?
        }
        cpal::SampleFormat::I16 => {
            let writer = writer;
            device.build_input_stream(
                &config,
                move |data: &[i16], _| {
                    if let Some(writer) = writer.lock().unwrap().as_mut() {
                        for &sample in data {
                            let _ = writer.write_sample(sample);
                        }
                    }
                },
                error,
                None,
            )?
        }
        cpal::SampleFormat::U16 => {
            let writer = writer;
            device.build_input_stream(
                &config,
                move |data: &[u16], _| {
                    if let Some(writer) = writer.lock().unwrap().as_mut() {
                        for &sample in data {
                            let value = (sample as i32 - 32768) as i16;
                            let _ = writer.write_sample(value);
                        }
                    }
                },
                error,
                None,
            )?
        }
        other => return Err(anyhow!("Sample format {other:?} is not supported")),
    };
    Ok(stream)
}

pub fn list_input_devices() -> Vec<String> {
    cpal::default_host()
        .input_devices()
        .map(|devices| devices.filter_map(|device| device.name().ok()).collect())
        .unwrap_or_default()
}

pub fn detect_default_input_device() -> Option<String> {
    cpal::default_host()
        .default_input_device()
        .and_then(|device| device.name().ok())
}

pub fn detect_active_system_audio_source() -> Option<String> {
    #[cfg(target_os = "linux")]
    {
        let output = Command::new("pactl")
            .args(["list", "short", "sink-inputs"])
            .output()
            .ok()?;
        if !output.status.success() {
            return None;
        }
        let sink_id = String::from_utf8_lossy(&output.stdout)
            .lines()
            .find_map(|line| line.split_whitespace().nth(1))?
            .to_string();
        let output = Command::new("pactl")
            .args(["list", "short", "sinks"])
            .output()
            .ok()?;
        if !output.status.success() {
            return None;
        }
        return String::from_utf8_lossy(&output.stdout)
            .lines()
            .find(|line| line.split_whitespace().next() == Some(&sink_id))
            .and_then(|line| line.split_whitespace().nth(1))
            .map(|sink| format!("{sink}.monitor"));
    }

    #[cfg(target_os = "windows")]
    {
        return cpal::default_host()
            .default_output_device()
            .and_then(|device| device.name().ok());
    }

    #[cfg(not(any(target_os = "linux", target_os = "windows")))]
    None
}

pub fn list_system_audio_sources() -> Vec<String> {
    #[cfg(target_os = "linux")]
    {
        let output = Command::new("pactl")
            .args(["list", "short", "sources"])
            .output();
        return output
            .ok()
            .filter(|output| output.status.success())
            .map(|output| {
                String::from_utf8_lossy(&output.stdout)
                    .lines()
                    .filter_map(|line| line.split_whitespace().nth(1))
                    .filter(|name| name.ends_with(".monitor"))
                    .map(str::to_string)
                    .collect()
            })
            .unwrap_or_default();
    }

    #[cfg(target_os = "windows")]
    {
        return cpal::default_host()
            .output_devices()
            .map(|devices| devices.filter_map(|device| device.name().ok()).collect())
            .unwrap_or_default();
    }

    #[cfg(not(any(target_os = "linux", target_os = "windows")))]
    Vec::new()
}

// ---------------- Thread handle ----------------

enum RecCommand {
    Start {
        mic: Option<String>,
        system_audio_enabled: bool,
        system_audio_source: Option<String>,
        mic_gain: f32,
        system_audio_gain: f32,
        reply: mpsc::Sender<Result<(), String>>,
    },
    Stop {
        reply: mpsc::Sender<Result<Recording, String>>,
    },
    Discard {
        reply: mpsc::Sender<Result<(), String>>,
    },
}

fn send_and_wait<T>(
    tx: &mpsc::Sender<RecCommand>,
    command: impl FnOnce(mpsc::Sender<Result<T, String>>) -> RecCommand,
) -> Result<T, String> {
    let (reply_tx, reply_rx) = mpsc::channel();
    tx.send(command(reply_tx))
        .map_err(|error| error.to_string())?;
    reply_rx.recv().map_err(|error| error.to_string())?
}

#[derive(Clone)]
pub struct RecorderHandle {
    tx: mpsc::Sender<RecCommand>,
}

impl RecorderHandle {
    pub fn new() -> Self {
        let (tx, rx) = mpsc::channel::<RecCommand>();
        std::thread::spawn(move || {
            let mut current: Option<Recorder> = None;
            while let Ok(command) = rx.recv() {
                match command {
                    RecCommand::Start {
                        mic,
                        system_audio_enabled,
                        system_audio_source,
                        mic_gain,
                        system_audio_gain,
                        reply,
                    } => {
                        let result = if current.is_some() {
                            Err("A recording is already in progress.".to_string())
                        } else {
                            match Recorder::start(
                                mic.as_deref(),
                                system_audio_enabled,
                                system_audio_source.as_deref(),
                                mic_gain,
                                system_audio_gain,
                            ) {
                                Ok(recorder) => {
                                    current = Some(recorder);
                                    Ok(())
                                }
                                Err(error) => Err(error.to_string()),
                            }
                        };
                        let _ = reply.send(result);
                    }
                    RecCommand::Stop { reply } => {
                        let result = match current.take() {
                            Some(recorder) => recorder.stop().map_err(|error| error.to_string()),
                            None => Err("No recording is active.".to_string()),
                        };
                        let _ = reply.send(result);
                    }
                    RecCommand::Discard { reply } => {
                        let result = match current.take() {
                            Some(recorder) => recorder.discard().map_err(|error| error.to_string()),
                            None => Err("No recording is active.".to_string()),
                        };
                        let _ = reply.send(result);
                    }
                }
            }
        });
        Self { tx }
    }

    pub fn start(
        &self,
        mic: Option<String>,
        system_audio_enabled: bool,
        system_audio_source: Option<String>,
        mic_gain: f32,
        system_audio_gain: f32,
    ) -> Result<(), String> {
        send_and_wait(&self.tx, |reply| RecCommand::Start {
            mic,
            system_audio_enabled,
            system_audio_source,
            mic_gain,
            system_audio_gain,
            reply,
        })
    }

    pub fn stop(&self) -> Result<Recording, String> {
        send_and_wait(&self.tx, |reply| RecCommand::Stop { reply })
    }

    pub fn discard(&self) -> Result<(), String> {
        send_and_wait(&self.tx, |reply| RecCommand::Discard { reply })
    }
}
