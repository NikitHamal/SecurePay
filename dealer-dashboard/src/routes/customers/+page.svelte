<script lang="ts">
  import CustomerTable from '$lib/components/CustomerTable.svelte';
  import CustomerDrawer from '$lib/components/CustomerDrawer.svelte';
  import PageHeader from '$lib/components/ui/PageHeader.svelte';
  import TopBar from '$lib/components/layout/TopBar.svelte';
  import { customers } from '$lib/stores/customers';
  import { portfolioMetrics, kpis } from '$lib/stores/portfolio';
  import { formatCurrency } from '$lib/utils/format';
  import { openNewLoan, openProvision } from '$lib/stores/ui';
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
    { id: 'LOCKED', label: 'Locked', accent: 'text-crimson' },
    { id: 'STOLEN', label: 'Stolen', accent: 'text-crimson-secondary' }
  ];

  $: counts = {
    ALL: $customers.length,
    ACTIVE: $customers.filter(c => c.status === 'ACTIVE').length,
    WARNING: $customers.filter(c => c.status === 'WARNING').length,
    LOCKED: $customers.filter(c => c.status === 'LOCKED').length,
    STOLEN: $customers.filter(c => c.status === 'STOLEN').length
  };
</script>

<svelte:head>
  <title>Customers · Touch Base</title>
</svelte:head>

<div class="page">
  <TopBar searchPlaceholder="Search by name, IMEI, phone…" />

  <PageHeader
    eyebrow="Portfolio"
    title="Customer Accounts"
    subtitle="Manage payment timers and remote device locks for every financed account."
  >
    <div slot="actions" class="flex items-center gap-2">
      <button type="button" class="btn-outline" on:click={() => openProvision()}>
        <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/>
          <rect x="3" y="14" width="7" height="7" rx="1"/><path d="M14 14h3v3M21 14v3M14 21h3M17 17h4v4" stroke-linecap="round"/>
        </svg>
        Provision
      </button>
      <button type="button" class="btn-primary" on:click={() => openNewLoan()}>
        <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4">
          <path d="M12 5v14M5 12h14" stroke-linecap="round" />
        </svg>
        Enroll customer
      </button>
    </div>
  </PageHeader>

  <div class="grid grid-cols-2 gap-3 sm:grid-cols-2 lg:grid-cols-4">
    <div class="card p-4">
      <p class="text-xs text-ink-muted font-medium">Total accounts</p>
      <p class="mt-1 text-2xl font-semibold tabular-nums text-ink-primary">{$customers.length}</p>
      <p class="mt-0.5 text-xs text-ink-muted">Across all statuses</p>
    </div>
    <div class="card p-4">
      <p class="text-xs text-ink-muted font-medium">Active</p>
      <p class="mt-1 text-2xl font-semibold tabular-nums text-emerald">{$kpis.activeNodes}</p>
      <p class="mt-0.5 text-xs text-ink-muted">In good standing</p>
    </div>
    <div class="card p-4">
      <p class="text-xs text-ink-muted font-medium">Outstanding</p>
      <p class="mt-1 text-2xl font-semibold tabular-nums text-ink-primary">{formatCurrency($portfolioMetrics.totalOutstanding)}</p>
      <p class="mt-0.5 text-xs text-ink-muted">Balance to collect</p>
    </div>
    <div class="card p-4">
      <p class="text-xs text-ink-muted font-medium">Paid ratio</p>
      <p class="mt-1 text-2xl font-semibold tabular-nums text-emerald">{Math.round($portfolioMetrics.paidRatio)}%</p>
      <p class="mt-0.5 text-xs text-ink-muted">Of total loaned out</p>
    </div>
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