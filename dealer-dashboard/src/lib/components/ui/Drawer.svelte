<script lang="ts">
  import { fade, fly } from 'svelte/transition';
  import { quintOut } from 'svelte/easing';

  export let open = false;
  export let onClose: () => void = () => {};
  export let title: string = '';
  export let width: string = 'max-w-md';

  function handleKey(e: KeyboardEvent) {
    if (e.key === 'Escape') onClose();
  }
</script>

<svelte:window on:keydown={handleKey} />

{#if open}
  <div
    class="fixed inset-0 z-40 flex justify-end"
    transition:fade={{ duration: 180 }}
  >
    <button
      type="button"
      class="absolute inset-0 h-full w-full cursor-default border-0 p-0"
      style="background: var(--overlay-bg); backdrop-filter: blur(4px);"
      aria-label="Close {title}"
      on:click={onClose}
    ></button>
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="drawer-title"
      class="relative flex h-full w-full {width} flex-col border-l border-edge bg-surface-200 shadow-card-hover"
      transition:fly={{ x: 360, duration: 280, easing: quintOut }}
    >
      <header class="flex items-center justify-between gap-3 border-b border-edge px-6 py-5">
        <h2 id="drawer-title" class="text-lg font-semibold text-ink-primary">{title}</h2>
        <button
          type="button"
          class="btn-ghost h-8 w-8 !p-0"
          on:click={onClose}
          aria-label="Close drawer"
        >
          <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M6 6l12 12M6 18L18 6" stroke-linecap="round" />
          </svg>
        </button>
      </header>
      <div class="flex-1 overflow-y-auto p-6">
        <slot name="body" />
      </div>
      {#if $$slots.footer}
        <footer class="flex items-center gap-2 border-t border-edge bg-surface-200/80 px-6 py-4 backdrop-blur">
          <slot name="footer" />
        </footer>
      {/if}
    </div>
  </div>
{/if}