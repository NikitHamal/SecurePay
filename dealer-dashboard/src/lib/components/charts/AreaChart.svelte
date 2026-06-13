<script lang="ts">
  export let values: { label: string; value: number }[] = [];
  export let height = 180;
  export let color = '#10B981';

  $: nums = values.map((v) => v.value);
  $: max = Math.max(1, ...nums);
  $: min = Math.min(0, ...nums);
  $: range = max - min || 1;

  const padding = { top: 16, right: 8, bottom: 28, left: 36 };
  $: chartW = 100;
  $: chartH = 100;
  $: innerW = chartW - padding.left - padding.right;
  $: innerH = chartH - padding.top - padding.bottom;
  $: stepX = values.length > 1 ? innerW / (values.length - 1) : 0;

  type Pt = { x: number; y: number };
  $: points = values.map(
    (v, i): Pt => ({
      x: padding.left + i * stepX,
      y: padding.top + innerH - ((v.value - min) / range) * innerH
    })
  );

  $: linePath = points
    .map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(2)},${p.y.toFixed(2)}`)
    .join(' ');
  $: areaPath = points.length > 0
    ? `${linePath} L${points[points.length - 1].x},${padding.top + innerH} L${points[0].x},${padding.top + innerH} Z`
    : '';

  $: yTicks = Array.from({ length: 4 }, (_, i) => min + (range * (4 - i)) / 4);
</script>

<svg
  viewBox="0 0 {chartW} {chartH}"
  preserveAspectRatio="none"
  class="block w-full"
  style="height: {height}px;"
  role="img"
  aria-label="Area chart"
>
  <defs>
    <linearGradient id="area-grad-{color.replace('#','')}" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color={color} stop-opacity="0.45" />
      <stop offset="100%" stop-color={color} stop-opacity="0" />
    </linearGradient>
  </defs>

  {#each yTicks as t, i (i)}
    {@const y = padding.top + ((i / 3) * innerH)}
    <line
      x1={padding.left}
      x2={chartW - padding.right}
      y1={y}
      y2={y}
      stroke="var(--border-subtle)"
      stroke-dasharray="2 3"
    />
    <text x={padding.left - 4} y={y + 1.5} text-anchor="end" font-size="3" fill="var(--text-muted)">
      {t.toFixed(0)}
    </text>
  {/each}

  {#if areaPath}
    <path d={areaPath} fill="url(#area-grad-{color.replace('#','')})" />
  {/if}
  {#if linePath}
    <path
      d={linePath}
      fill="none"
      stroke={color}
      stroke-width="0.9"
      stroke-linecap="round"
      stroke-linejoin="round"
      style=""
    />
  {/if}
  {#each points as p, i (i)}
    <circle cx={p.x} cy={p.y} r="1.4" fill={color} fill-opacity="0.9" />
  {/each}
  {#each values as v, i (i)}
    {#if i % Math.max(1, Math.floor(values.length / 6)) === 0 || i === values.length - 1}
      <text
        x={padding.left + i * stepX}
        y={chartH - padding.bottom + 12}
        text-anchor="middle"
        font-size="3"
        fill="var(--text-secondary)"
      >
        {v.label}
      </text>
    {/if}
  {/each}
</svg>