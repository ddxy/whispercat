use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::PathBuf;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct Config {
    /// "openai" | "faster-whisper" | "open-webui"
    pub whisper_server: String,
    pub api_key: String,
    pub faster_url: String,
    pub faster_model: String,
    pub faster_language: String,
    pub owui_url: String,
    pub owui_key: String,
    pub custom_ai_url: String,
    pub custom_ai_key: String,
    pub n8n_url: String,
    pub n8n_token: String,
    pub mic_name: Option<String>,
    pub system_audio_enabled: bool,
    pub system_audio_source: Option<String>,
    pub mic_gain: f32,
    pub system_audio_gain: f32,
    pub selected_postprocessing: Option<String>,
    pub hotkey: String,
    pub auto_paste: bool,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            whisper_server: "openai".to_string(),
            api_key: String::new(),
            faster_url: "http://localhost:8000".to_string(),
            faster_model: "Systran/faster-whisper-base".to_string(),
            faster_language: String::new(),
            owui_url: String::new(),
            owui_key: String::new(),
            custom_ai_url: String::new(),
            custom_ai_key: String::new(),
            n8n_url: String::new(),
            n8n_token: String::new(),
            mic_name: None,
            system_audio_enabled: false,
            system_audio_source: None,
            mic_gain: 1.0,
            system_audio_gain: 1.0,
            selected_postprocessing: None,
            hotkey: "Ctrl+Shift+R".to_string(),
            auto_paste: true,
        }
    }
}

/// Plattformgerechter Konfigurationsordner:
/// Linux: ~/.config/whispercat, macOS: ~/Library/Application Support/whispercat, Windows: %APPDATA%/whispercat
pub fn app_dir() -> PathBuf {
    let dir = dirs::config_dir()
        .unwrap_or_else(|| PathBuf::from("."))
        .join("whispercat");
    let _ = std::fs::create_dir_all(&dir);
    dir
}

fn config_path() -> PathBuf {
    app_dir().join("config.json")
}

pub fn save(cfg: &Config) -> anyhow::Result<()> {
    std::fs::write(config_path(), serde_json::to_string_pretty(cfg)?)?;
    Ok(())
}

pub fn load() -> Config {
    // 1. Bestehende JSON-Konfiguration laden
    if let Ok(raw) = std::fs::read_to_string(config_path()) {
        if let Ok(cfg) = serde_json::from_str::<Config>(&raw) {
            return cfg;
        }
        tracing::warn!("config.json konnte nicht gelesen werden, verwende Defaults.");
    }
    // 2. Migration aus dem alten Java-Projekt (config.properties)
    if let Some(cfg) = migrate_legacy() {
        tracing::info!("Alte config.properties migriert -> config.json");
        if let Err(e) = save(&cfg) {
            tracing::warn!("Migrierte Config konnte nicht gespeichert werden: {e}");
        }
        return cfg;
    }
    Config::default()
}

fn legacy_config_path() -> Option<PathBuf> {
    match std::env::consts::OS {
        "windows" => Some(
            PathBuf::from(std::env::var("APPDATA").ok()?)
                .join("WhisperCat")
                .join("config.properties"),
        ),
        "macos" => dirs::home_dir()
            .map(|h| h.join("Library/Application Support/WhisperCat/config.properties")),
        _ => dirs::home_dir().map(|h| h.join("WhisperCat/.config/config.properties")),
    }
}

/// Migriert die Java-Properties-Datei des alten Projekts.
/// Der alte Hotkey (JNativeHook-Keycodes) wird bewusst NICHT migriert —
/// bitte einmalig neu einstellen.
fn migrate_legacy() -> Option<Config> {
    let path = legacy_config_path()?;
    let raw = std::fs::read_to_string(path).ok()?;

    let mut map: HashMap<String, String> = HashMap::new();
    for line in raw.lines() {
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') || line.starts_with('!') {
            continue;
        }
        if let Some((k, v)) = line.split_once('=') {
            map.insert(k.trim().to_string(), v.trim().to_string());
        }
    }
    if map.is_empty() {
        return None;
    }

    let get = |key: &str| map.get(key).cloned().unwrap_or_default();

    let mut cfg = Config::default();
    cfg.api_key = get("apiKey");
    cfg.whisper_server = match get("whisperServer")
        .to_lowercase()
        .replace(' ', "-")
        .as_str()
    {
        "faster-whisper" => "faster-whisper".to_string(),
        "open-webui" | "openwebui" => "open-webui".to_string(),
        _ => "openai".to_string(),
    };
    let fw_url = get("fasterWhisperServerUrl");
    if !fw_url.is_empty() {
        cfg.faster_url = fw_url;
    }
    let fw_model = get("fasterWhisperModel");
    if !fw_model.is_empty() {
        cfg.faster_model = fw_model;
    }
    cfg.faster_language = get("fasterWhisperLanguage");
    cfg.owui_url = get("openWebUIServerUrl");
    cfg.owui_key = get("openWebUIApiKey");
    cfg.auto_paste = get("autoPaste") != "false";
    let mic = get("selectedMicrophone");
    if !mic.is_empty() {
        cfg.mic_name = Some(mic);
    }
    Some(cfg)
}
