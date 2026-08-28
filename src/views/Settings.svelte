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
      toast('success', 'Einstellungen gespeichert.');
    } catch (e) {
      toast('error', String(e));
    }
  }
</script>

{#if cfg}
  <div class="settings">
    <div class="card">
      <h2>Allgemein</h2>
      <label>
        Hotkey zum Starten/Stoppen (z.B. „Ctrl+Shift+R“, „Alt+F9“)
        <input bind:value={cfg.hotkey} placeholder="Ctrl+Shift+R" />
      </label>
      <p class="hint">Modifiers: ctrl, shift, alt, super/cmd. Tasten: A–Z, 0–9, F1–F12, space, enter, tab, esc… Hinweis: unter Wayland sind globale Hotkeys ggf. eingeschränkt.</p>
      <label class="row" style="margin-top: 10px;">
        <input type="checkbox" bind:checked={cfg.auto_paste} style="width: auto;" />
        Text nach Transkription automatisch einfügen (simuliert Ctrl+V)
      </label>
      <label style="margin-top: 14px;">
        Mikrofon (leer = Systemstandard)
        <select bind:value={cfg.mic_name}>
          <option value={null}>Systemstandard</option>
          {#each devices as d}
            <option value={d}>{d}</option>
          {/each}
        </select>
      </label>
    </div>

    <div class="card">
      <h2>Whisper-Backend</h2>
      <div class="row servers">
        {#each [['openai', 'OpenAI'], ['faster-whisper', 'Faster-Whisper'], ['open-webui', 'Open WebUI']] as s}
          <label class="row server" class:selected={cfg.whisper_server === s[0]}>
            <input type="radio" bind:group={cfg.whisper_server} value={s[0]} />
            {s[1]}
          </label>
        {/each}
      </div>

      {#if cfg.whisper_server === 'openai'}
        <label>OpenAI API-Key <input type="password" bind:value={cfg.api_key} placeholder="sk-…" /></label>
      {:else if cfg.whisper_server === 'faster-whisper'}
        <label>Server-URL <input bind:value={cfg.faster_url} placeholder="http://localhost:8000" /></label>
        <label>Modell <input bind:value={cfg.faster_model} placeholder="Systran/faster-whisper-base" /></label>
        <label>Sprache (z.B. „de“, leer = automatisch) <input bind:value={cfg.faster_language} /></label>
      {:else}
        <label>Server-URL <input bind:value={cfg.owui_url} placeholder="https://dein-openwebui-host" /></label>
        <label>API-Key <input type="password" bind:value={cfg.owui_key} /></label>
      {/if}
      <p class="hint">Der OpenAI- bzw. Open-WebUI-Key wird auch für Post-Processing-Prompts genutzt.</p>
    </div>

    <button class="primary" on:click={save}>💾 Speichern</button>
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
