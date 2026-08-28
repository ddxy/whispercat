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
- Workflow selection from the app and tray context menu
- Automatic clipboard copy and optional auto-paste
- Persistent settings, device detection, light professional interface

## Install

Download the matching installer or bundle from the [Releases page](https://github.com/ddxy/whispercat/releases).

On Linux, system-audio capture requires `pactl` and `parec` (typically supplied by PulseAudio/PipeWire compatibility packages).

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
