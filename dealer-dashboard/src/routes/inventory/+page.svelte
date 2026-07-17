<script lang="ts">
  import PageHeader from '$lib/components/ui/PageHeader.svelte';
  import TopBar from '$lib/components/layout/TopBar.svelte';
  import StatusBadge from '$lib/components/ui/StatusBadge.svelte';
  import Donut from '$lib/components/charts/Donut.svelte';
  import { customers } from '$lib/stores/customers';
  import { portfolioMetrics } from '$lib/stores/portfolio';
  import { formatCurrency } from '$lib/utils/format';
  import { deleteDevice, getSecurityPolicy, listDevices, updateSecurityPolicy } from '$lib/api/client';
  import { openAddDevice, openNewLoan, openProvision } from '$lib/stores/ui';
  import { onMount } from 'svelte';

  interface DeviceRow {
    id: string;
    imei: string;
    model: string;
    status: string;
    createdAt: number;
    customerName: string | null;
  }

  let view: 'cards' | 'table' = 'table';
  let devices: DeviceRow[] = [];
  let devicesLoading = false;
  let inventoryError: string | null = null;
  let frpAccountIdsText = '';
  let securityStatus = 'Loading EFRP policy...';
  let securityError: string | null = null;
  let isSavingSecurity = false;

  $: m = $portfolioMetrics;
  $: inStockCount = devices.filter(d => d.status === 'in_stock').length;

  function initials(name: string) {
    return (name || '?').split(' ').map(p => p[0]).join('').slice(0, 2).toUpperCase();
  }
  function progressColor(ratio: number) {
    if (ratio > 80) return '#10B981';
    if (ratio > 50) return '#F59E0B';
    return '#DC2626';
  }

  onMount(() => { Promise.all([loadSecurityPolicy(), loadDevices()]); });

  async function loadSecurityPolicy() {
    try {
      const policy = await getSecurityPolicy();
      frpAccountIdsText = policy.frpAccountIds.join('\n');
      securityStatus = policy.frpEnabled
        ? `EFRP enabled with ${policy.frpAccountIds.length} admin ID(s).`
        : 'EFRP not configured. Add Google admin numeric IDs before production provisioning.';
    } catch (e) {
      securityError = e instanceof Error ? e.message : 'Failed to load security policy';
    }
  }

  async function loadDevices() {
    devicesLoading = true;
    inventoryError = null;
    try { devices = await listDevices(); }
    catch (e) { inventoryError = e instanceof Error ? e.message : 'Failed to load inventory'; }
    finally { devicesLoading = false; }
  }

  async function removeDevice(device: DeviceRow) {
    if (device.status !== 'in_stock') {
      inventoryError = 'Delete the linked customer account first. Sold devices cannot be removed directly.';
      return;
    }
    if (!confirm(`Delete ${device.imei}? This cannot be undone.`)) return;
    devicesLoading = true;
    inventoryError = null;
    try {
      await deleteDevice(device.id);
      devices = devices.filter(r => r.id !== device.id);
    } catch (e) {
      inventoryError = e instanceof Error ? e.message : 'Failed to delete device';
    } finally {
      devicesLoading = false;
    }
  }

  async function saveSecurityPolicy() {
    isSavingSecurity = true;
    securityError = null;
    try {
      const ids = frpAccountIdsText.split(/[\s,]+/).map(id => id.trim()).filter(Boolean);
      const policy = await updateSecurityPolicy(ids);
      frpAccountIdsText = policy.frpAccountIds.join('\n');
      securityStatus = policy.frpEnabled
        ? `EFRP enabled with ${policy.frpAccountIds.length} admin ID(s). Generate fresh QRs after this change.`
        : 'EFRP is not configured.';
    } catch (e) {
      securityError = e instanceof Error ? e.message : 'Failed to save security policy';
    } finally { isSavingSecurity = false; }
  }
</script>

<svelte:head><title>Inventory · Touch Base</title></svelte:head>

