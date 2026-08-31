use crate::{
    config::{self, Config},
    screenshot::{Screenshot, ScreenshotSession},
};
use anyhow::{Context, Result};
use reqwest::multipart;
use serde::{Deserialize, Serialize};
use std::future::Future;
use std::path::PathBuf;
use std::pin::Pin;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

const MAX_STEP_EXECUTIONS: u32 = 1_000;
const MAX_REPEAT_INTERVAL_SECONDS: u64 = 86_400;

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
        #[serde(default, skip_serializing_if = "Option::is_none")]
        repeat: Option<Repeat>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        repeat_interval_seconds: Option<u64>,
        provider: String, // "openai" | "open-webui"
        model: String,
        system_prompt: String,
        user_prompt: String, // darf {{input}} enthalten
    },
    Replace {
        #[serde(default, skip_serializing_if = "Option::is_none")]
        repeat: Option<Repeat>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        repeat_interval_seconds: Option<u64>,
        from: String,
        to: String,
    },
    N8n {
        #[serde(default, skip_serializing_if = "Option::is_none")]
        repeat: Option<Repeat>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        repeat_interval_seconds: Option<u64>,
        path: String,
    },
    ScreenshotWebhook {
        #[serde(default, skip_serializing_if = "Option::is_none")]
        repeat: Option<Repeat>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        repeat_interval_seconds: Option<u64>,
        path: String,
    },
    Screenshot {
        #[serde(default, skip_serializing_if = "Option::is_none")]
        repeat: Option<Repeat>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        repeat_interval_seconds: Option<u64>,
        #[serde(flatten)]
        target: ScreenshotTarget,
    },
    Group {
        #[serde(default, skip_serializing_if = "Option::is_none")]
        repeat: Option<Repeat>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        repeat_interval_seconds: Option<u64>,
        #[serde(default)]
        title: String,
        #[serde(default)]
        steps: Vec<Step>,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "target", rename_all = "lowercase")]
pub enum ScreenshotTarget {
    Webhook { path: String },
    Folder { folder: String },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(untagged)]
pub enum Repeat {
    Count(u32),
    Infinite(String),
}

// ---------- Ablauf ----------

pub async fn apply(cfg: &Config, pp: &PostProcessing, input: &str) -> Result<String> {
    let screenshot_session = prepare_screenshot_session(pp).await?;
    let result = apply_with_screenshot_session(cfg, pp, input, screenshot_session.as_ref()).await;
    if let Some(session) = screenshot_session {
        session.close().await;
    }
    result
}

pub async fn prepare_screenshot_session(pp: &PostProcessing) -> Result<Option<ScreenshotSession>> {
    if contains_screenshot_step(&pp.steps) {
        Ok(Some(ScreenshotSession::start().await?))
    } else {
        Ok(None)
    }
}

pub async fn apply_with_screenshot_session(
    cfg: &Config,
    pp: &PostProcessing,
    input: &str,
    screenshot_session: Option<&ScreenshotSession>,
) -> Result<String> {
    let mut text = input.to_string();
    let mut executions = 0;
    for step in &pp.steps {
        text = apply_step(cfg, step, text, &mut executions, screenshot_session).await?;
    }
    Ok(text)
}

fn contains_screenshot_step(steps: &[Step]) -> bool {
    steps.iter().any(|step| match step {
        Step::ScreenshotWebhook { .. } | Step::Screenshot { .. } => true,
        Step::Group { steps, .. } => contains_screenshot_step(steps),
        _ => false,
    })
}

fn apply_step<'a>(
    cfg: &'a Config,
    step: &'a Step,
    input: String,
    executions: &'a mut u32,
    screenshot_session: Option<&'a ScreenshotSession>,
) -> Pin<Box<dyn Future<Output = Result<String>> + Send + 'a>> {
    Box::pin(async move {
        let repeat = repeat_count(step)?;
        let repeat_interval = repeat_interval(step)?;
        let mut text = input;
        let mut pass = 0;

        loop {
            if let Some(count) = repeat {
                if pass >= count {
                    return Ok(text);
                }
            }

            let previous = text.clone();
            text = apply_step_once(cfg, step, text, executions, screenshot_session).await?;
            pass += 1;

            // Infinite repeats converge when a complete pass leaves text unchanged.
            if repeat.is_none() && text == previous {
                return Ok(text);
            }

            let has_next_pass = repeat.is_none_or(|count| pass < count);
            if has_next_pass {
                if let Some(interval) = repeat_interval {
                    tokio::time::sleep(interval).await;
                }
            }
        }
    })
}

