<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import * as api from '../lib/backend';
  import { toast } from '../lib/toast';
  import type { Config, PostProcessing, Run } from '../lib/types';
  import RunResult from '../components/RunResult.svelte';

  let recording = false;
  let recordingSystemAudio = false;
  let stopping = false;
  let pps: PostProcessing[] = [];
  let selectedUuid = '';
  let config: Config | null = null;
  let currentRun: Run | null = null;
  let pendingTranscriptRunId = '';
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
    if (currentRun.id === pendingTranscriptRunId && currentRun.transcript) {
      manualText = currentRun.transcript;
      pendingTranscriptRunId = '';
    }
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
        recordingSystemAudio = await api.startRecording();
        recording = true;
      } catch (error) {
        toast('error', String(error));
      }
      return;
    }

    recording = false;
    recordingSystemAudio = false;
    stopping = true;
    try {
      const recording = await api.stopRecording();
      const workflow = pps.find((pp) => pp.uuid === selectedUuid) ?? null;
      currentRun = await api.queueRun(recording, workflow);
      pendingTranscriptRunId = currentRun.id;
      toast('info', 'Recording queued. Finalizing and processing continue in History.');
    } catch (error) {
      toast('error', String(error));
    } finally {
      stopping = false;
    }
  }

  async function discardRecording() {
    if (!recording || stopping) return;
    recording = false;
    recordingSystemAudio = false;
    stopping = true;
    try {
      await api.discardRecording();
      toast('info', 'Recording discarded.');
    } catch (error) {
      toast('error', String(error));
    } finally {
      stopping = false;
    }
  }

  async function runWorkflow() {
    const workflow = pps.find((pp) => pp.uuid === selectedUuid);
    if (!workflow || !manualText.trim()) return;
    try {
      currentRun = await api.queueTextRun(manualText, workflow);
      pendingTranscriptRunId = '';
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
        {#if recording}
          <button class="discard-recording" on:click={discardRecording} disabled={stopping}>Discard</button>
        {/if}
        <div class="record-status" aria-live="polite">
        <strong>{#if stopping}Saving recording …{:else if recording}WhisperCat is listening …{:else}WhisperCat is ready to listen.{/if}</strong>
        <span>
          {#if recording && recordingSystemAudio}
            <span class="system-audio-indicator" title="System audio is being recorded" aria-label="System audio is being recorded">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 10v4h4l5 4V6l-5 4H4Zm12.5 2a4.5 4.5 0 0 0-2.2-3.86v1.88a2.75 2.75 0 0 1 0 3.96v1.88A4.5 4.5 0 0 0 16.5 12Zm0-8.3v1.84a7.1 7.1 0 0 1 0 12.92v1.84a8.85 8.85 0 0 0 0-16.6Z" /></svg>
              System audio
            </span>
          {:else}
            {recording ? 'Stop when you are finished. Previous runs keep processing.' : 'Capture it. Clean it. Keep moving.'}
          {/if}
        </span>
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

  {#if currentRun}
    <div class="latest-run">
      <RunResult
        run={currentRun}
        input={manualText}
        workflowEnabled={Boolean(selectedUuid && manualText.trim())}
        onInputChange={(text) => (manualText = text)}
        onRunWorkflow={runWorkflow}
      />
    </div>
  {:else}
    <section class="manual-input">
      <label for="workflow-input">Transcript / input</label>
      <textarea id="workflow-input" bind:value={manualText} rows="6" placeholder="Type or paste text to process …"></textarea>
      <div class="manual-actions">
        <button on:click={runWorkflow} disabled={!selectedUuid || !manualText.trim()}>Run workflow</button>
      </div>
    </section>
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
   .discard-recording { color: var(--danger); border-color: var(--danger-soft); padding: 6px 10px; font-size: 12px; }
   .discard-recording:hover:not(:disabled) { background: var(--danger); border-color: var(--danger); color: #fff; }
  @keyframes pulse { 0%, 100% { box-shadow: 0 0 0 0 rgba(211, 61, 61, 0.34); } 50% { box-shadow: 0 0 0 10px rgba(211, 61, 61, 0); } }
   .record-status { display: grid; gap: 3px; min-width: 0; }
   .record-status strong { color: var(--text); font-size: 14px; }
   .record-status span { color: var(--text-dim); font-size: 12px; }
   .record-status .system-audio-indicator { display: inline-flex; align-items: center; gap: 5px; color: var(--accent); font-weight: 600; }
   .system-audio-indicator svg { width: 14px; height: 14px; fill: currentColor; }
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
  .latest-run { margin-top: 18px; }
  .manual-input { display: grid; gap: 8px; margin-top: 18px; padding: 16px; border: 1px solid var(--border); border-radius: 12px; background: var(--bg-card); box-shadow: var(--shadow-sm); }
  .manual-input label { color: var(--text-dim); font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.06em; }
  .manual-input textarea { min-height: 118px; margin: 0; resize: vertical; }
  .manual-actions { display: flex; justify-content: flex-end; }
  .manual-actions button { padding: 7px 11px; font-size: 12px; }
  @media (max-width: 700px) { header { align-items: flex-start; flex-wrap: wrap; } .header-spacer { display: none; } .workflow-picker { margin-left: auto; } .quick-options { align-self: center; } }
</style>
