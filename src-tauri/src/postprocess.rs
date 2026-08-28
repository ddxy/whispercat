use crate::config::{self, Config};
use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use std::path::PathBuf;

// ---------- Datenmodell ----------

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PostProcessing {
    #[serde(default)]
    pub uuid: String,
    pub title: String,
    #[serde(default)]
    pub description: String,
    #[serde(default)]
    pub steps: Vec<Step>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "lowercase")]
pub enum Step {
    Prompt {
        provider: String, // "openai" | "open-webui"
        model: String,
        system_prompt: String,
        user_prompt: String, // darf {{input}} enthalten
    },
    Replace {
        from: String,
        to: String,
    },
}

// ---------- Ablauf ----------

pub async fn apply(cfg: &Config, pp: &PostProcessing, input: &str) -> Result<String> {
    let mut text = input.to_string();
    for step in &pp.steps {
        text = match step {
            Step::Replace { from, to } => text.replace(from.as_str(), to),
            Step::Prompt {
                provider,
                model,
                system_prompt,
                user_prompt,
            } => {
                let user = user_prompt.replace("{{input}}", &text);
                chat(cfg, provider, model, system_prompt, &user).await?
            }
        };
    }
    Ok(text)
}

async fn chat(
    cfg: &Config,
    provider: &str,
    model: &str,
    system_prompt: &str,
    user_prompt: &str,
) -> Result<String> {
    let (url, key) = match provider.to_lowercase().replace(' ', "-").as_str() {
        "open-webui" | "openwebui" => (
            format!(
                "{}/api/chat/completions",
                cfg.owui_url.trim().trim_end_matches('/')
            ),
            cfg.owui_key.clone(),
        ),
        _ => (
            "https://api.openai.com/v1/chat/completions".to_string(),
            cfg.api_key.clone(),
        ),
    };

    let mut messages = Vec::new();
    if !system_prompt.trim().is_empty() {
        messages.push(serde_json::json!({ "role": "system", "content": system_prompt }));
    }
    messages.push(serde_json::json!({ "role": "user", "content": user_prompt }));
    let body = serde_json::json!({ "model": model, "messages": messages });

    let client = reqwest::Client::new();
    let mut req = client.post(&url).json(&body);
    if !key.is_empty() {
        req = req.bearer_auth(key);
    }
    let res = req.send().await.context("HTTP-Request fehlgeschlagen")?;
    let status = res.status();
    let body_text = res.text().await.context("Antwort nicht lesbar")?;
    if !status.is_success() {
        anyhow::bail!("API-Fehler ({status}): {body_text}");
    }
    let json: serde_json::Value =
        serde_json::from_str(&body_text).context("Ungültige JSON-Antwort")?;
    Ok(json["choices"][0]["message"]["content"]
        .as_str()
        .unwrap_or_default()
        .trim()
        .to_string())
}

// ---------- Persistenz (postprocessings.json im App-Verzeichnis) ----------

fn pp_path() -> PathBuf {
    config::app_dir().join("postprocessings.json")
}

pub fn load_all() -> Vec<PostProcessing> {
    std::fs::read_to_string(pp_path())
        .ok()
        .and_then(|raw| serde_json::from_str(&raw).ok())
        .unwrap_or_default()
}

fn save_all(list: &[PostProcessing]) -> Result<()> {
    std::fs::write(pp_path(), serde_json::to_string_pretty(list)?)?;
    Ok(())
}

pub fn upsert(mut pp: PostProcessing) -> Result<PostProcessing> {
    let mut all = load_all();
    if pp.uuid.is_empty() {
        pp.uuid = uuid::Uuid::new_v4().to_string();
    }
    if let Some(pos) = all.iter().position(|p| p.uuid == pp.uuid) {
        all[pos] = pp.clone();
    } else {
        all.push(pp.clone());
    }
    save_all(&all)?;
    Ok(pp)
}

pub fn delete(uuid: &str) -> Result<()> {
    let mut all = load_all();
    all.retain(|p| p.uuid != uuid);
    save_all(&all)
}
