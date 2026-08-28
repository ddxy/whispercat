// Dünne Client-Schicht: kapselt alle Tauri-Commands.
// Läuft das Frontend im normalen Browser (kein __TAURI_INTERNALS__),
// werden Mocks geliefert, damit UI-Entwicklung ohne native Deps funktioniert.

import { invoke } from '@tauri-apps/api/core';
import type { Config, PostProcessing } from './types.ts';

const isTauri = typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window;

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

// ---------- Mock-State für den Browser-Dev-Modus ----------
let mockConfig: Config = {
  whisper_server: 'openai',
  api_key: '',
  faster_url: 'http://localhost:8000',
  faster_model: 'Systran/faster-whisper-base',
  faster_language: '',
  owui_url: '',
  owui_key: '',
  mic_name: null,
  hotkey: 'Ctrl+Shift+R',
  auto_paste: true,
};
let mockPps: PostProcessing[] = [
  {
    uuid: 'demo-1',
    title: 'Demo: Füllwörter entfernen',
    description: 'Mock-Eintrag aus dem Browser-Modus',
    steps: [
      {
        type: 'prompt',
        provider: 'openai',
        model: 'gpt-4o-mini',
        system_prompt: 'Entferne Füllwörter und Unflüssigkeiten aus dem Text.',
        user_prompt: 'Bereinige dieses Transkript:\n{{input}}',
      },
      { type: 'replace', from: '  ', to: ' ' },
    ],
  },
];

// ---------- Recording / Transkription ----------

export async function startRecording(): Promise<void> {
  if (!isTauri) return void console.log('[mock] startRecording');
  return invoke('start_recording');
}

export async function stopRecording(): Promise<string> {
  if (!isTauri) return sleep(400).then(() => '/tmp/mock_recording.wav');
  return invoke('stop_recording');
}

export async function transcribe(path: string): Promise<string> {
  if (!isTauri) return sleep(600).then(() => 'Mock-Transkript aus dem Browser-Dev-Modus. Starte mit "npm run tauri dev" für echte Transkription.');
  return invoke('transcribe_audio', { path });
}

export async function postprocess(pp: PostProcessing, text: string): Promise<string> {
  if (!isTauri) return sleep(500).then(() => `[Mock-PP "${pp.title}"] ${text}`);
  return invoke('postprocess_text', { pp, text });
}

export async function pasteText(text: string): Promise<void> {
  if (!isTauri) {
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      console.log('[mock] Zwischenablage nicht verfügbar (nur HTTPS).');
    }
    return;
  }
  return invoke('paste_text', { text });
}

// ---------- Geräte ----------

export async function listInputDevices(): Promise<string[]> {
  if (!isTauri) return ['Mock-Mikrofon 1', 'Mock-Mikrofon 2'];
  return invoke('list_input_devices');
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

// ---------- Hotkey-Event vom Backend (Tray / Global Shortcut) ----------

export async function onHotkey(cb: () => void): Promise<() => void> {
  if (!isTauri) return () => {};
  const { listen } = await import('@tauri-apps/api/event');
  return listen('hotkey-toggle', () => cb());
}
