<script lang="ts">
  import CustomerCommandTable from '$lib/components/CustomerCommandTable.svelte';
  import KpiCard from '$lib/components/KpiCard.svelte';
  import Sidebar from '$lib/components/Sidebar.svelte';
  import {
    extendTimer,
    fetchCustomerNodes,
    fetchKpiSummary,
    forceRemoteLock
  } from '$lib/api/mockDealerApi';
  import type { CustomerNode } from '$lib/models/dashboard';

  const money = new Intl.NumberFormat('en-KE', {
    style: 'currency',
    currency: 'KES',
    maximumFractionDigits: 0
  });

  let customers: CustomerNode[] = fetchCustomerNodes();
  $: kpis = fetchKpiSummary();

  function refreshCustomers() {
    customers = fetchCustomerNodes();
  }

  function handleExtend(customerId: string) {
    const result = extendTimer(customerId);
    refreshCustomers();
    return result;
  }

  function handleForceLock(customerId: string) {
    const result = forceRemoteLock(customerId);
    refreshCustomers();
    return result;
  }
</script>

<svelte:head>
  <title>SecurePay Dealer Dashboard</title>
  <meta
    name="description"
    content="SecurePay dealer dashboard for financed smartphone inventory, customers, and remote lock operations."
  />
</svelte:head>

<main class="min-h-screen bg-charcoal-950 text-white">
  <div class="flex min-h-screen flex-col md:flex-row">
    <Sidebar active="Overview" />

    <section class="flex-1 px-4 py-5 sm:px-6 lg:px-8">
      <div class="mb-6 flex flex-col gap-3 border-b border-white/10 pb-5 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <p class="text-sm font-semibold uppercase text-emerald-active">Operations overview</p>
          <h1 class="mt-2 text-3xl font-bold tracking-normal text-white">Financed Device Fleet</h1>
        </div>
        <div class="rounded-lg border border-white/10 bg-charcoal-900 px-4 py-3 text-sm text-white/62">
          Mock API command layer · Live table state
        </div>
      </div>

      <section class="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
        <KpiCard label="Active Nodes" value={String(kpis.activeNodes)} tone="active" />
        <KpiCard label="Warnings" value={String(kpis.warningNodes)} tone="warning" />
        <KpiCard label="Locked Devices" value={String(kpis.lockedNodes)} tone="danger" />
        <KpiCard
          label="Portfolio Balance"
          value={money.format(kpis.portfolioBalanceCents / 100)}
          tone="neutral"
        />
        <KpiCard
          label="Collected Today"
          value={money.format(kpis.collectedTodayCents / 100)}
          tone="active"
        />
      </section>

      <CustomerCommandTable
        {customers}
        onExtendTimer={handleExtend}
        onForceLock={handleForceLock}
      />
    </section>
  </div>
</main>
