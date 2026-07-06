<script lang="ts">
  export let segments: { value: number; color: string; label?: string }[] = [];
  export let size = 200;
  export let stroke = 22;
  export let gap = 2;
  export let centerTitle: string = '';
  export let centerSubtitle: string = '';
  export let showLegend = true;
  export let legendValues = false;

  $: total = segments.reduce((sum, s) => sum + Math.max(0, s.value), 0) || 1;
  $: radius = (size - stroke) / 2;
  $: circumference = 2 * Math.PI * radius;
  $: itemGap = (gap / 360) * circumference;

  type Arc = {
    color: string;
    length: number;
    offset: number;
    label?: string;
    value: number;
  };

  let arcs: Arc[] = [];
  let prevOffset = 0;
  $: {
    arcs = [];
    prevOffset = 0;
    for (const seg of segments) {
      const length = (Math.max(0, seg.value) / total) * circumference;
      const offset = prevOffset;
      arcs.push({
        color: seg.color,
        length: Math.max(0, length - itemGap),
        offset,
        label: seg.label,
        value: seg.value
      });
      prevOffset += length;
    }
  }
</script>

<div class="flex items-center gap-6">
  <div class="relative" style="width: {size}px; height: {size}px;">
    <svg
      width={size}
      height={size}
      viewBox="0 0 {size} {size}"
      class="block -rotate-90"
      role="img"
      aria-label="Donut chart"
    >
      <circle
        cx={size / 2}
        cy={size / 2}
        r={radius}
        fill="none"
        stroke="var(--progress-track)"
        stroke-width={stroke}
      />
      {#each arcs as arc, i (i)}
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={arc.color}
          stroke-width={stroke}
          stroke-dasharray="{arc.length} {circumference - arc.length}"
          stroke-dashoffset={-arc.offset}
          stroke-linecap="butt"
          class="transition-all duration-700 ease-out"
        />
      {/each}
    </svg>
    {#if centerTitle || centerSubtitle}
      <div class="absolute inset-0 flex flex-col items-center justify-center text-center">
        {#if centerTitle}
          <span class="text-2xl font-semibold text-ink-primary tabular-nums">{centerTitle}</span>
        {/if}
        {#if centerSubtitle}
          <span class="text-xs text-ink-secondary mt-0.5">{centerSubtitle}</span>
        {/if}
      </div>
    {/if}
  </div>

  {#if showLegend}
    <ul class="flex flex-col gap-2 text-sm">
      {#each segments as seg, i (i)}
        {#if seg.label !== undefined}
          <li class="flex items-center gap-2.5">
            <span
              class="h-2.5 w-2.5 rounded-full"
              style="background: {seg.color};"
            ></span>
            <span class="text-ink-secondary">{seg.label}</span>
            {#if legendValues}
              <span class="ml-auto pl-4 font-medium text-ink-primary tabular-nums">
                {((seg.value / total) * 100).toFixed(0)}%
              </span>
            {/if}
          </li>
        {/if}
      {/each}
    </ul>
  {/if}
</div>