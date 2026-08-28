mod clipboard;
mod config;
mod hotkeys;
mod postprocess;
mod recorder;
mod transcribe;
mod tray;

use config::Config;
use postprocess::PostProcessing;
use tauri::{Emitter, Manager, State};

pub struct AppState {
    cfg: std::sync::Mutex<Config>,
    rec: recorder::RecorderHandle,
}

impl AppState {
    fn config(&self) -> Config {
        match self.cfg.lock() {
            Ok(g) => g.clone(),
            Err(poisoned) => poisoned.into_inner().clone(),
        }
    }
}

#[tauri::command]
fn start_recording(state: State<AppState>) -> Result<(), String> {
    let cfg = state.config();
    state.rec.start(
        cfg.mic_name.filter(|mic| !mic.is_empty()),
        cfg.system_audio_enabled,
        cfg.system_audio_source.filter(|source| !source.is_empty()),
        cfg.mic_gain,
        cfg.system_audio_gain,
    )
}

#[tauri::command]
fn stop_recording(state: State<AppState>) -> Result<String, String> {
    let path = state.rec.stop()?;
    Ok(path.to_string_lossy().to_string())
}

#[tauri::command]
async fn transcribe_audio(state: State<'_, AppState>, path: String) -> Result<String, String> {
    let cfg = state.config();
    transcribe::transcribe(&cfg, &path)
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
async fn postprocess_text(
    state: State<'_, AppState>,
    pp: PostProcessing,
    text: String,
) -> Result<String, String> {
    let cfg = state.config();
    postprocess::apply(&cfg, &pp, &text)
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
async fn paste_text(state: State<'_, AppState>, text: String) -> Result<(), String> {
    let auto_paste = state.config().auto_paste;
    clipboard::copy_and_maybe_paste(&text, auto_paste).await
}

#[tauri::command]
fn list_input_devices() -> Vec<String> {
    recorder::list_input_devices()
}

#[tauri::command]
fn list_system_audio_sources() -> Vec<String> {
    recorder::list_system_audio_sources()
}

#[tauri::command]
fn detect_default_input_device() -> Option<String> {
    recorder::detect_default_input_device()
}

#[tauri::command]
fn detect_active_system_audio_source() -> Option<String> {
    recorder::detect_active_system_audio_source()
}

#[tauri::command]
fn get_config(state: State<AppState>) -> Config {
    state.config()
}

#[tauri::command]
fn save_config(app: tauri::AppHandle, state: State<AppState>, cfg: Config) -> Result<(), String> {
    config::save(&cfg).map_err(|e| e.to_string())?;
    *state.cfg.lock().map_err(|e| e.to_string())? = cfg.clone();
    // Hotkey direkt neu registrieren
    if let Err(e) = hotkeys::register(&app, &cfg.hotkey) {
        tracing::warn!("Hotkey nach Speichern nicht registrierbar: {e}");
        return Err(e);
    }
    Ok(())
}

#[tauri::command]
fn list_postprocessings() -> Vec<PostProcessing> {
    postprocess::load_all()
}

#[tauri::command]
fn upsert_postprocessing(pp: PostProcessing) -> Result<PostProcessing, String> {
    postprocess::upsert(pp).map_err(|e| e.to_string())
}

#[tauri::command]
fn delete_postprocessing(uuid: String) -> Result<(), String> {
    postprocess::delete(&uuid).map_err(|e| e.to_string())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tracing_subscriber::fmt()
        .with_env_filter(tracing_subscriber::EnvFilter::new(
            std::env::var("RUST_LOG").unwrap_or_else(|_| "info".to_string()),
        ))
        .init();

    let cfg = config::load();

    tauri::Builder::default()
        .plugin(
            tauri_plugin_global_shortcut::Builder::new()
                .with_handler(|app, _shortcut, event| {
                    if matches!(
                        event.state,
                        tauri_plugin_global_shortcut::ShortcutState::Pressed
                    ) {
                        let _ = app.emit("hotkey-toggle", ());
                    }
                })
                .build(),
        )
        .manage(AppState {
            cfg: std::sync::Mutex::new(cfg),
            rec: recorder::RecorderHandle::new(),
        })
        .setup(|app| {
            // Hauptfenster programmatisch anlegen (Config-Fenster werden erst nach setup() erstellt,
            // wir brauchen es aber schon hier für den Tray-/Close-Handler).
            let window = tauri::WebviewWindowBuilder::new(
                app,
                "main",
                tauri::WebviewUrl::App("index.html".into()),
            )
            .title("WhisperCat")
            .inner_size(920.0, 680.0)
            .min_inner_size(700.0, 520.0)
            .build()?;

            // Schließen -> in den Tray minimieren statt beenden
            let win_for_event = window.clone();
            window.on_window_event(move |event| {
                if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                    api.prevent_close();
                    let _ = win_for_event.hide();
                }
            });

            if let Err(e) = tray::setup(app) {
                tracing::warn!("System-Tray nicht verfügbar: {e}");
            }

            let hotkey = app.state::<AppState>().config().hotkey;
            if let Err(e) = hotkeys::register(&app.handle(), &hotkey) {
                tracing::warn!("Globaler Hotkey nicht registrierbar: {e}");
            }

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            start_recording,
            stop_recording,
            transcribe_audio,
            postprocess_text,
            paste_text,
            list_input_devices,
            list_system_audio_sources,
            detect_default_input_device,
            detect_active_system_audio_source,
            get_config,
            save_config,
            list_postprocessings,
            upsert_postprocessing,
            delete_postprocessing
        ])
        .run(tauri::generate_context!())
        .expect("Fehler beim Start der Tauri-Anwendung");
}
