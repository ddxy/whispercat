<script lang="ts">
  import { toasts } from './lib/toast';
</script>

<div class="toasts">
  {#each $toasts as t (t.id)}
    <div class="toast {t.type}">{t.msg}</div>
  {/each}
</div>

<style>
  .toasts {
    position: fixed; bottom: 14px; right: 14px; z-index: 100;
    display: flex; flex-direction: column; gap: 8px; max-width: 380px;
    pointer-events: none;
  }
  .toast {
    padding: 10px 16px; border-radius: 10px; color: #fff; font-size: 13px;
    box-shadow: 0 4px 18px rgba(0, 0, 0, 0.4); animation: slidein 0.2s ease-out;
  }
  .toast.success { background: var(--success); }
  .toast.info { background: var(--info); }
  .toast.error { background: var(--danger); }
  @keyframes slidein {
    from { transform: translateX(30px); opacity: 0; }
    to { transform: translateX(0); opacity: 1; }
  }
</style>
