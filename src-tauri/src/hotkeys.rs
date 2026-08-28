use tauri::AppHandle;
use tauri_plugin_global_shortcut::{Code, GlobalShortcutExt, Modifiers, Shortcut};

/// Registriert den globalen Hotkey aus dem Config-String
/// (z.B. "Ctrl+Shift+R", "Alt+F9", "Super+Space"). Leerer String = deaktiviert.
pub fn register(app: &AppHandle, spec: &str) -> Result<(), String> {
    let gs = app.global_shortcut();
    let _ = gs.unregister_all();
    let trimmed = spec.trim();
    if trimmed.is_empty() {
        tracing::info!("Kein globaler Hotkey konfiguriert.");
        return Ok(());
    }
    let shortcut = parse_shortcut(trimmed)?;
    gs.register(shortcut)
        .map_err(|e| format!("Hotkey '{trimmed}' konnte nicht registriert werden: {e}"))?;
    tracing::info!("Globaler Hotkey aktiv: {trimmed}");
    Ok(())
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
