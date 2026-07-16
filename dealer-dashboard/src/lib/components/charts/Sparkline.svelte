<script lang="ts">
  export let values: number[] = [];
  export let width = 120;
  export let height = 36;
  export let color = '#10B981';
  export let fill = true;
  export let showLastDot = true;

  $: min = Math.min(...values);
  $: max = Math.max(...values);
  $: range = max - min || 1;
  $: stepX = values.length > 1 ? width / (values.length - 1) : 0;

  type Pt = { x: number; y: number };
  $: points = values.map((v, i): Pt => {
    const x = i * stepX;
    const y = height - ((v - min) / range) * (height - 4) - 2;
    return { x, y };
  });

  $: linePath = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(2)},${p.y.toFixed(2)}`).join(' ');
  $: areaPath =
    points.length > 0
      ? `${linePath} L${points[points.length - 1].x.toFixed(2)},${height} L0,${height} Z`
      : '';
  $: last = points[points.length - 1];
</script>

<svg
  {width}
  {height}
  viewBox="0 0 {width} {height}"
  class="block overflow-visible"
  role="img"
  aria-label="Sparkline"
>
  <defs>
    <linearGradient id="spark-fill-{color.replace('#','')}" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color={color} stop-opacity="0.35" />
      <stop offset="100%" stop-color={color} stop-opacity="0" />
    </linearGradient>
  </defs>
  {#if fill && areaPath}
    <path d={areaPath} fill="url(#spark-fill-{color.replace('#','')})" />
  {/if}
  {#if linePath}
    <path
      d={linePath}
      fill="none"
      stroke={color}
      stroke-width="1.75"
      stroke-linecap="round"
      stroke-linejoin="round"
      style=""
    />
  {/if}
  {#if showLastDot && last}
    <circle cx={last.x} cy={last.y} r="2.5" fill={color} />
    <circle cx={last.x} cy={last.y} r="5" fill={color} fill-opacity="0.25" />
  {/if}
</svg>