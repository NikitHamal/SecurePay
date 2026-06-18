<script lang="ts">
  import CustomerTable from '$lib/components/CustomerTable.svelte';
  import CustomerDrawer from '$lib/components/CustomerDrawer.svelte';
  import PageHeader from '$lib/components/ui/PageHeader.svelte';
  import TopBar from '$lib/components/layout/TopBar.svelte';
  import KpiCard from '$lib/components/ui/KpiCard.svelte';
  import { customers } from '$lib/stores/customers';
  import { portfolioMetrics, kpis } from '$lib/stores/portfolio';
  import { formatCurrency } from '$lib/utils/format';
  import type { Status } from '$lib/types';

  let statusFilter: Status | 'ALL' = 'ALL';
  let search = '';
  let selectedId: string | null = null;

  function selectCustomer(id: string): void {
    selectedId = id;
  }
  function closeDrawer(): void {
    selectedId = null;
  }

  const filters: { id: Status | 'ALL'; label: string; accent: string }[] = [
    { id: 'ALL', label: 'All', accent: 'text-ink-primary' },
    { id: 'ACTIVE', label: 'Active', accent: 'text-emerald' },
    { id: 'WARNING', label: 'Warning', accent: 'text-amber' },
    { id: 'LOCKED', label: 'Locked', accent: 'text-crimson' }
  ];

  $: counts = {
    ALL: $customers.length,
    ACTIVE: $kpis.activeNodes,
    WARNING: $kpis.warningCount,
    LOCKED: $kpis.lockedCount
  };
</script>

<svelte:head>
  <title>Customers · SecurePay Dealer Console</title>
</svelte:head>

<div class="page">
  <TopBar searchPlaceholder="Search by name, IMEI, phone…" />

  <PageHeader
    eyebrow="Portfolio"
    title="Customer Accounts"
    subtitle="Manage payment timers and remote device locks for every financed account."
  >
    <div slot="actions" class="flex items-center gap-2">
      <button type="button" class="btn-outline">
        <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 5v14M5 12h14" stroke-linecap="round" />
        </svg>
        Import CSV
      </button>
      <button type="button" class="btn-primary">+ New loan</button>
    </div>
  </PageHeader>

  <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
    <KpiCard
      title="Total accounts"
      value={$customers.length.toString()}
      sublabel="Across all statuses"
      accent="sky"
      progress={100}
      icon="M16 14a4 4 0 10-8 0M12 7a3 3 0 100 6 3 3 0 000-6z"
    />
    <KpiCard
      title="Active"
      value={$kpis.activeNodes.toString()}
      sublabel="In good standing"
      accent="emerald"
      progress={($kpis.activeNodes / Math.max(1, $customers.length)) * 100}
    />
    <KpiCard
      title="Outstanding"
      value={formatCurrency($portfolioMetrics.totalOutstanding)}
      sublabel="Loan balance to collect"
      accent="amber"
      progress={$portfolioMetrics.paidRatio}
    />
    <KpiCard
      title="Paid ratio"
      value={`${Math.round($portfolioMetrics.paidRatio)}%`}
      sublabel="Of total loaned out"
      accent="violet"
      progress={$portfolioMetrics.paidRatio}
    />
  </div>

  <div class="card mt-6 flex flex-wrap items-center gap-3 p-4">
    <div class="flex flex-wrap items-center gap-2">
      {#each filters as f (f.id)}
        <button
          type="button"
          on:click={() => (statusFilter = f.id)}
          class="rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors
                 {statusFilter === f.id
            ? 'border-emerald-300/30 bg-emerald-300/10 text-emerald'
            : 'border-edge bg-surface-100/40 text-ink-secondary hover:text-ink-primary hover:bg-hover'}"
        >
          <span class={f.accent}>{f.label}</span>
          <span class="ml-1.5 rounded-md bg-surface-100 px-1.5 py-0.5 text-2xs text-ink-muted tabular-nums">
            {counts[f.id]}
          </span>
        </button>
      {/each}
    </div>

    <div class="ml-auto flex flex-1 items-center gap-2 sm:flex-none">
      <label class="relative flex w-full items-center sm:w-64">
        <svg class="absolute left-3 h-3.5 w-3.5 text-ink-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <circle cx="11" cy="11" r="7" />
          <path d="M20 20l-3.5-3.5" stroke-linecap="round" />
        </svg>
        <input
          type="search"
          class="input pl-8"
          placeholder="Filter accounts…"
          bind:value={search}
        />
      </label>
    </div>
  </div>

  <div class="mt-4">
    <CustomerTable {statusFilter} {search} onSelect={selectCustomer} />
  </div>
</div>

<CustomerDrawer customerId={selectedId} onClose={closeDrawer} />