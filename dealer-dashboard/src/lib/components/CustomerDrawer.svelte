<script lang="ts">
  import type { Customer } from '$lib/types';
  import { customers, extendTimer, forceRemoteLock, pending } from '$lib/stores/customers';
  import { formatCountdown, formatCurrency, formatDate, formatPhone, formatRelative } from '$lib/utils/format';
  import StatusBadge from '$lib/components/ui/StatusBadge.svelte';
  import ProgressRing from '$lib/components/charts/ProgressRing.svelte';
  import { fade } from 'svelte/transition';

  export let customerId: string | null = null;
  export let onClose: () => void = () => {};

  $: customer = customerId ? $customers.find((c) => c.id === customerId) ?? null : null;
  $: ratio = customer && customer.totalLoanAmount > 0
    ? (customer.amountPaid / customer.totalLoanAmount) * 100
    : 0;
  $: ringColor = ratio > 80 ? '#10B981' : ratio > 50 ? '#F59E0B' : '#EF4444';
  $: isPending = customer ? $pending.has(customer.id) : false;

  async function extend(): Promise<void> {
    if (customer) await extendTimer(customer.id, 24);
  }
  async function lock(): Promise<void> {
    if (customer) await forceRemoteLock(customer.id);
  }

  function backdropClick(e: MouseEvent) {
    if (e.target === e.currentTarget) onClose();
  }

  function handleKey(e: KeyboardEvent) {
    if (e.key === 'Escape') onClose();
  }
</script>

<svelte:window on:keydown={handleKey} />

{#if customer}
  <div
    class="fixed inset-0 z-40 flex justify-end"
    style="background: var(--overlay-bg); backdrop-filter: blur(4px);"
    on:click={backdropClick}
    role="dialog"
    aria-modal="true"
    aria-labelledby="drawer-title"
    transition:fade={{ duration: 180 }}
  >
    <aside
      class="relative flex h-full w-full max-w-md flex-col border-l border-edge bg-surface-200 shadow-card-hover"
      style="transform: translateX(0); transition: transform 280ms cubic-bezier(0.22, 1, 0.36, 1);"
    >
      <header class="flex items-start justify-between gap-3 border-b border-edge px-6 py-5">
        <div class="flex items-center gap-3 min-w-0">
          <span
            class="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl text-sm font-semibold text-white"
            style="background: linear-gradient(135deg, hsl({(parseInt(customer.id.replace(/\D/g,'')) * 37) % 360}, 70%, 60%), hsl({(parseInt(customer.id.replace(/\D/g,'')) * 37 + 40) % 360}, 70%, 50%));"
          >
            {customer.customerName.split(' ').map((p) => p[0]).join('').slice(0, 2)}
          </span>
          <div class="min-w-0">
            <h2 id="drawer-title" class="truncate text-lg font-semibold text-ink-primary">{customer.customerName}</h2>
            <p class="text-2xs text-ink-muted">{customer.id} · {formatPhone(customer.phoneNumber)}</p>
          </div>
        </div>
        <button
          type="button"
          class="btn-ghost h-8 w-8 !p-0"
          on:click={onClose}
          aria-label="Close drawer"
        >
          <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M6 6l12 12M6 18L18 6" stroke-linecap="round" />
          </svg>
        </button>
      </header>

      <div class="flex-1 overflow-y-auto p-6">
        <div class="card flex items-center justify-between p-4">
          <div>
            <p class="section-title">Status</p>
            <div class="mt-2"><StatusBadge status={customer.status} /></div>
          </div>
          <div class="text-right">
            <p class="section-title">Next payment</p>
            <p
              class="mt-1 font-mono text-lg tabular-nums {customer.nextPaymentDueEpochMillis - Date.now() <= 0 ? 'text-crimson font-semibold' : 'text-ink-primary'}"
            >
              {formatCountdown(customer.nextPaymentDueEpochMillis - Date.now())}
            </p>
            <p class="text-2xs text-ink-muted">{formatDate(customer.nextPaymentDueEpochMillis)}</p>
          </div>
        </div>

        <div class="card mt-4 p-5">
          <div class="flex items-center justify-between">
            <p class="section-title">Loan progress</p>
            <span class="text-2xs text-ink-muted">{formatRelative(customer.nextPaymentDueEpochMillis)}</span>
          </div>
          <div class="mt-3 flex items-center gap-5">
            <ProgressRing percent={ratio} size={84} stroke={9} color={ringColor} label={`${ratio.toFixed(0)}%`} />
            <div class="flex-1 grid grid-cols-2 gap-3 text-sm">
              <div>
                <p class="text-2xs uppercase tracking-wide text-ink-muted">Total loan</p>
                <p class="font-semibold text-ink-primary tabular-nums">{formatCurrency(customer.totalLoanAmount)}</p>
              </div>
              <div>
                <p class="text-2xs uppercase tracking-wide text-ink-muted">Paid</p>
                <p class="font-semibold text-emerald tabular-nums">{formatCurrency(customer.amountPaid)}</p>
              </div>
              <div>
                <p class="text-2xs uppercase tracking-wide text-ink-muted">Outstanding</p>
                <p class="font-semibold text-ink-primary tabular-nums">{formatCurrency(customer.remainingBalance)}</p>
              </div>
              <div>
                <p class="text-2xs uppercase tracking-wide text-ink-muted">Daily rate</p>
                <p class="font-semibold text-ink-primary tabular-nums">{formatCurrency(customer.dailyRate)}</p>
              </div>
            </div>
          </div>
        </div>

        <div class="card mt-4 p-5">
          <p class="section-title">Device · Plan</p>
          <div class="mt-3 grid grid-cols-1 gap-3 text-sm">
            <div class="flex items-center justify-between">
              <span class="text-ink-secondary">Device</span>
              <span class="text-ink-primary font-medium">{customer.deviceModel}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-ink-secondary">IMEI</span>
              <span class="font-mono text-2xs text-ink-muted">{customer.imei}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-ink-secondary">Plan</span>
              <span class="text-ink-primary font-medium">{customer.planName}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-ink-secondary">National ID</span>
              <span class="font-mono text-2xs text-ink-muted">{customer.nationalId}</span>
            </div>
          </div>
        </div>
      </div>

      <footer class="flex items-center gap-2 border-t border-edge bg-surface-200/80 px-6 py-4 backdrop-blur">
        <button
          type="button"
          class="btn-emerald flex-1"
          disabled={isPending}
          on:click={extend}
        >
          <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 8v8M8 12h8" stroke-linecap="round" />
          </svg>
          Extend timer +24h
        </button>
        <button
          type="button"
          class="btn-crimson flex-1"
          disabled={customer.status === 'LOCKED' || isPending}
          on:click={lock}
        >
          <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M6 11V8a6 6 0 1112 0v3M5 11h14v10H5z" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          Force remote lock
        </button>
      </footer>
    </aside>
  </div>
{/if}