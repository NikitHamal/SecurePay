<script lang="ts">
  import PageHeader from '$lib/components/ui/PageHeader.svelte';
  import TopBar from '$lib/components/layout/TopBar.svelte';
  import KpiCard from '$lib/components/ui/KpiCard.svelte';
  import Donut from '$lib/components/charts/Donut.svelte';
  import BarChart from '$lib/components/charts/BarChart.svelte';
  import AreaChart from '$lib/components/charts/AreaChart.svelte';
  import Gauge from '$lib/components/charts/Gauge.svelte';
  import StackedBar from '$lib/components/charts/StackedBar.svelte';
  import StatusBadge from '$lib/components/ui/StatusBadge.svelte';
  import { portfolioMetrics, kpis } from '$lib/stores/portfolio';
  import { customers } from '$lib/stores/customers';
  import { formatCurrency, formatRelative } from '$lib/utils/format';
  import { derived } from 'svelte/store';

  export let data: unknown = undefined;

  const deltas = {
    active: { delta: '+2 this week', trend: 'up' as const },
    warning: { delta: '3 approaching due', trend: 'flat' as const },
    locked: { delta: '2 overdue', trend: 'down' as const },
    outstanding: { delta: '-1.8% MoM', trend: 'up' as const },
    collected: { delta: '+12% vs avg', trend: 'up' as const }
  };

  function last7(series: { value: number }[]): number[] {
    return series.slice(-7).map((s) => s.value);
  }

  const recentActivity = derived(customers, ($c) => {
    return [...$c]
      .sort((a, b) => b.nextPaymentDueEpochMillis - a.nextPaymentDueEpochMillis)
      .slice(0, 5)
      .map((c) => ({
        id: c.id,
        name: c.customerName,
        amount: c.dailyRate,
        ms: c.nextPaymentDueEpochMillis - Date.now(),
        status: c.status
      }));
  });

  const today = new Date().toLocaleDateString('en-KE', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric'
  });
</script>

<svelte:head>
  <title>Overview · SecurePay Dealer Console</title>
</svelte:head>

