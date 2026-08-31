use std::process::Command;

use tauri::AppHandle;
use tauri_plugin_global_shortcut::{Code, GlobalShortcutExt, Modifiers, Shortcut};

/// Registriert den globalen Hotkey aus dem Config-String
/// (z.B. "Ctrl+Shift+R", "Alt+F9", "Super+Space"). Leerer String = deaktiviert.
pub fn register(app: &AppHandle, spec: &str) -> Result<(), String> {
    #[cfg(target_os = "linux")]
    if std::env::var_os("WAYLAND_DISPLAY").is_some() {
        return register_gnome_shortcut(spec);
    }

    let gs = app.global_shortcut();
    let trimmed = spec.trim();
    if trimmed.is_empty() {
        gs.unregister_all()
            .map_err(|error| format!("Could not disable global hotkey: {error}"))?;
        tracing::info!("Kein globaler Hotkey konfiguriert.");
        return Ok(());
    }
    let shortcut = parse_shortcut(trimmed)?;
    gs.unregister_all()
        .map_err(|error| format!("Could not replace global hotkey: {error}"))?;
    gs.register(shortcut)
        .map_err(|e| format!("Hotkey '{trimmed}' konnte nicht registriert werden: {e}"))?;
    tracing::info!("Globaler Hotkey aktiv: {trimmed}");
    Ok(())
}

#[cfg(target_os = "linux")]
fn register_gnome_shortcut(spec: &str) -> Result<(), String> {
    if !is_gnome_desktop() {
        return Err("Global hotkeys are unavailable in this Wayland session. Use an X11 session, or a desktop environment that provides the XDG Global Shortcuts portal.".to_string());
    }

    let binding = gnome_binding(spec)?;
    let path = "/org/gnome/settings-daemon/plugins/media-keys/custom-keybindings/whispercat/";
    update_gnome_shortcut_list(path, binding.is_empty())?;
    if binding.is_empty() {
        run_gsettings(&["reset-recursively", &format!("org.gnome.settings-daemon.plugins.media-keys.custom-keybinding:{path}")])?;
        return Ok(());
    }

    let executable = std::env::current_exe()
        .map_err(|error| format!("Could not locate WhisperCat executable: {error}"))?;
    let command = format!("{} --toggle", shell_quote(&executable.to_string_lossy()));
    let schema = format!("org.gnome.settings-daemon.plugins.media-keys.custom-keybinding:{path}");
    run_gsettings(&["set", &schema, "name", &gvariant_string("WhisperCat: Start/Stop Recording")])?;
    run_gsettings(&["set", &schema, "command", &gvariant_string(&command)])?;
    run_gsettings(&["set", &schema, "binding", &gvariant_string(&binding)])?;
    tracing::info!("GNOME global hotkey active: {spec}");
    Ok(())
}

#[cfg(target_os = "linux")]
fn is_gnome_desktop() -> bool {
    std::env::var("XDG_CURRENT_DESKTOP")
        .unwrap_or_default()
        .split(':')
        .any(|desktop| matches!(desktop.to_ascii_lowercase().as_str(), "gnome" | "ubuntu"))
}

#[cfg(target_os = "linux")]
fn update_gnome_shortcut_list(path: &str, remove: bool) -> Result<(), String> {
    let output = Command::new("gsettings")
        .args(["get", "org.gnome.settings-daemon.plugins.media-keys", "custom-keybindings"])
        .output()
        .map_err(|error| format!("Could not read GNOME shortcut settings: {error}"))?;
    if !output.status.success() {
        return Err(format!("Could not read GNOME shortcut settings: {}", String::from_utf8_lossy(&output.stderr).trim()));
    }
    let mut paths = String::from_utf8_lossy(&output.stdout)
        .split('\'')
        .skip(1)
        .step_by(2)
        .map(str::to_string)
        .collect::<Vec<_>>();
    paths.retain(|entry| entry != path);
    if !remove {
        paths.push(path.to_string());
    }
    let value = format!("[{}]", paths.iter().map(|entry| format!("'{entry}'")).collect::<Vec<_>>().join(", "));
    run_gsettings(&["set", "org.gnome.settings-daemon.plugins.media-keys", "custom-keybindings", &value])
}

