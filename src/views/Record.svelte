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
  let activeText: 'raw' | 'processed' = 'raw';
  let autoPaste = true;
  let config: Config | null = null;
  let optionsMenu: HTMLDetailsElement;
  let workflowMenu: HTMLDetailsElement;
  let unlisten: (() => void) | null = null;
  let unlistenProcess: (() => void) | null = null;

  onMount(async () => {
    try {
      config = await api.getConfig();
      autoPaste = config.auto_paste;
      pps = await api.listPostprocessings();
      selectedUuid = config.selected_postprocessing ?? '';
      usePP = Boolean(selectedUuid);
    } catch (e) {
      toast('error', String(e));
    }
    unlisten = await api.onHotkey(() => toggle());
    unlistenProcess = await api.onSelectedPostprocessingChanged((uuid) => {
      selectedUuid = uuid ?? '';
      usePP = Boolean(uuid);
    });
  });

  onDestroy(() => {
    unlisten?.();
    unlistenProcess?.();
  });

  async function saveConfig() {
    if (!config) return;
    try {
      await api.saveConfig(config);
    } catch (e) {
      toast('error', String(e));
    }
  }

  async function updateSelectedPostprocessing() {
    if (!config) return;
    if (usePP && !selectedUuid && pps.length > 0) selectedUuid = pps[0].uuid;
    config.selected_postprocessing = usePP ? selectedUuid || null : null;
    await saveConfig();
  }

  async function selectPostprocessing(uuid: string) {
    selectedUuid = uuid;
    usePP = Boolean(uuid);
    workflowMenu.open = false;
    await updateSelectedPostprocessing();
  }

  function closeMenus(event: MouseEvent) {
    const target = event.target as Node;
    if (optionsMenu?.open && !optionsMenu.contains(target)) optionsMenu.open = false;
    if (workflowMenu?.open && !workflowMenu.contains(target)) workflowMenu.open = false;
  }

  async function detectSystemAudio() {
    if (!config) return;
    try {
      const source = await api.detectActiveSystemAudioSource();
      if (!source) return toast('error', 'No active system-audio source detected.');
      config.system_audio_source = source;
      config.system_audio_enabled = true;
      await saveConfig();
      toast('success', `Selected system-audio source: ${source}`);
    } catch (e) {
      toast('error', String(e));
    }
  }

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
    // Stop + process
    recording = false;
    busy = true;
    try {
      const path = await api.stopRecording();
      raw = await api.transcribe(path);
      processed = '';
      let finalText = raw;
      const pp = pps.find((p) => p.uuid === selectedUuid);
      if (usePP && pp) {
        toast('info', 'Post-processing running …');
        processed = await api.postprocess(pp, raw);
        activeText = 'processed';
        finalText = processed;
      }
      try {
        await api.pasteText(finalText);
        toast('success', autoPaste ? 'Done! Text pasted.' : 'Done! Text copied to clipboard.');
      } catch (e) {
        toast('error', 'Clipboard/auto-paste failed: ' + String(e));
      }
    } catch (e) {
      toast('error', String(e));
    } finally {
      busy = false;
    }
  }
</script>

<svelte:window on:click={closeMenus} />

