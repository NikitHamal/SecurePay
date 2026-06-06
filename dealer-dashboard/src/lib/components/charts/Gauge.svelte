<script lang="ts">
  export let percent = 0;
  export let size = 220;
  export let stroke = 18;
  export let label = 'Portfolio Health';
  export let color = '#10B981';
  export let caption = '';

  $: clamped = Math.max(0, Math.min(100, percent));
  $: radius = (size - stroke) / 2;
  $: circumference = Math.PI * radius;
  $: offset = circumference - (clamped / 100) * circumference;
</script>

<div class="flex flex-col items-center">
  <div class="relative" style="width: {size}px; height: {size / 2 + stroke}px;">
    <svg
      width={size}
      height={size / 2 + stroke}
      viewBox="0 0 {size} {size / 2 + stroke}"
      class="block"
      role="img"
      aria-label="{label}: {clamped.toFixed(0)}%"
    >
      <defs>
        <linearGradient id="gauge-grad" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stop-color={color} stop-opacity="0.6" />
          <stop offset="100%" stop-color={color} stop-opacity="1" />
        </linearGradient>
      </defs>
      <path
        d="M{stroke / 2},{size / 2} A{radius},{radius} 0 0 1 {size - stroke / 2},{size / 2}"
        fill="none"
        stroke="var(--progress-track)"
        stroke-width={stroke}
        stroke-linecap="round"
      />
      <path
        d="M{stroke / 2},{size / 2} A{radius},{radius} 0 0 1 {size - stroke / 2},{size / 2}"
        fill="none"
        stroke="url(#gauge-grad)"
        stroke-width={stroke}
        stroke-linecap="round"
        stroke-dasharray={circumference}
        stroke-dashoffset={offset}
        style="transition: stroke-dashoffset 1.1s cubic-bezier(0.4, 0, 0.2, 1);"
      />
    </svg>
    <div class="absolute inset-x-0 bottom-2 flex flex-col items-center text-center">
      <span class="text-4xl font-semibold tabular-nums text-ink-primary">
        {clamped.toFixed(0)}<span class="text-xl text-ink-secondary">%</span>
      </span>
      <span class="mt-0.5 text-xs font-medium text-ink-secondary">{label}</span>
      {#if caption}
        <span class="mt-2 text-2xs text-ink-muted">{caption}</span>
      {/if}
    </div>
  </div>
</div>