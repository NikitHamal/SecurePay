<script lang="ts">
  import type { Component } from 'svelte';

  export let title: string;
  export let value: string;
  export let delta: string;
  export let icon: Component<any>;
  export let tone: 'emerald' | 'crimson' | 'cyan' | 'amber' = 'emerald';

  $: toneClass = {
    emerald: 'bg-secure-emerald/15 text-secure-emerald',
    crimson: 'bg-secure-crimson/15 text-secure-crimson',
    cyan: 'bg-secure-cyan/15 text-secure-cyan',
    amber: 'bg-secure-amber/15 text-secure-amber'
  }[tone];
</script>

<section class="rounded-m3 border border-white/10 bg-charcoal-850 p-4 shadow-m3">
  <div class="flex items-center justify-between gap-4">
    <div>
      <p class="text-sm text-slate-400">{title}</p>
      <p class="mt-2 text-2xl font-semibold tracking-normal text-slate-50">{value}</p>
    </div>
    <div class={`grid size-11 place-items-center rounded-m3 ${toneClass}`}>
      <svelte:component this={icon} size={22} strokeWidth={2.2} />
    </div>
  </div>
  <p class="mt-4 text-sm text-slate-400">{delta}</p>
</section>
