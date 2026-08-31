// Thin client layer: encapsulates all Tauri commands.
// When the frontend runs in a regular browser (without __TAURI_INTERNALS__),
// mocks are provided so UI development works without native dependencies.

import { invoke } from '@tauri-apps/api/core';
import type { Config, PostProcessing, Run, RunStep } from './types.ts';

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
  custom_ai_url: '',
  custom_ai_key: '',
  n8n_url: '',
  n8n_token: '',
  mic_name: null,
  system_audio_enabled: false,
  system_audio_source: null,
  mic_gain: 1,
  system_audio_gain: 1,
  selected_postprocessing: null,
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
let mockRuns: Run[] = [];

function workflowSteps(steps: PostProcessing['steps'], parentPath: number[] = []): RunStep[] {
  return steps.flatMap((step, index) => {
    const path = [...parentPath, index];
    if (step.type === 'group') return workflowSteps(step.steps, path);
    const label = step.type === 'prompt'
      ? 'AI prompt'
      : step.type === 'replace'
        ? 'Replace text'
        : step.type === 'n8n'
          ? 'n8n webhook'
          : 'Screenshot';
    return [{ path, label, status: 'pending', output: '' }];
  });
}

function updateMockRun(id: string, change: (run: Run) => Run) {
  mockRuns = mockRuns.map((run) => run.id === id ? change(run) : run);
}

async function processMockWorkflow(run: Run, text: string, workflow: PostProcessing | null) {
  let output = text;
  for (const step of run.steps) {
    updateMockRun(run.id, (entry) => ({
      ...entry,
      steps: entry.steps.map((candidate) => candidate.path.join('.') === step.path.join('.') ? { ...candidate, status: 'processing' } : candidate),
    }));
    await sleep(450);
    output = `[Mock ${step.label}] ${output}`;
    updateMockRun(run.id, (entry) => ({
      ...entry,
      steps: entry.steps.map((candidate) => candidate.path.join('.') === step.path.join('.') ? { ...candidate, status: 'done', output } : candidate),
    }));
  }
  updateMockRun(run.id, (entry) => ({ ...entry, status: 'done', result: workflow ? output : text }));
}

// ---------- Recording / transcription ----------

export async function startRecording(): Promise<boolean> {
  if (!isTauri) return mockConfig.system_audio_enabled;
  return invoke('start_recording');
}

export async function stopRecording(): Promise<unknown> {
  if (!isTauri) return sleep(400).then(() => ({ mic_path: '/tmp/mock_recording.wav', system_track: null, mic_gain: 1, system_audio_gain: 1 }));
  return invoke('stop_recording');
}

export async function discardRecording(): Promise<void> {
  if (!isTauri) {
    await sleep(200);
    return;
  }
  await invoke('discard_recording');
}

export async function transcribe(path: string): Promise<string> {
  if (!isTauri) return sleep(600).then(() => 'Mock transcript from browser development mode. Run "npm run tauri dev" for real transcription.');
  return invoke('transcribe_audio', { path });
}

export async function postprocess(pp: PostProcessing, text: string): Promise<string> {
  if (!isTauri) return sleep(500).then(() => `[Mock PP "${pp.title}"] ${text}`);
  return invoke('postprocess_text', { pp, text });
}

export async function queueRun(recording: unknown, workflow: PostProcessing | null): Promise<Run> {
  if (!isTauri) {
    const run: Run = {
      id: crypto.randomUUID(),
      created_at: Date.now(),
      status: 'processing',
      workflow_uuid: workflow?.uuid ?? null,
      workflow_title: workflow?.title ?? 'No workflow',
      recording_dir: '/tmp/whispercat/mock-recording',
      transcript: '',
      result: '',
      steps: workflow ? workflowSteps(workflow.steps) : [],
    };
    mockRuns = [run, ...mockRuns];
    void sleep(700).then(async () => {
      const transcript = 'Mock transcript from browser development mode. Run "npm run tauri dev" for real transcription.';
      updateMockRun(run.id, (entry) => ({ ...entry, transcript }));
      await processMockWorkflow(run, transcript, workflow);
    });
    return run;
  }
  return invoke('queue_run', { recording, workflow });
}

export async function queueTextRun(text: string, workflow: PostProcessing): Promise<Run> {
  if (!isTauri) {
    const run: Run = {
      id: crypto.randomUUID(),
      created_at: Date.now(),
      status: 'processing',
      workflow_uuid: workflow.uuid,
      workflow_title: workflow.title,
      transcript: text,
      result: '',
      steps: workflowSteps(workflow.steps),
    };
    mockRuns = [run, ...mockRuns];
    void processMockWorkflow(run, text, workflow);
    return run;
  }
  return invoke('queue_text_run', { text, workflow });
}

export async function listRuns(): Promise<Run[]> {
  if (!isTauri) return structuredClone(mockRuns);
  return invoke('list_runs');
}

export async function clearRuns(): Promise<void> {
  if (!isTauri) {
    mockRuns = [];
    return;
  }
  return invoke('clear_runs');
}

export async function openRecordingFolder(id: string): Promise<void> {
  if (!isTauri) {
    console.log(`[mock] Open recording folder for ${id}.`);
    return;
  }
  return invoke('open_recording_folder', { id });
}

export async function copyText(text: string): Promise<void> {
  if (!isTauri) {
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      console.log('[mock] Clipboard unavailable (HTTPS only).');
    }
    return;
  }
  return invoke('copy_text', { text });
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

export async function onSelectedPostprocessingChanged(cb: (uuid: string | null) => void): Promise<() => void> {
  if (!isTauri) return () => {};
  const { listen } = await import('@tauri-apps/api/event');
  return listen<string | null>('selected-postprocessing-changed', (event) => cb(event.payload));
}
