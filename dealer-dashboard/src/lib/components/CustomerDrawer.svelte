<script lang="ts">
  import type { Customer } from '$lib/types';
  import { approveRelease, customers, deleteCustomer, extendTimer, forceRemoteLock, pending, updateCustomer } from '$lib/stores/customers';
  import { formatCountdown, formatCurrency, formatDate, formatPhone, formatRelative } from '$lib/utils/format';
  import StatusBadge from '$lib/components/ui/StatusBadge.svelte';
  import ProgressRing from '$lib/components/charts/ProgressRing.svelte';
  import { fade } from 'svelte/transition';
  import { getAccountLocations } from '$lib/api/client';

  export let customerId: string | null = null;
  export let onClose: () => void = () => {};

  let editing = false;
  let saving = false;
  let editName = '';
  let editNationalId = '';
  let editPhone = '';
  let editDailyRate = '';
  let editTotalLoan = '';
  let editTermDays = '';

  $: customer = customerId ? $customers.find((c) => c.id === customerId) ?? null : null;
  $: ratio = customer && customer.totalLoanAmount > 0
    ? (customer.amountPaid / customer.totalLoanAmount) * 100
    : 0;
  $: ringColor = ratio > 80 ? '#10B981' : ratio > 50 ? '#F59E0B' : '#EF4444';
  $: isPending = customer ? $pending.has(customer.id) : false;

  function startEditing() {
    if (!customer) return;
    editName = customer.customerName;
    editNationalId = customer.nationalId;
    editPhone = customer.phoneNumber;
    editDailyRate = String(customer.dailyRate);
    editTotalLoan = String(customer.totalLoanAmount);
    editTermDays = String(customer.termDays);
    editing = true;
  }

  function cancelEditing() {
    editing = false;
  }

  async function saveEditing() {
    if (!customer) return;
    saving = true;
    try {
      await updateCustomer(customer.id, {
        customerName: editName.trim(),
        nationalId: editNationalId.trim(),
        phoneNumber: editPhone.trim(),
        dailyRate: Number(editDailyRate),
        totalLoanAmount: Number(editTotalLoan),
        termDays: Number(editTermDays),
      });
      editing = false;
    } finally {
      saving = false;
    }
  }

  async function extend(): Promise<void> {
    if (customer) await extendTimer(customer.id, 24);
  }
  async function lock(): Promise<void> {
    if (customer) await forceRemoteLock(customer.id);
  }
  async function releaseAccount(): Promise<void> {
    if (customer) await approveRelease(customer.id, customer.remainingBalance > 0);
  }
  async function removeCustomer(): Promise<void> {
    if (!customer) return;
    const ok = window.confirm(`Delete ${customer.customerName}? This removes the account, payments, lock events, provisioning tokens, and returns the device to in-stock.`);
    if (!ok) return;
    await deleteCustomer(customer.id);
    onClose();
  }

  function avatarHue(id: string): number {
    const digits = id.replace(/\D/g, '');
    const seed = Number.parseInt(digits || '23', 10);
    return (seed * 37) % 360;
  }

  function handleKey(e: KeyboardEvent) {
    if (e.key === 'Escape') {
      if (editing) { cancelEditing(); }
      else { onClose(); }
    }
  }

  let locations: any[] = [];
  let loadingLocations = false;

  async function loadLocations(id: string) {
    loadingLocations = true;
    try {
      locations = await getAccountLocations(id);
    } catch (e) {
      console.error('Failed to load locations', e);
      locations = [];
    } finally {
      loadingLocations = false;
    }
  }

  $: if (customerId) {
    loadLocations(customerId);
  }
</script>

<svelte:window on:keydown={handleKey} />

