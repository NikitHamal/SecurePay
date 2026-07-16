<script lang="ts">
  import PageHeader from '$lib/components/ui/PageHeader.svelte';
  import TopBar from '$lib/components/layout/TopBar.svelte';
  import StatusBadge from '$lib/components/ui/StatusBadge.svelte';
  import Donut from '$lib/components/charts/Donut.svelte';
  import BarChart from '$lib/components/charts/BarChart.svelte';
  import { customers } from '$lib/stores/customers';
  import { portfolioMetrics } from '$lib/stores/portfolio';
  import { formatCurrency } from '$lib/utils/format';
  import { deleteDevice, getSecurityPolicy, listDevices, updateSecurityPolicy } from '$lib/api/client';
  import { onMount } from 'svelte';

  interface DeviceRow {
    id: string;
    imei: string;
    model: string;
    status: string;
    createdAt: number;
  }

  let view: 'cards' | 'table' = 'cards';
  let devices: DeviceRow[] = [];
  let devicesLoading = false;
  let inventoryError: string | null = null;
  let frpAccountIdsText = '';
  let securityStatus = 'Loading EFRP policy...';
  let securityError: string | null = null;
  let isSavingSecurity = false;

  $: m = $portfolioMetrics;
  $: inStockCount = devices.filter((device) => device.status === 'in_stock').length;

  function avatarHue(id: string): number {
    const digits = id.replace(/\D/g, '');
    const seed = Number.parseInt(digits || '19', 10);
    return (seed * 37) % 360;
  }

  onMount(async () => {
    await Promise.all([loadSecurityPolicy(), loadDevices()]);
  });

  async function loadSecurityPolicy() {
    try {
      const policy = await getSecurityPolicy();
      frpAccountIdsText = policy.frpAccountIds.join(String.fromCharCode(10));
      securityStatus = policy.frpEnabled
        ? `EFRP enabled with ${policy.frpAccountIds.length} admin account ID(s).`
        : 'EFRP is not configured yet. Add Google admin numeric user IDs before production provisioning.';
    } catch (error) {
      securityError = error instanceof Error ? error.message : 'Failed to load security policy';
    }
  }

  async function loadDevices() {
    devicesLoading = true;
    inventoryError = null;
    try {
      devices = await listDevices();
    } catch (error) {
      inventoryError = error instanceof Error ? error.message : 'Failed to load inventory';
    } finally {
      devicesLoading = false;
    }
  }

  async function removeDevice(device: DeviceRow) {
    if (device.status !== 'in_stock') {
      inventoryError = 'Delete the linked customer account first. Sold devices are protected from direct inventory deletion.';
      return;
    }
    const ok = window.confirm(`Delete inventory device ${device.imei}? This cannot be undone.`);
    if (!ok) return;
    devicesLoading = true;
    inventoryError = null;
    try {
      await deleteDevice(device.id);
      devices = devices.filter((row) => row.id !== device.id);
    } catch (error) {
      inventoryError = error instanceof Error ? error.message : 'Failed to delete device';
    } finally {
      devicesLoading = false;
    }
  }

  async function saveSecurityPolicy() {
    isSavingSecurity = true;
    securityError = null;
    try {
      const ids = frpAccountIdsText.split(/[\s,]+/).map((id) => id.trim()).filter(Boolean);
      const policy = await updateSecurityPolicy(ids);
      frpAccountIdsText = policy.frpAccountIds.join(String.fromCharCode(10));
      securityStatus = policy.frpEnabled
        ? `EFRP enabled with ${policy.frpAccountIds.length} admin account ID(s). Generate fresh QRs after this change.`
        : 'EFRP is not configured. Factory-reset recovery protection will not be added to new QRs.';
    } catch (error) {
      securityError = error instanceof Error ? error.message : 'Failed to save security policy';
    } finally {
      isSavingSecurity = false;
    }
  }
</script>

<svelte:head>
  <title>Inventory · SecurePay Dealer Console</title>
</svelte:head>

