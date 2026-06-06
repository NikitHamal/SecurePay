<script lang="ts">
  import { Clock3, LockKeyhole } from '@lucide/svelte';
  import { customers, extendTimer, forceRemoteLock } from '$lib/api/mockCommands';
  import StatusPill from '$lib/components/StatusPill.svelte';

  let commandInFlight: Record<string, 'extend' | 'lock' | null> = {};

  const money = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD'
  });

  const date = new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });

  async function runExtend(customerId: string) {
    commandInFlight = { ...commandInFlight, [customerId]: 'extend' };
    await extendTimer(customerId);
    commandInFlight = { ...commandInFlight, [customerId]: null };
  }

  async function runLock(customerId: string) {
    commandInFlight = { ...commandInFlight, [customerId]: 'lock' };
    await forceRemoteLock(customerId);
    commandInFlight = { ...commandInFlight, [customerId]: null };
  }
</script>

<section class="overflow-hidden rounded-m3 border border-white/10 bg-charcoal-850 shadow-m3">
  <div class="flex flex-col gap-2 border-b border-white/10 px-4 py-4 md:flex-row md:items-center md:justify-between">
    <div>
      <h2 class="text-lg font-semibold text-slate-50">Live Customer Command Grid</h2>
      <p class="text-sm text-slate-400">Active and locked financed nodes with remote timer controls.</p>
    </div>
  </div>

  <div class="overflow-x-auto">
    <table class="min-w-full divide-y divide-white/10 text-left text-sm">
      <thead class="bg-charcoal-800 text-xs uppercase tracking-wide text-slate-400">
        <tr>
          <th class="px-4 py-3 font-semibold">Customer</th>
          <th class="px-4 py-3 font-semibold">IMEI</th>
          <th class="px-4 py-3 font-semibold">Status</th>
          <th class="px-4 py-3 font-semibold">Next Due</th>
          <th class="px-4 py-3 font-semibold">Balance</th>
          <th class="px-4 py-3 text-right font-semibold">Actions</th>
        </tr>
      </thead>
      <tbody class="divide-y divide-white/10">
        {#each $customers as customer}
          <tr class="text-slate-200">
            <td class="px-4 py-4">
              <div class="font-semibold text-slate-50">{customer.customerName}</div>
              <div class="text-xs text-slate-400">{customer.phoneNumber} · {customer.region}</div>
            </td>
            <td class="px-4 py-4 font-mono text-xs text-slate-300">{customer.imei}</td>
            <td class="space-x-2 px-4 py-4">
              <StatusPill status={customer.status} />
              <StatusPill status={customer.paymentStatus} />
            </td>
            <td class="px-4 py-4 text-slate-300">{date.format(customer.nextDueEpochMs)}</td>
            <td class="px-4 py-4 text-slate-300">{money.format(customer.outstandingBalanceCents / 100)}</td>
            <td class="px-4 py-4">
              <div class="flex justify-end gap-2">
                <button
                  type="button"
                  class="inline-flex h-9 items-center gap-2 rounded-m3 bg-secure-emerald px-3 text-xs font-semibold text-charcoal-950 disabled:cursor-not-allowed disabled:opacity-50"
                  disabled={commandInFlight[customer.id] !== undefined && commandInFlight[customer.id] !== null}
                  on:click={() => runExtend(customer.id)}
                >
                  <Clock3 size={15} />
                  {commandInFlight[customer.id] === 'extend' ? 'Extending' : 'Extend Timer'}
                </button>
                <button
                  type="button"
                  class="inline-flex h-9 items-center gap-2 rounded-m3 bg-secure-crimson px-3 text-xs font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
                  disabled={commandInFlight[customer.id] !== undefined && commandInFlight[customer.id] !== null}
                  on:click={() => runLock(customer.id)}
                >
                  <LockKeyhole size={15} />
                  {commandInFlight[customer.id] === 'lock' ? 'Locking' : 'Force Remote Lock'}
                </button>
              </div>
            </td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
</section>
