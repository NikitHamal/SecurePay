<script lang="ts">
  import type { CustomerNode, RemoteCommandResult } from '$lib/models/dashboard';

  export let customers: CustomerNode[] = [];
  export let onExtendTimer: (customerId: string) => RemoteCommandResult;
  export let onForceLock: (customerId: string) => RemoteCommandResult;

  const money = new Intl.NumberFormat('en-KE', {
    style: 'currency',
    currency: 'KES',
    maximumFractionDigits: 0
  });

  let commandResult: RemoteCommandResult | null = null;

  function runExtend(customerId: string) {
    commandResult = onExtendTimer(customerId);
  }

  function runLock(customerId: string) {
    commandResult = onForceLock(customerId);
  }

  function statusClass(status: CustomerNode['status']): string {
    if (status === 'ACTIVE') return 'bg-emerald-container text-emerald-active';
    if (status === 'WARNING') return 'bg-amber-warning/15 text-amber-warning';
    return 'bg-crimson-container text-crimson-vivid';
  }
</script>

<section class="overflow-hidden rounded-lg border border-white/10 bg-charcoal-900 shadow-m3">
  <div class="flex flex-col gap-3 border-b border-white/10 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
    <div>
      <h2 class="text-xl font-bold text-white">Live Customer Nodes</h2>
      <p class="mt-1 text-sm text-white/58">Remote commands dispatch to mock device-control APIs.</p>
    </div>
    {#if commandResult}
      <p class="rounded-lg bg-charcoal-800 px-3 py-2 text-sm text-white/70">{commandResult.message}</p>
    {/if}
  </div>

  <div class="overflow-x-auto">
    <table class="min-w-full divide-y divide-white/10 text-left text-sm">
      <thead class="bg-charcoal-800 text-xs uppercase text-white/50">
        <tr>
          <th class="px-5 py-3 font-semibold">Customer</th>
          <th class="px-5 py-3 font-semibold">IMEI</th>
          <th class="px-5 py-3 font-semibold">Status</th>
          <th class="px-5 py-3 font-semibold">Balance</th>
          <th class="px-5 py-3 font-semibold">Due</th>
          <th class="px-5 py-3 font-semibold">Actions</th>
        </tr>
      </thead>
      <tbody class="divide-y divide-white/8">
        {#each customers as customer}
          <tr class="hover:bg-white/4">
            <td class="px-5 py-4">
              <p class="font-semibold text-white">{customer.customerName}</p>
              <p class="text-white/50">{customer.phoneNumber} · {customer.dealerRegion}</p>
            </td>
            <td class="px-5 py-4 font-mono text-white/72">{customer.imei}</td>
            <td class="px-5 py-4">
              <span class={`inline-flex rounded-lg px-3 py-1 text-xs font-bold ${statusClass(customer.status)}`}>
                {customer.status}
              </span>
            </td>
            <td class="px-5 py-4 text-white/80">{money.format(customer.balanceCents / 100)}</td>
            <td class="px-5 py-4 text-white/62">{new Date(customer.dueAtIso).toLocaleString()}</td>
            <td class="px-5 py-4">
              <div class="flex min-w-72 gap-2">
                <button
                  class="rounded-lg bg-emerald-active px-3 py-2 text-xs font-bold text-charcoal-950 transition hover:brightness-110"
                  type="button"
                  on:click={() => runExtend(customer.id)}
                >
                  Extend Timer
                </button>
                <button
                  class="rounded-lg bg-crimson-vivid px-3 py-2 text-xs font-bold text-white transition hover:brightness-110"
                  type="button"
                  on:click={() => runLock(customer.id)}
                >
                  Force Remote Lock
                </button>
              </div>
            </td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
</section>
