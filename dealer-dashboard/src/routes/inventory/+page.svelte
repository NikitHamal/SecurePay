<script lang="ts">
  import PageHeader from '$lib/components/ui/PageHeader.svelte';
  import TopBar from '$lib/components/layout/TopBar.svelte';
  import StatusBadge from '$lib/components/ui/StatusBadge.svelte';
  import Donut from '$lib/components/charts/Donut.svelte';
  import { customers } from '$lib/stores/customers';
  import { portfolioMetrics } from '$lib/stores/portfolio';
  import { formatCurrency } from '$lib/utils/format';
  import { deleteDevice, getSecurityPolicy, listDevices, updateSecurityPolicy, type InventoryDevice, listProductModels, createProductModel, updateProductModel, deleteProductModel, assignDevice, type ProductModel } from '$lib/api/client';
  import { openAddDevice, openNewLoan, openProvision } from '$lib/stores/ui';
  import { dealer } from '$lib/stores/auth';
  import { onMount } from 'svelte';

  type DeviceRow = InventoryDevice;

  let view: 'cards' | 'table' = 'table';

  // Agents can register devices but can never delete them (admin-only).
  $: canDeleteDevices = $dealer ? $dealer.role !== 'AGENT' : false;
  $: isAdmin = $dealer ? $dealer.role !== 'AGENT' : false;
  $: isAgentView = $dealer?.role === 'AGENT';

  function regLocationUrl(device: DeviceRow): string | null {
    return device.registrationLat != null && device.registrationLng != null
      ? `https://www.google.com/maps?q=${device.registrationLat},${device.registrationLng}`
      : null;
  }
  let devices: DeviceRow[] = [];
  let devicesLoading = false;
  let inventoryError: string | null = null;
  let frpAccountIdsText = '';
  let securityStatus = 'Loading EFRP policy...';
  let securityError: string | null = null;
  let isSavingSecurity = false;

  // Product catalog (admin-owned pricing)
  let productModels: ProductModel[] = [];
  let catalogLoading = false;
  let catalogError: string | null = null;
  let newProd = { name: '', model: '', totalGhs: '', downGhs: '', dailyGhs: '', term: '' };
  let creatingProd = false;

  // Edit product modal state
  let editProdModal = false;
  let editProd = { id: '', name: '', model: '', totalGhs: '', downGhs: '', dailyGhs: '', term: '', isActive: true };
  let editingProd = false;

  // Assignment
  let agents: { id: string; name: string }[] = [];
  let agentsLoading = false;
  let assignBusyId: string | null = null;

  $: m = $portfolioMetrics;
  $: inStockCount = devices.filter(d => d.status === 'in_stock').length;
  $: assignedCount = devices.filter(d => d.assignedTo).length;

  function initials(name: string) {
    return (name || '?').split(' ').map(p => p[0]).join('').slice(0, 2).toUpperCase();
  }
  function progressColor(ratio: number) {
    if (ratio > 80) return '#10B981';
    if (ratio > 50) return '#F59E0B';
    return '#DC2626';
  }

  onMount(() => { Promise.all([loadSecurityPolicy(), loadDevices(), loadProductModels(), loadAgentsIfAdmin()]); });

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

  async function loadProductModels() {
    if (!isAdmin) return;
    catalogLoading = true;
    catalogError = null;
    try { productModels = await listProductModels(); }
    catch (e) { catalogError = e instanceof Error ? e.message : 'Failed to load catalog'; }
    finally { catalogLoading = false; }
  }
  async function loadAgentsIfAdmin() {
    if (!isAdmin) return;
    agentsLoading = true;
    try {
      const res = await fetch('/api/agents', { headers: {} });
      if (res.ok) agents = await res.json();
      else agents = [];
    } catch { agents = []; }
    finally { agentsLoading = false; }
  }
  async function handleCreateProduct() {
    catalogError = null;
    if (!newProd.name.trim() || !newProd.model.trim()) { catalogError = 'Name and model required'; return; }
    const total = Math.round(parseFloat(newProd.totalGhs || '0')*100);
    const down = Math.round(parseFloat(newProd.downGhs || '0')*100);
    const daily = Math.round(parseFloat(newProd.dailyGhs || '0')*100);
    const term = parseInt(newProd.term || '0',10);
    if (!total || !daily || !term) { catalogError = 'Total, daily and term are required'; return; }
    if (down > total) { catalogError = 'Down payment cannot exceed total'; return; }
    creatingProd = true;
    try {
      const pm = await createProductModel({ name: newProd.name.trim(), model: newProd.model.trim(), totalAmount: total, downPayment: down, dailyRate: daily, termDays: term });
      productModels = [pm, ...productModels];
      newProd = { name: '', model: '', totalGhs: '', downGhs: '', dailyGhs: '', term: '' };
    } catch (e) { catalogError = e instanceof Error ? e.message : 'Create failed'; }
    finally { creatingProd = false; }
  }

  function openEditProduct(pm: ProductModel) {
    editProd = {
      id: pm.id,
      name: pm.name,
      model: pm.model,
      totalGhs: (pm.totalAmount / 100).toFixed(2),
      downGhs: (pm.downPayment / 100).toFixed(2),
      dailyGhs: (pm.dailyRate / 100).toFixed(2),
      term: String(pm.termDays),
      isActive: pm.isActive
    };
    catalogError = null;
    editProdModal = true;
  }

  async function handleSaveEditProduct() {
    catalogError = null;
    if (!editProd.name.trim() || !editProd.model.trim()) { catalogError = 'Name and model required'; return; }
    const total = Math.round(parseFloat(editProd.totalGhs || '0') * 100);
    const down = Math.round(parseFloat(editProd.downGhs || '0') * 100);
    const daily = Math.round(parseFloat(editProd.dailyGhs || '0') * 100);
    const term = parseInt(editProd.term || '0', 10);
    if (!total || !daily || !term) { catalogError = 'Total, daily and term are required'; return; }
    if (down > total) { catalogError = 'Down payment cannot exceed total'; return; }
    editingProd = true;
    try {
      const updated = await updateProductModel(editProd.id, {
        name: editProd.name.trim(),
        model: editProd.model.trim(),
        totalAmount: total,
        downPayment: down,
        dailyRate: daily,
        termDays: term,
        isActive: editProd.isActive
      });
      productModels = productModels.map(p => p.id === updated.id ? updated : p);
      editProdModal = false;
    } catch (e) {
      catalogError = e instanceof Error ? e.message : 'Update failed';
    } finally {
      editingProd = false;
    }
  }

  async function handleDeleteProduct(pm: ProductModel) {
    if (!confirm(`Delete product "${pm.name}" (${pm.model})? This cannot be undone.`)) return;
    catalogError = null;
    try {
      await deleteProductModel(pm.id);
      productModels = productModels.filter(p => p.id !== pm.id);
    } catch (e) {
      catalogError = e instanceof Error ? e.message : 'Delete failed';
    }
  }
  async function handleAssign(deviceId: string, agentId: string) {
    if (!agentId) return;
    assignBusyId = deviceId;
    inventoryError = null;
    try {
      await assignDevice(deviceId, agentId);
      await loadDevices();
    } catch (e) { inventoryError = e instanceof Error ? e.message : 'Assign failed'; }
    finally { assignBusyId = null; }
  }
  async function handleUnassign(deviceId: string) {
    assignBusyId = deviceId;
    inventoryError = null;
    try { await assignDevice(deviceId, null); await loadDevices(); }
    catch (e) { inventoryError = e instanceof Error ? e.message : 'Unassign failed'; }
    finally { assignBusyId = null; }
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
        {#if isAdmin}
        <button class="btn-primary" on:click={() => openAddDevice()}>
          <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M12 5v14M5 12h14" stroke-linecap="round"/></svg>
          Add device
        </button>
        {:else}
        <span class="text-xs text-ink-muted hidden sm:inline">Your assigned devices only</span>
        {/if}
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
      <p class="text-xs text-ink-muted">Assigned to agents</p>
      <p class="mt-1 text-2xl font-semibold tabular-nums text-ink-primary">{assignedCount}</p>
    </div>
    <div class="card p-4">
      <p class="text-xs text-ink-muted">Active loans</p>
      <p class="mt-1 text-2xl font-semibold tabular-nums text-ink-primary">{$customers.length}</p>
    </div>
  </div>

  {#if isAgentView}
    <div class="mb-5 rounded-lg border border-sky/20 bg-sky/10 px-4 py-3 text-sm text-sky">You see only the {devices.length} phones assigned to you. Ask your admin to assign more IMEIs if you need stock.</div>
  {/if}

  <!-- Product catalog (admin controls pricing) -->
  {#if isAdmin}
  <div class="card mb-5 p-5">
    <div class="flex items-center justify-between">
      <div>
        <p class="section-title">Product catalog · Admin-set pricing</p>
        <p class="text-xs text-ink-muted">Create a phone model once with its fixed price plan. Every IMEI linked to it inherits the locked terms — agents cannot change them.</p>
      </div>
      <button class="btn-outline !py-1.5 text-xs" on:click={loadProductModels} disabled={catalogLoading}>{catalogLoading ? 'Loading…' : 'Refresh'}</button>
    </div>
    {#if catalogError}<p class="mt-2 text-xs text-crimson">{catalogError}</p>{/if}
    <div class="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-5 items-end">
      <div><label class="label">Catalog name</label><input class="input !py-1.5 text-xs" bind:value={newProd.name} placeholder="Samsung A07" /></div>
      <div><label class="label">Model</label><input class="input !py-1.5 text-xs" bind:value={newProd.model} placeholder="A07 4/64" /></div>
      <div><label class="label">Total GH₵</label><input class="input !py-1.5 text-xs" type="number" bind:value={newProd.totalGhs} placeholder="1500" /></div>
      <div><label class="label">Down GH₵</label><input class="input !py-1.5 text-xs" type="number" bind:value={newProd.downGhs} placeholder="300" /></div>
      <div><label class="label">Daily / Term</label><div class="flex gap-1"><input class="input !py-1.5 text-xs flex-1" type="number" bind:value={newProd.dailyGhs} placeholder="10" /><input class="input !py-1.5 text-xs w-16" type="number" bind:value={newProd.term} placeholder="120" /></div></div>
    </div>
    <div class="mt-2 flex justify-end"><button class="btn-primary !py-1.5 text-xs" on:click={handleCreateProduct} disabled={creatingProd}>{creatingProd ? 'Creating…' : 'Create product'}</button></div>
    {#if productModels.length > 0}
    <div class="mt-4 overflow-x-auto">
      <table class="data-table min-w-[560px]">
        <thead><tr><th>Name</th><th>Model</th><th>Total</th><th>Down</th><th>Daily</th><th>Term</th><th class="text-right">Actions</th></tr></thead>
        <tbody>
          {#each productModels as pm (pm.id)}
            <tr>
              <td class="text-xs font-medium">{pm.name}</td>
              <td class="text-xs">{pm.model}</td>
              <td class="text-xs tabular-nums">{formatCurrency(pm.totalAmount)}</td>
              <td class="text-xs tabular-nums">{formatCurrency(pm.downPayment)}</td>
              <td class="text-xs tabular-nums">{formatCurrency(pm.dailyRate)}</td>
              <td class="text-xs">{pm.termDays}d</td>
              <td class="text-right">
                <div class="flex items-center justify-end gap-1">
                  <button class="btn-outline !py-1 !px-2.5 text-xs" on:click={() => openEditProduct(pm)}>Edit</button>
                  <button class="btn-outline !py-1 !px-2.5 text-xs text-crimson hover:bg-crimson/10" on:click={() => handleDeleteProduct(pm)}>Delete</button>
                </div>
              </td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
    {:else if !catalogLoading}<p class="mt-3 text-xs text-ink-muted">No products yet — create Samsung A07 as the first.</p>{/if}
  </div>
  {/if}

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
      <table class="data-table min-w-[920px]">
        <thead>
          <tr>
            <th>IMEI</th>
            <th>Model</th>
            <th>Pricing</th>
            <th>Assigned to</th>
            <th>Status</th>
            <th>Added</th>
            <th class="text-right">Actions</th>
          </tr>
        </thead>
        <tbody>
          {#if devicesLoading && devices.length === 0}
            <tr><td colspan="7" class="py-10 text-center text-ink-muted">Loading…</td></tr>
          {:else if devices.length === 0}
            <tr><td colspan="7" class="py-12 text-center">
              <p class="text-sm text-ink-primary">No devices yet</p>
              <p class="mt-1 text-xs text-ink-muted">Add IMEIs to your inventory to begin selling.</p>
              <button class="btn-primary mt-3" on:click={() => openAddDevice()}>+ Add device</button>
            </td></tr>
          {:else}
            {#each devices as device (device.id)}
              <tr class="hover:bg-hover transition-colors">
                <td class="font-mono text-xs text-ink-secondary">{device.imei}</td>
                <td class="text-sm text-ink-primary">{device.model}{#if device.productName}<span class="block text-[10px] text-ink-muted">{device.productName}</span>{/if}</td>
                <td class="text-xs">
                  {#if device.totalAmount != null}
                    <span class="font-semibold tabular-nums text-ink-primary">{formatCurrency(device.totalAmount)}</span>
                    <span class="block text-[10px] leading-tight text-ink-muted">Down {device.downPayment != null ? formatCurrency(device.downPayment) : '—'} · {device.dailyRate != null ? formatCurrency(device.dailyRate) : '—'}/d · {device.termDays ?? '—'}d</span>
                  {:else}
                    <span class="text-amber font-medium">No pricing</span>
                    {#if isAdmin}<span class="block text-[10px] text-ink-muted">Set via Add device → product</span>{/if}
                  {/if}
                </td>
                <td class="text-xs">
                  {#if device.assignedToName}
                    <span class="font-medium text-ink-primary">{device.assignedToName}</span>
                  {:else if device.assignedTo}
                    <span class="font-mono text-[11px]">{device.assignedTo.slice(0,8)}…</span>
                  {:else}
                    <span class="text-ink-muted">Unassigned</span>
                  {/if}
                  {#if isAdmin && device.status === 'in_stock'}
                    <div class="mt-1 flex items-center gap-1">
                      <select class="input !py-0.5 !px-1 text-[11px] max-w-[110px]" value={device.assignedTo ?? ''} on:change={(e) => handleAssign(device.id, (e.target as HTMLSelectElement).value)} disabled={assignBusyId === device.id}>
                        <option value="">{agentsLoading ? 'Loading…' : 'Assign to…'}</option>
                        {#each agents as ag}<option value={ag.id}>{ag.name}</option>{/each}
                      </select>
                      {#if device.assignedTo}<button class="text-[11px] text-crimson hover:underline" disabled={assignBusyId===device.id} on:click={() => handleUnassign(device.id)}>×</button>{/if}
                    </div>
                  {/if}
                </td>
                <td><span class={device.status === 'in_stock' ? 'chip-emerald' : 'chip-amber'}>{device.status.replace('_', ' ')}</span></td>
                <td class="text-xs text-ink-muted">
                  {new Date(device.createdAt).toLocaleDateString()}
                  {#if device.registeredByName}
                    <p class="mt-0.5 text-[11px] text-ink-muted">by {device.registeredByName}</p>
                  {/if}
                  {#if regLocationUrl(device)}
                    <a href={regLocationUrl(device)} target="_blank" rel="noreferrer" class="mt-0.5 inline-flex items-center gap-0.5 text-[11px] text-sky hover:underline">
                      <svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 21s-7-5.1-7-11a7 7 0 1114 0c0 5.9-7 11-7 11z"/><circle cx="12" cy="10" r="2.6"/></svg>
                      GPS
                    </a>
                  {/if}
                </td>
                <td class="text-right">
                  <div class="flex justify-end gap-2">
                    {#if device.status === 'in_stock'}
                      <button class="btn-primary !py-1 !px-2.5 text-xs" on:click={() => openNewLoan({ imei: device.imei, deviceModel: device.model })}>Enroll</button>
                      <button class="btn-outline !py-1 !px-2.5 text-xs" on:click={() => openProvision(device.imei)}>Provision</button>
                      {#if canDeleteDevices}
                        <button class="btn-outline !py-1 !px-2.5 text-xs text-crimson hover:bg-crimson/10" disabled={devicesLoading} on:click={() => removeDevice(device)}>Delete</button>
                      {/if}
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
          <p class="mt-3 text-xs text-ink-muted">
            Added {new Date(device.createdAt).toLocaleDateString()}{#if device.registeredByName} by {device.registeredByName}{/if}
            {#if regLocationUrl(device)}
              · <a href={regLocationUrl(device)} target="_blank" rel="noreferrer" class="text-sky hover:underline">GPS</a>
            {/if}
          </p>
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

  {#if editProdModal}
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm">
    <div class="card w-full max-w-lg p-6 shadow-2xl">
      <div class="flex items-center justify-between pb-3 border-b border-edge">
        <h3 class="text-base font-semibold text-ink-primary">Edit Product Model</h3>
        <button class="text-ink-muted hover:text-ink-primary" on:click={() => editProdModal = false}>
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg>
        </button>
      </div>

      {#if catalogError}
        <div class="mt-3 rounded-lg border border-crimson/20 bg-crimson/10 px-3 py-2 text-xs text-crimson">{catalogError}</div>
      {/if}

      <div class="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div class="sm:col-span-2">
          <label class="label">Catalog Name</label>
          <input class="input" bind:value={editProd.name} placeholder="e.g. Samsung Galaxy A07" />
        </div>
        <div class="sm:col-span-2">
          <label class="label">Phone Model</label>
          <input class="input" bind:value={editProd.model} placeholder="e.g. A07 4/64" />
        </div>
        <div>
          <label class="label">Total Price (GH₵)</label>
          <input class="input" type="number" step="0.01" bind:value={editProd.totalGhs} placeholder="1500" />
        </div>
        <div>
          <label class="label">Down Payment (GH₵)</label>
          <input class="input" type="number" step="0.01" bind:value={editProd.downGhs} placeholder="300" />
        </div>
        <div>
          <label class="label">Daily Rate (GH₵)</label>
          <input class="input" type="number" step="0.01" bind:value={editProd.dailyGhs} placeholder="10" />
        </div>
        <div>
          <label class="label">Term (Days)</label>
          <input class="input" type="number" bind:value={editProd.term} placeholder="120" />
        </div>
      </div>

      <div class="mt-6 flex justify-end gap-2">
        <button class="btn-outline text-xs" on:click={() => editProdModal = false} disabled={editingProd}>Cancel</button>
        <button class="btn-primary text-xs" on:click={handleSaveEditProduct} disabled={editingProd}>
          {editingProd ? 'Saving…' : 'Save Changes'}
        </button>
      </div>
    </div>
  </div>
  {/if}
</div>
