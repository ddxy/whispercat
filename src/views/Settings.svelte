<script lang="ts">
  import { onDestroy, onMount } from 'svelte';
  import * as api from '../lib/backend';
  import { toast } from '../lib/toast';
  import type { Config } from '../lib/types';

  let cfg: Config | null = null;
  let devices: string[] = [];
  let systemAudioSources: string[] = [];
  let saveTimer: ReturnType<typeof setTimeout> | null = null;

  onMount(async () => {
    try {
      cfg = await api.getConfig();
    } catch (e) {
      toast('error', String(e));
    }
    try {
      devices = await api.listInputDevices();
    } catch {
      devices = [];
    }
    try {
      systemAudioSources = await api.listSystemAudioSources();
    } catch {
      systemAudioSources = [];
    }
  });

  $: if (cfg) {
    JSON.stringify(cfg);
    if (saveTimer) clearTimeout(saveTimer);
    saveTimer = setTimeout(async () => {
      try {
        if (cfg) await api.saveConfig(cfg);
      } catch (e) {
        toast('error', String(e));
      }
    }, 350);
  }

  onDestroy(() => {
    if (saveTimer) clearTimeout(saveTimer);
    if (cfg) void api.saveConfig(cfg).catch((e) => toast('error', String(e)));
  });

  async function detectMicrophone() {
    if (!cfg) return;
    const device = await api.detectDefaultInputDevice();
    if (!device) return toast('error', 'No default microphone detected.');
    cfg.mic_name = device;
    toast('success', `Selected microphone: ${device}`);
  }

  async function detectSystemAudio() {
    if (!cfg) return;
    const source = await api.detectActiveSystemAudioSource();
    if (!source) return toast('error', 'No active system-audio source detected.');
    cfg.system_audio_source = source;
    cfg.system_audio_enabled = true;
    toast('success', `Selected system-audio source: ${source}`);
  }

</script>

{#if cfg}
  <div class="settings">
    <div class="card">
      <h2>General</h2>
      <label>
        Hotkey to start/stop (e.g. “Ctrl+Shift+R”, “Alt+F9”)
        <input bind:value={cfg.hotkey} placeholder="Ctrl+Shift+R" />
      </label>
      <p class="hint">Modifiers: ctrl, shift, alt, super/cmd. Keys: A–Z, 0–9, F1–F12, space, enter, tab, esc… Note: global hotkeys may be limited under Wayland.</p>
      <label class="row" style="margin-top: 10px;">
        <input type="checkbox" bind:checked={cfg.auto_paste} style="width: auto;" />
        Automatically paste text after transcription (simulates Ctrl+V)
      </label>
      <label style="margin-top: 14px;">
        Microphone (empty = system default)
        <select bind:value={cfg.mic_name}>
          <option value={null}>System default</option>
          {#each devices as d}
            <option value={d}>{d}</option>
          {/each}
        </select>
      </label>
      <button on:click={detectMicrophone}>Detect default microphone</button>

      {#if systemAudioSources.length > 0}
        <label class="row" style="margin-top: 10px;">
          <input type="checkbox" bind:checked={cfg.system_audio_enabled} style="width: auto;" />
          Record system audio
        </label>
        {#if cfg.system_audio_enabled}
          <label>
            System audio source
            <select bind:value={cfg.system_audio_source}>
              <option value={null}>Default output monitor</option>
              {#each systemAudioSources as source}
                <option value={source}>{source}</option>
              {/each}
            </select>
          </label>
          <button on:click={detectSystemAudio}>Detect active audio source</button>
          <div class="row gain-controls">
            <label>
              Microphone level: {Math.round(cfg.mic_gain * 100)}%
              <input type="range" min="0" max="2" step="0.05" bind:value={cfg.mic_gain} />
            </label>
            <label>
              System audio level: {Math.round(cfg.system_audio_gain * 100)}%
              <input type="range" min="0" max="2" step="0.05" bind:value={cfg.system_audio_gain} />
            </label>
          </div>
        {/if}
      {/if}
    </div>

    <div class="card">
      <h2>Whisper-Backend</h2>
      <div class="row servers">
        {#each [['openai', 'OpenAI'], ['faster-whisper', 'Faster-Whisper']] as s}
          <label class="row server" class:selected={cfg.whisper_server === s[0]}>
            <input type="radio" bind:group={cfg.whisper_server} value={s[0]} />
            {s[1]}
          </label>
        {/each}
      </div>

      {#if cfg.whisper_server === 'openai'}
        <label>OpenAI API key <input type="password" bind:value={cfg.api_key} placeholder="sk-…" /></label>
      {:else if cfg.whisper_server === 'faster-whisper'}
        <label>Server URL <input bind:value={cfg.faster_url} placeholder="http://localhost:8000" /></label>
        <label>Model <input bind:value={cfg.faster_model} placeholder="Systran/faster-whisper-base" /></label>
        <label>Language (e.g. “de”, empty = automatic) <input bind:value={cfg.faster_language} /></label>
      {:else}
        <label>Server URL <input bind:value={cfg.owui_url} placeholder="https://your-openwebui-host" /></label>
        <label>API key <input type="password" bind:value={cfg.owui_key} /></label>
      {/if}
      <p class="hint">The OpenAI or Open WebUI key is also used for post-processing prompts.</p>
    </div>

  </div>
{/if}

<style>
  .settings { max-width: 640px; margin: 0 auto; }
  .servers { margin-bottom: 14px; }
  .server {
    padding: 10px 14px; border: 1px solid var(--border); border-radius: 10px;
    cursor: pointer; color: var(--text);
  }
  .server.selected { border-color: var(--accent); background: var(--accent); color: #fff; }
  .server input { width: auto; }
  .gain-controls { align-items: flex-start; }
  .gain-controls input { padding: 0; }
</style>
