// Verhindert zusätzliches Konsolenfenster im Windows-Release-Build
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    whispercat_lib::run()
}