async fn apply_step_once(
    cfg: &Config,
    step: &Step,
    text: String,
    executions: &mut u32,
    screenshot_session: Option<&ScreenshotSession>,
) -> Result<String> {
    match step {
        Step::Group { steps, .. } => {
            if steps.is_empty() {
                anyhow::bail!("Workflow groups must contain at least one step.");
            }
            let mut text = text;
            for step in steps {
                text = apply_step(cfg, step, text, executions, screenshot_session).await?;
            }
            Ok(text)
        }
        Step::Replace { from, to, .. } => {
            count_execution(executions)?;
            Ok(text.replace(from.as_str(), to))
        }
        Step::Prompt {
            provider,
            model,
            system_prompt,
            user_prompt,
            ..
        } => {
            count_execution(executions)?;
            let user = user_prompt.replace("{{input}}", &text);
            chat(cfg, provider, model, system_prompt, &user).await
        }
        Step::N8n { path, .. } => {
            count_execution(executions)?;
            n8n(cfg, path, &text).await
        }
        Step::ScreenshotWebhook { path, .. } => {
            count_execution(executions)?;
            let screenshots = screenshot_session
                .context("Screenshot workflow step has no active Wayland sharing session")?
                .capture()
                .await?;
            screenshot_webhook(cfg, path, &text, screenshots).await?;
            Ok(text)
        }
        Step::Screenshot { target, .. } => {
            count_execution(executions)?;
            let screenshots = screenshot_session
                .context("Screenshot workflow step has no active Wayland sharing session")?
                .capture()
                .await?;
            match target {
                ScreenshotTarget::Webhook { path } => {
                    screenshot_webhook(cfg, path, &text, screenshots).await?;
                }
                ScreenshotTarget::Folder { folder } => save_screenshots(folder, screenshots)?,
            }
            Ok(text)
        }
    }
}

fn repeat_count(step: &Step) -> Result<Option<u32>> {
    let repeat = match step {
        Step::Prompt { repeat, .. }
        | Step::Replace { repeat, .. }
        | Step::N8n { repeat, .. }
        | Step::ScreenshotWebhook { repeat, .. }
        | Step::Screenshot { repeat, .. }
        | Step::Group { repeat, .. } => repeat.as_ref(),
    };

    match repeat {
        None => Ok(Some(1)),
        Some(Repeat::Count(count @ 1..=MAX_STEP_EXECUTIONS)) => Ok(Some(*count)),
        Some(Repeat::Count(_)) => {
            anyhow::bail!("Repeat count must be between 1 and {MAX_STEP_EXECUTIONS}.")
        }
        Some(Repeat::Infinite(value)) if value == "infinite" => Ok(None),
        Some(Repeat::Infinite(_)) => anyhow::bail!("Repeat mode must be a number or 'infinite'."),
    }
}

fn repeat_interval(step: &Step) -> Result<Option<Duration>> {
    let seconds = match step {
        Step::Prompt {
            repeat_interval_seconds,
            ..
        }
        | Step::Replace {
            repeat_interval_seconds,
            ..
        }
        | Step::N8n {
            repeat_interval_seconds,
            ..
        }
        | Step::ScreenshotWebhook {
            repeat_interval_seconds,
            ..
        }
        | Step::Screenshot {
            repeat_interval_seconds,
            ..
        }
        | Step::Group {
            repeat_interval_seconds,
            ..
        } => *repeat_interval_seconds,
    };

    match seconds {
        None => Ok(None),
        Some(seconds @ 1..=MAX_REPEAT_INTERVAL_SECONDS) => Ok(Some(Duration::from_secs(seconds))),
        Some(_) => anyhow::bail!(
            "Repeat interval must be between 1 and {MAX_REPEAT_INTERVAL_SECONDS} seconds."
        ),
    }
}

fn count_execution(executions: &mut u32) -> Result<()> {
    if *executions >= MAX_STEP_EXECUTIONS {
        anyhow::bail!("Workflow repeat limit of {MAX_STEP_EXECUTIONS} reached.");
    }
    *executions += 1;
    Ok(())
}

async fn n8n(cfg: &Config, path: &str, text: &str) -> Result<String> {
    let base_url = cfg.n8n_url.trim().trim_end_matches('/');
    if base_url.is_empty() {
        anyhow::bail!("n8n instance URL is not configured.");
    }

    let path = path.trim();
    if !path.starts_with('/') {
        anyhow::bail!("n8n webhook path must start with '/'.");
    }

    let url = format!("{base_url}{path}");
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(30))
        .build()
        .context("Failed to create n8n HTTP client")?;
    let mut req = client.post(&url).json(&serde_json::json!({ "text": text }));
    if !cfg.n8n_token.is_empty() {
        req = req.bearer_auth(&cfg.n8n_token);
    }

    let res = req.send().await.context("n8n webhook request failed")?;
    let status = res.status();
    let body = res
        .text()
        .await
        .context("Failed to read n8n webhook response")?;
    if !status.is_success() {
        anyhow::bail!("n8n webhook failed ({status}): {body}");
    }

    let json: serde_json::Value =
        serde_json::from_str(&body).context("n8n webhook returned invalid JSON")?;
    json["text"]
        .as_str()
        .map(str::to_string)
        .context("n8n webhook response must contain a string 'text' field")
}

