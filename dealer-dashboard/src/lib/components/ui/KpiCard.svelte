<script lang="ts">
  export let title: string;
  export let value: string;
  export let sublabel: string = '';
  export let delta: string = '';
  export let trend: 'up' | 'down' | 'flat' = 'flat';
  export let accent: 'emerald' | 'crimson' | 'amber' | 'sky' = 'emerald';

  const accentMap: Record<string, { text: string; chip: string }> = {
    emerald: { text: 'text-emerald', chip: 'bg-emerald/10 text-emerald' },
    crimson: { text: 'text-crimson', chip: 'bg-crimson/10 text-crimson' },
    amber:   { text: 'text-amber',   chip: 'bg-amber/10 text-amber' },
    sky:     { text: 'text-sky',     chip: 'bg-sky/10 text-sky' },
  };

  $: a = accentMap[accent] || accentMap.emerald;
</script>

<div class="card p-4 transition-colors hover:border-edge-strong">
  <p class="text-xs font-medium text-ink-muted">{title}</p>
  <p class="mt-1 text-2xl font-semibold tabular-nums text-ink-primary">{value}</p>
  {#if sublabel}
    <p class="mt-0.5 text-xs text-ink-muted">{sublabel}</p>
  {/if}
  {#if delta}
    <span class="mt-2 inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-[11px] font-medium {a.chip}">
      {#if trend === 'up'}
        <svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M7 14l5-5 5 5" stroke-linecap="round" stroke-linejoin="round"/></svg>
      {:else if trend === 'down'}
        <svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M7 10l5 5 5-5" stroke-linecap="round" stroke-linejoin="round"/></svg>
      {:else}
        <svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M5 12h14" stroke-linecap="round"/></svg>
      {/if}
      {delta}
    </span>
  {/if}
</div>
