<script lang="ts">
  import * as api from '../lib/backend';
  import { toast } from '../lib/toast';
  import type { Run } from '../lib/types';

  let { run }: { run: Run } = $props();
  let activeText = $state<'transcript' | 'result'>('transcript');

  async function copy() {
    const text = run.result || run.transcript;
    if (!text) return;
    try {
      await api.copyText(text);
      toast('success', 'Copied to clipboard.');
    } catch (error) {
      toast('error', 'Clipboard failed: ' + String(error));
    }
  }

</script>

<section class="result" aria-live="polite">
  <header>
    <div>
      <strong>{run.workflow_title}</strong>
      <span class:processing={run.status === 'processing'} class:done={run.status === 'done'} class:failed={run.status === 'failed'}>{run.status}</span>
    </div>
    {#if run.status === 'done'}
      <div class="actions">
        <button onclick={copy}>Copy</button>
      </div>
    {/if}
  </header>

  {#if run.status === 'failed'}
    <p class="error">{run.error ?? 'Processing failed.'}</p>
  {:else}
    <div class="tabs" role="tablist" aria-label="Run text">
      <button class:active={activeText === 'transcript'} role="tab" aria-selected={activeText === 'transcript'} onclick={() => (activeText = 'transcript')}>Transcript</button>
      {#if run.result && run.result !== run.transcript}
        <button class:active={activeText === 'result'} role="tab" aria-selected={activeText === 'result'} onclick={() => (activeText = 'result')}>Processed</button>
      {/if}
    </div>
    <textarea readonly rows="12" value={activeText === 'result' && run.result ? run.result : run.transcript} placeholder={run.status === 'processing' ? 'Processing recording …' : 'No text available.'}></textarea>
  {/if}
</section>

<style>
  .result { overflow: hidden; border: 1px solid var(--border); border-radius: 12px; background: var(--bg-card); box-shadow: var(--shadow-sm); }
  header { display: flex; justify-content: space-between; align-items: center; gap: 12px; min-height: 58px; padding: 10px 16px; border-bottom: 1px solid var(--border); }
  header > div:first-child { display: flex; align-items: center; gap: 9px; min-width: 0; }
  strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  span { border-radius: 999px; padding: 3px 7px; background: var(--bg-subtle); color: var(--text-dim); font-size: 11px; font-weight: 600; text-transform: capitalize; }
  span.processing { color: var(--info); background: var(--accent-soft); }
  span.done { color: var(--success); background: #e8f7ee; }
  span.failed { color: var(--danger); background: var(--danger-soft); }
  .actions { display: flex; gap: 8px; }
  .actions button { padding: 6px 10px; font-size: 12px; }
  .tabs { display: flex; min-height: 46px; padding: 0 18px; gap: 4px; border-bottom: 1px solid var(--border); }
  .tabs button { border: 0; border-radius: 0; background: transparent; box-shadow: none; color: var(--text-dim); font-weight: 600; }
  .tabs button.active { color: var(--accent); border-bottom: 2px solid var(--accent); }
  textarea { display: block; width: calc(100% - 36px); min-height: 250px; margin: 18px; border: 0; background: transparent; box-shadow: none; resize: vertical; }
  textarea:focus { box-shadow: none; }
  .error { margin: 18px; color: var(--danger); white-space: pre-wrap; }
</style>
