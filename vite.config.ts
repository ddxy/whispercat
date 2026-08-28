import { defineConfig } from "vite";
import { svelte } from "@sveltejs/vite-plugin-svelte";

// https://vite.dev/config/
export default defineConfig({
  plugins: [svelte()],
  // Verhindert, dass Vite die Tauri-CLI-Ausgabe überschreibt
  clearScreen: false,
  server: {
    // Tauri erwartet exakt diesen Port (siehe tauri.conf.json -> devUrl)
    port: 1420,
    strictPort: true,
    watch: {
      // Rust-Dateien nicht vom Vite-Watcher beobachten (tauri-cli kümmert sich)
      ignored: ["**/src-tauri/**"],
    },
  },
  envPrefix: ["VITE_", "TAURI_ENV_*"],
});