<div class="page">
  <TopBar searchPlaceholder="Search customers, IMEI, transactions…" />

  <PageHeader
    eyebrow="Today · {today}"
    title="Portfolio Overview"
    subtitle="Real-time device financing health across your fleet. All four apps stay in sync through the shared status rule."
  >
    <div slot="actions" class="flex items-center gap-2">
      <button type="button" class="btn-outline">
        <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 5v14M5 12h14" stroke-linecap="round" />
        </svg>
        Export CSV
      </button>
      <button type="button" class="btn-primary">
        <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4">
          <path d="M12 5v14M5 12h14" stroke-linecap="round" />
        </svg>
        New loan
      </button>
    </div>
  </PageHeader>

  {#if $portfolioMetrics}
    {@const m = $portfolioMetrics}
    {@const spark = last7(m.collectionSeries)}

    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <KpiCard
        title="Active nodes"
        value={$kpis.activeNodes.toString()}
        sublabel="Devices in good standing"
        delta={deltas.active.delta}
        trend={deltas.active.trend}
        accent="emerald"
        progress={($kpis.activeNodes / Math.max(1, m.totalAccounts)) * 100}
        spark={spark}
        icon="M9 12l2 2 4-4M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"
      />
      <KpiCard
        title="Approaching due"
        value={$kpis.warningCount.toString()}
        sublabel="Within 24 hours"
        delta={deltas.warning.delta}
        trend={deltas.warning.trend}
        accent="amber"
        progress={($kpis.warningCount / Math.max(1, m.totalAccounts)) * 100}
        spark={spark.map((v, i) => v + (i % 2 === 0 ? -150 : 80))}
        icon="M12 9v4M12 17h.01M12 2l10 18H2L12 2z"
      />
      <KpiCard
        title="Locked / Overdue"
        value={$kpis.lockedCount.toString()}
        sublabel="Past due date"
        delta={deltas.locked.delta}
        trend={deltas.locked.trend}
        accent="crimson"
        progress={($kpis.lockedCount / Math.max(1, m.totalAccounts)) * 100}
        spark={spark.map((v, i) => v * 0.4 + (i % 3) * 30)}
        icon="M6 11V8a6 6 0 1112 0v3M5 11h14v10H5z"
      />
      <KpiCard
        title="Total outstanding"
        value={formatCurrency(m.totalOutstanding)}
        sublabel="Across {m.totalAccounts} financed devices"
        delta={deltas.outstanding.delta}
        trend={deltas.outstanding.trend}
        accent="sky"
        progress={m.paidRatio}
        spark={spark}
        icon="M3 17l6-6 4 4 8-8"
      />
    </div>

    <div class="mt-6 grid grid-cols-1 gap-4 lg:grid-cols-3">
      <div class="card relative overflow-hidden p-6">
        <div
          class="pointer-events-none absolute inset-x-0 top-0 h-32 opacity-30"
          style="background: radial-gradient(ellipse at 50% 0%, {m.health >= 70 ? 'rgba(16,185,129,0.4)' : m.health >= 50 ? 'rgba(245,158,11,0.4)' : 'rgba(239,68,68,0.4)'}, transparent 70%);"
        ></div>
        <div class="relative">
          <p class="section-title">Portfolio Health</p>
          <p class="mt-1 text-sm text-ink-secondary">Weighted by status mix + paid ratio</p>
          <div class="mt-4 flex justify-center">
            <Gauge
              percent={m.health}
              label={m.health >= 70 ? 'Strong' : m.health >= 50 ? 'Watch' : 'At risk'}
              color={m.health >= 70 ? '#10B981' : m.health >= 50 ? '#F59E0B' : '#EF4444'}
              caption="0% = critical · 100% = optimal"
            />
          </div>
        </div>
      </div>

      <div class="card p-6">
        <div class="flex items-center justify-between">
          <div>
            <p class="section-title">Status mix</p>
            <p class="mt-1 text-sm text-ink-secondary">Live count by account state</p>
          </div>
          <span class="chip">{m.totalAccounts} total</span>
        </div>
        <div class="mt-4">
          <Donut
            segments={[
              { label: 'Active', value: $kpis.activeNodes, color: '#10B981' },
              { label: 'Warning', value: $kpis.warningCount, color: '#F59E0B' },
              { label: 'Locked', value: $kpis.lockedCount, color: '#EF4444' }
            ]}
            size={180}
            stroke={20}
            gap={3}
            centerTitle={m.totalAccounts.toString()}
            centerSubtitle="accounts"
            legendValues
          />
        </div>
      </div>

      <div class="card p-6">
        <div class="flex items-center justify-between">
          <div>
            <p class="section-title">Daily collections</p>
            <p class="mt-1 text-sm text-ink-secondary">Last 14 days · KES</p>
          </div>
          <span class="chip-emerald tabular-nums">
            <span class="h-1.5 w-1.5 rounded-full bg-emerald"></span>
            {formatCurrency(m.collectionSeries.slice(-7).reduce((s, x) => s + x.value, 0))} / 7d
          </span>
        </div>
        <div class="mt-4">
          <AreaChart
            values={m.collectionSeries}
            color="#10B981"
            height={180}
          />
        </div>
      </div>
    </div>

    <div class="mt-6 grid grid-cols-1 gap-4 lg:grid-cols-3">
      <div class="card p-6 lg:col-span-2">
        <div class="flex items-center justify-between">
          <div>
            <p class="section-title">Devices by model</p>
            <p class="mt-1 text-sm text-ink-secondary">Distribution across your fleet</p>
          </div>
          <span class="chip">{m.deviceDistribution.length} models</span>
        </div>
        <div class="mt-4">
          <BarChart
            values={m.deviceDistribution.map((d) => ({
              label: d.name.length > 10 ? d.name.split(' ')[0].slice(0, 8) : d.name,
              value: d.value,
              color: d.color
            }))}
            color="#38BDF8"
            height={220}
            yTicks={3}
            xLabelRotation={-22}
            formatY={(n) => n.toString()}
          />
        </div>
        <div class="mt-4">
          <StackedBar segments={m.deviceDistribution.map((d) => ({ value: d.value, color: d.color, label: d.name }))} />
        </div>
      </div>

      <div class="card p-6">
        <div class="flex items-center justify-between">
          <p class="section-title">Next due</p>
          <span class="chip-amber">{m.upcoming.length} queued</span>
        </div>
        <ul class="mt-4 flex flex-col gap-2">
          {#each m.upcoming as u (u.id)}
            <li class="flex items-center gap-3 rounded-lg border border-edge bg-surface-100/40 p-3">
              <span
                class="flex h-9 w-9 shrink-0 items-center justify-center rounded-md text-xs font-semibold text-white"
                style="background: {u.status === 'LOCKED' ? '#EF4444' : u.status === 'WARNING' ? '#F59E0B' : '#10B981'};"
              >
                {u.name.split(' ').map((p) => p[0]).join('').slice(0, 2)}
              </span>
              <div class="flex-1 min-w-0">
                <p class="truncate text-sm font-medium text-ink-primary">{u.name}</p>
                <p class="text-2xs text-ink-muted">{formatRelative(u.ms)} · {formatCurrency(u.amount)}</p>
              </div>
              <StatusBadge status={u.status} size="sm" />
            </li>
          {/each}
        </ul>
      </div>
    </div>

    <div class="mt-6 grid grid-cols-1 gap-4 lg:grid-cols-3">
      <div class="card p-6">
        <p class="section-title">Payment method mix</p>
        <p class="mt-1 text-sm text-ink-secondary">Across all settled installments</p>
        <div class="mt-4">
          <Donut
            segments={m.methodSeries.map((s) => ({ label: s.label, value: s.value, color: s.color }))}
            size={170}
            stroke={20}
            gap={3}
            centerTitle={formatCurrency(m.methodSeries.reduce((s, x) => s + x.value, 0))}
            centerSubtitle="settled"
            legendValues={false}
            showLegend
          />
        </div>
      </div>

      <div class="card p-6 lg:col-span-2">
        <p class="section-title">Recent activity</p>
        <p class="mt-1 text-sm text-ink-secondary">Latest status changes across the portfolio</p>
        <ul class="mt-4 flex flex-col">
          {#each $recentActivity as a, i (a.id)}
            <li class="flex items-start gap-4 {i < $recentActivity.length - 1 ? 'pb-4' : ''}">
              <div class="flex flex-col items-center">
                <span
                  class="flex h-7 w-7 items-center justify-center rounded-full ring-4"
                  style="background: {a.status === 'LOCKED' ? 'rgba(239,68,68,0.15)' : a.status === 'WARNING' ? 'rgba(245,158,11,0.15)' : 'rgba(16,185,129,0.15)'}; --tw-ring-color: var(--bg-base);"
                >
                  <span
                    class="h-2 w-2 rounded-full"
                    style="background: {a.status === 'LOCKED' ? '#EF4444' : a.status === 'WARNING' ? '#F59E0B' : '#10B981'};"
                  ></span>
                </span>
                {#if i < $recentActivity.length - 1}
                  <span class="mt-1 w-px flex-1 bg-edge"></span>
                {/if}
              </div>
              <div class="flex-1 -mt-0.5">
                <p class="text-sm text-ink-primary">
                  <span class="font-medium">{a.name}</span>
                  {#if a.status === 'LOCKED'}
                    missed payment · locked
                  {:else if a.status === 'WARNING'}
                    approaching due
                  {:else}
                    is on track
                  {/if}
                </p>
                <p class="text-2xs text-ink-muted">{formatCurrency(a.amount)} · {a.ms > 0 ? formatRelative(a.ms) : 'past due'}</p>
              </div>
              <StatusBadge status={a.status} size="sm" />
            </li>
          {/each}
        </ul>
      </div>
    </div>
  {/if}
</div>