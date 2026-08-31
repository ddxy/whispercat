mod clipboard;
mod config;
mod hotkey_ipc;
mod hotkeys;
mod postprocess;
mod recorder;
mod runs;
mod screenshot;
mod transcribe;
mod tray;

use config::Config;
use postprocess::PostProcessing;
use runs::{Run, RunStore};
use tauri::{Emitter, Manager, State};

pub struct AppState {
    cfg: std::sync::Mutex<Config>,
    rec: recorder::RecorderHandle,
    runs: RunStore,
}

impl AppState {
    pub(crate) fn config(&self) -> Config {
        match self.cfg.lock() {
            Ok(g) => g.clone(),
            Err(poisoned) => poisoned.into_inner().clone(),
        }
    }

    pub(crate) fn set_selected_postprocessing(
        &self,
        selected: Option<String>,
    ) -> Result<(), String> {
        let mut cfg = self.cfg.lock().map_err(|error| error.to_string())?;
        cfg.selected_postprocessing = selected;
        config::save(&cfg).map_err(|error| error.to_string())
    }
}

#[tauri::command]
fn start_recording(state: State<AppState>) -> Result<bool, String> {
    let cfg = state.config();
    state.rec.start(
        cfg.mic_name.filter(|mic| !mic.is_empty()),
        cfg.system_audio_enabled,
        cfg.system_audio_source.filter(|source| !source.is_empty()),
        cfg.mic_gain,
        cfg.system_audio_gain,
    )?;
    Ok(cfg.system_audio_enabled)
}

#[tauri::command]
async fn stop_recording(state: State<'_, AppState>) -> Result<recorder::Recording, String> {
    let recorder = state.rec.clone();
    tokio::task::spawn_blocking(move || recorder.stop())
        .await
        .map_err(|error| error.to_string())?
}

