<script lang="ts">
  import { Boxes, CircleDollarSign, LockKeyhole, RadioTower } from '@lucide/svelte';
  import { customers } from '$lib/api/mockCommands';
  import KpiCard from '$lib/components/KpiCard.svelte';
  import LiveCustomerTable from '$lib/components/LiveCustomerTable.svelte';
  import Sidebar from '$lib/components/Sidebar.svelte';
  import StatusPill from '$lib/components/StatusPill.svelte';
  import { inventoryDevices, ledgerEntries } from '$lib/data/customers';
  import type { SectionKey } from '$lib/types';

  let activeSection: SectionKey = 'overview';

  const money = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0
  });

  $: activeNodes = $customers.filter((customer) => customer.status === 'active').length;
  $: lockedNodes = $customers.filter((customer) => customer.status === 'locked').length;
  $: defaultCount = $customers.filter((customer) => customer.paymentStatus === 'overdue').length;
  $: receivables = $customers.reduce((sum, customer) => sum + customer.outstandingBalanceCents, 0);
  $: availableInventory = inventoryDevices.filter((device) => device.status === 'available').length;
</script>

<svelte:head>
  <title>SecurePay Dealer Console</title>
</svelte:head>

<main class="min-h-screen bg-charcoal-950 text-slate-100 lg:grid lg:grid-cols-[18rem_1fr]">
  <div class="lg:min-h-screen">
    <Sidebar active={activeSection} onNavigate={(section) => (activeSection = section)} />
  </div>

  <section class="min-w-0 px-4 py-5 sm:px-6 lg:px-8">
    <div class="mb-6 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
      <div>
        <p class="text-sm font-semibold uppercase tracking-[0.18em] text-secure-emerald">Financing Operations</p>
        <h2 class="mt-2 text-2xl font-semibold text-slate-50">SecurePay Network Control</h2>
      </div>
      <div class="rounded-m3 border border-secure-emerald/30 bg-secure-emerald/10 px-3 py-2 text-sm font-semibold text-secure-emerald">
        Live mock API
      </div>
    </div>

    {#if activeSection === 'overview'}
      <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <KpiCard
          title="Active Nodes"
          value={`${activeNodes}`}
          delta="Devices currently inside financed access windows"
          icon={RadioTower}
          tone="emerald"
        />
        <KpiCard
          title="Locked / Restricted"
          value={`${lockedNodes}`}
          delta={`${defaultCount} overdue contracts require recovery action`}
          icon={LockKeyhole}
          tone="crimson"
        />
        <KpiCard
          title="Receivables"
          value={money.format(receivables / 100)}
          delta="Open financed balance across active dealer portfolio"
          icon={CircleDollarSign}
          tone="cyan"
        />
        <KpiCard
          title="Ready Inventory"
          value={`${availableInventory}`}
          delta={`${inventoryDevices.length} total tracked IMEI records`}
          icon={Boxes}
          tone="amber"
        />
      </div>

      <div class="mt-6">
        <LiveCustomerTable />
      </div>
    {:else if activeSection === 'inventory'}
      <section class="rounded-m3 border border-white/10 bg-charcoal-850 shadow-m3">
        <div class="border-b border-white/10 px-4 py-4">
          <h2 class="text-lg font-semibold">Inventory IMEI Matrix</h2>
          <p class="text-sm text-slate-400">Dealer stock, assignment state, and lock eligibility.</p>
        </div>
        <div class="overflow-x-auto">
          <table class="min-w-full divide-y divide-white/10 text-left text-sm">
            <thead class="bg-charcoal-800 text-xs uppercase tracking-wide text-slate-400">
              <tr>
                <th class="px-4 py-3">IMEI</th>
                <th class="px-4 py-3">Model</th>
                <th class="px-4 py-3">Batch</th>
                <th class="px-4 py-3">Location</th>
                <th class="px-4 py-3">Status</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-white/10">
              {#each inventoryDevices as device}
                <tr>
                  <td class="px-4 py-4 font-mono text-xs">{device.imei}</td>
                  <td class="px-4 py-4">{device.model}</td>
                  <td class="px-4 py-4">{device.batchId}</td>
                  <td class="px-4 py-4">{device.dealerLocation}</td>
                  <td class="px-4 py-4"><StatusPill status={device.status === 'assigned' ? 'active' : device.status} /></td>
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
      </section>
    {:else if activeSection === 'customers'}
      <LiveCustomerTable />
    {:else}
      <section class="rounded-m3 border border-white/10 bg-charcoal-850 shadow-m3">
        <div class="border-b border-white/10 px-4 py-4">
          <h2 class="text-lg font-semibold">Payment Ledger</h2>
          <p class="text-sm text-slate-400">Recent cash, mobile money, and card postings.</p>
        </div>
        <div class="overflow-x-auto">
          <table class="min-w-full divide-y divide-white/10 text-left text-sm">
            <thead class="bg-charcoal-800 text-xs uppercase tracking-wide text-slate-400">
              <tr>
                <th class="px-4 py-3">Ledger ID</th>
                <th class="px-4 py-3">Customer</th>
                <th class="px-4 py-3">Amount</th>
                <th class="px-4 py-3">Channel</th>
                <th class="px-4 py-3">Posted</th>
                <th class="px-4 py-3">Status</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-white/10">
              {#each ledgerEntries as entry}
                <tr>
                  <td class="px-4 py-4 font-mono text-xs">{entry.id}</td>
                  <td class="px-4 py-4">{entry.customerName}</td>
                  <td class="px-4 py-4">{money.format(entry.amountCents / 100)}</td>
                  <td class="px-4 py-4 capitalize">{entry.channel.replace('_', ' ')}</td>
                  <td class="px-4 py-4">{entry.postedAt}</td>
                  <td class="px-4 py-4"><StatusPill status={entry.status === 'settled' ? 'current' : 'due_soon'} /></td>
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
      </section>
    {/if}
  </section>
</main>