#[cfg(target_os = "linux")]
fn run_gsettings(args: &[&str]) -> Result<(), String> {
    let output = Command::new("gsettings")
        .args(args)
        .output()
        .map_err(|error| format!("Could not run GNOME settings: {error}"))?;
    if output.status.success() {
        Ok(())
    } else {
        Err(format!("Could not update GNOME shortcut: {}", String::from_utf8_lossy(&output.stderr).trim()))
    }
}

#[cfg(target_os = "linux")]
fn gnome_binding(spec: &str) -> Result<String, String> {
    let spec = spec.trim();
    if spec.is_empty() {
        return Ok(String::new());
    }
    parse_shortcut(spec)?;
    let tokens: Vec<_> = spec
        .split('+')
        .map(|token| token.trim().to_ascii_lowercase())
        .filter(|token| !token.is_empty())
        .collect();
    let (key, modifiers) = tokens.split_last().expect("validated non-empty shortcut");
    let mut binding = modifiers.iter().map(|modifier| match modifier.as_str() {
        "ctrl" | "control" | "strg" => Ok("<Control>"),
        "shift" | "umschalt" => Ok("<Shift>"),
        "alt" => Ok("<Alt>"),
        "super" | "meta" | "win" | "cmd" | "command" => Ok("<Super>"),
        other => Err(format!("Unknown modifier: '{other}'")),
    }).collect::<Result<String, _>>()?;
    binding.push_str(match key.as_str() {
        "space" | "leertaste" => "space",
        "enter" | "return" => "Return",
        "tab" => "Tab",
        "esc" | "escape" => "Escape",
        "backspace" => "BackSpace",
        "delete" | "del" | "entf" => "Delete",
        "insert" | "ins" | "einf" => "Insert",
        "home" | "pos1" => "Home",
        "end" | "ende" => "End",
        "pageup" | "pgup" => "Page_Up",
        "pagedown" | "pgdn" => "Page_Down",
        "up" | "arrowup" => "Up",
        "down" | "arrowdown" => "Down",
        "left" | "arrowleft" => "Left",
        "right" | "arrowright" => "Right",
        "minus" | "-" => "minus",
        "equal" | "=" | "plus" => "equal",
        "comma" | "," => "comma",
        "period" | "." => "period",
        "slash" | "/" => "slash",
        "backslash" | "\\" => "backslash",
        key if key.len() == 1 => key,
        key if key.starts_with('f') && key[1..].parse::<u8>().is_ok() => return Ok(format!("{binding}{}", key.to_ascii_uppercase())),
        _ => return Err(format!("Unknown key: '{key}'")),
    });
    Ok(binding)
}

#[cfg(target_os = "linux")]
fn shell_quote(value: &str) -> String {
    format!("'{}'", value.replace('\'', "'\\''"))
}

#[cfg(target_os = "linux")]
fn gvariant_string(value: &str) -> String {
    format!("'{}'", value.replace('\\', "\\\\").replace('\'', "\\'"))
}

pub fn parse_shortcut(spec: &str) -> Result<Shortcut, String> {
    let tokens: Vec<String> = spec
        .split('+')
        .map(|t| t.trim().to_ascii_lowercase())
        .filter(|t| !t.is_empty())
        .collect();
    if tokens.is_empty() {
        return Err("Leerer Hotkey".to_string());
    }
    let (key_token, mod_tokens) = tokens.split_last().unwrap();

    let mut mods = Modifiers::empty();
    for tok in mod_tokens {
        mods |= match tok.as_str() {
            "ctrl" | "control" | "strg" => Modifiers::CONTROL,
            "shift" | "umschalt" => Modifiers::SHIFT,
            "alt" => Modifiers::ALT,
            "super" | "meta" | "win" | "cmd" | "command" => Modifiers::SUPER,
            other => return Err(format!("Unbekannter Modifier: '{other}'")),
        };
    }

    let code = parse_code(key_token)?;
    Ok(Shortcut::new(
        if mods.is_empty() { None } else { Some(mods) },
        code,
    ))
}

