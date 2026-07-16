<script lang="ts">
  export let segments: { value: number; color: string; label: string }[] = [];
  export let height = 14;
  export let showLabels = true;

  $: total = segments.reduce((s, x) => s + Math.max(0, x.value), 0) || 1;
</script>

<div class="w-full">
  <div
    class="flex w-full overflow-hidden rounded-full ring-1 ring-edge"
    style="height: {height}px; background: var(--progress-track);"
    role="img"
    aria-label="Stacked bar"
  >
    {#each segments as s, i (i)}
      <div
        class="h-full transition-all duration-500"
        style="width: {(s.value / total) * 100}%; background: {s.color}; {i === 0
          ? 'border-top-left-radius: 9999px; border-bottom-left-radius: 9999px;'
          : ''} {i === segments.length - 1
          ? 'border-top-right-radius: 9999px; border-bottom-right-radius: 9999px;'
          : ''}"
        title="{s.label}: {s.value}"
      ></div>
    {/each}
  </div>
  {#if showLabels}
    <div class="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
      {#each segments as s, i (i)}
        <span class="inline-flex items-center gap-1.5 text-ink-secondary">
          <span class="h-2 w-2 rounded-full" style="background: {s.color};"></span>
          {s.label}
          <span class="text-ink-muted tabular-nums">{((s.value / total) * 100).toFixed(0)}%</span>
        </span>
      {/each}
    </div>
  {/if}
</div>