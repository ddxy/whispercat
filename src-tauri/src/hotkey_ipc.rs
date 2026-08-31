#[cfg(target_os = "linux")]
use std::{
    io,
    os::{
        unix::{fs::{FileTypeExt, MetadataExt, PermissionsExt}, net::{UnixListener, UnixStream}},
    },
    path::PathBuf,
};

#[cfg(target_os = "linux")]
use tauri::{AppHandle, Emitter};

#[cfg(target_os = "linux")]
fn socket_path() -> Result<PathBuf, String> {
    let runtime_dir = std::env::var_os("XDG_RUNTIME_DIR")
        .ok_or_else(|| "XDG_RUNTIME_DIR is not available for local hotkey signaling.".to_string())?;
    Ok(PathBuf::from(runtime_dir).join("whispercat-toggle.sock"))
}

#[cfg(target_os = "linux")]
pub fn is_toggle_invocation() -> bool {
    std::env::args_os().any(|argument| argument == "--toggle")
}

#[cfg(not(target_os = "linux"))]
pub fn is_toggle_invocation() -> bool {
    false
}

#[cfg(target_os = "linux")]
pub fn send_toggle() -> Result<(), String> {
    UnixStream::connect(socket_path()?).map(|_| ()).map_err(|error| {
        format!("WhisperCat is not running or cannot receive the hotkey signal: {error}")
    })
}

#[cfg(not(target_os = "linux"))]
pub fn send_toggle() -> Result<(), String> {
    Err("Local hotkey signaling is only available on Linux.".to_string())
}

#[cfg(target_os = "linux")]
pub fn setup(app: AppHandle) -> Result<(), String> {
    let path = socket_path()?;
    if let Ok(metadata) = std::fs::symlink_metadata(&path) {
        if metadata.uid() != unsafe { libc::geteuid() } {
            return Err("Existing hotkey socket is not owned by current user.".to_string());
        }
        if !metadata.file_type().is_socket() {
            return Err("Hotkey socket path is occupied by a non-socket file.".to_string());
        }
        std::fs::remove_file(&path)
            .map_err(|error| format!("Could not remove stale hotkey socket: {error}"))?;
    }
    let listener = UnixListener::bind(&path)
        .map_err(|error| format!("Could not create local hotkey socket: {error}"))?;
    std::fs::set_permissions(&path, std::fs::Permissions::from_mode(0o600))
        .map_err(|error| format!("Could not secure local hotkey socket: {error}"))?;

    std::thread::spawn(move || {
        for connection in listener.incoming() {
            match connection {
                Ok(_) => {
                    if let Err(error) = app.emit("hotkey-toggle", ()) {
                        tracing::warn!("Could not deliver local hotkey signal: {error}");
                    }
                }
                Err(error) if error.kind() == io::ErrorKind::Interrupted => continue,
                Err(error) => {
                    tracing::warn!("Local hotkey listener stopped: {error}");
                    break;
                }
            }
        }
    });
    Ok(())
}

#[cfg(not(target_os = "linux"))]
pub fn setup(_: tauri::AppHandle) -> Result<(), String> {
    Ok(())
}
