<script lang="ts">
  import { onMount } from 'svelte';
  import * as api from '../lib/backend';
  import { toast } from '../lib/toast';
  import type { PostProcessing } from '../lib/types';

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
    current = { uuid: '', title: 'Neues Post-Processing', description: '', steps: [] };
  }

  function select(pp: PostProcessing) {
    current = JSON.parse(JSON.stringify(pp));
  }

  function addStep() {
    if (!current) return;
    current.steps = [...current.steps, { type: 'replace', from: '', to: '' }];
  }

  function removeStep(i: number) {
    if (!current) return;
    current.steps = current.steps.filter((_, x) => x !== i);
  }

  function setType(i: number, t: 'prompt' | 'replace') {
    if (!current) return;
    current.steps[i] =
      t === 'prompt'
        ? { type: 'prompt', provider: 'openai', model: 'gpt-4o-mini', system_prompt: '', user_prompt: '{{input}}' }
        : { type: 'replace', from: '', to: '' };
    current = current;
  }

  async function save() {
    if (!current) return;
    try {
      const saved = await api.upsertPostprocessing(current);
      current = JSON.parse(JSON.stringify(saved));
      pps = await api.listPostprocessings();
      toast('success', 'Gespeichert.');
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
    <button class="primary new" on:click={newPP}>＋ Neues Post-Processing</button>
    <ul>
      {#each pps as pp}
        <li>
          <button class:active={current?.uuid === pp.uuid} on:click={() => select(pp)}>
            {pp.title}
          </button>
        </li>
      {/each}
      {#if pps.length === 0}<li class="hint">Noch keine Einträge.</li>{/if}
    </ul>
  </aside>

  {#if current}
    <section class="editor">
      <div class="card">
        <label>Titel <input bind:value={current.title} /></label>
        <label>Beschreibung <input bind:value={current.description} /></label>
      </div>

      {#each current.steps as step, i}
        <div class="card">
          <div class="row" style="margin-bottom: 12px;">
            <select value={step.type} on:change={(e) => setType(i, (e.currentTarget as HTMLSelectElement).value as 'prompt' | 'replace')} style="max-width: 220px;">
              <option value="prompt">🧠 Prompt (KI)</option>
              <option value="replace">🔁 Text-Ersetzung</option>
            </select>
            <span class="spacer"></span>
            <button class="danger" on:click={() => removeStep(i)} title="Schritt entfernen">🗑</button>
          </div>

          {#if step.type === 'replace'}
            <div class="row">
              <label>Suchen nach <input bind:value={step.from} /></label>
              <label>Ersetzen durch <input bind:value={step.to} /></label>
            </div>
          {:else}
            <div class="row">
              <label>
                Provider
                <select bind:value={step.provider}>
                  <option value="openai">OpenAI</option>
                  <option value="open-webui">Open WebUI</option>
                </select>
              </label>
              <label>Modell <input bind:value={step.model} placeholder="gpt-4o-mini" /></label>
            </div>
            <label style="margin-top: 12px;">System-Prompt <textarea rows="2" bind:value={step.system_prompt}></textarea></label>
            <label>User-Prompt (mit &#123;&#123;input&#125;&#125; als Platzhalter für den vorigen Text) <textarea rows="3" bind:value={step.user_prompt}></textarea></label>
          {/if}
        </div>
      {/each}

      <div class="row">
        <button on:click={addStep}>＋ Schritt hinzufügen</button>
        <span class="spacer"></span>
        <button class="danger" on:click={remove}>Löschen</button>
        <button class="primary" on:click={save}>💾 Speichern</button>
      </div>
    </section>
  {/if}
</div>

<style>
  .columns { display: flex; gap: 18px; max-width: 900px; margin: 0 auto; align-items: flex-start; }
  aside { width: 240px; flex-shrink: 0; }
  aside ul { list-style: none; padding: 0; margin: 12px 0 0; display: flex; flex-direction: column; gap: 6px; }
  aside button { width: 100%; text-align: left; }
  aside button.active { background: var(--accent); border-color: var(--accent); color: #fff; }
  .editor { flex: 1; }
</style>
