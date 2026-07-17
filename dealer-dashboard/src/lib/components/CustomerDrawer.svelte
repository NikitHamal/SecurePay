<script lang="ts">
  import type { Customer } from '$lib/types';
  import { approveRelease, customers, deleteCustomer, extendTimer, forceRemoteLock, pending, updateCustomer } from '$lib/stores/customers';
  import { formatCountdown, formatCurrency, formatDate, formatPhone, formatRelative } from '$lib/utils/format';
  import StatusBadge from '$lib/components/ui/StatusBadge.svelte';
  import ProgressRing from '$lib/components/charts/ProgressRing.svelte';
  import Map from '$lib/components/ui/Map.svelte';
  import { fade } from 'svelte/transition';
  import { onDestroy } from 'svelte';
  import { getAccountLocations } from '$lib/api/client';
  import { openProvision } from '$lib/stores/ui';
  import PayWithMoMoModal from '$lib/components/PayWithMoMoModal.svelte';

  export let customerId: string | null = null;
  export let onClose: () => void = () => {};

  let editing = false;
  let payMoMoOpen = false;
  let momoRefreshTick = 0; // bump to force reload after payment
  let saving = false;
  let editName = '';
  let editNationalId = '';
  let editPhone = '';
  let editDailyRate = '';
  let editTotalLoan = '';
  let editTermDays = '';

  // KYC photo uploads
  let photoFile: File | null = null;
  let idFrontFile: File | null = null;
  let idBackFile: File | null = null;
  let photoPreview: string | null = null;
  let idFrontPreview: string | null = null;
  let idBackPreview: string | null = null;
  let photoUploading = false;

  function fileToBase64(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = () => reject(reader.error);
      reader.readAsDataURL(file);
    });
  }

  async function pickPhoto(field: 'photo' | 'id_front' | 'id_back', e: Event) {
    const input = e.target as HTMLInputElement;
    const f = input.files?.[0];
    if (!f) return;
    if (f.size > 5 * 1024 * 1024) {
      alert('Image must be smaller than 5MB');
      input.value = '';
      return;
    }
    const dataUrl = await fileToBase64(f);
    if (field === 'photo') { photoFile = f; photoPreview = dataUrl; }
    if (field === 'id_front') { idFrontFile = f; idFrontPreview = dataUrl; }
    if (field === 'id_back') { idBackFile = f; idBackPreview = dataUrl; }
  }

  function clearPhoto(field: 'photo' | 'id_front' | 'id_back') {
    if (field === 'photo') { photoFile = null; photoPreview = null; }
    if (field === 'id_front') { idFrontFile = null; idFrontPreview = null; }
    if (field === 'id_back') { idBackFile = null; idBackPreview = null; }
  }

  async function uploadPhotos() {
    if (!customer) return;
    if (!photoFile && !idFrontFile && !idBackFile) return;
    photoUploading = true;
    try {
      const patch: Record<string, string | null> = {};
      if (photoFile) patch.customerPhoto = await fileToBase64(photoFile);
      if (idFrontFile) patch.nationalIdFront = await fileToBase64(idFrontFile);
      if (idBackFile) patch.nationalIdBack = await fileToBase64(idBackFile);
      await updateCustomer(customer.id, patch);
      photoFile = idFrontFile = idBackFile = null;
      photoPreview = idFrontPreview = idBackPreview = null;
    } catch (e) {
      alert(e instanceof Error ? e.message : 'Failed to upload photos');
    } finally {
      photoUploading = false;
    }
  }

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
    editDailyRate = String(customer.dailyRate / 100);
    editTotalLoan = String(customer.totalLoanAmount / 100);
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
      const patch: Record<string, any> = {
        customerName: editName.trim(),
        nationalId: editNationalId.trim(),
        phoneNumber: editPhone.trim(),
        dailyRate: Math.round(Number(editDailyRate) * 100),
        totalLoanAmount: Math.round(Number(editTotalLoan) * 100),
        termDays: Number(editTermDays),
      };
      if (photoFile) patch.customerPhoto = await fileToBase64(photoFile);
      if (idFrontFile) patch.nationalIdFront = await fileToBase64(idFrontFile);
      if (idBackFile) patch.nationalIdBack = await fileToBase64(idBackFile);
      await updateCustomer(customer.id, patch);
      photoFile = idFrontFile = idBackFile = null;
      photoPreview = idFrontPreview = idBackPreview = null;
      editing = false;
    } catch (e) {
      alert(e instanceof Error ? e.message : 'Failed to save');
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

  async function toggleStolen(flag: boolean): Promise<void> {
    if (!customer) return;
    const action = flag ? 'Flag' : 'Recover';
    const ok = window.confirm(`${action} device for ${customer.customerName}?`);
    if (!ok) return;
    try {
      await updateCustomer(customer.id, { isStolen: flag });
    } catch (e) {
      console.error(e);
    }
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
  let loadedLocationCustomerId: string | null = null;
  let locationRefreshTimer: ReturnType<typeof setInterval> | null = null;

  async function loadLocations(id: string) {
    loadingLocations = true;
    try {
      const latest = await getAccountLocations(id);
      if (customerId === id) {
        locations = latest;
      }
    } catch (e) {
      console.error('Failed to load locations', e);
      if (customerId === id) {
        locations = [];
      }
    } finally {
      if (customerId === id) {
        loadingLocations = false;
      }
    }
  }

  function stopLocationRefresh() {
    if (locationRefreshTimer) {
      clearInterval(locationRefreshTimer);
      locationRefreshTimer = null;
    }
  }

  function startLocationRefresh(id: string) {
    stopLocationRefresh();
    loadLocations(id);
    locationRefreshTimer = setInterval(() => {
      loadLocations(id);
    }, 15_000);
  }

  $: if (customerId !== loadedLocationCustomerId) {
    loadedLocationCustomerId = customerId;
    locations = [];
    if (customerId) {
      startLocationRefresh(customerId);
    } else {
      stopLocationRefresh();
    }
  }

  $: if (customer && customer.isStolen && customer.id === loadedLocationCustomerId && !locationRefreshTimer) {
    startLocationRefresh(customer.id);
  }

  onDestroy(stopLocationRefresh);
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
          {#if customer.customerPhotoPath}
            <img
              src="/api/accounts/{customer.id}/photos/photo"
              alt={customer.customerName}
              class="h-12 w-12 shrink-0 rounded-lg object-cover border border-edge"
            />
          {:else}
            <span
              class="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg text-sm font-semibold text-white"
              style="background-color: var(--brand);"
            >
              {customer.customerName.split(' ').map((p) => p[0]).join('').slice(0, 2)}
            </span>
          {/if}
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
              <div class="flex items-center gap-2">
                <StatusBadge status={customer.status} />
                {#if customer.status === 'STOLEN'}
                  <button
                    type="button"
                    class="rounded-lg border border-emerald-300/30 bg-emerald-300/10 px-2 py-1 text-2xs font-semibold uppercase tracking-wide text-emerald hover:bg-emerald-300/20 transition-colors"
                    disabled={isPending || editing}
                    on:click={() => toggleStolen(false)}
                  >
                    Recovered
                  </button>
                {:else}
                  <button
                    type="button"
                    class="rounded-lg border border-crimson-300/30 bg-crimson-300/10 px-2 py-1 text-2xs font-semibold uppercase tracking-wide text-crimson hover:bg-crimson-300/20 transition-colors"
                    disabled={isPending || editing}
                    on:click={() => toggleStolen(true)}
                  >
                    Flag Stolen
                  </button>
                {/if}
              </div>
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
              <div>
                <p class="text-2xs uppercase tracking-wide text-ink-muted">Down payment</p>
                <p class="font-semibold text-ink-primary tabular-nums">{formatCurrency(customer.downPayment)}</p>
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
                <label class="text-2xs text-ink-muted block mb-1" for="edit-customer-name">Customer name</label>
                <input id="edit-customer-name" type="text" class="input w-full" bind:value={editName} />
              </div>
              <div>
                <label class="text-2xs text-ink-muted block mb-1" for="edit-national-id">National ID</label>
                <input id="edit-national-id" type="text" class="input w-full" bind:value={editNationalId} />
              </div>
              <div>
                <label class="text-2xs text-ink-muted block mb-1" for="edit-phone">Phone number</label>
                <input id="edit-phone" type="text" class="input w-full" bind:value={editPhone} />
              </div>
              <div class="grid grid-cols-3 gap-2">
                <div>
                  <label class="text-2xs text-ink-muted block mb-1" for="edit-daily-rate">Daily rate (GHS)</label>
                  <input id="edit-daily-rate" type="number" class="input w-full" bind:value={editDailyRate} />
                </div>
                <div>
                  <label class="text-2xs text-ink-muted block mb-1" for="edit-total-loan">Total loan (GHS)</label>
                  <input id="edit-total-loan" type="number" class="input w-full" bind:value={editTotalLoan} />
                </div>
                <div>
                  <label class="text-2xs text-ink-muted block mb-1" for="edit-term-days">Term (days)</label>
                  <input id="edit-term-days" type="number" class="input w-full" bind:value={editTermDays} />
                </div>
              </div>

              <div class="mt-3 border-t border-edge pt-3">
                <p class="section-title mb-2">KYC Photos</p>
                <div class="grid grid-cols-3 gap-2">
                  <label class="relative flex flex-col items-center justify-center gap-1 rounded-lg border {photoPreview || customer.customerPhotoPath ? 'border-edge' : 'border-dashed border-edge bg-surface-100'} p-2 text-center cursor-pointer hover:border-brand/60 hover:bg-brand-soft/40 transition overflow-hidden aspect-square">
                    {#if photoPreview}
                      <img src={photoPreview} alt="" class="absolute inset-0 h-full w-full object-cover" />
                    {:else if customer.customerPhotoPath}
                      <img src={`/api/accounts/${customer.id}/photos/photo`} alt="" class="absolute inset-0 h-full w-full object-cover" />
                    {:else}
                      <svg class="h-5 w-5 text-ink-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                        <path d="M4 7h3l2-2h6l2 2h3a2 2 0 012 2v9a2 2 0 01-2 2H4a2 2 0 01-2-2V9a2 2 0 012-2z"/><circle cx="12" cy="13" r="3.5"/>
                      </svg>
                    {/if}
                    <span class="relative z-10 text-2xs font-medium text-ink-primary px-1.5 py-0.5 rounded bg-surface-200/80 backdrop-blur mt-auto">Selfie</span>
                    <input type="file" accept="image/*" class="hidden" on:change={(e)=>pickPhoto('photo',e)} />
                    {#if photoPreview}<button type="button" class="absolute top-1 right-1 z-10 flex h-5 w-5 items-center justify-center rounded-full bg-crimson text-white text-xs" on:click|stopPropagation={()=>clearPhoto('photo')}>×</button>{/if}
                  </label>
                  <label class="relative flex flex-col items-center justify-center gap-1 rounded-lg border {idFrontPreview || customer.nationalIdFrontPath ? 'border-edge' : 'border-dashed border-edge bg-surface-100'} p-2 text-center cursor-pointer hover:border-brand/60 hover:bg-brand-soft/40 transition overflow-hidden aspect-square">
                    {#if idFrontPreview}
                      <img src={idFrontPreview} alt="" class="absolute inset-0 h-full w-full object-cover" />
                    {:else if customer.nationalIdFrontPath}
                      <img src={`/api/accounts/${customer.id}/photos/id_front`} alt="" class="absolute inset-0 h-full w-full object-cover" />
                    {:else}
                      <svg class="h-5 w-5 text-ink-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                        <path d="M4 7h3l2-2h6l2 2h3a2 2 0 012 2v9a2 2 0 01-2 2H4a2 2 0 01-2-2V9a2 2 0 012-2z"/><circle cx="12" cy="13" r="3.5"/>
                      </svg>
                    {/if}
                    <span class="relative z-10 text-2xs font-medium text-ink-primary px-1.5 py-0.5 rounded bg-surface-200/80 backdrop-blur mt-auto">ID Front</span>
                    <input type="file" accept="image/*" class="hidden" on:change={(e)=>pickPhoto('id_front',e)} />
                    {#if idFrontPreview}<button type="button" class="absolute top-1 right-1 z-10 flex h-5 w-5 items-center justify-center rounded-full bg-crimson text-white text-xs" on:click|stopPropagation={()=>clearPhoto('id_front')}>×</button>{/if}
                  </label>
                  <label class="relative flex flex-col items-center justify-center gap-1 rounded-lg border {idBackPreview || customer.nationalIdBackPath ? 'border-edge' : 'border-dashed border-edge bg-surface-100'} p-2 text-center cursor-pointer hover:border-brand/60 hover:bg-brand-soft/40 transition overflow-hidden aspect-square">
                    {#if idBackPreview}
                      <img src={idBackPreview} alt="" class="absolute inset-0 h-full w-full object-cover" />
                    {:else if customer.nationalIdBackPath}
                      <img src={`/api/accounts/${customer.id}/photos/id_back`} alt="" class="absolute inset-0 h-full w-full object-cover" />
                    {:else}
                      <svg class="h-5 w-5 text-ink-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                        <path d="M4 7h3l2-2h6l2 2h3a2 2 0 012 2v9a2 2 0 01-2 2H4a2 2 0 01-2-2V9a2 2 0 012-2z"/><circle cx="12" cy="13" r="3.5"/>
                      </svg>
                    {/if}
                    <span class="relative z-10 text-2xs font-medium text-ink-primary px-1.5 py-0.5 rounded bg-surface-200/80 backdrop-blur mt-auto">ID Back</span>
                    <input type="file" accept="image/*" class="hidden" on:change={(e)=>pickPhoto('id_back',e)} />
                    {#if idBackPreview}<button type="button" class="absolute top-1 right-1 z-10 flex h-5 w-5 items-center justify-center rounded-full bg-crimson text-white text-xs" on:click|stopPropagation={()=>clearPhoto('id_back')}>×</button>{/if}
                  </label>
                </div>
                <p class="mt-1 text-2xs text-ink-muted">Upload JPG/PNG up to 5MB. Saved to encrypted R2 storage.</p>
              </div>

              <div class="mt-2 flex gap-2">
                <button
                  type="button"
                  class="btn-primary flex-1"
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
              <p class="section-title">Live Location Tracking</p>
              {#if loadingLocations}
                <span class="text-2xs text-ink-muted">Updating...</span>
              {:else if locations.length > 0}
                <span class="text-2xs text-emerald font-semibold uppercase tracking-wide">Live</span>
              {/if}
            </div>
            
            {#if locations.length > 0}
              {@const latest = locations[0]}
              <div class="grid grid-cols-1 gap-4">
                <div class="h-64 w-full overflow-hidden rounded-xl border border-edge bg-surface-100">
                  <Map lat={latest.latitude} lng={latest.longitude} />
                </div>
                
                <div class="grid grid-cols-2 gap-3 text-sm">
                  <div class="flex items-center justify-between p-2 rounded-lg bg-surface-100">
                    <span class="text-ink-secondary">Accuracy</span>
                    <span class="font-medium text-ink-primary">±{latest.accuracy?.toFixed(1) || 'N/A'}m</span>
                  </div>
                  <div class="flex items-center justify-between p-2 rounded-lg bg-surface-100">
                    <span class="text-ink-secondary">Battery</span>
                    <span class="font-medium text-ink-primary">{latest.batteryLevel || 'N/A'}%</span>
                  </div>
                </div>
                <div class="flex items-center justify-between text-xs p-2 rounded-lg bg-surface-100">
                  <span class="text-ink-secondary">Last updated</span>
                  <span class="text-ink-primary font-medium">{formatDate(latest.timestamp * 1000)}</span>
                </div>
                
                <a
                  href="https://www.google.com/maps/search/?api=1&query={latest.latitude},{latest.longitude}"
                  target="_blank"
                  class="btn-primary w-full text-center flex items-center justify-center gap-2"
                >
                  <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0118 0z" />
                    <circle cx="12" cy="10" r="3" />
                  </svg>
                  Navigate to Device
                </a>
              </div>
            {:else if !loadingLocations}
              <div class="flex flex-col items-center justify-center py-8 text-center">
                <svg class="h-12 w-12 text-ink-muted mb-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                  <path d="M12 2v20M2 12h20" stroke-linecap="round" />
                  <circle cx="12" cy="12" r="9" />
                </svg>
                <p class="text-sm text-ink-secondary">No location data reported yet.</p>
                <p class="text-xs text-ink-muted mt-1">Device must be flagged as stolen to begin tracking.</p>
              </div>
            {/if}
          </div>
        {/if}

        <div class="card mt-4 p-5">
          <div class="flex items-center justify-between mb-3">
            <p class="section-title !mb-0">KYC Photos</p>
            <label class="btn-ghost !py-1 !px-2 text-2xs cursor-pointer">
              <input type="file" accept="image/*" multiple class="hidden" on:change={async (e) => {
                const files = (e.target as HTMLInputElement).files;
                if (!files || !customer) return;
                const patch: Record<string,string> = {};
                for (let i = 0; i < Math.min(files.length, 3); i++) {
                  const b64 = await fileToBase64(files[i]);
                  if (i === 0) patch.customerPhoto = b64;
                  else if (i === 1) patch.nationalIdFront = b64;
                  else patch.nationalIdBack = b64;
                }
                photoUploading = true;
                try { await updateCustomer(customer.id, patch); }
                catch (err) { alert(err instanceof Error ? err.message : 'Upload failed'); }
                finally { photoUploading = false; (e.target as HTMLInputElement).value=''; }
              }} />
              {photoUploading ? 'Uploading…' : '+ Upload photos'}
            </label>
          </div>
          <div class="grid grid-cols-3 gap-2">
            {#each [
              { label: 'Selfie', src: customer.customerPhotoPath ? `/api/accounts/${customer.id}/photos/photo` : null },
              { label: 'ID Front', src: customer.nationalIdFrontPath ? `/api/accounts/${customer.id}/photos/id_front` : null },
              { label: 'ID Back', src: customer.nationalIdBackPath ? `/api/accounts/${customer.id}/photos/id_back` : null }
            ] as item}
              <a
                href={item.src || '#'}
                target={item.src ? '_blank' : undefined}
                class="relative flex flex-col items-center justify-center rounded-lg overflow-hidden aspect-square {item.src ? 'border border-edge cursor-zoom-in' : 'border border-dashed border-edge bg-surface-100'}"
              >
                {#if item.src}
                  <img src={item.src} alt={item.label} class="absolute inset-0 h-full w-full object-cover" />
                {:else}
                  <svg class="h-5 w-5 text-ink-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                    <path d="M4 7h3l2-2h6l2 2h3a2 2 0 012 2v9a2 2 0 01-2 2H4a2 2 0 01-2-2V9a2 2 0 012-2z"/><circle cx="12" cy="13" r="3.5"/>
                  </svg>
                {/if}
                <span class="relative z-10 text-2xs font-medium text-ink-primary px-1.5 py-0.5 rounded bg-surface-200/80 backdrop-blur mt-auto">{item.label}</span>
              </a>
            {/each}
          </div>
        </div>

      </div>

      <footer class="flex flex-wrap items-center gap-2 border-t border-edge bg-surface-200/80 px-6 py-4 backdrop-blur">
        <button
          type="button"
          class="btn-primary flex-1"
          disabled={isPending || editing}
          on:click={() => { payMoMoOpen = true; }}
        >
          <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="2" y="6" width="20" height="12" rx="2"/><path d="M2 10h20"/><path d="M6 15h4"/>
          </svg>
          Pay with MoMo
        </button>
        <button
          type="button"
          class="btn-outline flex-1"
          disabled={isPending || editing}
          on:click={() => { openProvision(customer.imei); onClose(); }}
        >
          <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/>
            <rect x="3" y="14" width="7" height="7" rx="1"/><path d="M14 14h3v3M21 14v3M14 21h3M17 17h4v4"/>
          </svg>
          Provision
        </button>
        <button
          type="button"
          class="btn-emerald flex-1"
          disabled={isPending || editing}
          on:click={extend}
        >
          <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 8v8M8 12h8" stroke-linecap="round" />
          </svg>
          Extend +24h
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
          Lock
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

<PayWithMoMoModal
  open={payMoMoOpen && customer !== null}
  accountId={customer?.id || ''}
  customerName={customer?.customerName || ''}
  customerPhone={customer?.phoneNumber || ''}
  remainingBalance={customer?.remainingBalance || 0}
  on:close={() => { payMoMoOpen = false; }}
  on:paid={() => { momoRefreshTick++; }}
/>
