use anyhow::{anyhow, Context, Result};
use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use hound::{WavReader, WavSpec, WavWriter};
use std::fs::File;
use std::io::{BufReader, BufWriter, Read};
use std::path::{Path, PathBuf};
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
    mic_path: PathBuf,
    output_path: PathBuf,
    spec: WavSpec,
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

struct SystemAudioTrack {
    path: PathBuf,
    spec: WavSpec,
    raw_pcm: bool,
}

impl SystemAudioCapture {
    fn start(source: Option<&str>, mic_spec: WavSpec, timestamp: u128) -> Result<Self> {
        #[cfg(target_os = "linux")]
        {
            let raw_path = std::env::temp_dir().join(format!("whispercat_{timestamp}_system.raw"));
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
            let path = std::env::temp_dir().join(format!("whispercat_{timestamp}_system.wav"));
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
            let _ = (source, mic_spec, timestamp);
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
                    spec,
                    raw_pcm: true,
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
                    spec,
                    raw_pcm: false,
                })
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
        let mic_path = std::env::temp_dir().join(format!("whispercat_{timestamp}_mic.wav"));
        let output_path = std::env::temp_dir().join(format!("whispercat_{timestamp}.wav"));
        let writer: SharedWriter = Arc::new(Mutex::new(Some(WavWriter::create(&mic_path, spec)?)));
        let stream = build_stream(&device, &supported, writer.clone())?;
        stream.play()?;

        let system_capture = if system_audio_enabled {
            match SystemAudioCapture::start(system_audio_source, spec, timestamp) {
                Ok(capture) => Some(capture),
                Err(error) => {
                    drop(stream);
                    if let Some(writer) = writer.lock().map_err(|e| anyhow!("{e}"))?.take() {
                        writer.finalize()?;
                    }
                    let _ = std::fs::remove_file(&mic_path);
                    return Err(error);
                }
            }
        } else {
            None
        };

        Ok(Self {
            stream,
            writer,
            mic_path,
            output_path,
            spec,
            system_capture,
            mic_gain: mic_gain.clamp(0.0, 2.0),
            system_audio_gain: system_audio_gain.clamp(0.0, 2.0),
        })
    }

    fn stop(self) -> Result<PathBuf> {
        let Recorder {
            stream,
            writer,
            mic_path,
            output_path,
            spec,
            system_capture,
            mic_gain,
            system_audio_gain,
        } = self;
        drop(stream);
        if let Some(writer) = writer.lock().map_err(|e| anyhow!("{e}"))?.take() {
            writer.finalize()?;
        }

        let result = match system_capture {
            Some(capture) => {
                let system_track = capture.stop()?;
                let mixed = mix_audio(
                    &mic_path,
                    &system_track,
                    &output_path,
                    spec,
                    mic_gain,
                    system_audio_gain,
                );
                let _ = std::fs::remove_file(system_track.path);
                mixed
            }
            None => std::fs::rename(&mic_path, &output_path).map_err(Into::into),
        };
        let _ = std::fs::remove_file(&mic_path);
        result?;

        tracing::info!("Recording saved: {}", output_path.display());
        Ok(output_path)
    }
}

fn mix_audio(
    mic_path: &Path,
    system_track: &SystemAudioTrack,
    output_path: &Path,
    spec: WavSpec,
    mic_gain: f32,
    system_gain: f32,
) -> Result<()> {
    let mic_samples = read_wav_samples(mic_path)?;
    let system_samples = if system_track.raw_pcm {
        read_raw_samples(&system_track.path)?
    } else {
        read_wav_samples(&system_track.path)?
    };
    let system_samples = resample_and_remix(system_samples, system_track.spec, spec);
    let mut writer = WavWriter::create(output_path, spec)?;
    let total_samples = mic_samples.len().max(system_samples.len());

    for index in 0..total_samples {
        let mic = mic_samples.get(index).copied().unwrap_or(0);
        let system = system_samples.get(index).copied().unwrap_or(0);
        writer.write_sample(mix_sample(mic, system, mic_gain, system_gain))?;
    }

    writer.finalize()?;
    Ok(())
}

fn read_wav_samples(path: &Path) -> Result<Vec<i16>> {
    let mut reader = WavReader::open(path)?;
    reader
        .samples::<i16>()
        .collect::<Result<Vec<_>, _>>()
        .map_err(Into::into)
}

fn read_raw_samples(path: &Path) -> Result<Vec<i16>> {
    let bytes = std::fs::read(path)?;
    Ok(bytes
        .chunks_exact(2)
        .map(|chunk| i16::from_le_bytes([chunk[0], chunk[1]]))
        .collect())
}

fn resample_and_remix(samples: Vec<i16>, input: WavSpec, output: WavSpec) -> Vec<i16> {
    if input.channels == 0
        || output.channels == 0
        || input.sample_rate == 0
        || output.sample_rate == 0
    {
        return Vec::new();
    }

    let input_channels = usize::from(input.channels);
    let output_channels = usize::from(output.channels);
    let input_frames = samples.len() / input_channels;
    if input_frames == 0 {
        return Vec::new();
    }
    let output_frames = (input_frames as u64 * u64::from(output.sample_rate)
        / u64::from(input.sample_rate)) as usize;
    let mut converted = Vec::with_capacity(output_frames * output_channels);

    for output_frame in 0..output_frames {
        let input_frame = ((output_frame as u64 * u64::from(input.sample_rate))
            / u64::from(output.sample_rate)) as usize;
        let input_frame = input_frame.min(input_frames - 1);
        let source = &samples[input_frame * input_channels..(input_frame + 1) * input_channels];
        let mono =
            source.iter().map(|sample| i32::from(*sample)).sum::<i32>() / input_channels as i32;
        for channel in 0..output_channels {
            let sample = if input_channels == output_channels {
                source[channel]
            } else {
                mono as i16
            };
            converted.push(sample);
        }
    }

    converted
}

fn mix_sample(mic: i16, system: i16, mic_gain: f32, system_gain: f32) -> i16 {
    let mixed = mic as f32 * mic_gain + system as f32 * system_gain;
    mixed.clamp(i16::MIN as f32, i16::MAX as f32) as i16
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
        reply: mpsc::Sender<Result<PathBuf, String>>,
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

    pub fn stop(&self) -> Result<PathBuf, String> {
        send_and_wait(&self.tx, |reply| RecCommand::Stop { reply })
    }
}
