<script lang="ts">
  import * as api from '../lib/backend';
  import { toast } from '../lib/toast';
  import type { Run, RunStep } from '../lib/types';

	let {
		run,
		input,
		workflowEnabled = false,
		onInputChange,
		onRunWorkflow,
	}: {
		run: Run;
		input?: string;
		workflowEnabled?: boolean;
		onInputChange?: (text: string) => void;
		onRunWorkflow?: () => void;
	} = $props();
	let activeText = $state<'transcript' | 'result'>('transcript');
	let displayedRunId = $state('');
	let selectedStepPath = $state<string | null>(null);
	let selectedStepManually = $state(false);
	const hasWorkflow = $derived(run.steps.length > 0);
	const completedSteps = $derived(run.steps.filter((step) => step.status === 'done'));
	const selectedStep = $derived(run.steps.find((step) => step.path.join('.') === selectedStepPath) ?? null);
	const latestCompletedStep = $derived(completedSteps[completedSteps.length - 1] ?? null);
	const processedText = $derived(selectedStep?.output ?? latestCompletedStep?.output ?? run.result);
	const processedTitle = $derived(selectedStep ? `Processed: ${selectedStep.label}` : 'Processed');

	$effect(() => {
		if (run.id !== displayedRunId) {
			displayedRunId = run.id;
			selectedStepManually = false;
			if (run.status === 'done' && latestCompletedStep) {
				selectedStepPath = latestCompletedStep.path.join('.');
				activeText = 'result';
			} else {
				selectedStepPath = null;
				activeText = 'transcript';
			}
		}
		if (!selectedStepManually && selectedStepPath && !completedSteps.some((step) => step.path.join('.') === selectedStepPath)) {
			selectedStepPath = null;
		}
		if (run.status === 'done' && hasWorkflow && latestCompletedStep) {
			selectedStepPath = latestCompletedStep.path.join('.');
			activeText = 'result';
		}
	});

	function selectStep(step: RunStep) {
		if (step.status !== 'done') return;
		selectedStepManually = true;
		selectedStepPath = step.path.join('.');
		activeText = 'result';
	}

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
     <div class="run-header">
       <strong>{run.workflow_title}</strong>
       {#if run.steps.length > 0}
         <div class="workflow-steps" aria-label="Workflow progress">
           {#each run.steps as step}
             <button
               class:active={selectedStepPath === step.path.join('.')}
               class:processing={step.status === 'processing'}
               class:done={step.status === 'done'}
               class:failed={step.status === 'failed'}
               disabled={step.status === 'pending'}
               aria-label={`${step.label}: ${step.status}`}
               title={`${step.label}: ${step.status}`}
               onclick={() => selectStep(step)}
             >
               {#if step.status === 'done'}
                 <svg viewBox="0 0 16 16" aria-hidden="true"><path d="m3.5 8.2 2.8 2.7 6.2-6" /></svg>
               {:else if step.status === 'failed'}
                 <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M8 3.4v5.1M8 11.8h.01" /></svg>
               {:else}
                 <i aria-hidden="true"></i>
               {/if}
             </button>
           {/each}
         </div>
       {/if}
       <span class:processing={run.status === 'processing'} class:done={run.status === 'done'} class:failed={run.status === 'failed'}>{run.status}</span>
     </div>
    {#if run.status === 'done'}
      <div class="actions">
        <button onclick={copy}>Copy</button>
      </div>
    {/if}
  </header>

   <div class="tabs" role="tablist" aria-label="Run text">
     <button class:active={activeText === 'transcript'} role="tab" aria-selected={activeText === 'transcript'} onclick={() => (activeText = 'transcript')}>Transcript</button>
      {#if hasWorkflow}
        <button class:active={activeText === 'result'} role="tab" aria-selected={activeText === 'result'} onclick={() => (activeText = 'result')}>{processedTitle}</button>
     {/if}
   </div>
   {#if activeText === 'transcript' && onInputChange}
     <textarea rows="12" value={input} oninput={(event) => onInputChange(event.currentTarget.value)} placeholder="Type or paste text to process …"></textarea>
     <div class="workflow-action"><button onclick={onRunWorkflow} disabled={!workflowEnabled}>Run workflow</button></div>
   {:else}
      <textarea readonly rows="12" value={activeText === 'result' ? processedText : run.transcript} placeholder={activeText === 'result' && hasWorkflow ? 'Waiting for first workflow step …' : run.status === 'processing' ? 'Processing recording …' : 'No text available.'}></textarea>
   {/if}
   {#if run.status === 'failed'}
     <p class="error">{run.error ?? 'Processing failed.'}</p>
   {/if}
</section>

<style>
  .result { overflow: hidden; border: 1px solid var(--border); border-radius: 12px; background: var(--bg-card); box-shadow: var(--shadow-sm); }
  header { display: flex; justify-content: space-between; align-items: center; gap: 12px; min-height: 58px; padding: 10px 16px; border-bottom: 1px solid var(--border); }
   .run-header { display: flex; align-items: center; gap: 9px; min-width: 0; }
   strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  span { border-radius: 999px; padding: 3px 7px; background: var(--bg-subtle); color: var(--text-dim); font-size: 11px; font-weight: 600; text-transform: capitalize; }
  span.processing { color: var(--info); background: var(--accent-soft); }
  span.done { color: var(--success); background: #e8f7ee; }
   span.failed { color: var(--danger); background: var(--danger-soft); }
   .workflow-steps { display: flex; align-items: center; gap: 5px; min-width: 0; overflow: hidden; }
   .workflow-steps button { position: relative; display: grid; width: 19px; height: 19px; flex: 0 0 19px; place-items: center; padding: 0; border: 1px solid var(--border-strong); border-radius: 999px; background: transparent; box-shadow: none; color: var(--text-dim); }
   .workflow-steps button:not(:last-child)::after { content: ''; position: absolute; width: 5px; height: 1px; margin-left: 25px; background: var(--border); }
   .workflow-steps button:disabled { cursor: default; opacity: 0.7; }
   .workflow-steps button.done { border-color: var(--success); background: var(--success); color: white; }
   .workflow-steps button.failed { border-color: var(--danger); background: var(--danger); color: white; }
   .workflow-steps button.active { outline: 2px solid var(--accent-soft); outline-offset: 2px; }
   .workflow-steps button.processing { border-color: var(--accent); color: var(--accent); }
   .workflow-steps svg { width: 12px; height: 12px; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
   .workflow-steps i { width: 7px; height: 7px; border-radius: 999px; background: currentColor; }
   .workflow-steps .processing i { animation: pulse 1s ease-in-out infinite; }
   @keyframes pulse { 50% { opacity: 0.25; transform: scale(0.7); } }
  .actions { display: flex; gap: 8px; }
  .actions button { padding: 6px 10px; font-size: 12px; }
  .tabs { display: flex; min-height: 46px; padding: 0 18px; gap: 4px; border-bottom: 1px solid var(--border); }
  .tabs button { border: 0; border-radius: 0; background: transparent; box-shadow: none; color: var(--text-dim); font-weight: 600; }
  .tabs button.active { color: var(--accent); border-bottom: 2px solid var(--accent); }
	  textarea { display: block; width: calc(100% - 36px); min-height: 250px; margin: 18px; border: 0; background: transparent; box-shadow: none; resize: vertical; }
	  textarea:focus { box-shadow: none; }
	  .workflow-action { display: flex; justify-content: flex-end; padding: 0 18px 18px; }
	  .workflow-action button { padding: 7px 11px; font-size: 12px; }
   .error { margin: 0 18px 18px; color: var(--danger); white-space: pre-wrap; }
   @media (max-width: 600px) { .workflow-steps { max-width: 120px; } }
</style>
