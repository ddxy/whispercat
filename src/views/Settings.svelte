<script lang="ts">
  import { onMount } from 'svelte';
  import * as api from '../lib/backend';
  import { toast } from '../lib/toast';
  import type { Config } from '../lib/types';

  let cfg: Config | null = null;
  let devices: string[] = [];

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
  });

  async function save() {
    if (!cfg) return;
    try {
      await api.saveConfig(cfg);
      toast('success', 'Settings saved.');
    } catch (e) {
      toast('error', String(e));
    }
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

    <button class="primary" on:click={save}>💾 Save</button>
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
</style>
