use anyhow::{anyhow, Result};
use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use hound::{WavSpec, WavWriter};
use std::fs::File;
use std::io::BufWriter;
use std::path::PathBuf;
use std::sync::mpsc;
use std::sync::{Arc, Mutex};
use std::time::{SystemTime, UNIX_EPOCH};

type SharedWriter = Arc<Mutex<Option<WavWriter<BufWriter<File>>>>>;

/// cpal::Stream ist auf einigen Plattformen nicht Send. Deshalb lebt der
/// Recorder in einem eigenen Thread und wird ausschließlich über
/// Kanal-Kommandos gesteuert.
pub struct Recorder {
    stream: cpal::Stream,
    writer: SharedWriter,
    path: PathBuf,
}

impl Recorder {
    fn start(mic_name: Option<&str>) -> Result<Self> {
        let host = cpal::default_host();
        let device = match mic_name {
            Some(name) if !name.is_empty() => host
                .input_devices()?
                .find(|d| d.name().map(|n| n.contains(name)).unwrap_or(false))
                .ok_or_else(|| anyhow!("Mikrofon '{name}' nicht gefunden"))?,
            _ => host
                .default_input_device()
                .ok_or_else(|| anyhow!("Kein Standard-Eingabegerät gefunden"))?,
        };
        let supported = device.default_input_config()?;
        let device_name = device.name().unwrap_or_else(|_| "?".to_string());
        tracing::info!(
            "Aufnahme über Gerät '{}' — {} Hz / {} Kanal(e) / {:?}",
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
        let path = std::env::temp_dir().join(format!(
            "whispercat_{}.wav",
            SystemTime::now().duration_since(UNIX_EPOCH)?.as_millis()
        ));
        let writer: SharedWriter = Arc::new(Mutex::new(Some(WavWriter::create(&path, spec)?)));
        let stream = build_stream(&device, &supported, writer.clone())?;
        stream.play()?;
        Ok(Self { stream, writer, path })
    }

    fn stop(self) -> Result<PathBuf> {
        let Recorder { stream, writer, path } = self;
        drop(stream); // Stream-Drop beendet die Aufnahme
        if let Some(w) = writer.lock().map_err(|e| anyhow!("{e}"))?.take() {
            w.finalize()?;
        }
        tracing::info!("Aufnahme gespeichert: {}", path.display());
        Ok(path)
    }
}

fn build_stream(
    device: &cpal::Device,
    supported: &cpal::SupportedStreamConfig,
    writer: SharedWriter,
) -> Result<cpal::Stream> {
    let err_fn = |e| tracing::error!("Audio-Stream-Fehler: {e}");
    let config: cpal::StreamConfig = supported.config();
    let stream = match supported.sample_format() {
        cpal::SampleFormat::F32 => {
            let w = writer;
            device.build_input_stream(
                &config,
                move |data: &[f32], _| {
                    if let Some(wr) = w.lock().unwrap().as_mut() {
                        for &s in data {
                            let v = (s.clamp(-1.0, 1.0) * i16::MAX as f32) as i16;
                            let _ = wr.write_sample(v);
                        }
                    }
                },
                err_fn,
                None,
            )?
        }
        cpal::SampleFormat::I16 => {
            let w = writer;
            device.build_input_stream(
                &config,
                move |data: &[i16], _| {
                    if let Some(wr) = w.lock().unwrap().as_mut() {
                        for &s in data {
                            let _ = wr.write_sample(s);
                        }
                    }
                },
                err_fn,
                None,
            )?
        }
        cpal::SampleFormat::U16 => {
            let w = writer;
            device.build_input_stream(
                &config,
                move |data: &[u16], _| {
                    if let Some(wr) = w.lock().unwrap().as_mut() {
                        for &s in data {
                            let v = (s as i32 - 32768) as i16;
                            let _ = wr.write_sample(v);
                        }
                    }
                },
                err_fn,
                None,
            )?
        }
        other => return Err(anyhow!("Sample-Format {other:?} wird nicht unterstützt")),
    };
    Ok(stream)
}

pub fn list_input_devices() -> Vec<String> {
    cpal::default_host()
        .input_devices()
        .map(|devs| devs.filter_map(|d| d.name().ok()).collect())
        .unwrap_or_default()
}

// ---------------- Thread-Handle ----------------

enum RecCommand {
    Start {
        mic: Option<String>,
        reply: mpsc::Sender<Result<(), String>>,
    },
    Stop {
        reply: mpsc::Sender<Result<PathBuf, String>>,
    },
}

fn send_and_wait<T>(
    tx: &mpsc::Sender<RecCommand>,
    cmd: impl FnOnce(mpsc::Sender<Result<T, String>>) -> RecCommand,
) -> Result<T, String> {
    let (rtx, rrx) = mpsc::channel();
    tx.send(cmd(rtx)).map_err(|e| e.to_string())?;
    rrx.recv().map_err(|e| e.to_string())?
}

pub struct RecorderHandle {
    tx: mpsc::Sender<RecCommand>,
}

impl RecorderHandle {
    pub fn new() -> Self {
        let (tx, rx) = mpsc::channel::<RecCommand>();
        std::thread::spawn(move || {
            let mut current: Option<Recorder> = None;
            while let Ok(cmd) = rx.recv() {
                match cmd {
                    RecCommand::Start { mic, reply } => {
                        let res = if current.is_some() {
                            Err("Es läuft bereits eine Aufnahme.".to_string())
                        } else {
                            match Recorder::start(mic.as_deref()) {
                                Ok(r) => {
                                    current = Some(r);
                                    Ok(())
                                }
                                Err(e) => Err(e.to_string()),
                            }
                        };
                        let _ = reply.send(res);
                    }
                    RecCommand::Stop { reply } => {
                        let res = match current.take() {
                            Some(r) => r.stop().map_err(|e| e.to_string()),
                            None => Err("Keine Aufnahme aktiv.".to_string()),
                        };
                        let _ = reply.send(res);
                    }
                }
            }
        });
        Self { tx }
    }

    pub fn start(&self, mic: Option<String>) -> Result<(), String> {
        send_and_wait(&self.tx, |reply| RecCommand::Start { mic, reply })
    }

    pub fn stop(&self) -> Result<PathBuf, String> {
        send_and_wait(&self.tx, |reply| RecCommand::Stop { reply })
    }
}
