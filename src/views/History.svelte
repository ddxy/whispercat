<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import * as api from '../lib/backend';
  import { toast } from '../lib/toast';
  import type { Run } from '../lib/types';
  import RunResult from '../components/RunResult.svelte';

  let { onRecordAgain }: { onRecordAgain: () => void } = $props();
  let runs = $state<Run[]>([]);
  let selectedId = $state('');
  let refreshTimer: ReturnType<typeof setInterval> | null = null;

  const selectedRun = $derived(runs.find((run) => run.id === selectedId) ?? runs[0] ?? null);

  onMount(async () => {
    await refresh();
    refreshTimer = setInterval(() => void refresh(), 1000);
  });

  onDestroy(() => {
    if (refreshTimer) clearInterval(refreshTimer);
  });

  async function refresh() {
    try {
      runs = await api.listRuns();
    } catch (error) {
      toast('error', String(error));
    }
  }

  async function recordAgain() {
    if (!selectedRun) return;
    try {
      const config = await api.getConfig();
      config.selected_postprocessing = selectedRun.workflow_uuid;
      await api.saveConfig(config);
      onRecordAgain();
    } catch (error) {
      toast('error', String(error));
    }
  }

  async function clear() {
    if (!confirm('Clear all history entries?')) return;
    try {
      await api.clearRuns();
      runs = [];
      selectedId = '';
    } catch (error) {
      toast('error', String(error));
    }
  }

  async function openRecordingFolder() {
    if (!selectedRun?.recording_dir) return;
    try {
      await api.openRecordingFolder(selectedRun.id);
    } catch (error) {
      toast('error', String(error));
    }
  }

  function formatTime(timestamp: number) {
    return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(timestamp);
  }
</script>

<div class="history">
  <header class="page-header">
    <div>
      <h1>History</h1>
      <p>Previous recordings keep their transcript and result here.</p>
    </div>
    {#if runs.length > 0}<button class="danger" onclick={clear}>Clear history</button>{/if}
  </header>

  {#if runs.length === 0}
    <div class="empty">No recordings yet. Start from Record to create your first entry.</div>
  {:else}
    <div class="content">
      <aside aria-label="Recording history">
        {#each runs as run}
          <button class:active={selectedRun?.id === run.id} onclick={() => (selectedId = run.id)}>
            <span class="run-topline"><strong>{run.workflow_title}</strong><em class:processing={run.status === 'processing'} class:done={run.status === 'done'} class:failed={run.status === 'failed'}>{run.status}</em></span>
            <span>{formatTime(run.created_at)}</span>
          </button>
        {/each}
      </aside>
      <section class="detail">
        {#if selectedRun}
          <div class="detail-header">
            <p>{formatTime(selectedRun.created_at)}</p>
            <div class="actions">
              {#if selectedRun.recording_dir}<button onclick={openRecordingFolder}>Open folder</button>{/if}
              <button class="primary" onclick={recordAgain}>Record again</button>
            </div>
          </div>
          <RunResult run={selectedRun} />
        {/if}
      </section>
    </div>
  {/if}
</div>

<style>
  .history { width: min(100%, 1320px); margin: 0 auto; }
  .page-header { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-bottom: 24px; }
  h1 { margin: 0; font-size: 24px; letter-spacing: -0.5px; }
  .page-header p { margin: 5px 0 0; color: var(--text-dim); }
  .content { display: grid; grid-template-columns: 300px minmax(0, 1fr); gap: 18px; align-items: start; }
  aside { display: grid; gap: 5px; padding: 8px; border: 1px solid var(--border); border-radius: 12px; background: var(--bg-card); box-shadow: var(--shadow-sm); }
  aside button { display: grid; gap: 6px; width: 100%; border: 0; background: transparent; box-shadow: none; text-align: left; }
  aside button:hover:not(.active) { background: var(--bg-subtle); }
  aside button.active { background: var(--accent-soft); color: var(--accent); }
  .run-topline { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
  strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  aside button > span:last-child { color: var(--text-dim); font-size: 12px; }
  em { flex: 0 0 auto; border-radius: 999px; padding: 3px 7px; background: var(--bg-subtle); color: var(--text-dim); font-size: 10px; font-style: normal; font-weight: 600; text-transform: capitalize; }
  em.processing { color: var(--info); background: var(--accent-soft); }
  em.done { color: var(--success); background: #e8f7ee; }
  em.failed { color: var(--danger); background: var(--danger-soft); }
  .detail-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 8px; }
  .detail-header p { margin: 0; color: var(--text-dim); font-size: 12px; }
  .actions { display: flex; gap: 8px; }
  .empty { padding: 42px 24px; border: 1px dashed var(--border-strong); border-radius: 12px; color: var(--text-dim); text-align: center; }
  @media (max-width: 800px) { .content { grid-template-columns: 1fr; } aside { grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); } }
  @media (max-width: 500px) { .page-header { align-items: flex-start; flex-direction: column; } }
</style>
