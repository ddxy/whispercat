// Mirrors the Rust structs (serde, snake_case) in src-tauri/src

export interface Config {
  whisper_server: 'openai' | 'faster-whisper' | 'open-webui';
  api_key: string;
  faster_url: string;
  faster_model: string;
  faster_language: string;
  owui_url: string;
  owui_key: string;
  mic_name: string | null;
  hotkey: string;
  auto_paste: boolean;
}

export type Step =
  | { type: 'replace'; from: string; to: string }
  | {
      type: 'prompt';
      provider: 'openai' | 'open-webui' | string;
      model: string;
      system_prompt: string;
      user_prompt: string;
    };

export interface PostProcessing {
  uuid: string;
  title: string;
  description: string;
  steps: Step[];
}
