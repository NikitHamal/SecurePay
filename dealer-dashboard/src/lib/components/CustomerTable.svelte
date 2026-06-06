<script lang="ts">
  import { onDestroy, onMount } from 'svelte';
  import type { Customer } from '$lib/types';
  import { customers, extendTimer, forceRemoteLock, pending } from '$lib/stores/customers';
  import { formatCountdown, formatCurrency } from '$lib/utils/format';
  import StatusBadge from './StatusBadge.svelte';

  /** Number of hours added by the Extend Timer action. */
  export let extendHours = 24;

  /** Ticking clock used to drive the live countdowns. */
  let now: number = Date.now();
  let interval: ReturnType<typeof setInterval> | undefined;

  onMount(() => {
    interval = setInterval(() => {
      now = Date.now();
    }, 1000);
  });

  onDestroy(() => {
    if (interval) {
      clearInterval(interval);
    }
  });

  function remainingMs(customer: Customer): number {
    return customer.nextPaymentDueEpochMillis - now;
  }

  function isPending(set: Set<string>, id: string): boolean {
    return set.has(id);
  }

  async function onExtend(id: string): Promise<void> {
    await extendTimer(id, extendHours);
  }

  async function onLock(id: string): Promise<void> {
    await forceRemoteLock(id);
  }
</script>

<div class="card overflow-hidden">
  <div class="overflow-x-auto">
    <table class="w-full min-w-[860px] border-collapse text-left text-sm">
      <thead>
        <tr class="border-b border-white/5 text-xs uppercase tracking-wide text-text-secondary">
          <th class="px-4 py-3 font-medium">Customer</th>
          <th class="px-4 py-3 font-medium">IMEI</th>
          <th class="px-4 py-3 font-medium">Device</th>
          <th class="px-4 py-3 text-right font-medium">Balance</th>
          <th class="px-4 py-3 font-medium">Status</th>
          <th class="px-4 py-3 font-medium">Next Due</th>
          <th class="px-4 py-3 text-right font-medium">Actions</th>
        </tr>
      </thead>
      <tbody>
        {#each $customers as customer (customer.id)}
          {@const left = remainingMs(customer)}
          <tr class="border-b border-white/5 transition-colors last:border-b-0 hover:bg-white/[0.03]">
            <td class="px-4 py-3">
              <div class="font-medium text-text-primary">{customer.customerName}</div>
              <div class="text-xs text-text-secondary">{customer.phoneNumber}</div>
            </td>
            <td class="px-4 py-3 font-mono text-xs text-text-secondary">{customer.imei}</td>
            <td class="px-4 py-3">
              <div class="text-text-primary">{customer.deviceModel}</div>
              <div class="text-xs text-text-secondary">{customer.planName}</div>
            </td>
            <td class="px-4 py-3 text-right">
              <div class="font-medium text-text-primary">{formatCurrency(customer.remainingBalance)}</div>
              <div class="text-xs text-text-secondary">of {formatCurrency(customer.totalLoanAmount)}</div>
            </td>
            <td class="px-4 py-3">
              <StatusBadge status={customer.status} />
            </td>
            <td class="px-4 py-3">
              <span
                class="font-mono text-sm tabular-nums {left <= 0 ? 'font-semibold text-crimson' : 'text-text-primary'}"
              >
                {formatCountdown(left)}
              </span>
            </td>
            <td class="px-4 py-3">
              <div class="flex justify-end gap-2">
                <button
                  type="button"
                  class="btn-emerald"
                  disabled={isPending($pending, customer.id)}
                  on:click={() => onExtend(customer.id)}
                >
                  Extend Timer
                </button>
                <button
                  type="button"
                  class="btn-crimson"
                  disabled={customer.status === 'LOCKED' || isPending($pending, customer.id)}
                  on:click={() => onLock(customer.id)}
                >
                  Force Remote Lock
                </button>
              </div>
            </td>
          </tr>
        {/each}

        {#if $customers.length === 0}
          <tr>
            <td colspan="7" class="px-4 py-10 text-center text-text-secondary"> No customer accounts loaded. </td>
          </tr>
        {/if}
      </tbody>
    </table>
  </div>
</div>