#[tauri::command]
fn discard_recording(state: State<AppState>) -> Result<(), String> {
    state.rec.discard()
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
async fn copy_text(text: String) -> Result<(), String> {
    clipboard::copy_and_maybe_paste(&text, false).await
}

#[tauri::command]
async fn queue_run(
    state: State<'_, AppState>,
    recording: recorder::Recording,
    workflow: Option<PostProcessing>,
) -> Result<Run, String> {
    let screenshot_session = match workflow.as_ref() {
        Some(workflow) => postprocess::prepare_screenshot_session(workflow)
            .await
            .map_err(|error| error.to_string())?,
        None => None,
    };
    let recording_dir = recording.session_dir().to_path_buf();
    let run = state
        .runs
        .create(
            workflow.as_ref(),
            String::new(),
            Some(recording_dir.clone()),
        )
        .map_err(|error| error.to_string())?;
    let cfg = state.config();
    let runs = state.runs.clone();
    let run_id = run.id.clone();

    tokio::spawn(async move {
        let outcome = async {
            let finalized = tokio::task::spawn_blocking(move || recorder::finalize(recording))
                .await
                .map_err(anyhow::Error::from)??;
            let transcription = transcribe::transcribe_files(&cfg, finalized.chunks()).await;
            let transcript = transcription?;
            if let Err(error) = std::fs::write(recording_dir.join("transcript.txt"), &transcript) {
                tracing::warn!("Could not save transcript in recording folder: {error}");
            }
            runs.set_transcript(&run_id, transcript.clone())?;
            let result = match workflow {
                Some(workflow) => {
                    let progress_runs = runs.clone();
                    let progress_run_id = run_id.clone();
                    let progress = move |path: &[usize], progress| match progress {
                        postprocess::StepProgress::Started => {
                            if let Err(error) = progress_runs.start_step(&progress_run_id, path) {
                                tracing::warn!("Could not save workflow step start: {error}");
                            }
                        }
                        postprocess::StepProgress::Completed(output) => {
                            if let Err(error) =
                                progress_runs.complete_step(&progress_run_id, path, output)
                            {
                                tracing::warn!("Could not save workflow step output: {error}");
                            }
                        }
                    };
                    postprocess::apply_with_screenshot_session(
                        &cfg,
                        &workflow,
                        &transcript,
                        screenshot_session.as_ref(),
                        Some(&progress),
                    )
                    .await?
                }
                None => transcript,
            };
            runs.complete(&run_id, result.clone())?;
            if let Err(error) = std::fs::write(recording_dir.join("result.txt"), &result) {
                tracing::warn!("Could not save result in recording folder: {error}");
            }
            if let Err(error) = clipboard::copy_and_maybe_paste(&result, cfg.auto_paste).await {
                tracing::warn!("Could not copy completed recording to clipboard: {error}");
            }
            Ok::<(), anyhow::Error>(())
        }
        .await;

        if let Some(session) = screenshot_session {
            session.close().await;
        }

        if let Err(error) = outcome {
            if let Err(store_error) = runs.fail(&run_id, error.to_string()) {
                tracing::warn!("Could not save failed history run: {store_error}");
            }
        }
    });

    Ok(run)
}

#[tauri::command]
async fn queue_text_run(
    state: State<'_, AppState>,
    text: String,
    workflow: PostProcessing,
) -> Result<Run, String> {
    let screenshot_session = postprocess::prepare_screenshot_session(&workflow)
        .await
        .map_err(|error| error.to_string())?;
    let run = state
        .runs
        .create(Some(&workflow), text.clone(), None)
        .map_err(|error| error.to_string())?;
    let cfg = state.config();
    let runs = state.runs.clone();
    let run_id = run.id.clone();

    tokio::spawn(async move {
        let outcome = async {
            let progress_runs = runs.clone();
            let progress_run_id = run_id.clone();
            let progress = move |path: &[usize], progress| match progress {
                postprocess::StepProgress::Started => {
                    if let Err(error) = progress_runs.start_step(&progress_run_id, path) {
                        tracing::warn!("Could not save workflow step start: {error}");
                    }
                }
                postprocess::StepProgress::Completed(output) => {
                    if let Err(error) = progress_runs.complete_step(&progress_run_id, path, output) {
                        tracing::warn!("Could not save workflow step output: {error}");
                    }
                }
            };
            let result = postprocess::apply_with_screenshot_session(
                &cfg,
                &workflow,
                &text,
                screenshot_session.as_ref(),
                Some(&progress),
            )
            .await?;
            runs.complete(&run_id, result.clone())?;
            if let Err(error) = clipboard::copy_and_maybe_paste(&result, cfg.auto_paste).await {
                tracing::warn!("Could not copy completed workflow output to clipboard: {error}");
            }
            Ok::<(), anyhow::Error>(())
        }
        .await;

        if let Some(session) = screenshot_session {
            session.close().await;
        }

        if let Err(error) = outcome {
            if let Err(store_error) = runs.fail(&run_id, error.to_string()) {
                tracing::warn!("Could not save failed history run: {store_error}");
            }
        }
    });

    Ok(run)
}

#[tauri::command]
fn list_runs(state: State<AppState>) -> Vec<Run> {
    state.runs.list()
}

#[tauri::command]
fn clear_runs(state: State<AppState>) -> Result<(), String> {
    state.runs.clear().map_err(|error| error.to_string())
}

#[tauri::command]
fn open_recording_folder(state: State<AppState>, id: String) -> Result<(), String> {
    state
        .runs
        .open_recording_dir(&id)
        .map_err(|error| error.to_string())
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
    let previous = state.config();
    if previous.hotkey != cfg.hotkey {
        hotkeys::register(&app, &cfg.hotkey)?;
    }
    if let Err(error) = config::save(&cfg) {
        if previous.hotkey != cfg.hotkey {
            let _ = hotkeys::register(&app, &previous.hotkey);
        }
        return Err(error.to_string());
    }
    *state.cfg.lock().map_err(|e| e.to_string())? = cfg;
    Ok(())
}

#[tauri::command]
fn list_postprocessings() -> Vec<PostProcessing> {
    postprocess::load_all()
}

#[tauri::command]
fn upsert_postprocessing(
    app: tauri::AppHandle,
    pp: PostProcessing,
) -> Result<PostProcessing, String> {
    let saved = postprocess::upsert(pp).map_err(|error| error.to_string())?;
    tray::refresh_postprocessing_menu(&app).map_err(|error| error.to_string())?;
    Ok(saved)
}

#[tauri::command]
fn delete_postprocessing(
    app: tauri::AppHandle,
    state: State<AppState>,
    uuid: String,
) -> Result<(), String> {
    postprocess::delete(&uuid).map_err(|error| error.to_string())?;
    if state.config().selected_postprocessing.as_deref() == Some(uuid.as_str()) {
        state.set_selected_postprocessing(None)?;
        app.emit("selected-postprocessing-changed", Option::<String>::None)
            .map_err(|error| error.to_string())?;
    }
    tray::refresh_postprocessing_menu(&app).map_err(|error| error.to_string())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    if hotkey_ipc::is_toggle_invocation() {
        if let Err(error) = hotkey_ipc::send_toggle() {
            eprintln!("{error}");
        }
        return;
    }

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
            runs: RunStore::load(),
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
            .inner_size(1180.0, 820.0)
            .min_inner_size(900.0, 640.0)
            .build()?;

            // Schließen -> in den Tray minimieren statt beenden
            let win_for_event = window.clone();
            window.on_window_event(move |event| {
                if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                    api.prevent_close();
                    let _ = win_for_event.hide();
                }
            });

            if let Err(error) = hotkey_ipc::setup(app.handle().clone()) {
                tracing::warn!("Local hotkey signaling unavailable: {error}");
            }

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
            discard_recording,
            transcribe_audio,
            postprocess_text,
            copy_text,
            queue_run,
            queue_text_run,
            list_runs,
            clear_runs,
            open_recording_folder,
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
