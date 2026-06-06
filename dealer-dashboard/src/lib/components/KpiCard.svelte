<script lang="ts">
  export let label: string;
  export let value: string;
  export let tone: 'active' | 'warning' | 'danger' | 'neutral' = 'neutral';

  $: accent =
    tone === 'active'
      ? 'text-emerald-active'
      : tone === 'warning'
        ? 'text-amber-warning'
        : tone === 'danger'
          ? 'text-crimson-vivid'
          : 'text-white';
</script>

<article class="rounded-lg border border-white/10 bg-charcoal-900 p-5 shadow-m3">
  <p class="text-sm font-medium text-white/58">{label}</p>
  <p class={`mt-3 text-3xl font-bold tracking-normal ${accent}`}>{value}</p>
</article>