async fn screenshot_webhook(
    cfg: &Config,
    path: &str,
    text: &str,
    screenshots: Vec<Screenshot>,
) -> Result<()> {
    let url = n8n_url(cfg, path)?;
    let mut form = multipart::Form::new().text("text", text.to_string());
    for screenshot in screenshots {
        form = form.part(
            "screenshots",
            multipart::Part::bytes(screenshot.bytes)
                .file_name(screenshot.filename)
                .mime_str("image/png")?,
        );
    }

    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(30))
        .build()
        .context("Failed to create n8n HTTP client")?;
    let mut req = client.post(url).multipart(form);
    if !cfg.n8n_token.is_empty() {
        req = req.bearer_auth(&cfg.n8n_token);
    }
    let res = req
        .send()
        .await
        .context("n8n screenshot webhook request failed")?;
    let status = res.status();
    let body = res
        .text()
        .await
        .context("Failed to read n8n screenshot webhook response")?;
    if !status.is_success() {
        anyhow::bail!("n8n screenshot webhook failed ({status}): {body}");
    }
    Ok(())
}

fn save_screenshots(folder: &str, screenshots: Vec<Screenshot>) -> Result<()> {
    let folder = folder.trim();
    if folder.is_empty() {
        anyhow::bail!("Screenshot output folder is not configured.");
    }

    let folder = PathBuf::from(folder);
    std::fs::create_dir_all(&folder).with_context(|| {
        format!(
            "Could not create screenshot output folder '{}'",
            folder.display()
        )
    })?;
    let timestamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .context("System clock is before Unix epoch")?
        .as_millis();

    for screenshot in screenshots {
        let path = folder.join(format!("{timestamp}_{}", screenshot.filename));
        std::fs::write(&path, screenshot.bytes)
            .with_context(|| format!("Could not save screenshot to '{}'", path.display()))?;
    }
    Ok(())
}

fn n8n_url(cfg: &Config, path: &str) -> Result<String> {
    let base_url = cfg.n8n_url.trim().trim_end_matches('/');
    if base_url.is_empty() {
        anyhow::bail!("n8n instance URL is not configured.");
    }
    let path = path.trim();
    if !path.starts_with('/') {
        anyhow::bail!("n8n webhook path must start with '/'.");
    }
    Ok(format!("{base_url}{path}"))
}

async fn chat(
    cfg: &Config,
    provider: &str,
    model: &str,
    system_prompt: &str,
    user_prompt: &str,
) -> Result<String> {
    let provider = provider.to_lowercase().replace(' ', "-");
    let (url, key) = match provider.as_str() {
        "open-webui" | "openwebui" => (
            format!(
                "{}/api/chat/completions",
                cfg.owui_url.trim().trim_end_matches('/')
            ),
            cfg.owui_key.clone(),
        ),
        "custom" => {
            let url = cfg.custom_ai_url.trim();
            if url.is_empty() {
                anyhow::bail!("Custom AI chat completions URL is not configured.");
            }
            (url.to_string(), cfg.custom_ai_key.clone())
        }
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
    let res = req.send().await.context("AI provider request failed")?;
    let status = res.status();
    let body_text = res
        .text()
        .await
        .context("Failed to read AI provider response")?;
    if !status.is_success() {
        anyhow::bail!("AI provider request failed ({status}): {body_text}");
    }
    let json: serde_json::Value =
        serde_json::from_str(&body_text).context("AI provider returned invalid JSON")?;
    json["choices"][0]["message"]["content"]
        .as_str()
        .map(|content| content.trim().to_string())
        .context("AI provider response must contain choices[0].message.content")
}

// ---------- Persistenz (postprocessings.json im App-Verzeichnis) ----------

fn pp_path() -> PathBuf {
    config::app_dir().join("postprocessings.json")
}

pub fn load_all() -> Vec<PostProcessing> {
    let mut list: Vec<PostProcessing> = std::fs::read_to_string(pp_path())
        .ok()
        .and_then(|raw| serde_json::from_str(&raw).ok())
        .unwrap_or_default();
    if list
        .iter_mut()
        .any(|postprocessing| migrate_legacy_screenshot_steps(&mut postprocessing.steps))
    {
        if let Err(error) = save_all(&list) {
            tracing::warn!("Could not save migrated screenshot workflows: {error}");
        }
    }
    list
}

fn migrate_legacy_screenshot_steps(steps: &mut [Step]) -> bool {
    let mut changed = false;
    for step in steps {
        match step {
            Step::ScreenshotWebhook {
                repeat,
                repeat_interval_seconds,
                path,
            } => {
                let target = ScreenshotTarget::Webhook {
                    path: std::mem::take(path),
                };
                *step = Step::Screenshot {
                    repeat: repeat.take(),
                    repeat_interval_seconds: repeat_interval_seconds.take(),
                    target,
                };
                changed = true;
            }
            Step::Group { steps, .. } => changed |= migrate_legacy_screenshot_steps(steps),
            _ => {}
        }
    }
    changed
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
