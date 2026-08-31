<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import * as api from '../lib/backend';
  import { toast } from '../lib/toast';
  import type { Config, PostProcessing, Run } from '../lib/types';
  import RunResult from '../components/RunResult.svelte';

  let recording = false;
  let stopping = false;
  let pps: PostProcessing[] = [];
  let selectedUuid = '';
  let config: Config | null = null;
  let currentRun: Run | null = null;
  let manualText = '';
  let optionsMenu: HTMLDetailsElement;
  let workflowMenu: HTMLDetailsElement;
  let unlisten: (() => void) | null = null;
  let unlistenProcess: (() => void) | null = null;
  let refreshTimer: ReturnType<typeof setInterval> | null = null;

  onMount(async () => {
    try {
      config = await api.getConfig();
      pps = await api.listPostprocessings();
      selectedUuid = config.selected_postprocessing ?? '';
      await refreshCurrentRun();
    } catch (error) {
      toast('error', String(error));
    }
    unlisten = await api.onHotkey(() => toggle());
    unlistenProcess = await api.onSelectedPostprocessingChanged((uuid) => {
      selectedUuid = uuid ?? '';
    });
    refreshTimer = setInterval(() => void refreshCurrentRun(), 1000);
  });

  onDestroy(() => {
    unlisten?.();
    unlistenProcess?.();
    if (refreshTimer) clearInterval(refreshTimer);
  });

  async function refreshCurrentRun() {
    if (!currentRun) return;
    const runs = await api.listRuns();
    currentRun = runs.find((run) => run.id === currentRun?.id) ?? currentRun;
  }

  async function saveConfig() {
    if (!config) return;
    try {
      await api.saveConfig(config);
    } catch (error) {
      toast('error', String(error));
    }
  }

  async function selectPostprocessing(uuid: string) {
    selectedUuid = uuid;
    if (config) {
      config.selected_postprocessing = uuid || null;
      await saveConfig();
    }
    workflowMenu.open = false;
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
    } catch (error) {
      toast('error', String(error));
    }
  }

  async function toggle() {
    if (stopping) return;
    if (!recording) {
      try {
        await api.startRecording();
        recording = true;
      } catch (error) {
        toast('error', String(error));
      }
      return;
    }

    recording = false;
    stopping = true;
    try {
      const path = await api.stopRecording();
      const workflow = pps.find((pp) => pp.uuid === selectedUuid) ?? null;
      currentRun = await api.queueRun(path, workflow);
      toast('info', 'Recording queued. Processing continues in History.');
    } catch (error) {
      toast('error', String(error));
    } finally {
      stopping = false;
    }
  }

  async function runWorkflow() {
    const workflow = pps.find((pp) => pp.uuid === selectedUuid);
    if (!workflow) return;
    try {
      currentRun = await api.queueTextRun(manualText, workflow);
      toast('info', 'Workflow queued. Processing continues in History.');
    } catch (error) {
      toast('error', String(error));
    }
  }
</script>

<svelte:window on:click={closeMenus} />

