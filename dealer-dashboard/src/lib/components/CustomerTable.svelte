<script lang="ts">
  import { onDestroy, onMount } from 'svelte';
  import type { Customer, Status } from '$lib/types';
  import { customers, extendTimer, forceRemoteLock, pending } from '$lib/stores/customers';
  import { formatCountdown, formatCurrency, formatPhone } from '$lib/utils/format';
  import StatusBadge from '$lib/components/ui/StatusBadge.svelte';
  import ProgressRing from '$lib/components/charts/ProgressRing.svelte';

  export let extendHours = 24;
  export let statusFilter: Status | 'ALL' = 'ALL';
  export let search: string = '';
  export let onSelect: (id: string) => void = () => {};

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

  function paidRatio(customer: Customer): number {
    if (customer.totalLoanAmount === 0) return 0;
    return (customer.amountPaid / customer.totalLoanAmount) * 100;
  }

  async function onExtend(id: string): Promise<void> {
    await extendTimer(id, extendHours);
  }

  async function onLock(id: string): Promise<void> {
    await forceRemoteLock(id);
  }

  $: visible = $customers.filter((c) => {
    const matchesStatus = statusFilter === 'ALL' || c.status === statusFilter;
    const q = search.trim().toLowerCase();
    if (!q) return matchesStatus;
    const haystack = `${c.customerName} ${c.phoneNumber} ${c.imei} ${c.deviceModel} ${c.id}`.toLowerCase();
    return matchesStatus && haystack.includes(q);
  });
</script>

<div class="card overflow-hidden">
  <div class="overflow-x-auto">
    <table class="w-full min-w-[920px] border-collapse text-left text-sm">
      <thead>
        <tr class="border-b border-edge text-2xs uppercase tracking-[0.12em] text-ink-muted">
          <th class="px-4 py-3 font-semibold">Customer</th>
          <th class="px-4 py-3 font-semibold">Device · Plan</th>
          <th class="px-4 py-3 font-semibold">Loan progress</th>
          <th class="px-4 py-3 font-semibold">Status</th>
          <th class="px-4 py-3 font-semibold">Next due</th>
          <th class="px-4 py-3 text-right font-semibold">Actions</th>
        </tr>
      </thead>
      <tbody>
        {#each visible as customer (customer.id)}
          {@const ratio = paidRatio(customer)}
          {@const left = remainingMs(customer)}
          <tr
            class="group cursor-pointer border-b border-edge/60 last:border-b-0 transition-colors hover:bg-hover"
            on:click={() => onSelect(customer.id)}
          >
            <td class="px-4 py-3.5">
              <div class="flex items-center gap-3">
                <span
                  class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-xs font-semibold text-white"
                  style="background: linear-gradient(135deg, hsl({(parseInt(customer.id.replace(/\D/g,'')) * 37) % 360}, 70%, 60%), hsl({(parseInt(customer.id.replace(/\D/g,'')) * 37 + 40) % 360}, 70%, 50%));"
                  aria-hidden="true"
                >
                  {customer.customerName.split(' ').map((p) => p[0]).join('').slice(0, 2)}
                </span>
                <div class="min-w-0">
                  <div class="font-medium text-ink-primary truncate">{customer.customerName}</div>
                  <div class="text-2xs text-ink-muted">{customer.id} · {formatPhone(customer.phoneNumber)}</div>
                </div>
              </div>
            </td>
            <td class="px-4 py-3.5">
              <div class="text-ink-primary">{customer.deviceModel}</div>
              <div class="text-2xs text-ink-muted">{customer.planName} · {formatCurrency(customer.dailyRate)}/day</div>
            </td>
            <td class="px-4 py-3.5 min-w-[200px]">
              <div class="flex items-center gap-3">
                <div class="flex-1">
                  <div class="mb-1 flex items-baseline justify-between text-2xs text-ink-secondary">
                    <span class="font-medium text-ink-primary tabular-nums">{formatCurrency(customer.amountPaid)}</span>
                    <span class="tabular-nums">{ratio.toFixed(0)}%</span>
                  </div>
                  <div class="h-1.5 w-full overflow-hidden rounded-full" style="background: var(--progress-track);">
                    <div
                      class="h-full rounded-full"
                      style="width: {Math.min(100, ratio)}%; background: linear-gradient(90deg, {ratio > 80 ? '#10B981' : ratio > 50 ? '#F59E0B' : '#EF4444'}, {ratio > 80 ? '#34D399' : ratio > 50 ? '#FBBF24' : '#F87171'});"
                    ></div>
                  </div>
                  <div class="mt-1 text-2xs text-ink-muted">
                    {formatCurrency(customer.remainingBalance)} left
                  </div>
                </div>
              </div>
            </td>
            <td class="px-4 py-3.5">
              <StatusBadge status={customer.status} size="sm" />
            </td>
            <td class="px-4 py-3.5">
              <span
                class="font-mono text-sm tabular-nums {left <= 0 ? 'font-semibold text-crimson' : 'text-ink-primary'}"
              >
                {formatCountdown(left)}
              </span>
            </td>
            <td class="px-4 py-3.5">
              <div class="flex justify-end gap-2 opacity-70 transition-opacity group-hover:opacity-100" on:click|stopPropagation>
                <button
                  type="button"
                  class="btn-emerald"
                  disabled={isPending($pending, customer.id)}
                  on:click={() => onExtend(customer.id)}
                >
                  +{extendHours}h
                </button>
                <button
                  type="button"
                  class="btn-crimson"
                  disabled={customer.status === 'LOCKED' || isPending($pending, customer.id)}
                  on:click={() => onLock(customer.id)}
                >
                  Lock
                </button>
              </div>
            </td>
          </tr>
        {/each}

        {#if visible.length === 0}
          <tr>
            <td colspan="6" class="px-4 py-14 text-center">
              <div class="mx-auto flex max-w-xs flex-col items-center gap-2">
                <span class="flex h-10 w-10 items-center justify-center rounded-full bg-surface-100 text-ink-muted">
                  <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                    <path d="M21 21l-4.3-4.3M11 18a7 7 0 110-14 7 7 0 010 14z" stroke-linecap="round" />
                  </svg>
                </span>
                <p class="text-sm text-ink-secondary">No accounts match your filters.</p>
                <p class="text-2xs text-ink-muted">Try a different status or search term.</p>
              </div>
            </td>
          </tr>
        {/if}
      </tbody>
    </table>
  </div>
</div>