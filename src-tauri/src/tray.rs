use tauri::{
    menu::{Menu, MenuItem},
    tray::TrayIconBuilder,
    App, Emitter, Manager,
};

pub fn setup(app: &App) -> Result<(), Box<dyn std::error::Error>> {
    let open = MenuItem::with_id(app, "open", "Open", true, None::<&str>)?;
    let toggle = MenuItem::with_id(app, "toggle", "Start/Stop Recording", true, None::<&str>)?;
    let quit = MenuItem::with_id(app, "quit", "Quit", true, None::<&str>)?;
    let menu = Menu::with_items(app, &[&open, &toggle, &quit])?;

    let icon = app
        .default_window_icon()
        .map(|img| img.clone().to_owned())
        .unwrap_or_else(fallback_icon);

    let _tray = TrayIconBuilder::new()
        .icon(icon)
        .tooltip("WhisperCat")
        .menu(&menu)
        .show_menu_on_left_click(false)
        .on_menu_event(|app, event| match event.id().as_ref() {
            "open" => {
                if let Some(w) = app.get_webview_window("main") {
                    let _ = w.show();
                    let _ = w.unminimize();
                    let _ = w.set_focus();
                }
            }
            "toggle" => {
                let _ = app.emit("hotkey-toggle", ());
            }
            "quit" => app.exit(0),
            _ => {}
        })
        .build(app)?;

    Ok(())
}

/// Falls noch keine Icons gebündelt sind, wird programmatisch ein
/// 32x32-Icon in der WhisperCat-Farbe (#676795) erzeugt.
fn fallback_icon() -> tauri::image::Image<'static> {
    const SIZE: usize = 32;
    let mut rgba = vec![0u8; SIZE * SIZE * 4];
    for px in rgba.chunks_exact_mut(4) {
        px[0] = 0x67;
        px[1] = 0x67;
        px[2] = 0x97;
        px[3] = 0xFF;
    }
    tauri::image::Image::new_owned(rgba, SIZE as u32, SIZE as u32)
}
