<script lang="ts">
  import ProgressRing from '$lib/components/charts/ProgressRing.svelte';
  import Sparkline from '$lib/components/charts/Sparkline.svelte';

  export let title: string;
  export let value: string;
  export let sublabel: string = '';
  export let delta: string = '';
  export let trend: 'up' | 'down' | 'flat' = 'flat';
  export let accent: 'emerald' | 'crimson' | 'amber' | 'sky' | 'violet' = 'emerald';
  export let spark: number[] = [];
  export let progress: number | null = null;
  export let icon: string = '';

  const accentMap: Record<string, { ring: string; text: string; chip: string }> = {
    emerald: { ring: '#10B981', text: 'text-emerald', chip: 'bg-emerald/10 text-emerald' },
    crimson: { ring: '#EF4444', text: 'text-crimson', chip: 'bg-crimson/10 text-crimson' },
    amber:   { ring: '#F59E0B', text: 'text-amber',   chip: 'bg-amber/10 text-amber' },
    sky:     { ring: '#38BDF8', text: 'text-sky',     chip: 'bg-sky/10 text-sky' },
    violet:  { ring: '#A78BFA', text: 'text-violet',  chip: 'bg-violet/10 text-violet' },
  };

  $: a = accentMap[accent];
</script>

<div class="card card-hover p-5 group animate-fade-in">
  <div class="flex items-start justify-between gap-4">
    <div class="min-w-0 flex-1">
      <p class="section-title">{title}</p>
      <p class="mt-2 text-3xl font-semibold tabular-nums {a.text}">
        {value}
      </p>
      {#if sublabel}
        <p class="mt-1 text-xs text-ink-secondary">{sublabel}</p>
      {/if}
      {#if delta}
        <span class="mt-2 inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-2xs font-semibold {a.chip}">
          {#if trend === 'up'}
            <svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M7 14l5-5 5 5" stroke-linecap="round" stroke-linejoin="round" /></svg>
          {:else if trend === 'down'}
            <svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M7 10l5 5 5-5" stroke-linecap="round" stroke-linejoin="round" /></svg>
          {:else}
            <svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M5 12h14" stroke-linecap="round" /></svg>
          {/if}
          {delta}
        </span>
      {/if}
    </div>

    {#if progress !== null}
      <ProgressRing
        percent={progress}
        size={56}
        stroke={6}
        color={a.ring}
        trackColor="var(--progress-track)"
      />
    {:else if icon}
      <span
        class="flex h-11 w-11 items-center justify-center rounded-xl {a.chip}"
        aria-hidden="true"
      >
        <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d={icon} />
        </svg>
      </span>
    {/if}
  </div>

  {#if spark.length > 0}
    <div class="relative mt-4 -mb-1">
      <Sparkline values={spark} width={260} height={36} color={a.ring} fill />
    </div>
  {/if}
</div>