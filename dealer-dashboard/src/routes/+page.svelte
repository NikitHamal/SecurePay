<script lang="ts">
  import PageHeader from '$lib/components/ui/PageHeader.svelte';
  import TopBar from '$lib/components/layout/TopBar.svelte';
  import KpiCard from '$lib/components/ui/KpiCard.svelte';
  import Donut from '$lib/components/charts/Donut.svelte';
  import BarChart from '$lib/components/charts/BarChart.svelte';
  import AreaChart from '$lib/components/charts/AreaChart.svelte';
  import Gauge from '$lib/components/charts/Gauge.svelte';
  import StatusBadge from '$lib/components/ui/StatusBadge.svelte';
  import { portfolioMetrics, kpis } from '$lib/stores/portfolio';
  import { customers } from '$lib/stores/customers';
  import { formatCurrency, formatRelative } from '$lib/utils/format';
  import { openNewLoan, openAddDevice, openProvision } from '$lib/stores/ui';
  import { derived } from 'svelte/store';

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

  const today = new Date().toLocaleDateString('en-GH', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric'
  });

  function statusColor(status: string) {
    if (status === 'LOCKED') return '#DC2626';
    if (status === 'WARNING') return '#F59E0B';
    return '#10B981';
  }
  function statusBg(status: string) {
    if (status === 'LOCKED') return 'rgba(220,38,38,0.12)';
    if (status === 'WARNING') return 'rgba(245,158,11,0.12)';
    return 'rgba(16,185,129,0.12)';
  }
  function initials(name: string) {
    return (name || '?').split(' ').map(p => p[0]).join('').slice(0, 2);
  }
</script>

<svelte:head><title>Overview · Touch Base</title></svelte:head>