<div class="wrap">
  <section class="text-workspace">
    <header class="workspace-header">
      <div class="record-action">
        <button class="record-control" class:recording class:busy on:click={toggle} disabled={busy} aria-label={busy ? 'Processing recording' : recording ? 'Stop recording' : 'Start recording'}>
          {#if busy}⌛{:else if recording}⏹{:else}<img src="/whispercat.svg" alt="" />{/if}
        </button>
        <span>{recording ? 'Stop' : 'Record'}</span>
      </div>
      <div class="record-status" aria-live="polite">
        <strong>{#if busy}WhisperCat is tidying your words …{:else if recording}WhisperCat is listening …{:else}WhisperCat is ready to listen.{/if}</strong>
        <span>Capture it. Clean it. Keep moving.</span>
      </div>
      <div class="header-spacer"></div>
      <details class="workflow-picker" bind:this={workflowMenu}>
        <summary>{pps.find((pp) => pp.uuid === selectedUuid)?.title ?? 'Workflow'}</summary>
        <div class="workflow-panel">
          <button class:active={!selectedUuid} on:click={() => selectPostprocessing('')}>No workflow</button>
          {#each pps as pp}
            <button class:active={pp.uuid === selectedUuid} on:click={() => selectPostprocessing(pp.uuid)}>{pp.title}</button>
          {/each}
        </div>
      </details>
      {#if config}
        <details class="quick-options" bind:this={optionsMenu}>
          <summary>Options</summary>
          <div class="quick-options-panel">
            <label><input type="checkbox" bind:checked={config.auto_paste} on:change={saveConfig} /> Paste result automatically</label>
            <label><input type="checkbox" bind:checked={config.system_audio_enabled} on:change={saveConfig} /> Record system audio</label>
            {#if config.system_audio_enabled}
              <button on:click={detectSystemAudio}>Detect active source</button>
            {/if}
          </div>
        </details>
      {/if}
    </header>

    <header class="editor-header">
      <div class="text-tabs" role="tablist" aria-label="Transcript view">
        <button class:active={activeText === 'raw'} role="tab" aria-selected={activeText === 'raw'} on:click={() => (activeText = 'raw')}>Transcript</button>
        {#if processed}
          <button class:active={activeText === 'processed'} role="tab" aria-selected={activeText === 'processed'} on:click={() => (activeText = 'processed')}>Processed</button>
        {/if}
      </div>
    </header>

      {#if activeText === 'processed' && processed}
        <textarea class="workspace-text" bind:value={processed} rows="16"></textarea>
      {:else}
        <textarea class="workspace-text" bind:value={raw} rows="16" placeholder="The transcript will appear here …"></textarea>
      {/if}
  </section>
</div>

<style>
  .wrap { width: min(100%, 1320px); margin: 0 auto; }
  .workflow-picker { position: relative; }
  .workflow-picker summary { list-style: none; padding: 8px 11px; border: 1px solid var(--border); border-radius: 8px; background: var(--bg-card); box-shadow: var(--shadow-sm); color: var(--text-dim); font-size: 13px; cursor: pointer; }
  .workflow-picker summary::-webkit-details-marker { display: none; }
  .workflow-panel { position: absolute; z-index: 10; top: calc(100% + 8px); right: 0; display: grid; min-width: 190px; padding: 6px; border: 1px solid var(--border); border-radius: 10px; background: var(--bg-card); box-shadow: var(--shadow-md); }
  .workflow-panel button { border: 0; background: transparent; box-shadow: none; color: var(--text); text-align: left; }
  .workflow-panel button:hover { background: var(--bg-subtle); }
  .workflow-panel button.active { background: var(--accent-soft); color: var(--accent); font-weight: 600; }
  .quick-options { position: relative; }
  .quick-options summary { list-style: none; padding: 8px 11px; border: 1px solid var(--border); border-radius: 8px; background: var(--bg-card); box-shadow: var(--shadow-sm); color: var(--text-dim); font-size: 13px; cursor: pointer; }
  .quick-options summary::-webkit-details-marker { display: none; }
  .quick-options-panel { position: absolute; z-index: 10; top: calc(100% + 8px); right: 0; display: grid; gap: 10px; min-width: 245px; padding: 14px; border: 1px solid var(--border); border-radius: 10px; background: var(--bg-card); box-shadow: var(--shadow-md); }
  .quick-options-panel label { display: flex; align-items: center; gap: 8px; margin: 0; color: var(--text); font-size: 13px; }
  .quick-options-panel input { width: auto; margin: 0; accent-color: var(--accent); }
  .quick-options-panel button { justify-self: start; padding: 6px 10px; font-size: 12px; }
  .text-workspace { overflow: hidden; min-height: 520px; border: 1px solid var(--border); border-radius: 12px; background: var(--bg-card); box-shadow: var(--shadow-sm); }
  .workspace-header { display: flex; align-items: center; min-height: 72px; gap: 12px; padding: 10px 16px; border-bottom: 1px solid var(--border); }
  .record-action { display: grid; justify-items: center; gap: 3px; color: var(--text-dim); font-size: 11px; font-weight: 600; }
  .record-control { flex: 0 0 auto; display: grid; width: 48px; height: 48px; place-items: center; padding: 0; border-radius: 50%; border: 1px solid var(--border-strong); background: var(--bg-card); box-shadow: var(--shadow-sm); font-size: 20px; }
  .record-control img { width: 32px; height: 32px; object-fit: contain; }
  .record-control:hover:not(:disabled) { transform: scale(1.05); border-color: var(--accent); }
  .record-control.recording { border-color: var(--danger); background: var(--danger-soft); animation: pulse 1.4s infinite; }
  .record-control.busy { border-color: var(--info); }
  @keyframes pulse {
    0%, 100% { box-shadow: 0 0 0 0 rgba(211, 61, 61, 0.34); }
    50% { box-shadow: 0 0 0 10px rgba(211, 61, 61, 0); }
  }
  .record-status { display: grid; gap: 3px; min-width: 0; }
  .record-status strong { color: var(--text); font-size: 14px; }
  .record-status span { color: var(--text-dim); font-size: 12px; }
  .header-spacer { flex: 1; }
  .editor-header { display: flex; min-height: 46px; padding: 0 18px; border-bottom: 1px solid var(--border); }
  .text-tabs { display: flex; align-self: stretch; gap: 4px; }
  .text-tabs button { align-self: stretch; border: 0; border-radius: 0; background: transparent; box-shadow: none; color: var(--text-dim); font-weight: 600; }
  .text-tabs button.active { color: var(--accent); border-bottom: 2px solid var(--accent); }
  .workspace-text { display: block; width: calc(100% - 36px); min-height: 445px; margin: 18px; border: 0; background: transparent; box-shadow: none; resize: vertical; }
  .workspace-text:focus { box-shadow: none; }
  .workspace-text:focus-visible { outline: 2px solid var(--accent); outline-offset: 3px; }
  @media (max-width: 700px) {
    .workspace-header { align-items: flex-start; flex-wrap: wrap; }
    .header-spacer { display: none; }
    .workflow-picker { margin-left: auto; }
    .quick-options { align-self: center; }
  }
</style>
