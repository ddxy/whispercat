<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import * as api from '../lib/backend';
  import { toast } from '../lib/toast';
  import type { Config, PostProcessing } from '../lib/types';

  let recording = false;
  let busy = false;
  let raw = '';
  let processed = '';
  let pps: PostProcessing[] = [];
  let selectedUuid = '';
  let usePP = false;
  let autoPaste = true;
  let unlisten: (() => void) | null = null;

  onMount(async () => {
    try {
      const cfg: Config = await api.getConfig();
      autoPaste = cfg.auto_paste;
      pps = await api.listPostprocessings();
      if (pps.length > 0) selectedUuid = pps[0].uuid;
    } catch (e) {
      toast('error', String(e));
    }
    unlisten = await api.onHotkey(() => toggle());
  });

  onDestroy(() => unlisten?.());

  async function toggle() {
    if (busy) return;
    if (!recording) {
      try {
        await api.startRecording();
        recording = true;
      } catch (e) {
        toast('error', String(e));
      }
      return;
    }
    // Stoppen + verarbeiten
    recording = false;
    busy = true;
    try {
      const path = await api.stopRecording();
      raw = await api.transcribe(path);
      processed = '';
      let finalText = raw;
      const pp = pps.find((p) => p.uuid === selectedUuid);
      if (usePP && pp) {
        toast('info', 'Post-Processing läuft …');
        processed = await api.postprocess(pp, raw);
        finalText = processed;
      }
      try {
        await api.pasteText(finalText);
        toast('success', autoPaste ? 'Fertig! Text eingefügt.' : 'Fertig! Text in Zwischenablage.');
      } catch (e) {
        toast('error', 'Zwischenablage/Auto-Paste fehlgeschlagen: ' + String(e));
      }
    } catch (e) {
      toast('error', String(e));
    } finally {
      busy = false;
    }
  }
</script>

<div class="wrap">
  <button class="mic" class:recording class:busy on:click={toggle} disabled={busy}>
    {#if busy}⌛{:else if recording}⏹{:else}🎙{/if}
  </button>
  <p class="status">
    {#if busy}Verarbeite …{:else if recording}Aufnahme läuft — klicke oder drücke den Hotkey zum Stoppen{:else}Bereit{/if}
  </p>

  <div class="card">
    <h2>Transkript</h2>
    <textarea bind:value={raw} rows="4" placeholder="Das Transkript erscheint hier …"></textarea>
  </div>

  {#if processed}
    <div class="card pp">
      <h2>Post-Processed</h2>
      <textarea bind:value={processed} rows="4"></textarea>
    </div>
  {/if}

  {#if pps.length > 0}
    <div class="card">
      <label class="row">
        <input type="checkbox" bind:checked={usePP} />
        Post-Processing nach jeder Aufnahme anwenden
      </label>
      {#if usePP}
        <select bind:value={selectedUuid}>
          {#each pps as pp}
            <option value={pp.uuid}>{pp.title}</option>
          {/each}
        </select>
      {/if}
    </div>
  {/if}
</div>

<style>
  .wrap { max-width: 560px; margin: 0 auto; display: flex; flex-direction: column; align-items: center; }
  .mic {
    width: 150px; height: 150px; border-radius: 50%; font-size: 56px;
    display: flex; align-items: center; justify-content: center;
    border: 3px solid var(--border); background: var(--bg-card);
    transition: transform 0.1s, border-color 0.2s, background 0.2s;
  }
  .mic:hover:not(:disabled) { transform: scale(1.05); }
  .mic.recording { border-color: var(--danger); background: #55201f; animation: pulse 1.4s infinite; }
  .mic.busy { border-color: var(--info); }
  @keyframes pulse {
    0%, 100% { box-shadow: 0 0 0 0 rgba(198, 40, 40, 0.5); }
    50% { box-shadow: 0 0 0 18px rgba(198, 40, 40, 0); }
  }
  .status { color: var(--text-dim); margin: 14px 0 18px; }
  .card { width: 100%; }
  .card.pp { border-color: var(--accent); }
  .card select { margin-top: 8px; }
</style>
