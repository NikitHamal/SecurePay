<script lang="ts">
  export let label: string;
  export let value: string | number;
  export let accent: 'emerald' | 'amber' | 'crimson' | 'neutral' = 'neutral';
  export let hint: string = '';

  const accentText: Record<typeof accent, string> = {
    emerald: 'text-brand-emerald',
    amber: 'text-brand-amber',
    crimson: 'text-brand-crimson',
    neutral: 'text-brand-text'
  };

  const accentBar: Record<typeof accent, string> = {
    emerald: 'bg-brand-emerald',
    amber: 'bg-brand-amber',
    crimson: 'bg-brand-crimson',
    neutral: 'bg-brand-border'
  };
</script>

<div class="m3-card relative overflow-hidden p-5">
  <span class="absolute inset-y-0 left-0 w-1 {accentBar[accent]}"></span>
  <p class="text-xs font-medium uppercase tracking-wider text-brand-textMuted">{label}</p>
  <p class="mt-2 text-2xl font-semibold {accentText[accent]}">{value}</p>
  {#if hint}
    <p class="mt-1 text-xs text-brand-textMuted">{hint}</p>
  {/if}
</div>
