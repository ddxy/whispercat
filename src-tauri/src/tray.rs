use crate::{postprocess, AppState};
use tauri::{
    menu::{CheckMenuItem, Menu, MenuItem, PredefinedMenuItem, Submenu},
    tray::TrayIconBuilder,
    App, AppHandle, Emitter, Manager, Wry,
};

pub struct TrayProcessMenu(pub Submenu<Wry>);

pub fn setup(app: &App) -> Result<(), Box<dyn std::error::Error>> {
    let open = MenuItem::with_id(app, "open", "Open", true, None::<&str>)?;
    let toggle = MenuItem::with_id(app, "toggle", "Start/Stop Recording", true, None::<&str>)?;
    let processes = build_process_menu(app)?;
    let quit = MenuItem::with_id(app, "quit", "Quit", true, None::<&str>)?;
    let separator = PredefinedMenuItem::separator(app)?;
    let menu = Menu::with_items(app, &[&open, &toggle, &separator, &processes, &quit])?;

    app.manage(TrayProcessMenu(processes));

    let icon = app
        .default_window_icon()
        .map(|img| img.clone().to_owned())
        .unwrap_or_else(fallback_icon);

    let _tray = TrayIconBuilder::new()
        .icon(icon)
        .tooltip("WhisperCat")
        .menu(&menu)
        .show_menu_on_left_click(false)
        .on_menu_event(|app, event| {
            let id = event.id().as_ref();
            match id {
                "open" => {
                    if let Some(window) = app.get_webview_window("main") {
                        let _ = window.show();
                        let _ = window.unminimize();
                        let _ = window.set_focus();
                    }
                }
                "toggle" => {
                    let _ = app.emit("hotkey-toggle", ());
                }
                "quit" => app.exit(0),
                _ if id.starts_with("pp:") => {
                    let uuid = id.trim_start_matches("pp:").to_string();
                    if let Err(error) = app
                        .state::<AppState>()
                        .set_selected_postprocessing(Some(uuid.clone()))
                    {
                        tracing::warn!("Unable to select post-processing from tray: {error}");
                        return;
                    }
                    if let Err(error) = refresh_postprocessing_menu(app) {
                        tracing::warn!("Unable to refresh tray post-processing menu: {error}");
                    }
                    let _ = app.emit("selected-postprocessing-changed", Some(uuid));
                }
                _ => {}
            }
        })
        .build(app)?;

    Ok(())
}

pub fn refresh_postprocessing_menu(app: &AppHandle) -> Result<(), Box<dyn std::error::Error>> {
    let menu = &app.state::<TrayProcessMenu>().0;
    for item in menu.items()? {
        menu.remove(&item)?;
    }

    let selected = app.state::<AppState>().config().selected_postprocessing;
    let processes = postprocess::load_all();
    if processes.is_empty() {
        menu.append(&MenuItem::with_id(
            app,
            "pp:none",
            "No saved processes",
            false,
            None::<&str>,
        )?)?;
        return Ok(());
    }

    for process in processes {
        let item = CheckMenuItem::with_id(
            app,
            format!("pp:{}", process.uuid),
            process.title,
            true,
            selected.as_deref() == Some(process.uuid.as_str()),
            None::<&str>,
        )?;
        menu.append(&item)?;
    }
    Ok(())
}

fn build_process_menu(app: &App) -> Result<Submenu<Wry>, Box<dyn std::error::Error>> {
    let menu = Submenu::with_id(app, "postprocessings", "Post-processing", true)?;
    let selected = app.state::<AppState>().config().selected_postprocessing;
    let processes = postprocess::load_all();

    if processes.is_empty() {
        menu.append(&MenuItem::with_id(
            app,
            "pp:none",
            "No saved processes",
            false,
            None::<&str>,
        )?)?;
    } else {
        for process in processes {
            let item = CheckMenuItem::with_id(
                app,
                format!("pp:{}", process.uuid),
                process.title,
                true,
                selected.as_deref() == Some(process.uuid.as_str()),
                None::<&str>,
            )?;
            menu.append(&item)?;
        }
    }

    Ok(menu)
}

fn fallback_icon() -> tauri::image::Image<'static> {
    const SIZE: usize = 32;
    let mut rgba = vec![0u8; SIZE * SIZE * 4];
    for pixel in rgba.chunks_exact_mut(4) {
        pixel[0] = 0x4a;
        pixel[1] = 0x5a;
        pixel[2] = 0xeb;
        pixel[3] = 0xff;
    }
    tauri::image::Image::new_owned(rgba, SIZE as u32, SIZE as u32)
}
