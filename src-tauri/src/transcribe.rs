use crate::config::Config;
use anyhow::{anyhow, Context, Result};
use reqwest::multipart;

/// Sendet die WAV-Datei an das konfigurierte Whisper-Backend
/// und gibt den Text aus dem JSON-Feld "text" zurück.
pub async fn transcribe(cfg: &Config, audio_path: &str) -> Result<String> {
    let (url, api_key, mut form) = match cfg.whisper_server.as_str() {
        "openai" => (
            "https://api.openai.com/v1/audio/transcriptions".to_string(),
            cfg.api_key.clone(),
            multipart::Form::new().text("model", "whisper-1"),
        ),
        "faster-whisper" => {
            let mut f = multipart::Form::new().text("model", cfg.faster_model.clone());
            if !cfg.faster_language.trim().is_empty() {
                f = f.text("language", cfg.faster_language.trim().to_string());
            }
            (
                format!("{}/v1/audio/transcriptions", normalize(&cfg.faster_url, "http")),
                String::new(),
                f,
            )
        }
        "open-webui" => (
            format!("{}/api/v1/audio/transcriptions", normalize(&cfg.owui_url, "https")),
            cfg.owui_key.clone(),
            multipart::Form::new(),
        ),
        other => return Err(anyhow!("Unbekannter Whisper-Server: {other}")),
    };

    tracing::info!("Transkribiere {} über {}", audio_path, url);

    let bytes = tokio::fs::read(audio_path)
        .await
        .with_context(|| format!("Audiodatei nicht lesbar: {audio_path}"))?;
    let filename = std::path::Path::new(audio_path)
        .file_name()
        .map(|n| n.to_string_lossy().to_string())
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
    let res = req.send().await.context("HTTP-Request fehlgeschlagen")?;
    let status = res.status();
    let body = res.text().await.context("Antwort nicht lesbar")?;
    if !status.is_success() {
        return Err(anyhow!("API-Fehler ({status}): {body}"));
    }
    let json: serde_json::Value =
        serde_json::from_str(&body).context("Ungültige JSON-Antwort vom Server")?;
    Ok(json["text"].as_str().unwrap_or_default().trim().to_string())
}

fn normalize(url: &str, scheme: &str) -> String {
    let u = url.trim().trim_end_matches('/');
    if u.starts_with("http://") || u.starts_with("https://") {
        u.to_string()
    } else {
        format!("{scheme}://{u}")
    }
}
