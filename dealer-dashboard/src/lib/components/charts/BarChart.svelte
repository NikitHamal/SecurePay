<script lang="ts">
  export let values: { label: string; value: number; color?: string; highlight?: boolean }[] = [];
  export let height = 220;
  export let barRadius = 6;
  export let yTicks = 4;
  export let formatY: (n: number) => string = (n) => n.toString();
  export let yMax: number | 'auto' = 'auto';
  export let color = '#10B981';
  export let showValues = false;
  export let xLabelRotation: number = 0;

  const padding = { top: 18, right: 8, bottom: 38, left: 44 };

  $: max = yMax === 'auto' ? Math.max(1, ...values.map((v) => v.value)) : yMax;
  $: ticks = Array.from({ length: yTicks + 1 }, (_, i) => (max / yTicks) * i);
  $: chartW = 100;
  $: chartH = 100;
  $: innerW = chartW - padding.left - padding.right;
  $: innerH = chartH - padding.top - padding.bottom;
  $: slot = values.length > 0 ? innerW / values.length : 0;
  $: barWidth = values.length > 0 ? slot * 0.55 : 0;
  $: barGap = values.length > 0 ? slot * 0.45 : 0;

  function valueToY(v: number): number {
    return padding.top + innerH - (v / max) * innerH;
  }
</script>

<svg
  viewBox="0 0 {chartW} {chartH}"
  preserveAspectRatio="none"
  class="block w-full"
  style="height: {height}px;"
  role="img"
  aria-label="Bar chart"
>
  <defs>
    <linearGradient id="bar-grad" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color={color} stop-opacity="1" />
      <stop offset="100%" stop-color={color} stop-opacity="0.35" />
    </linearGradient>
  </defs>

  {#each ticks as t, i (i)}
    {@const y = padding.top + innerH - (t / max) * innerH}
    <line
      x1={padding.left}
      x2={chartW - padding.right}
      y1={y}
      y2={y}
      stroke="var(--border-subtle)"
      stroke-dasharray="2 3"
    />
    <text
      x={padding.left - 2}
      y={y + 1.2}
      text-anchor="end"
      font-size="3.2"
      fill="var(--text-muted)"
    >
      {formatY(t)}
    </text>
  {/each}

  {#each values as v, i (i)}
    {@const x = padding.left + i * slot + barGap / 2}
    {@const y = valueToY(v.value)}
    {@const h = (v.value / max) * innerH}
    {@const barColor = v.color ?? color}
    <g>
      <rect
        x={x}
        y={y}
        width={barWidth}
        height={h}
        rx={barRadius / 2}
        ry={barRadius / 2}
        fill={v.highlight ? barColor : `url(#bar-grad)`}
        opacity={v.highlight ? 1 : 0.95}
        style=""
      />
      {#if showValues}
        <text
          x={x + barWidth / 2}
          y={y - 2}
          text-anchor="middle"
          font-size="3.2"
          fill={barColor}
          font-weight="600"
        >
          {formatY(v.value)}
        </text>
      {/if}
      <text
        x={x + barWidth / 2}
        y={chartH - padding.bottom + 14}
        text-anchor={xLabelRotation === 0 ? 'middle' : 'end'}
        font-size="3"
        fill="var(--text-secondary)"
        transform="rotate({xLabelRotation} {x + barWidth / 2} {chartH - padding.bottom + 14})"
      >
        {v.label}
      </text>
    </g>
  {/each}
</svg>