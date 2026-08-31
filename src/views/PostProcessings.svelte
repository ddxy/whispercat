<script lang="ts">
  import { onMount } from 'svelte';
  import * as api from '../lib/backend';
  import { toast } from '../lib/toast';
  import type { PostProcessing, Step } from '../lib/types';

  let pps: PostProcessing[] = [];
  let current: PostProcessing | null = null;

  onMount(async () => {
    try {
      pps = await api.listPostprocessings();
    } catch (e) {
      toast('error', String(e));
    }
  });

  function newPP() {
    current = { uuid: '', title: 'New workflow', description: '', steps: [] };
  }

  function select(pp: PostProcessing) {
    current = JSON.parse(JSON.stringify(pp));
  }

  function addStep() {
    if (!current) return;
    current.steps = [...current.steps, { type: 'replace', from: '', to: '' }];
  }

  function addGroup() {
    if (!current) return;
    current.steps = [...current.steps, { type: 'group', title: 'New group', steps: [] }];
  }

  function addStepToGroup(group: Extract<Step, { type: 'group' }>) {
    group.steps = [...group.steps, { type: 'replace', from: '', to: '' }];
    current = current;
  }

  function removeStep(i: number) {
    if (!current) return;
    current.steps = current.steps.filter((_, x) => x !== i);
  }

  function removeGroupStep(group: Extract<Step, { type: 'group' }>, i: number) {
    group.steps = group.steps.filter((_, x) => x !== i);
    current = current;
  }

  type EditableStepType = 'n8n' | 'prompt' | 'replace' | 'screenshot';

  function newStep(type: EditableStepType): Exclude<Step, { type: 'group' }> {
    if (type === 'n8n') return { type: 'n8n', path: '' };
    if (type === 'screenshot') return { type: 'screenshot', target: 'webhook', path: '' };
    if (type === 'prompt') {
      return { type: 'prompt', provider: 'openai', model: 'gpt-4o-mini', system_prompt: '', user_prompt: '{{input}}' };
    }
    return { type: 'replace', from: '', to: '' };
  }

  function setType(i: number, t: EditableStepType) {
    if (!current) return;
    current.steps[i] = newStep(t);
    current = current;
  }

  function setGroupStepType(group: Extract<Step, { type: 'group' }>, i: number, t: EditableStepType) {
    group.steps[i] = newStep(t);
    current = current;
  }

  function setScreenshotTarget(step: Extract<Step, { type: 'screenshot' }>, target: 'webhook' | 'folder') {
    const repeat = step.repeat;
    const repeatInterval = step.repeat_interval_seconds;
    delete (step as { path?: string }).path;
    delete (step as { folder?: string }).folder;
    Object.assign(step, target === 'webhook'
      ? { type: 'screenshot', target, path: '', repeat, repeat_interval_seconds: repeatInterval }
      : { type: 'screenshot', target, folder: '', repeat, repeat_interval_seconds: repeatInterval });
    current = current;
  }

  function setRepeat(step: Step, value: string) {
    step.repeat = value === 'infinite'
      ? 'infinite'
      : Math.min(1000, Math.max(1, Number(value) || 1));
    current = current;
  }

  function repeatValue(step: Step) {
    return step.repeat === 'infinite' ? 'infinite' : String(step.repeat ?? 1);
  }

  function setRepeatInterval(step: Step, value: string) {
    const seconds = Number(value);
    step.repeat_interval_seconds = Number.isInteger(seconds) && seconds > 0
      ? Math.min(86400, seconds)
      : undefined;
    current = current;
  }

  function repeatIntervalValue(step: Step) {
    return step.repeat_interval_seconds ? String(step.repeat_interval_seconds) : '';
  }

  async function save() {
    if (!current) return;
    try {
      const saved = await api.upsertPostprocessing(current);
      current = JSON.parse(JSON.stringify(saved));
      pps = await api.listPostprocessings();
      toast('success', 'Saved.');
    } catch (e) {
      toast('error', String(e));
    }
  }

  async function remove() {
    if (!current) return;
    try {
      if (current.uuid) await api.deletePostprocessing(current.uuid);
      current = null;
      pps = await api.listPostprocessings();
    } catch (e) {
      toast('error', String(e));
    }
  }
</script>

