<script lang="ts">
  import type { KpiAccent } from '$lib/types';

  export let title: string;
  export let value: string;
  export let sublabel = '';
  export let accent: KpiAccent = 'neutral';

  const accentText: Record<KpiAccent, string> = {
    emerald: 'text-emerald',
    crimson: 'text-crimson',
    amber: 'text-amber',
    neutral: 'text-text-primary'
  };

  const accentBar: Record<KpiAccent, string> = {
    emerald: 'bg-emerald',
    crimson: 'bg-crimson',
    amber: 'bg-amber',
    neutral: 'bg-white/20'
  };
</script>

<div class="card relative overflow-hidden p-5">
  <span class="absolute inset-y-0 left-0 w-1 {accentBar[accent]}" aria-hidden="true"></span>
  <p class="text-xs font-medium uppercase tracking-wide text-text-secondary">{title}</p>
  <p class="mt-2 text-2xl font-semibold {accentText[accent]}">{value}</p>
  {#if sublabel}
    <p class="mt-1 text-xs text-text-secondary">{sublabel}</p>
  {/if}
</div>