{#if customer}
  <div
    class="fixed inset-0 z-40 flex justify-end"
    transition:fade={{ duration: 180 }}
  >
    <button
      type="button"
      class="absolute inset-0 h-full w-full cursor-default border-0 p-0"
      style="background: var(--overlay-bg); backdrop-filter: blur(4px);"
      aria-label="Close customer details"
      on:click={editing ? cancelEditing : onClose}
    ></button>
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="drawer-title"
      class="relative flex h-full w-full max-w-md flex-col border-l border-edge bg-surface-200 shadow-card-hover"
      style="transform: translateX(0); transition: transform 280ms cubic-bezier(0.22, 1, 0.36, 1);"
    >
      <header class="flex items-start justify-between gap-3 border-b border-edge px-6 py-5">
        <div class="flex items-center gap-3 min-w-0">
          <span
            class="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl text-sm font-semibold text-white"
            style="background: linear-gradient(135deg, hsl({avatarHue(customer.id)}, 70%, 60%), hsl({(avatarHue(customer.id) + 40) % 360}, 70%, 50%));"
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
          on:click={editing ? cancelEditing : onClose}
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
            <div class="mt-2 flex flex-col gap-2">
              <StatusBadge status={customer.status} />
              {#if customer.releaseApproved}
                <span class="rounded-full border border-emerald-300/30 bg-emerald-300/10 px-2 py-1 text-2xs font-semibold uppercase tracking-wide text-emerald">Release approved</span>
              {/if}
            </div>
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
          <div class="flex items-center justify-between mb-3">
            <p class="section-title">Device · Plan</p>
            <button
              type="button"
              class="btn-ghost text-2xs !px-2 !py-1"
              on:click={editing ? cancelEditing : startEditing}
            >
              {editing ? 'Cancel' : 'Edit customer'}
            </button>
          </div>
          <div class="grid grid-cols-1 gap-3 text-sm">
            {#if editing}
              <div>
                <label class="text-2xs text-ink-muted block mb-1">Customer name</label>
                <input type="text" class="input w-full" bind:value={editName} />
              </div>
              <div>
                <label class="text-2xs text-ink-muted block mb-1">National ID</label>
                <input type="text" class="input w-full" bind:value={editNationalId} />
              </div>
              <div>
                <label class="text-2xs text-ink-muted block mb-1">Phone number</label>
                <input type="text" class="input w-full" bind:value={editPhone} />
              </div>
              <div class="grid grid-cols-3 gap-2">
                <div>
                  <label class="text-2xs text-ink-muted block mb-1">Daily rate (cents)</label>
                  <input type="number" class="input w-full" bind:value={editDailyRate} />
                </div>
                <div>
                  <label class="text-2xs text-ink-muted block mb-1">Total loan (cents)</label>
                  <input type="number" class="input w-full" bind:value={editTotalLoan} />
                </div>
                <div>
                  <label class="text-2xs text-ink-muted block mb-1">Term (days)</label>
                  <input type="number" class="input w-full" bind:value={editTermDays} />
                </div>
              </div>
              <div class="mt-2 flex gap-2">
                <button
                  type="button"
                  class="btn-emerald flex-1"
                  disabled={saving || !editName.trim()}
                  on:click={saveEditing}
                >
                  {saving ? 'Saving...' : 'Save changes'}
                </button>
                <button
                  type="button"
                  class="btn-outline"
                  on:click={cancelEditing}
                >
                  Cancel
                </button>
              </div>
            {:else}
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
            {/if}
          </div>
        </div>

        {#if customer.status === 'STOLEN' || locations.length > 0}
          <div class="card mt-4 p-5">
            <div class="flex items-center justify-between mb-3">
              <p class="section-title">Last Known Location</p>
              {#if loadingLocations}
                <span class="text-2xs text-ink-muted">Loading...</span>
              {:else if locations.length > 0}
                <span class="text-2xs text-emerald font-semibold uppercase tracking-wide">Active</span>
              {/if}
            </div>
            
            {#if locations.length > 0}
              {@const latest = locations[0]}
              <div class="grid grid-cols-1 gap-3 text-sm">
                <div class="flex items-center justify-between">
                  <span class="text-ink-secondary">Coordinates</span>
                  <span class="font-mono text-ink-primary font-medium">
                    {latest.latitude.toFixed(6)}, {latest.longitude.toFixed(6)}
                  </span>
                </div>
                {#if latest.accuracy !== null}
                  <div class="flex items-center justify-between">
                    <span class="text-ink-secondary">Accuracy</span>
                    <span class="text-ink-primary font-medium">±{latest.accuracy.toFixed(1)}m</span>
                  </div>
                {/if}
                {#if latest.batteryLevel !== null}
                  <div class="flex items-center justify-between">
                    <span class="text-ink-secondary">Battery Level</span>
                    <span class="text-ink-primary font-medium">{latest.batteryLevel}%</span>
                  </div>
                {/if}
                <div class="flex items-center justify-between">
                  <span class="text-ink-secondary">Updated At</span>
                  <span class="text-ink-primary font-medium">{formatDate(latest.timestamp * 1000)}</span>
                </div>
                
                <div class="mt-2">
                  <a
                    href="https://www.google.com/maps/search/?api=1&query={latest.latitude},{latest.longitude}"
                    target="_blank"
                    class="btn-primary w-full text-center flex items-center justify-center gap-2"
                  >
                    <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0118 0z" />
                      <circle cx="12" cy="10" r="3" />
                    </svg>
                    Open in Google Maps
                  </a>
                </div>
              </div>
            {:else if !loadingLocations}
              <p class="text-2xs text-ink-muted">No location logs available yet.</p>
            {/if}
          </div>
        {/if}

        {#if customer.customerPhotoPath || customer.nationalIdFrontPath || customer.nationalIdBackPath}
          <div class="card mt-4 p-5">
            <p class="section-title">KYC Photo Validation</p>
            <div class="mt-4 flex flex-col gap-4">
              {#if customer.customerPhotoPath}
                <div class="flex items-center gap-4">
                  <a href="/api/accounts/{customer.id}/photos/photo" target="_blank" class="block shrink-0 rounded-full border border-edge shadow-sm overflow-hidden cursor-zoom-in">
                    <img 
                      src="/api/accounts/{customer.id}/photos/photo" 
                      alt="Customer Selfie" 
                      class="h-16 w-16 object-cover"
                    />
                  </a>
                  <div>
                    <p class="text-sm font-semibold text-ink-primary">Customer Selfie</p>
                    <p class="text-2xs text-ink-muted">Captured live during agent registration</p>
                  </div>
                </div>
              {/if}
              
              <div class="grid grid-cols-2 gap-3 mt-2">
                {#if customer.nationalIdFrontPath}
                  <div class="flex flex-col gap-1.5">
                    <span class="text-2xs font-semibold text-ink-secondary">ID Document Front</span>
                    <a 
                      href="/api/accounts/{customer.id}/photos/id_front" 
                      target="_blank" 
                      class="block overflow-hidden rounded-xl border border-edge bg-surface-100 cursor-zoom-in hover:border-emerald-300/40 transition"
                    >
                      <img 
                        src="/api/accounts/{customer.id}/photos/id_front" 
                        alt="ID Card Front" 
                        class="h-24 w-full object-cover hover:scale-105 transition duration-200"
                      />
                    </a>
                  </div>
                {/if}
                
                {#if customer.nationalIdBackPath}
                  <div class="flex flex-col gap-1.5">
                    <span class="text-2xs font-semibold text-ink-secondary">ID Document Back</span>
                    <a 
                      href="/api/accounts/{customer.id}/photos/id_back" 
                      target="_blank" 
                      class="block overflow-hidden rounded-xl border border-edge bg-surface-100 cursor-zoom-in hover:border-emerald-300/40 transition"
                    >
                      <img 
                        src="/api/accounts/{customer.id}/photos/id_back" 
                        alt="ID Card Back" 
                        class="h-24 w-full object-cover hover:scale-105 transition duration-200"
                      />
                    </a>
                  </div>
                {/if}
              </div>
            </div>
          </div>
        {/if}
      </div>

      <footer class="flex flex-wrap items-center gap-2 border-t border-edge bg-surface-200/80 px-6 py-4 backdrop-blur">
        <button
          type="button"
          class="btn-emerald flex-1"
          disabled={isPending || editing}
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
          disabled={customer.status === 'LOCKED' || isPending || editing}
          on:click={lock}
        >
          <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M6 11V8a6 6 0 1112 0v3M5 11h14v10H5z" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          Force remote lock
        </button>
        <button
          type="button"
          class="btn-outline flex-1"
          disabled={customer.releaseApproved || isPending || editing}
          on:click={releaseAccount}
          title={customer.remainingBalance > 0 ? 'Manual early release for test/settlement only' : 'Approve final app removal'}
        >
          <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 12l2 2 4-4" stroke-linecap="round" stroke-linejoin="round" />
            <path d="M12 3l7 4v5c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V7l7-4z" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          Release customer app
        </button>
        <button
          type="button"
          class="btn-outline flex-1 text-crimson hover:bg-crimson/10"
          disabled={isPending || editing}
          on:click={removeCustomer}
        >
          Delete
        </button>
      </footer>
    </div>
  </div>
{/if}