<div class="page">
  <TopBar searchPlaceholder="Search IMEI, model, customer…" />

  <PageHeader
    eyebrow="Hardware"
    title="IMEI Matrix"
    subtitle="Hardware inventory mapped to financed accounts and lock status."
  >
    <div slot="actions" class="flex items-center gap-1 rounded-lg border border-edge bg-surface-100 p-1">
      <button
        type="button"
        on:click={() => (view = 'cards')}
        class="rounded-md px-3 py-1 text-xs font-medium transition-colors
               {view === 'cards' ? 'bg-emerald-300/15 text-emerald' : 'text-ink-secondary hover:text-ink-primary'}"
      >
        Cards
      </button>
      <button
        type="button"
        on:click={() => (view = 'table')}
        class="rounded-md px-3 py-1 text-xs font-medium transition-colors
               {view === 'table' ? 'bg-emerald-300/15 text-emerald' : 'text-ink-secondary hover:text-ink-primary'}"
      >
        Table
      </button>
    </div>
  </PageHeader>

  <div class="grid grid-cols-1 gap-4 lg:grid-cols-3">
    <div class="card p-6 lg:col-span-3">
      <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div class="max-w-2xl">
          <p class="section-title">Production security policy</p>
          <p class="mt-1 text-sm text-ink-secondary">
            EFRP IDs are embedded into new Device Owner QRs and applied on the customer phone. Use numeric Google user IDs, not email addresses.
          </p>
          <p class="mt-2 text-xs {securityStatus.startsWith('EFRP enabled') ? 'text-emerald' : 'text-amber'}">{securityStatus}</p>
          {#if securityError}
            <p class="mt-2 text-xs text-crimson">{securityError}</p>
          {/if}
        </div>
        <div class="w-full lg:max-w-xl">
          <textarea
            bind:value={frpAccountIdsText}
            rows="3"
            class="w-full rounded-xl border border-edge bg-surface-100 px-3 py-2 font-mono text-xs text-ink-primary outline-none transition focus:border-emerald"
            placeholder="One Google numeric user ID per line"
          ></textarea>
          <div class="mt-2 flex justify-end">
            <button type="button" class="btn-primary" on:click={saveSecurityPolicy} disabled={isSavingSecurity}>
              {isSavingSecurity ? 'Saving...' : 'Save EFRP policy'}
            </button>
          </div>
        </div>
      </div>
    </div>

    <div class="card p-6">
      <p class="section-title">Model mix</p>
      <p class="mt-1 text-sm text-ink-secondary">{m.deviceDistribution.length} models deployed</p>
      <div class="mt-4">
        <Donut
          segments={m.deviceDistribution.map((d) => ({ label: d.name, value: d.value, color: d.color }))}
          size={170}
          stroke={20}
          gap={3}
          centerTitle={$customers.length.toString()}
          centerSubtitle="devices"
          legendValues={false}
          showLegend
        />
      </div>
    </div>

    <div class="card p-6 lg:col-span-2">
      <p class="section-title">Devices per model</p>
      <p class="mt-1 text-sm text-ink-secondary">Distribution across the fleet</p>
      <div class="mt-4">
        <BarChart
          values={m.deviceDistribution.map((d) => ({
            label: d.name.length > 10 ? d.name.split(' ')[0].slice(0, 8) : d.name,
            value: d.value,
            color: d.color
          }))}
          color="#38BDF8"
          height={220}
          xLabelRotation={-22}
        />
      </div>
    </div>
  </div>

  <div class="card mt-6 overflow-hidden">
    <div class="flex flex-col gap-3 border-b border-edge px-5 py-4 md:flex-row md:items-center md:justify-between">
      <div>
        <p class="section-title">Inventory records</p>
        <p class="mt-1 text-sm text-ink-secondary">{devices.length} total · {inStockCount} in stock. Delete sold devices by deleting their customer account first.</p>
      </div>
      <button type="button" class="btn-outline" on:click={loadDevices} disabled={devicesLoading}>
        {devicesLoading ? 'Refreshing…' : 'Refresh'}
      </button>
    </div>
    {#if inventoryError}
      <div class="mx-5 mt-4 rounded-lg border border-crimson-200/30 bg-crimson-200/10 px-3 py-2 text-sm text-crimson">
        {inventoryError}
      </div>
    {/if}
    <div class="overflow-x-auto">
      <table class="data-table min-w-[760px]">
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
            <tr><td colspan="5" class="py-10 text-center text-ink-muted">Loading inventory…</td></tr>
          {:else if devices.length === 0}
            <tr><td colspan="5" class="py-10 text-center text-ink-muted">No inventory devices yet.</td></tr>
          {:else}
            {#each devices as device (device.id)}
              <tr class="transition-colors hover:bg-hover">
                <td class="font-mono text-2xs text-ink-secondary">{device.imei}</td>
                <td class="text-ink-primary">{device.model}</td>
                <td><span class={device.status === 'in_stock' ? 'chip-emerald' : 'chip-amber'}>{device.status.replace('_', ' ')}</span></td>
                <td class="text-2xs text-ink-muted">{new Date(device.createdAt).toLocaleDateString()}</td>
                <td class="text-right">
                  <button
                    type="button"
                    class="btn-outline text-crimson hover:bg-crimson/10"
                    disabled={devicesLoading || device.status !== 'in_stock'}
                    title={device.status === 'in_stock' ? 'Delete inventory device' : 'Delete the linked customer account first'}
                    on:click={() => removeDevice(device)}
                  >
                    Delete
                  </button>
                </td>
              </tr>
            {/each}
          {/if}
        </tbody>
      </table>
    </div>
  </div>

  {#if view === 'cards'}
    <div class="mt-6 grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
      {#each $customers as customer (customer.id)}
        {@const ratio = customer.totalLoanAmount > 0 ? (customer.amountPaid / customer.totalLoanAmount) * 100 : 0}
        <article class="card card-hover p-4">
          <header class="flex items-start justify-between gap-2">
            <div class="flex items-center gap-3 min-w-0">
              <span
                class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-xs font-semibold text-white"
                style="background: linear-gradient(135deg, hsl({avatarHue(customer.id)}, 70%, 60%), hsl({(avatarHue(customer.id) + 40) % 360}, 70%, 50%));"
              >
                {customer.deviceModel.split(' ').map((p) => p[0]).join('').slice(0, 2)}
              </span>
              <div class="min-w-0">
                <p class="truncate text-sm font-semibold text-ink-primary">{customer.deviceModel}</p>
                <p class="truncate text-2xs text-ink-muted">{customer.customerName}</p>
              </div>
            </div>
            <StatusBadge status={customer.status} size="sm" />
          </header>

          <dl class="mt-3 grid grid-cols-2 gap-2 text-2xs">
            <div class="rounded-md bg-surface-100/40 px-2 py-1.5">
              <dt class="text-ink-muted">IMEI</dt>
              <dd class="font-mono text-ink-secondary truncate">{customer.imei}</dd>
            </div>
            <div class="rounded-md bg-surface-100/40 px-2 py-1.5">
              <dt class="text-ink-muted">Plan</dt>
              <dd class="text-ink-secondary truncate">{customer.planName}</dd>
            </div>
            <div class="rounded-md bg-surface-100/40 px-2 py-1.5">
              <dt class="text-ink-muted">Outstanding</dt>
              <dd class="font-medium text-ink-primary tabular-nums">{formatCurrency(customer.remainingBalance)}</dd>
            </div>
            <div class="rounded-md bg-surface-100/40 px-2 py-1.5">
              <dt class="text-ink-muted">Daily rate</dt>
              <dd class="font-medium text-ink-primary tabular-nums">{formatCurrency(customer.dailyRate)}</dd>
            </div>
          </dl>

          <div class="mt-3">
            <div class="mb-1 flex items-baseline justify-between text-2xs text-ink-secondary">
              <span>Loan progress</span>
              <span class="tabular-nums text-ink-primary">{ratio.toFixed(0)}%</span>
            </div>
            <div class="h-1.5 w-full overflow-hidden rounded-full" style="background: var(--progress-track);">
              <div
                class="h-full rounded-full"
                style="width: {Math.min(100, ratio)}%; background: linear-gradient(90deg, {ratio > 80 ? '#10B981' : ratio > 50 ? '#F59E0B' : '#EF4444'}, {ratio > 80 ? '#34D399' : ratio > 50 ? '#FBBF24' : '#F87171'});"
              ></div>
            </div>
          </div>
        </article>
      {/each}
    </div>
  {:else}
    <div class="card mt-6 overflow-hidden">
      <div class="overflow-x-auto">
        <table class="data-table min-w-[760px]">
          <thead>
            <tr>
              <th class="px-4 py-3 font-semibold">IMEI</th>
              <th class="px-4 py-3 font-semibold">Device Model</th>
              <th class="px-4 py-3 font-semibold">Assigned Customer</th>
              <th class="px-4 py-3 text-right font-semibold">Outstanding</th>
              <th class="px-4 py-3 font-semibold">Status</th>
            </tr>
          </thead>
          <tbody>
            {#each $customers as customer (customer.id)}
              <tr class="border-b border-edge/60 last:border-b-0 transition-colors hover:bg-hover">
                <td class="px-4 py-3 font-mono text-2xs text-ink-secondary">{customer.imei}</td>
                <td class="px-4 py-3 text-ink-primary">{customer.deviceModel}</td>
                <td class="px-4 py-3">
                  <div class="text-ink-primary">{customer.customerName}</div>
                  <div class="text-2xs text-ink-muted">{customer.id}</div>
                </td>
                <td class="px-4 py-3 text-right font-medium text-ink-primary tabular-nums">
                  {formatCurrency(customer.remainingBalance)}
                </td>
                <td class="px-4 py-3">
                  <StatusBadge status={customer.status} size="sm" />
                </td>
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
    </div>
  {/if}
</div>