<div class="page">
  <TopBar searchPlaceholder="Search IMEI, model, customer…" />

  <PageHeader
    eyebrow="Hardware"
    title="Inventory"
    subtitle="Manage IMEIs, stock, and Device Owner provisioning."
  >
    <svelte:fragment slot="actions">
      <div class="flex items-center gap-2">
        <div class="flex items-center gap-1 rounded-lg border border-edge bg-surface-100 p-1">
          <button on:click={() => view = 'table'} class="rounded-md px-3 py-1 text-xs font-medium transition {view === 'table' ? 'bg-surface-200 text-ink-primary shadow-sm' : 'text-ink-secondary'}">Table</button>
          <button on:click={() => view = 'cards'} class="rounded-md px-3 py-1 text-xs font-medium transition {view === 'cards' ? 'bg-surface-200 text-ink-primary shadow-sm' : 'text-ink-secondary'}">Cards</button>
        </div>
        <button class="btn-outline" on:click={() => openProvision()}>
          <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><path d="M14 14h3v3M21 14v3M14 21h3M17 17h4v4"/></svg>
          Provision
        </button>
        <button class="btn-primary" on:click={() => openAddDevice()}>
          <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M12 5v14M5 12h14" stroke-linecap="round"/></svg>
          Add device
        </button>
      </div>
    </svelte:fragment>
  </PageHeader>

  <!-- Stock summary -->
  <div class="mb-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
    <div class="card p-4">
      <p class="text-xs text-ink-muted">Total IMEIs</p>
      <p class="mt-1 text-2xl font-semibold tabular-nums text-ink-primary">{devices.length}</p>
    </div>
    <div class="card p-4">
      <p class="text-xs text-ink-muted">In stock</p>
      <p class="mt-1 text-2xl font-semibold tabular-nums text-emerald">{inStockCount}</p>
    </div>
    <div class="card p-4">
      <p class="text-xs text-ink-muted">Assigned</p>
      <p class="mt-1 text-2xl font-semibold tabular-nums text-ink-primary">{devices.length - inStockCount}</p>
    </div>
    <div class="card p-4">
      <p class="text-xs text-ink-muted">Active loans</p>
      <p class="mt-1 text-2xl font-semibold tabular-nums text-ink-primary">{$customers.length}</p>
    </div>
  </div>

  <!-- Security policy -->
  <div class="card mb-5 p-5">
    <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
      <div class="max-w-xl">
        <p class="section-title">Production security policy</p>
        <p class="mt-1 text-sm text-ink-secondary">
          EFRP Google admin numeric IDs are embedded in Device Owner QRs. Use numeric IDs only (not emails).
        </p>
        <p class="mt-2 text-xs {securityStatus.startsWith('EFRP enabled') ? 'text-emerald' : 'text-amber'}">{securityStatus}</p>
        {#if securityError}<p class="mt-1 text-xs text-crimson">{securityError}</p>{/if}
      </div>
      <div class="w-full lg:max-w-md">
        <textarea
          bind:value={frpAccountIdsText} rows="3"
          class="input font-mono text-xs"
          placeholder="One Google numeric user ID per line"
        ></textarea>
        <div class="mt-2 flex justify-end">
          <button class="btn-primary" on:click={saveSecurityPolicy} disabled={isSavingSecurity}>
            {isSavingSecurity ? 'Saving…' : 'Save policy'}
          </button>
        </div>
      </div>
    </div>
  </div>

  <!-- Inventory table/cards -->
  {#if inventoryError}
    <div class="mb-4 rounded-lg border border-crimson/20 bg-crimson/10 px-4 py-3 text-sm text-crimson">{inventoryError}</div>
  {/if}

  <div class="card overflow-hidden">
    <div class="flex items-center justify-between border-b border-edge px-5 py-3">
      <div>
        <p class="text-sm font-semibold text-ink-primary">IMEI inventory</p>
        <p class="text-xs text-ink-muted">{devices.length} records · {inStockCount} in stock</p>
      </div>
      <button class="btn-outline !py-1.5 text-xs" on:click={loadDevices} disabled={devicesLoading}>
        {devicesLoading ? 'Refreshing…' : 'Refresh'}
      </button>
    </div>

    <div class="overflow-x-auto">
      <table class="data-table min-w-[720px]">
        <thead>
          <tr>
            <th>IMEI</th>
            <th>Model</th>
            <th>Status</th>
            <th>Added</th>
            <th class="text-right">Actions</th>
          </tr>
        </thead>
        <tbody>
          {#if devicesLoading && devices.length === 0}
            <tr><td colspan="5" class="py-10 text-center text-ink-muted">Loading…</td></tr>
          {:else if devices.length === 0}
            <tr><td colspan="5" class="py-12 text-center">
              <p class="text-sm text-ink-primary">No devices yet</p>
              <p class="mt-1 text-xs text-ink-muted">Add IMEIs to your inventory to begin selling.</p>
              <button class="btn-primary mt-3" on:click={() => openAddDevice()}>+ Add device</button>
            </td></tr>
          {:else}
            {#each devices as device (device.id)}
              <tr class="hover:bg-hover transition-colors">
                <td class="font-mono text-xs text-ink-secondary">{device.imei}</td>
                <td class="text-sm text-ink-primary">{device.model}</td>
                <td><span class={device.status === 'in_stock' ? 'chip-emerald' : 'chip-amber'}>{device.status.replace('_', ' ')}</span></td>
                <td class="text-xs text-ink-muted">{new Date(device.createdAt).toLocaleDateString()}</td>
                <td class="text-right">
                  <div class="flex justify-end gap-2">
                    {#if device.status === 'in_stock'}
                      <button class="btn-primary !py-1 !px-2.5 text-xs" on:click={() => openNewLoan({ imei: device.imei, deviceModel: device.model })}>Enroll</button>
                      <button class="btn-outline !py-1 !px-2.5 text-xs" on:click={() => openProvision(device.imei)}>Provision</button>
                      <button class="btn-outline !py-1 !px-2.5 text-xs text-crimson hover:bg-crimson/10" disabled={devicesLoading} on:click={() => removeDevice(device)}>Delete</button>
                    {:else}
                      <span class="text-xs text-ink-muted">Assigned to {device.customerName || 'customer'}</span>
                    {/if}
                  </div>
                </td>
              </tr>
            {/each}
          {/if}
        </tbody>
      </table>
    </div>
  </div>

  {#if view === 'cards'}
    <div class="mt-5 grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
      {#each devices as device (device.id)}
        <article class="card card-hover p-4">
          <header class="flex items-start justify-between gap-2">
            <div class="min-w-0">
              <p class="truncate text-sm font-semibold text-ink-primary">{device.model}</p>
              <p class="font-mono text-xs text-ink-muted">{device.imei}</p>
            </div>
            <span class={device.status === 'in_stock' ? 'chip-emerald' : 'chip-amber'}>{device.status.replace('_', ' ')}</span>
          </header>
          <p class="mt-3 text-xs text-ink-muted">Added {new Date(device.createdAt).toLocaleDateString()}</p>
          {#if device.status === 'in_stock'}
            <div class="mt-3 flex gap-2">
              <button class="btn-primary flex-1 !py-1.5 text-xs" on:click={() => openNewLoan({ imei: device.imei, deviceModel: device.model })}>Enroll</button>
              <button class="btn-outline !py-1.5 text-xs" on:click={() => openProvision(device.imei)}>Provision</button>
            </div>
          {:else if device.customerName}
            <p class="mt-3 text-xs text-ink-secondary">Assigned to <span class="font-medium text-ink-primary">{device.customerName}</span></p>
          {/if}
        </article>
      {/each}
    </div>
  {/if}
</div>