<div class="page">
  <TopBar searchPlaceholder="Search customers, IMEI, transactions…" />

  <PageHeader
    eyebrow="Today · {today}"
    title="Portfolio Overview"
    subtitle="Real-time device financing health across your fleet."
  >
    <svelte:fragment slot="actions">
      <div class="flex items-center gap-2">
        <button type="button" class="btn-outline" on:click={() => openAddDevice()}>
          <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="5" y="2" width="14" height="20" rx="2"/><path d="M12 18h.01"/>
          </svg>
          Add device
        </button>
        <button type="button" class="btn-outline" on:click={() => openProvision()}>
          <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/>
            <rect x="3" y="14" width="7" height="7" rx="1"/><path d="M14 14h3v3M21 14v3M14 21h3M17 17h4v4"/>
          </svg>
          Provision
        </button>
        <button type="button" class="btn-primary" on:click={() => openNewLoan()}>
          <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4">
            <path d="M12 5v14M5 12h14" stroke-linecap="round" />
          </svg>
          Enroll customer
        </button>
      </div>
    </svelte:fragment>
  </PageHeader>

  {#if $portfolioMetrics}
    {@const m = $portfolioMetrics}

    <div class="grid grid-cols-2 gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <div class="card p-4">
        <p class="text-xs font-medium text-ink-muted">Active devices</p>
        <p class="mt-1 text-2xl font-semibold tabular-nums text-ink-primary">{$kpis.activeNodes}</p>
        <p class="mt-0.5 text-xs text-ink-muted">In good standing</p>
      </div>
      <div class="card p-4">
        <p class="text-xs font-medium text-ink-muted">Approaching due</p>
        <p class="mt-1 text-2xl font-semibold tabular-nums text-amber">{$kpis.warningCount}</p>
        <p class="mt-0.5 text-xs text-ink-muted">Within 24 hours</p>
      </div>
      <div class="card p-4">
        <p class="text-xs font-medium text-ink-muted">Locked / Overdue</p>
        <p class="mt-1 text-2xl font-semibold tabular-nums text-crimson">{$kpis.lockedCount}</p>
        <p class="mt-0.5 text-xs text-ink-muted">Past due date</p>
      </div>
      <div class="card p-4">
        <p class="text-xs font-medium text-ink-muted">Outstanding</p>
        <p class="mt-1 text-2xl font-semibold tabular-nums text-ink-primary">{formatCurrency(m.totalOutstanding)}</p>
        <p class="mt-0.5 text-xs text-ink-muted">{m.totalAccounts} financed devices</p>
      </div>
    </div>

    <div class="mt-5 grid grid-cols-1 gap-4 lg:grid-cols-3">
      <div class="card p-5">
        <p class="section-title">Portfolio Health</p>
        <p class="mt-1 text-xs text-ink-muted">Weighted by status mix + paid ratio</p>
        <div class="mt-4 flex justify-center">
          <Gauge
            percent={m.health}
            label={m.health >= 70 ? 'Strong' : m.health >= 50 ? 'Watch' : 'At risk'}
            color={m.health >= 70 ? '#10B981' : m.health >= 50 ? '#F59E0B' : '#DC2626'}
            caption="0% = critical · 100% = optimal"
          />
        </div>
      </div>

      <div class="card p-5">
        <div class="flex items-center justify-between">
          <div>
            <p class="section-title">Status mix</p>
            <p class="mt-1 text-xs text-ink-muted">Live count by state</p>
          </div>
          <span class="chip">{m.totalAccounts} total</span>
        </div>
        <div class="mt-4">
          <Donut
            segments={[
              { label: 'Active', value: $kpis.activeNodes, color: '#10B981' },
              { label: 'Warning', value: $kpis.warningCount, color: '#F59E0B' },
              { label: 'Locked', value: $kpis.lockedCount, color: '#DC2626' }
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

      <div class="card p-5">
        <div class="flex items-center justify-between">
          <div>
            <p class="section-title">Daily collections</p>
            <p class="mt-1 text-xs text-ink-muted">Last 14 days · GHS</p>
          </div>
          <span class="chip-emerald tabular-nums">
            <span class="h-1.5 w-1.5 rounded-full bg-emerald inline-block mr-1"></span>
            {formatCurrency(m.collectionSeries.slice(-7).reduce((s, x) => s + x.value, 0))} / 7d
          </span>
        </div>
        <div class="mt-4">
          <AreaChart values={m.collectionSeries} color="#10B981" height={180} />
        </div>
      </div>
    </div>

    <div class="mt-5 grid grid-cols-1 gap-4 lg:grid-cols-3">
      <div class="card p-5 lg:col-span-2">
        <div class="flex items-center justify-between">
          <div>
            <p class="section-title">Next due</p>
            <p class="mt-1 text-xs text-ink-muted">Upcoming payments needing attention</p>
          </div>
          <a href="/customers" class="text-xs text-emerald hover:underline">View all</a>
        </div>
        <ul class="mt-4 divide-y divide-edge">
          {#each m.upcoming as u (u.id)}
            <li class="flex items-center gap-3 py-3 first:pt-0 last:pb-0">
              <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-xs font-semibold text-white" style="background: {statusColor(u.status)};">
                {initials(u.name)}
              </span>
              <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-medium text-ink-primary">{u.name}</p>
                <p class="truncate text-xs text-ink-muted">{formatRelative(u.ms)} · {formatCurrency(u.amount)}</p>
              </div>
              <StatusBadge status={u.status} size="sm" />
            </li>
          {/each}
        </ul>
      </div>

      <div class="card p-5">
        <p class="section-title">Quick actions</p>
        <div class="mt-3 grid grid-cols-2 gap-2">
          <button class="rounded-lg border border-edge bg-surface-100 p-3 text-left hover:bg-hover transition" on:click={() => openNewLoan()}>
            <svg class="mb-1.5 h-5 w-5 text-emerald" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14" stroke-linecap="round"/></svg>
            <p class="text-sm font-medium text-ink-primary">Enroll customer</p>
            <p class="text-[11px] text-ink-muted">Enroll customer</p>
          </button>
          <button class="rounded-lg border border-edge bg-surface-100 p-3 text-left hover:bg-hover transition" on:click={() => openProvision()}>
            <svg class="mb-1.5 h-5 w-5 text-emerald" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><path d="M14 14h3v3M21 14v3M14 21h3M17 17h4v4"/></svg>
            <p class="text-sm font-medium text-ink-primary">Provision</p>
            <p class="text-[11px] text-ink-muted">Generate QR</p>
          </button>
          <button class="rounded-lg border border-edge bg-surface-100 p-3 text-left hover:bg-hover transition" on:click={() => openAddDevice()}>
            <svg class="mb-1.5 h-5 w-5 text-emerald" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="5" y="2" width="14" height="20" rx="2"/><path d="M12 18h.01"/></svg>
            <p class="text-sm font-medium text-ink-primary">Add device</p>
            <p class="text-[11px] text-ink-muted">Add IMEI</p>
          </button>
          <a href="/ledger" class="rounded-lg border border-edge bg-surface-100 p-3 text-left hover:bg-hover transition">
            <svg class="mb-1.5 h-5 w-5 text-emerald" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 7h6M9 11h6M9 15h4M5 21h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v14a2 2 0 002 2z"/></svg>
            <p class="text-sm font-medium text-ink-primary">Record payment</p>
            <p class="text-[11px] text-ink-muted">Open ledger</p>
          </a>
        </div>
      </div>
    </div>

    <div class="mt-5 card p-5">
      <p class="section-title">Recent activity</p>
      <p class="mt-1 text-xs text-ink-muted">Latest account status changes</p>
      <ul class="mt-4 divide-y divide-edge">
        {#each $recentActivity as a (a.id)}
          <li class="flex items-center gap-3 py-3 first:pt-0 last:pb-0">
            <span class="flex h-8 w-8 items-center justify-center rounded-full" style="background: {statusBg(a.status)};">
              <span class="h-2 w-2 rounded-full" style="background: {statusColor(a.status)};"></span>
            </span>
            <div class="flex-1 min-w-0">
              <p class="text-sm text-ink-primary truncate">
                <span class="font-medium">{a.name}</span>
                {#if a.status === 'LOCKED'}
                  <span class="text-ink-muted"> · missed payment · locked</span>
                {:else if a.status === 'WARNING'}
                  <span class="text-ink-muted"> · approaching due</span>
                {:else}
                  <span class="text-ink-muted"> · on track</span>
                {/if}
              </p>
              <p class="text-xs text-ink-muted">{formatCurrency(a.amount)} · {a.ms > 0 ? formatRelative(a.ms) : 'past due'}</p>
            </div>
            <StatusBadge status={a.status} size="sm" />
          </li>
        {/each}
      </ul>
    </div>
  {:else}
    <div class="flex items-center justify-center py-16">
      <div class="h-8 w-8 animate-spin rounded-full border-2 border-emerald border-t-transparent"></div>
    </div>
  {/if}
</div>
