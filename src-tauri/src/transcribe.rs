use crate::config::Config;
use anyhow::{anyhow, Context, Result};
use reqwest::multipart;
use std::path::{Path, PathBuf};

const MAX_UPLOAD_BYTES: u64 = 25 * 1024 * 1024;
const CHUNK_BYTES: u64 = 24 * 1024 * 1024;

/// Sends a WAV file to the configured Whisper backend and returns the text
/// from the JSON field "text". Files larger than 25 MiB are split into valid
/// WAV chunks and transcribed sequentially.
pub async fn transcribe(cfg: &Config, audio_path: &str) -> Result<String> {
    let path = Path::new(audio_path);
    let size = tokio::fs::metadata(path)
        .await
        .with_context(|| format!("Audio file cannot be read: {audio_path}"))?
        .len();

    if size <= MAX_UPLOAD_BYTES {
        return transcribe_file(cfg, path).await;
    }

    tracing::info!(
        "Splitting {} before transcription because it exceeds 25 MiB",
        audio_path
    );
    let chunks = split_wav(path)?;
    let mut transcripts = Vec::with_capacity(chunks.len());

    let result = async {
        for (index, chunk) in chunks.iter().enumerate() {
            let transcript = transcribe_file(cfg, chunk).await.with_context(|| {
                format!(
                    "Failed to transcribe chunk {} of {}",
                    index + 1,
                    chunks.len()
                )
            })?;
            transcripts.push(transcript);
        }
        Ok::<_, anyhow::Error>(())
    }
    .await;

    for chunk in &chunks {
        let _ = std::fs::remove_file(chunk);
    }
    result?;

    Ok(transcripts
        .into_iter()
        .filter(|text| !text.is_empty())
        .collect::<Vec<_>>()
        .join(" "))
}

fn split_wav(path: &Path) -> Result<Vec<PathBuf>> {
    let mut reader = hound::WavReader::open(path)
        .with_context(|| format!("Unable to open WAV file: {}", path.display()))?;
    let spec = reader.spec();
    if spec.sample_format != hound::SampleFormat::Int || spec.bits_per_sample != 16 {
        return Err(anyhow!(
            "Only 16-bit PCM WAV files can be split automatically: {}",
            path.display()
        ));
    }

    let bytes_per_frame = u64::from(spec.channels) * u64::from(spec.bits_per_sample / 8);
    let frames_per_chunk = CHUNK_BYTES / bytes_per_frame;
    if frames_per_chunk == 0 {
        return Err(anyhow!("Invalid WAV format: {}", path.display()));
    }

    let stem = path
        .file_stem()
        .and_then(|name| name.to_str())
        .unwrap_or("recording");
    let mut chunks = Vec::new();
    let mut chunk_index = 0usize;
    let mut frames_written = 0u64;
    let mut writer: Option<hound::WavWriter<std::io::BufWriter<std::fs::File>>> = None;

    for sample in reader.samples::<i16>() {
        if writer.is_none() || frames_written == frames_per_chunk {
            if let Some(current) = writer.take() {
                current.finalize()?;
            }

            let chunk_path = std::env::temp_dir().join(format!(
                "whispercat_{}_chunk_{}_{}.wav",
                stem,
                std::process::id(),
                chunk_index
            ));
            chunk_index += 1;
            writer = Some(hound::WavWriter::create(&chunk_path, spec)?);
            chunks.push(chunk_path);
            frames_written = 0;
        }

        writer
            .as_mut()
            .expect("WAV writer is initialized")
            .write_sample(sample?)?;

        // A frame contains one sample per channel. Start the next chunk only
        // after a complete frame so a stereo recording is never split mid-frame.
        if writer.as_ref().expect("WAV writer is initialized").len() % u32::from(spec.channels) == 0
        {
            frames_written += 1;
        }
    }

    if let Some(current) = writer {
        current.finalize()?;
    }

    if chunks.is_empty() {
        return Err(anyhow!(
            "WAV file contains no audio samples: {}",
            path.display()
        ));
    }

    Ok(chunks)
}

async fn transcribe_file(cfg: &Config, audio_path: &Path) -> Result<String> {
    let (url, api_key, mut form) = match cfg.whisper_server.as_str() {
        "openai" => (
            "https://api.openai.com/v1/audio/transcriptions".to_string(),
            cfg.api_key.clone(),
            multipart::Form::new().text("model", "whisper-1"),
        ),
        "faster-whisper" => {
            let mut form = multipart::Form::new().text("model", cfg.faster_model.clone());
            if !cfg.faster_language.trim().is_empty() {
                form = form.text("language", cfg.faster_language.trim().to_string());
            }
            (
                format!(
                    "{}/v1/audio/transcriptions",
                    normalize(&cfg.faster_url, "http")
                ),
                String::new(),
                form,
            )
        }
        "open-webui" => (
            format!(
                "{}/api/v1/audio/transcriptions",
                normalize(&cfg.owui_url, "https")
            ),
            cfg.owui_key.clone(),
            multipart::Form::new(),
        ),
        other => return Err(anyhow!("Unknown Whisper server: {other}")),
    };

    tracing::info!("Transcribing {} via {}", audio_path.display(), url);

    let bytes = tokio::fs::read(audio_path)
        .await
        .with_context(|| format!("Audio file cannot be read: {}", audio_path.display()))?;
    let filename = audio_path
        .file_name()
        .map(|name| name.to_string_lossy().to_string())
        .unwrap_or_else(|| "recording.wav".to_string());
    form = form.part(
        "file",
        multipart::Part::bytes(bytes)
            .file_name(filename)
            .mime_str("audio/wav")?,
    );

    let client = reqwest::Client::new();
    let mut req = client.post(&url).multipart(form);
    if !api_key.is_empty() {
        req = req.bearer_auth(api_key);
    }
    let res = req.send().await.context("HTTP request failed")?;
    let status = res.status();
    let body = res.text().await.context("Response cannot be read")?;
    if !status.is_success() {
        return Err(anyhow!("API error ({status}): {body}"));
    }
    let json: serde_json::Value =
        serde_json::from_str(&body).context("Invalid JSON response from server")?;
    Ok(json["text"].as_str().unwrap_or_default().trim().to_string())
}

fn normalize(url: &str, scheme: &str) -> String {
    let url = url.trim().trim_end_matches('/');
    if url.starts_with("http://") || url.starts_with("https://") {
        url.to_string()
    } else {
        format!("{scheme}://{url}")
    }
}
