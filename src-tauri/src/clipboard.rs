use std::sync::{Mutex, OnceLock};

/// arboard-Clipboard als statische Instanz: Auf X11 muss die Instance leben
/// bleiben, sonst verliert der Inhalt seine Eigentümerschaft.
static CLIPBOARD: OnceLock<Mutex<arboard::Clipboard>> = OnceLock::new();

fn clipboard() -> Result<&'static Mutex<arboard::Clipboard>, String> {
    if let Some(c) = CLIPBOARD.get() {
        return Ok(c);
    }
    let cb = arboard::Clipboard::new().map_err(|e| format!("Zwischenablage: {e}"))?;
    Ok(CLIPBOARD.get_or_init(|| Mutex::new(cb)))
}

pub async fn copy_and_maybe_paste(text: &str, auto_paste: bool) -> Result<(), String> {
    let text = text.to_string();
    tokio::task::spawn_blocking(move || {
        {
            let cb = clipboard()?;
            let mut guard = cb.lock().map_err(|e| e.to_string())?;
            guard.set_text(text).map_err(|e| format!("Zwischenablage: {e}"))?;
        }
        if auto_paste {
            // Kurze Pause, damit die Zielanwendung Fokus/Clipboard "merkt"
            std::thread::sleep(std::time::Duration::from_millis(250));
            simulate_paste()?;
        }
        Ok::<(), String>(())
    })
    .await
    .map_err(|e| e.to_string())??;
    Ok(())
}

fn simulate_paste() -> Result<(), String> {
    use enigo::{Direction, Enigo, Key, Keyboard, Settings};
    let mut enigo =
        Enigo::new(&Settings::default()).map_err(|e| format!("Auto-Paste (enigo): {e}"))?;
    // macOS: Cmd+V, sonst: Ctrl+V
    let modifier = if cfg!(target_os = "macos") { Key::Meta } else { Key::Control };
    enigo
        .key(modifier, Direction::Press)
        .map_err(|e| format!("Auto-Paste: {e}"))?;
    enigo
        .key(Key::Unicode('v'), Direction::Click)
        .map_err(|e| format!("Auto-Paste: {e}"))?;
    enigo
        .key(modifier, Direction::Release)
        .map_err(|e| format!("Auto-Paste: {e}"))?;
    Ok(())
}
