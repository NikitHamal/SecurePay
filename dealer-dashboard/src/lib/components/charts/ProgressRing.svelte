<script lang="ts">
  export let percent = 0;
  export let size = 56;
  export let stroke = 6;
  export let color = '#10B981';
  export let trackColor = 'var(--progress-track)';
  export let label: string = '';
  export let icon: boolean = false;

  $: clamped = Math.max(0, Math.min(100, percent));
  $: radius = (size - stroke) / 2;
  $: circumference = 2 * Math.PI * radius;
  $: offset = circumference - (clamped / 100) * circumference;
</script>

<div
  class="relative inline-flex items-center justify-center"
  style="width: {size}px; height: {size}px;"
>
  <svg
    width={size}
    height={size}
    viewBox="0 0 {size} {size}"
    class="block -rotate-90"
    aria-label="Progress {clamped.toFixed(0)}%"
  >
    <circle
      cx={size / 2}
      cy={size / 2}
      r={radius}
      fill="none"
      stroke={trackColor}
      stroke-width={stroke}
    />
    <circle
      cx={size / 2}
      cy={size / 2}
      r={radius}
      fill="none"
      stroke={color}
      stroke-width={stroke}
      stroke-linecap="round"
      stroke-dasharray={circumference}
      stroke-dashoffset={offset}
      style="transition: stroke-dashoffset 900ms cubic-bezier(0.4, 0, 0.2, 1);"
    />
  </svg>
  {#if icon}
    <span class="absolute inset-0 flex items-center justify-center text-ink-secondary">
      <slot name="icon" />
    </span>
  {:else if label}
    <span class="absolute inset-0 flex items-center justify-center text-xs font-semibold tabular-nums text-ink-primary">
      {label}
    </span>
  {/if}
</div>