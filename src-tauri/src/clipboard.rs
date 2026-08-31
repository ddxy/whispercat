use std::{process::{Command, Stdio}, sync::{Mutex, OnceLock}};

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
        set_clipboard_text(&text)?;
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

fn set_clipboard_text(text: &str) -> Result<(), String> {
    #[cfg(target_os = "linux")]
    if std::env::var_os("WAYLAND_DISPLAY").is_some() {
        let mut child = Command::new("wl-copy")
            .stdin(Stdio::piped())
            .spawn()
            .map_err(|error| format!("Clipboard (wl-copy): {error}"))?;
        use std::io::Write;
        child
            .stdin
            .take()
            .ok_or_else(|| "Clipboard (wl-copy): stdin unavailable".to_string())?
            .write_all(text.as_bytes())
            .map_err(|error| format!("Clipboard (wl-copy): {error}"))?;
        let status = child
            .wait()
            .map_err(|error| format!("Clipboard (wl-copy): {error}"))?;
        if status.success() {
            return Ok(());
        }
        return Err(format!("Clipboard (wl-copy) exited with {status}"));
    }

    let cb = clipboard()?;
    let mut guard = cb.lock().map_err(|error| error.to_string())?;
    guard
        .set_text(text)
        .map_err(|error| format!("Clipboard: {error}"))
}

fn simulate_paste() -> Result<(), String> {
    use enigo::{Direction, Enigo, Key, Keyboard, Settings};
    let mut enigo =
        Enigo::new(&Settings::default()).map_err(|e| format!("Auto-Paste (enigo): {e}"))?;
    // macOS: Cmd+V, sonst: Ctrl+V
    let modifier = if cfg!(target_os = "macos") {
        Key::Meta
    } else {
        Key::Control
    };
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
