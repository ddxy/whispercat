# WhisperCat 2.0

<p align="center">
  <img src="whispercat.svg" alt="WhisperCat icon" width="180" />
</p>

<p align="center"><strong>Your quiet writing companion.</strong></p>

WhisperCat is a native desktop app for capturing spoken thoughts and turning them into usable text. Record with a click or global hotkey, optionally apply a workflow, and send the result straight to the clipboard or active application.

## Highlights

- Native **Tauri 2** desktop app for Linux, Windows, and macOS
- Microphone recording with a configurable global hotkey and system-tray mode
- Optional system-audio capture:
  - Linux: PipeWire/PulseAudio monitor sources
  - Windows: WASAPI loopback
- OpenAI Whisper and Faster-Whisper-compatible transcription backends
- Automatic splitting and sequential transcription of WAV files larger than 25 MiB
- Reusable **Workflows**: text replacements and OpenAI prompt steps, applied in sequence
- Linux/Wayland screenshot workflow step: select one or more monitors once per run, then send PNG files and current text to an n8n webhook
- Workflow selection from the app and tray context menu
- Automatic clipboard copy and optional auto-paste
- Persistent settings, device detection, light professional interface

## Screenshot

<p align="center">
  <img src="screenshot.png" alt="WhisperCat desktop app" width="85%" />
</p>

## Install

Download the matching installer or bundle from the [Releases page](https://github.com/ddxy/whispercat/releases).

On Linux, system-audio capture requires `pactl` and `parec` (typically supplied by PulseAudio/PipeWire compatibility packages).

Wayland screenshot workflow steps require `xdg-desktop-portal`, a desktop-specific portal backend, and GStreamer with PipeWire source and PNG encoder plugins (commonly packages named `gstreamer1.0-pipewire` and `gstreamer1.0-plugins-good`). At workflow start, the portal lets you choose monitors once. Every screenshot step sends multipart fields `text` and one or more `screenshots` PNG files to the configured n8n webhook; its response does not change workflow text.

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the component layout, capture pipeline, persistence model, workflows, and platform-specific behavior.

## Develop

```bash
npm install
npm run tauri dev
```

Useful checks:

```bash
npm run check
npm run build
cargo check --manifest-path src-tauri/Cargo.toml
```

## License

MIT
