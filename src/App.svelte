<script lang="ts">
  import Record from './views/Record.svelte';
  import Settings from './views/Settings.svelte';
  import PostProcessings from './views/PostProcessings.svelte';
  import Toast from './Toast.svelte';

  let tab: 'record' | 'pp' | 'settings' = 'record';
</script>

<div class="shell">
  <header>
    <h1>🐱 WhisperCat</h1>
    <nav>
      <button class:active={tab === 'record'} on:click={() => (tab = 'record')}>🎙 Recording</button>
      <button class:active={tab === 'pp'} on:click={() => (tab = 'pp')}>⚙ Post-Processing</button>
      <button class:active={tab === 'settings'} on:click={() => (tab = 'settings')}>🔧 Settings</button>
    </nav>
  </header>

  <main>
    {#if tab === 'record'}
      <Record />
    {:else if tab === 'pp'}
      <PostProcessings />
    {:else}
      <Settings />
    {/if}
  </main>
</div>

<Toast />

<style>
  .shell { display: flex; flex-direction: column; height: 100%; }
  header {
    display: flex; align-items: center; justify-content: space-between;
    padding: 10px 20px; border-bottom: 1px solid var(--border);
    background: rgba(42, 44, 55, 0.6); backdrop-filter: blur(8px);
  }
  h1 { font-size: 17px; margin: 0; }
  nav { display: flex; gap: 8px; }
  nav button.active { background: var(--accent); border-color: var(--accent); color: #fff; }
  main { flex: 1; overflow-y: auto; padding: 22px; }
</style>
