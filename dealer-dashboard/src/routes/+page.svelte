<script lang="ts">
  import CustomerTable from '$lib/components/CustomerTable.svelte';
  import KpiCard from '$lib/components/KpiCard.svelte';
  import PageHeader from '$lib/components/PageHeader.svelte';
  import { kpiSummary, loading } from '$lib/stores/customers';
  import { formatCurrency } from '$lib/utils/format';

  $: summary = $kpiSummary;
</script>

<svelte:head>
  <title>Overview · SecurePay Dealer Console</title>
</svelte:head>

<section class="p-6">
  <PageHeader title="Portfolio Overview" subtitle="Real-time device financing status across your fleet" />

  <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
    <KpiCard
      title="Active Nodes"
      value={summary.activeNodes.toString()}
      sublabel="Devices in good standing"
      accent="emerald"
    />
    <KpiCard
      title="Default / Locked"
      value={summary.lockedCount.toString()}
      sublabel="{summary.warningCount} approaching due"
      accent="crimson"
    />
    <KpiCard
      title="Total Outstanding"
      value={formatCurrency(summary.totalOutstanding)}
      sublabel="Across all financed devices"
      accent="amber"
    />
    <KpiCard
      title="Collected Today"
      value={formatCurrency(summary.collectedToday)}
      sublabel="Estimated daily accrual"
      accent="neutral"
    />
  </div>

  <div class="mt-8 flex items-center justify-between">
    <h2 class="text-base font-semibold text-text-primary">Customer Accounts</h2>
    {#if $loading}
      <span class="text-xs text-text-secondary">Loading…</span>
    {/if}
  </div>

  <div class="mt-3">
    <CustomerTable />
  </div>
</section>