<div class="wrap">
  <section class="recording-card">
    <header>
      <div class="record-action">
        <button class="record-control" class:recording class:stopping on:click={toggle} disabled={stopping} aria-label={stopping ? 'Stopping recording' : recording ? 'Stop recording' : 'Start recording'}>
          {#if stopping}⌛{:else if recording}⏹{:else}<img src="/whispercat.svg" alt="" />{/if}
        </button>
        <span>{recording ? 'Stop' : 'Record'}</span>
      </div>
      <div class="record-status" aria-live="polite">
        <strong>{#if stopping}Saving recording …{:else if recording}WhisperCat is listening …{:else}WhisperCat is ready to listen.{/if}</strong>
        <span>{recording ? 'Stop when you are finished. Previous runs keep processing.' : 'Capture it. Clean it. Keep moving.'}</span>
      </div>
      <div class="header-spacer"></div>
      <details class="workflow-picker" bind:this={workflowMenu}>
        <summary>{pps.find((pp) => pp.uuid === selectedUuid)?.title ?? 'No workflow'}</summary>
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
            <label><input type="checkbox" bind:checked={config.system_audio_enabled} on:change={saveConfig} /> Record system audio</label>
            {#if config.system_audio_enabled}<button on:click={detectSystemAudio}>Detect active source</button>{/if}
          </div>
        </details>
      {/if}
    </header>
  </section>

  <section class:has-content={manualText.trim().length > 0} class="manual-input">
    <label for="workflow-input">Transcript / input</label>
    <textarea id="workflow-input" bind:value={manualText} rows="6" placeholder="Type or paste text to process …"></textarea>
    <div class="manual-actions">
      <button on:click={runWorkflow} disabled={!selectedUuid}>Run workflow</button>
    </div>
  </section>

  {#if currentRun}
    <div class="latest-run">
      <p class="section-label">Latest recording</p>
      <RunResult run={currentRun} />
    </div>
  {/if}
</div>

<style>
  .wrap { width: min(100%, 1320px); margin: 0 auto; }
  .recording-card { overflow: visible; border: 1px solid var(--border); border-radius: 12px; background: var(--bg-card); box-shadow: var(--shadow-sm); }
  header { display: flex; align-items: center; min-height: 72px; gap: 12px; padding: 10px 16px; }
  .record-action { display: grid; justify-items: center; gap: 3px; color: var(--text-dim); font-size: 11px; font-weight: 600; }
  .record-control { display: grid; width: 48px; height: 48px; place-items: center; padding: 0; border-radius: 50%; border: 1px solid var(--border-strong); background: var(--bg-card); font-size: 20px; }
  .record-control img { width: 32px; height: 32px; object-fit: contain; }
  .record-control:hover:not(:disabled) { transform: scale(1.05); border-color: var(--accent); }
  .record-control.recording { border-color: var(--danger); background: var(--danger-soft); animation: pulse 1.4s infinite; }
  .record-control.stopping { border-color: var(--info); }
  @keyframes pulse { 0%, 100% { box-shadow: 0 0 0 0 rgba(211, 61, 61, 0.34); } 50% { box-shadow: 0 0 0 10px rgba(211, 61, 61, 0); } }
  .record-status { display: grid; gap: 3px; min-width: 0; }
  .record-status strong { color: var(--text); font-size: 14px; }
  .record-status span { color: var(--text-dim); font-size: 12px; }
  .header-spacer { flex: 1; }
  .workflow-picker, .quick-options { position: relative; }
  .workflow-picker summary, .quick-options summary { list-style: none; padding: 8px 11px; border: 1px solid var(--border); border-radius: 8px; background: var(--bg-card); box-shadow: var(--shadow-sm); color: var(--text-dim); font-size: 13px; cursor: pointer; }
  summary::-webkit-details-marker { display: none; }
  .workflow-panel, .quick-options-panel { position: absolute; z-index: 10; top: calc(100% + 8px); right: 0; display: grid; min-width: 190px; padding: 6px; border: 1px solid var(--border); border-radius: 10px; background: var(--bg-card); box-shadow: var(--shadow-md); }
  .workflow-panel button { border: 0; background: transparent; box-shadow: none; text-align: left; }
  .workflow-panel button:hover { background: var(--bg-subtle); }
  .workflow-panel button.active { background: var(--accent-soft); color: var(--accent); font-weight: 600; }
  .quick-options-panel { gap: 10px; min-width: 245px; padding: 14px; }
  .quick-options-panel label { display: flex; align-items: center; gap: 8px; margin: 0; color: var(--text); font-size: 13px; }
  .quick-options-panel input { width: auto; margin: 0; accent-color: var(--accent); }
  .quick-options-panel button { justify-self: start; padding: 6px 10px; font-size: 12px; }
  .latest-run { margin-top: 26px; }
  .manual-input { display: grid; gap: 8px; margin-top: 18px; padding: 16px; border: 1px solid var(--border); border-radius: 12px; background: var(--bg-card); box-shadow: var(--shadow-sm); }
  .manual-input label { color: var(--text-dim); font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.06em; }
  .manual-input textarea { min-height: 118px; margin: 0; resize: vertical; }
  .manual-actions { display: flex; justify-content: flex-end; max-height: 0; overflow: hidden; opacity: 0; transition: max-height 140ms ease, opacity 140ms ease; }
  .manual-input:focus-within .manual-actions, .manual-input.has-content .manual-actions { max-height: 40px; opacity: 1; }
  .manual-actions button { padding: 7px 11px; font-size: 12px; }
  .section-label { margin: 0 0 8px; color: var(--text-dim); font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.06em; }
  @media (max-width: 700px) { header { align-items: flex-start; flex-wrap: wrap; } .header-spacer { display: none; } .workflow-picker { margin-left: auto; } .quick-options { align-self: center; } }
</style>