fn parse_code(token: &str) -> Result<Code, String> {
    let t = token.trim().to_ascii_uppercase();
    if let Some(rest) = t.strip_prefix('F') {
        if let Ok(n) = rest.parse::<u8>() {
            return Ok(match n {
                1 => Code::F1,
                2 => Code::F2,
                3 => Code::F3,
                4 => Code::F4,
                5 => Code::F5,
                6 => Code::F6,
                7 => Code::F7,
                8 => Code::F8,
                9 => Code::F9,
                10 => Code::F10,
                11 => Code::F11,
                12 => Code::F12,
                _ => return Err(format!("Unbekannte F-Taste: F{n}")),
            });
        }
    }
    match t.as_str() {
        "SPACE" | "LEERTASTE" => return Ok(Code::Space),
        "ENTER" | "RETURN" => return Ok(Code::Enter),
        "TAB" => return Ok(Code::Tab),
        "ESC" | "ESCAPE" => return Ok(Code::Escape),
        "BACKSPACE" => return Ok(Code::Backspace),
        "DELETE" | "DEL" | "ENTF" => return Ok(Code::Delete),
        "INSERT" | "INS" | "EINF" => return Ok(Code::Insert),
        "HOME" | "POS1" => return Ok(Code::Home),
        "END" | "ENDE" => return Ok(Code::End),
        "PAGEUP" | "PGUP" => return Ok(Code::PageUp),
        "PAGEDOWN" | "PGDN" => return Ok(Code::PageDown),
        "UP" | "ARROWUP" => return Ok(Code::ArrowUp),
        "DOWN" | "ARROWDOWN" => return Ok(Code::ArrowDown),
        "LEFT" | "ARROWLEFT" => return Ok(Code::ArrowLeft),
        "RIGHT" | "ARROWRIGHT" => return Ok(Code::ArrowRight),
        "MINUS" | "-" => return Ok(Code::Minus),
        "EQUAL" | "=" | "PLUS" => return Ok(Code::Equal),
        "COMMA" | "," => return Ok(Code::Comma),
        "PERIOD" | "." => return Ok(Code::Period),
        "SLASH" | "/" => return Ok(Code::Slash),
        "BACKSLASH" | "\\" => return Ok(Code::Backslash),
        _ => {}
    }
    if t.len() == 1 {
        let b = t.as_bytes()[0];
        if b.is_ascii_uppercase() {
            return Ok(match b {
                b'A' => Code::KeyA,
                b'B' => Code::KeyB,
                b'C' => Code::KeyC,
                b'D' => Code::KeyD,
                b'E' => Code::KeyE,
                b'F' => Code::KeyF,
                b'G' => Code::KeyG,
                b'H' => Code::KeyH,
                b'I' => Code::KeyI,
                b'J' => Code::KeyJ,
                b'K' => Code::KeyK,
                b'L' => Code::KeyL,
                b'M' => Code::KeyM,
                b'N' => Code::KeyN,
                b'O' => Code::KeyO,
                b'P' => Code::KeyP,
                b'Q' => Code::KeyQ,
                b'R' => Code::KeyR,
                b'S' => Code::KeyS,
                b'T' => Code::KeyT,
                b'U' => Code::KeyU,
                b'V' => Code::KeyV,
                b'W' => Code::KeyW,
                b'X' => Code::KeyX,
                b'Y' => Code::KeyY,
                b'Z' => Code::KeyZ,
                _ => unreachable!(),
            });
        }
        if b.is_ascii_digit() {
            return Ok(match b {
                b'0' => Code::Digit0,
                b'1' => Code::Digit1,
                b'2' => Code::Digit2,
                b'3' => Code::Digit3,
                b'4' => Code::Digit4,
                b'5' => Code::Digit5,
                b'6' => Code::Digit6,
                b'7' => Code::Digit7,
                b'8' => Code::Digit8,
                b'9' => Code::Digit9,
                _ => unreachable!(),
            });
        }
    }
    Err(format!("Unbekannte Taste: '{token}'"))
}
