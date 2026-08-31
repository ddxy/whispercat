<script lang="ts">
  import Record from './views/Record.svelte';
  import Settings from './views/Settings.svelte';
  import PostProcessings from './views/PostProcessings.svelte';
  import History from './views/History.svelte';
  import Toast from './Toast.svelte';

  let tab: 'record' | 'history' | 'pp' | 'settings' = 'record';
</script>

<div class="shell">
  <aside class="sidebar">
    <div class="brand">
      <div class="brand-mark"><img src="/whispercat.svg" alt="" /></div>
      <span>WhisperCat</span>
    </div>

    <nav aria-label="Main navigation">
      <button class:active={tab === 'record'} aria-current={tab === 'record' ? 'page' : undefined} on:click={() => (tab = 'record')}>
        <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="9" y="2.5" width="6" height="11" rx="3"/><path d="M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5v4M8.5 21.5h7"/></svg>
        <span>Record</span>
      </button>
      <button class:active={tab === 'history'} aria-current={tab === 'history' ? 'page' : undefined} on:click={() => (tab = 'history')}>
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 7v5l3.5 2M20 12a8 8 0 1 1-2.34-5.66L20 8.67M20 4v4.67h-4.67"/></svg>
        <span>History</span>
      </button>
      <button class:active={tab === 'pp'} aria-current={tab === 'pp' ? 'page' : undefined} on:click={() => (tab = 'pp')}>
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 5.5h16M4 12h16M4 18.5h10"/><circle cx="17" cy="18.5" r="3"/></svg>
        <span>Workflows</span>
      </button>
      <button class:active={tab === 'settings'} aria-current={tab === 'settings' ? 'page' : undefined} on:click={() => (tab = 'settings')}>
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.12 2.12-.06-.06a1.7 1.7 0 0 0-1.88-.34 1.7 1.7 0 0 0-1 1.56v.08h-3v-.08a1.7 1.7 0 0 0-1-1.56A1.7 1.7 0 0 0 8.9 19l-.06.06-2.12-2.12.06-.06A1.7 1.7 0 0 0 7.12 15a1.7 1.7 0 0 0-1.56-1H5.5v-3h.06a1.7 1.7 0 0 0 1.56-1 1.7 1.7 0 0 0-.34-1.88l-.06-.06L8.84 5.94l.06.06A1.7 1.7 0 0 0 10.78 6a1.7 1.7 0 0 0 1-1.56v-.08h3v.08a1.7 1.7 0 0 0 1 1.56 1.7 1.7 0 0 0 1.88-.34l.06-.06 2.12 2.12-.06.06A1.7 1.7 0 0 0 19.44 10a1.7 1.7 0 0 0 1.56 1h.08v3H21a1.7 1.7 0 0 0-1.6 1Z"/></svg>
        <span>Settings</span>
      </button>
    </nav>

    <p class="sidebar-note">Your quiet writing companion.</p>
  </aside>

  <main>
    <div hidden={tab !== 'record'}>
      <Record />
    </div>
    {#if tab === 'history'}
      <History onRecordAgain={() => (tab = 'record')} />
    {:else if tab === 'pp'}
      <PostProcessings />
    {:else if tab === 'settings'}
      <Settings />
    {/if}
  </main>
</div>

<Toast />

<style>
	:global(html), :global(body), :global(#app) { height: 100%; }
	:global(#app) { min-height: 100dvh; }
	.shell { display: grid; grid-template-columns: 224px minmax(0, 1fr); min-height: 100dvh; }
  .sidebar {
    grid-column: 1; grid-row: 1; display: flex; flex-direction: column;
    padding: 22px 14px; background: var(--bg-card); border-right: 1px solid var(--border);
  }
  .brand { display: flex; align-items: center; gap: 10px; padding: 0 10px; font-size: 16px; font-weight: 700; letter-spacing: -0.35px; }
  .brand-mark { display: grid; width: 31px; height: 31px; place-items: center; overflow: hidden; border-radius: 9px; background: var(--accent); }
  .brand-mark img { width: 100%; height: 100%; object-fit: cover; }
  nav { display: flex; flex-direction: column; gap: 4px; margin-top: 34px; }
  nav button { display: flex; align-items: center; gap: 11px; width: 100%; border: 0; background: transparent; box-shadow: none; color: var(--text-dim); text-align: left; padding: 9px 10px; }
  nav button:hover:not(.active) { background: var(--bg-subtle); color: var(--text); }
  nav button.active { background: var(--accent-soft); color: var(--accent); font-weight: 600; }
  nav svg { width: 18px; height: 18px; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
  .sidebar-note { margin: auto 10px 2px; color: var(--text-dim); font-size: 12px; line-height: 1.5; }
  main { grid-column: 2; grid-row: 1; min-width: 0; overflow-y: auto; padding: 44px 52px; }
  @media (max-width: 700px) {
    .shell { display: flex; flex-direction: column; }
    .sidebar { width: 100%; flex: none; padding: 12px 16px; border-right: 0; border-bottom: 1px solid var(--border); }
    .brand { padding: 0; }
    nav { flex-direction: row; margin-top: 12px; }
    nav button { justify-content: center; }
    .sidebar-note { display: none; }
    main { padding: 28px 18px; }
  }
</style>
