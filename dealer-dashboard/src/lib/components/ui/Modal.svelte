<script lang="ts">
  import { createEventDispatcher } from 'svelte';

  export let open = false;
  export let title = '';
  export let size: 'sm' | 'md' | 'lg' | 'xl' = 'md';
  export let dismissible = true;

  const dispatch = createEventDispatcher();

  function close() {
    if (!dismissible) return;
    dispatch('close');
  }

  function onKey(e: KeyboardEvent) {
    if (e.key === 'Escape') close();
  }

  $: sizeClass = {
    sm: 'max-w-sm',
    md: 'max-w-lg',
    lg: 'max-w-2xl',
    xl: 'max-w-4xl'
  }[size];
</script>

<svelte:window on:keydown={onKey} />

{#if open}
  <div class="fixed inset-0 z-50 flex items-end justify-center sm:items-center p-0 sm:p-4" role="dialog" aria-modal="true">
    <button
      type="button"
      class="absolute inset-0 h-full w-full cursor-default border-0 bg-black/50 p-0 backdrop-blur-sm"
      aria-label="Close dialog"
      on:click={close}
    ></button>

    <div
      class="relative w-full {sizeClass} max-h-[90vh] overflow-hidden rounded-t-2xl sm:rounded-xl border border-edge bg-surface-200 shadow-card-hover animate-fade-in flex flex-col"
    >
      {#if title}
        <div class="flex items-center justify-between border-b border-edge px-5 py-4">
          <h3 class="text-base font-semibold text-ink-primary">{title}</h3>
          {#if dismissible}
            <button
              type="button"
              class="btn-ghost h-8 w-8 !p-0"
              aria-label="Close"
              on:click={close}
            >
              <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M6 6l12 12M6 18L18 6" stroke-linecap="round"/>
              </svg>
            </button>
          {/if}
        </div>
      {/if}
      <div class="flex-1 overflow-y-auto p-5">
        <slot />
      </div>
      {#if $$slots.footer}
        <div class="border-t border-edge px-5 py-3 flex flex-wrap justify-end gap-2">
          <slot name="footer" />
        </div>
      {/if}
    </div>
  </div>
{/if}
