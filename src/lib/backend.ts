// Thin client layer: encapsulates all Tauri commands.
// When the frontend runs in a regular browser (without __TAURI_INTERNALS__),
// mocks are provided so UI development works without native dependencies.

import { invoke } from '@tauri-apps/api/core';
import type { Config, PostProcessing } from './types.ts';

const isTauri = typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window;

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

// ---------- Mock state for browser development mode ----------
let mockConfig: Config = {
  whisper_server: 'openai',
  api_key: '',
  faster_url: 'http://localhost:8000',
  faster_model: 'Systran/faster-whisper-base',
  faster_language: '',
  owui_url: '',
  owui_key: '',
  mic_name: null,
  system_audio_enabled: false,
  system_audio_source: null,
  mic_gain: 1,
  system_audio_gain: 1,
  hotkey: 'Ctrl+Shift+R',
  auto_paste: true,
};
let mockPps: PostProcessing[] = [
  {
    uuid: 'demo-1',
    title: 'Demo: Remove filler words',
    description: 'Mock entry from browser mode',
    steps: [
      {
        type: 'prompt',
        provider: 'openai',
        model: 'gpt-4o-mini',
        system_prompt: 'Remove filler words and disfluencies from the text.',
        user_prompt: 'Clean up this transcript:\n{{input}}',
      },
      { type: 'replace', from: '  ', to: ' ' },
    ],
  },
];

// ---------- Recording / transcription ----------

export async function startRecording(): Promise<void> {
  if (!isTauri) return void console.log('[mock] startRecording');
  return invoke('start_recording');
}

export async function stopRecording(): Promise<string> {
  if (!isTauri) return sleep(400).then(() => '/tmp/mock_recording.wav');
  return invoke('stop_recording');
}

export async function transcribe(path: string): Promise<string> {
  if (!isTauri) return sleep(600).then(() => 'Mock transcript from browser development mode. Run "npm run tauri dev" for real transcription.');
  return invoke('transcribe_audio', { path });
}

export async function postprocess(pp: PostProcessing, text: string): Promise<string> {
  if (!isTauri) return sleep(500).then(() => `[Mock PP "${pp.title}"] ${text}`);
  return invoke('postprocess_text', { pp, text });
}

export async function pasteText(text: string): Promise<void> {
  if (!isTauri) {
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      console.log('[mock] Clipboard unavailable (HTTPS only).');
    }
    return;
  }
  return invoke('paste_text', { text });
}

// ---------- Devices ----------

export async function listInputDevices(): Promise<string[]> {
  if (!isTauri) return ['Mock microphone 1', 'Mock microphone 2'];
  return invoke('list_input_devices');
}

export async function listSystemAudioSources(): Promise<string[]> {
  if (!isTauri) return ['Default output monitor', 'Mock speaker monitor'];
  return invoke('list_system_audio_sources');
}

export async function detectDefaultInputDevice(): Promise<string | null> {
  if (!isTauri) return 'Mock microphone 1';
  return invoke('detect_default_input_device');
}

export async function detectActiveSystemAudioSource(): Promise<string | null> {
  if (!isTauri) return 'Default output monitor';
  return invoke('detect_active_system_audio_source');
}

// ---------- Config ----------

export async function getConfig(): Promise<Config> {
  if (!isTauri) return structuredClone(mockConfig);
  return invoke('get_config');
}

export async function saveConfig(cfg: Config): Promise<void> {
  if (!isTauri) {
    mockConfig = structuredClone(cfg);
    return;
  }
  return invoke('save_config', { cfg });
}

// ---------- Post-Processings ----------

export async function listPostprocessings(): Promise<PostProcessing[]> {
  if (!isTauri) return structuredClone(mockPps);
  return invoke('list_postprocessings');
}

export async function upsertPostprocessing(pp: PostProcessing): Promise<PostProcessing> {
  if (!isTauri) {
    if (!pp.uuid) pp.uuid = 'mock-' + Math.random().toString(36).slice(2, 9);
    const i = mockPps.findIndex((p) => p.uuid === pp.uuid);
    if (i >= 0) mockPps[i] = pp; else mockPps.push(pp);
    return structuredClone(pp);
  }
  return invoke('upsert_postprocessing', { pp });
}

export async function deletePostprocessing(uuid: string): Promise<void> {
  if (!isTauri) {
    mockPps = mockPps.filter((p) => p.uuid !== uuid);
    return;
  }
  return invoke('delete_postprocessing', { uuid });
}

// ---------- Hotkey event from the backend (tray / global shortcut) ----------

export async function onHotkey(cb: () => void): Promise<() => void> {
  if (!isTauri) return () => {};
  const { listen } = await import('@tauri-apps/api/event');
  return listen('hotkey-toggle', () => cb());
}