<div class="columns">
  <aside>
    <button class="primary new" on:click={newPP}>＋ New workflow</button>
    <ul>
      {#each pps as pp}
        <li>
          <button class:active={current?.uuid === pp.uuid} on:click={() => select(pp)}>
            {pp.title}
          </button>
        </li>
      {/each}
      {#if pps.length === 0}<li class="hint">No entries yet.</li>{/if}
    </ul>
  </aside>

  {#if current}
    <section class="editor">
      <div class="card">
        <label>Title <input bind:value={current.title} /></label>
        <label>Description <input bind:value={current.description} /></label>
      </div>

      {#each current.steps as step, i}
        <div class="card">
          <div class="row" style="margin-bottom: 12px;">
            {#if step.type === 'group'}
              <strong>Group</strong>
            {:else}
              <select value={step.type} on:change={(e) => setType(i, (e.currentTarget as HTMLSelectElement).value as EditableStepType)} style="max-width: 220px;">
                <option value="prompt">🧠 Prompt (AI)</option>
                <option value="replace">🔁 Text replacement</option>
                <option value="n8n">n8n webhook</option>
                <option value="screenshot">Screenshot</option>
              </select>
            {/if}
            <label class="repeat">Repeat <select value={repeatValue(step)} on:change={(e) => setRepeat(step, (e.currentTarget as HTMLSelectElement).value)}>
              <option value="1">1</option><option value="2">2</option><option value="3">3</option><option value="5">5</option><option value="10">10</option><option value="25">25</option><option value="50">50</option><option value="100">100</option><option value="1000">1000</option><option value="infinite">∞</option>
            </select></label>
            <label class="repeat">Every <input type="number" min="1" max="86400" step="1" placeholder="0" value={repeatIntervalValue(step)} on:input={(e) => setRepeatInterval(step, (e.currentTarget as HTMLInputElement).value)} /> sec</label>
            <span class="spacer"></span>
            <button class="danger" on:click={() => removeStep(i)} title="Remove step" aria-label="Remove step">🗑</button>
          </div>

          {#if step.type === 'group'}
            <label>Group title <input bind:value={step.title} placeholder="Iterative cleanup" /></label>
            {#if step.repeat === 'infinite'}<p class="hint">Repeats until a full group pass leaves output unchanged, up to 1000 step executions per workflow run.</p>{/if}
            <div class="group-steps">
              {#each step.steps as groupStep, groupIndex}
                <div class="group-step">
                  <div class="row" style="margin-bottom: 12px;">
                    <select value={groupStep.type} on:change={(e) => setGroupStepType(step, groupIndex, (e.currentTarget as HTMLSelectElement).value as EditableStepType)} style="max-width: 220px;">
                      <option value="prompt">🧠 Prompt (AI)</option>
                      <option value="replace">🔁 Text replacement</option>
                      <option value="n8n">n8n webhook</option>
                      <option value="screenshot">Screenshot</option>
                    </select>
                    <label class="repeat">Repeat <select value={repeatValue(groupStep)} on:change={(e) => setRepeat(groupStep, (e.currentTarget as HTMLSelectElement).value)}>
                      <option value="1">1</option><option value="2">2</option><option value="3">3</option><option value="5">5</option><option value="10">10</option><option value="25">25</option><option value="50">50</option><option value="100">100</option><option value="1000">1000</option><option value="infinite">∞</option>
                    </select></label>
                    <label class="repeat">Every <input type="number" min="1" max="86400" step="1" placeholder="0" value={repeatIntervalValue(groupStep)} on:input={(e) => setRepeatInterval(groupStep, (e.currentTarget as HTMLInputElement).value)} /> sec</label>
                    <span class="spacer"></span>
                    <button class="danger" on:click={() => removeGroupStep(step, groupIndex)} title="Remove step" aria-label="Remove step">🗑</button>
                  </div>
                  {#if groupStep.type === 'replace'}
                    <div class="row"><label>Search for <input bind:value={groupStep.from} /></label><label>Replace with <input bind:value={groupStep.to} /></label></div>
                  {:else if groupStep.type === 'prompt'}
                    <div class="row"><label>Provider <select bind:value={groupStep.provider}><option value="openai">OpenAI</option><option value="custom">Custom AI</option></select></label><label>Model <input bind:value={groupStep.model} placeholder="gpt-4o-mini" /></label></div>
                    <label style="margin-top: 12px;">System prompt <textarea rows="2" bind:value={groupStep.system_prompt}></textarea></label>
                    <label>User prompt (with &#123;&#123;input&#125;&#125; as a placeholder for the previous text) <textarea rows="3" bind:value={groupStep.user_prompt}></textarea></label>
                  {:else if groupStep.type === 'n8n'}
                    <label>Webhook path <input bind:value={groupStep.path} placeholder="/webhook/clean-transcript" /></label>
                  {:else if groupStep.type === 'screenshot'}
                    <label>Destination <select value={groupStep.target} on:change={(e) => setScreenshotTarget(groupStep, (e.currentTarget as HTMLSelectElement).value as 'webhook' | 'folder')}><option value="webhook">n8n webhook</option><option value="folder">Save to folder</option></select></label>
                    {#if groupStep.target === 'webhook'}
                      <label>Webhook path <input bind:value={groupStep.path} placeholder="/webhook/screenshot" /></label>
                    {:else}
                      <label>Folder path <input bind:value={groupStep.folder} placeholder="/home/user/Pictures/WhisperCat" /></label>
                    {/if}
                    <p class="hint">At workflow start, select monitors once. PNG files include a timestamp and monitor number. Webhooks receive files plus current text; saving leaves text unchanged.</p>
                  {:else}
                    <p class="hint">Nested groups run correctly but cannot be edited here.</p>
                  {/if}
                </div>
              {/each}
            </div>
            <button on:click={() => addStepToGroup(step)}>＋ Add step to group</button>
          {:else if step.type === 'replace'}
            <div class="row">
              <label>Search for <input bind:value={step.from} /></label>
              <label>Replace with <input bind:value={step.to} /></label>
            </div>
          {:else if step.type === 'prompt'}
            <div class="row">
              <label>
                Provider
                <select bind:value={step.provider}>
                  <option value="openai">OpenAI</option>
                  <option value="custom">Custom AI</option>
                </select>
              </label>
              <label>Model <input bind:value={step.model} placeholder="gpt-4o-mini" /></label>
            </div>
            <label style="margin-top: 12px;">System prompt <textarea rows="2" bind:value={step.system_prompt}></textarea></label>
            <label>User prompt (with &#123;&#123;input&#125;&#125; as a placeholder for the previous text) <textarea rows="3" bind:value={step.user_prompt}></textarea></label>
          {:else if step.type === 'n8n'}
            <label>Webhook path <input bind:value={step.path} placeholder="/webhook/clean-transcript" /></label>
            <p class="hint">Combined with global n8n instance URL from Settings. Receives <code>{'{ "text": "..." }'}</code> and must return <code>{'{ "text": "..." }'}</code>.</p>
          {:else if step.type === 'screenshot'}
            <label>Destination <select value={step.target} on:change={(e) => setScreenshotTarget(step, (e.currentTarget as HTMLSelectElement).value as 'webhook' | 'folder')}><option value="webhook">n8n webhook</option><option value="folder">Save to folder</option></select></label>
            {#if step.target === 'webhook'}
              <label>Webhook path <input bind:value={step.path} placeholder="/webhook/screenshot" /></label>
            {:else}
              <label>Folder path <input bind:value={step.folder} placeholder="/home/user/Pictures/WhisperCat" /></label>
            {/if}
            <p class="hint">At workflow start, select monitors once. PNG files include a timestamp and monitor number. Webhooks receive files plus current text; saving leaves text unchanged.</p>
          {/if}
        </div>
      {/each}

      <div class="row">
        <button on:click={addStep}>＋ Add step</button>
        <button on:click={addGroup}>＋ Add group</button>
        <span class="spacer"></span>
        <button class="danger" on:click={remove}>Delete</button>
        <button class="primary" on:click={save}>💾 Save</button>
      </div>
    </section>
  {/if}
</div>

<style>
  .columns { display: flex; gap: 18px; max-width: 900px; margin: 0 auto; align-items: flex-start; }
  aside { width: 240px; flex-shrink: 0; padding: 10px; background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius); box-shadow: var(--shadow-sm); }
  aside ul { list-style: none; padding: 0; margin: 12px 0 0; display: flex; flex-direction: column; gap: 3px; }
  aside button { width: 100%; text-align: left; border: 0; background: transparent; box-shadow: none; }
  aside button:hover:not(.active) { background: var(--bg-subtle); }
  aside button.active { background: var(--accent-soft); color: var(--accent); font-weight: 600; }
  aside button.primary { background: var(--accent); color: #fff; }
  aside button:focus:not(:focus-visible) { outline: none; }
  .editor { flex: 1; }
  .repeat { display: flex; align-items: center; gap: 6px; margin: 0; color: var(--text-dim); font-size: 12px; white-space: nowrap; }
   .repeat select, .repeat input { width: auto; min-width: 58px; padding: 5px 7px; }
   .repeat input { width: 64px; }
  .group-steps { display: grid; gap: 10px; margin: 14px 0; }
  .group-step { padding: 14px; border: 1px solid var(--border); border-radius: 8px; background: var(--bg-subtle); }
  @media (max-width: 700px) {
    .columns { flex-direction: column; }
    aside { width: 100%; }
  }
</style>
