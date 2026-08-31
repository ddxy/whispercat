// Mirrors the Rust structs (serde, snake_case) in src-tauri/src

export interface Config {
  whisper_server: 'openai' | 'faster-whisper' | 'open-webui';
  api_key: string;
  faster_url: string;
  faster_model: string;
  faster_language: string;
  owui_url: string;
  owui_key: string;
  custom_ai_url: string;
  custom_ai_key: string;
  n8n_url: string;
  n8n_token: string;
  mic_name: string | null;
  system_audio_enabled: boolean;
  system_audio_source: string | null;
  mic_gain: number;
  system_audio_gain: number;
  selected_postprocessing: string | null;
  hotkey: string;
  auto_paste: boolean;
}

export type Repeat = number | 'infinite';

export type Step =
  | { type: 'replace'; repeat?: Repeat; repeat_interval_seconds?: number; from: string; to: string }
  | {
      type: 'prompt';
      repeat?: Repeat;
      repeat_interval_seconds?: number;
      provider: 'openai' | 'custom' | 'open-webui' | string;
      model: string;
      system_prompt: string;
      user_prompt: string;
    }
  | { type: 'n8n'; repeat?: Repeat; repeat_interval_seconds?: number; path: string }
  | {
      type: 'screenshot';
      repeat?: Repeat;
      repeat_interval_seconds?: number;
      target: 'webhook';
      path: string;
    }
  | {
      type: 'screenshot';
      repeat?: Repeat;
      repeat_interval_seconds?: number;
      target: 'folder';
      folder: string;
    }
  | { type: 'group'; repeat?: Repeat; repeat_interval_seconds?: number; title: string; steps: Step[] };

export interface PostProcessing {
  uuid: string;
  title: string;
  description: string;
  steps: Step[];
}

export type RunStatus = 'processing' | 'done' | 'failed';
export type RunStepStatus = 'pending' | 'processing' | 'done' | 'failed';

export interface RunStep {
  path: number[];
  label: string;
  status: RunStepStatus;
  output: string;
  error?: string;
}

export interface Run {
  id: string;
  created_at: number;
  status: RunStatus;
  workflow_uuid: string | null;
  workflow_title: string;
  recording_dir?: string;
  transcript: string;
  result: string;
  error?: string;
  steps: RunStep[];
}
