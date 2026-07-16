<script lang="ts">
  import { onMount } from 'svelte';
  import PageHeader from '$lib/components/ui/PageHeader.svelte';
  import TopBar from '$lib/components/layout/TopBar.svelte';
  import Donut from '$lib/components/charts/Donut.svelte';
  import BarChart from '$lib/components/charts/BarChart.svelte';
  import { listLedger } from '$lib/api/client';
  import type { LedgerEntry, PaymentMethod } from '$lib/types';
  import { formatCurrency, formatDateTime, formatRelative } from '$lib/utils/format';

  let entries: LedgerEntry[] = [];
  let loading = true;
  let loadError: string | null = null;
  let methodFilter: PaymentMethod | 'ALL' = 'ALL';

  const paymentMethods: PaymentMethod[] = ['MOBILE_MONEY', 'CARD', 'BANK', 'CASH'];

  const methodStyles: Record<PaymentMethod, { chip: string; color: string; label: string }> = {
    MOBILE_MONEY: { chip: 'chip-emerald', color: '#10B981', label: 'Mobile Money' },
    CARD:     { chip: 'chip-amber',   color: '#F59E0B', label: 'Card' },
    BANK:     { chip: 'chip-sky',     color: '#38BDF8', label: 'Bank' },
    CASH:     { chip: 'chip-violet',  color: '#A78BFA', label: 'Cash' }
  };

  $: total = entries.reduce((sum, entry) => sum + entry.amount, 0);
  $: filtered = methodFilter === 'ALL' ? entries : entries.filter((e) => e.method === methodFilter);
  $: methodBreakdown = paymentMethods.map((m) => ({
    label: methodStyles[m].label,
    value: entries.filter((e) => e.method === m).reduce((s, e) => s + e.amount, 0),
    color: methodStyles[m].color
  }));
  $: topEntry = entries[0];
  $: averageAmount = entries.length > 0 ? total / entries.length : 0;

  $: dailyTotals = (() => {
    const map = new Map<string, number>();
    for (const e of entries) {
      const d = new Date(e.dateEpochMillis);
      const key = d.toLocaleDateString('en-GH', { day: '2-digit', month: 'short' });
      map.set(key, (map.get(key) ?? 0) + e.amount);
    }
    return [...map.entries()].map(([label, value]) => ({ label, value }));
  })();

  onMount(async () => {
    try {
      entries = await listLedger();
    } catch (err) {
      loadError = err instanceof Error ? err.message : 'Failed to load ledger';
    } finally {
      loading = false;
    }
  });
</script>

<svelte:head>
  <title>Payment Ledger · SecurePay Dealer Console</title>
</svelte:head>

<div class="page">
  <TopBar searchPlaceholder="Search transactions…" />

  <PageHeader
    eyebrow="Finance"
    title="Payment Ledger"
    subtitle="Recorded installment collections across all financed accounts."
  >
    <div slot="actions" class="flex items-center gap-2">
      <button type="button" class="btn-outline">
        <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 5v14M5 12h14" stroke-linecap="round" />
        </svg>
        Export
      </button>
    </div>
  </PageHeader>

  {#if loadError}
    <div class="mb-4 rounded-xl border border-crimson-200/30 bg-crimson-200/10 px-4 py-3 text-sm text-crimson">
      {loadError}
    </div>
  {/if}

  <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
    <div class="card p-5">
      <p class="section-title">Total collected</p>
      <p class="mt-2 text-3xl font-semibold text-emerald tabular-nums">{formatCurrency(total)}</p>
      <p class="mt-1 text-xs text-ink-secondary">Across {entries.length} transactions</p>
    </div>
    <div class="card p-5">
      <p class="section-title">Average ticket</p>
      <p class="mt-2 text-3xl font-semibold text-sky tabular-nums">{formatCurrency(averageAmount)}</p>
      <p class="mt-1 text-xs text-ink-secondary">Per installment</p>
    </div>
    <div class="card p-5">
      <p class="section-title">Last payment</p>
      {#if topEntry}
        <p class="mt-2 text-2xl font-semibold text-ink-primary tabular-nums">{formatCurrency(topEntry.amount)}</p>
        <p class="mt-1 text-xs text-ink-secondary">
          {topEntry.customerName} · {formatRelative(topEntry.dateEpochMillis)}
        </p>
      {:else}
        <p class="mt-2 text-2xl font-semibold text-ink-muted">—</p>
      {/if}
    </div>
  </div>

  <div class="mt-6 grid grid-cols-1 gap-4 lg:grid-cols-3">
    <div class="card p-6">
      <p class="section-title">Method mix</p>
      <p class="mt-1 text-sm text-ink-secondary">By value</p>
      <div class="mt-4">
        <Donut
          segments={methodBreakdown.map((m) => ({ label: m.label, value: m.value, color: m.color }))}
          size={170}
          stroke={20}
          gap={3}
          centerTitle={formatCurrency(total)}
          centerSubtitle="total"
          legendValues
        />
      </div>
    </div>

    <div class="card p-6 lg:col-span-2">
      <p class="section-title">Daily collection totals</p>
      <p class="mt-1 text-sm text-ink-secondary">Sum of all transactions per day</p>
      <div class="mt-4">
        {#if loading}
          <div class="skeleton h-[220px] w-full rounded-lg"></div>
        {:else if dailyTotals.length > 0}
          <BarChart
            values={dailyTotals.map((d) => ({
              label: d.label,
              value: d.value,
              color: '#10B981'
            }))}
            color="#10B981"
            height={220}
            yTicks={3}
            xLabelRotation={-25}
            formatY={(n) => n >= 1000 ? `${(n / 1000).toFixed(0)}k` : n.toString()}
            showValues
          />
        {:else}
          <div class="flex h-[220px] items-center justify-center text-sm text-ink-muted">
            No collection data yet.
          </div>
        {/if}
      </div>
    </div>
  </div>

  <div class="card mt-6 flex flex-wrap items-center gap-3 p-4">
    <div class="flex flex-wrap items-center gap-2">
      <button
        type="button"
        on:click={() => (methodFilter = 'ALL')}
        class="rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors
               {methodFilter === 'ALL'
          ? 'border-emerald-300/30 bg-emerald-300/10 text-emerald'
          : 'border-edge bg-surface-100/40 text-ink-secondary hover:text-ink-primary hover:bg-hover'}"
      >
        All methods
        <span class="ml-1.5 rounded-md bg-surface-100 px-1.5 py-0.5 text-2xs text-ink-muted tabular-nums">{entries.length}</span>
      </button>
      {#each paymentMethods as m (m)}
        {@const count = entries.filter((e) => e.method === m).length}
        <button
          type="button"
          on:click={() => (methodFilter = m)}
          class="rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors
                 {methodFilter === m
            ? 'border-emerald-300/30 bg-emerald-300/10 text-emerald'
            : 'border-edge bg-surface-100/40 text-ink-secondary hover:text-ink-primary hover:bg-hover'}"
        >
          <span class="inline-flex items-center gap-1.5">
            <span class="h-1.5 w-1.5 rounded-full" style="background: {methodStyles[m].color};"></span>
            {methodStyles[m].label}
          </span>
          <span class="ml-1.5 rounded-md bg-surface-100 px-1.5 py-0.5 text-2xs text-ink-muted tabular-nums">{count}</span>
        </button>
      {/each}
    </div>

    <div class="ml-auto text-sm">
      <span class="text-ink-secondary">Total collected: </span>
      <span class="font-semibold text-emerald tabular-nums">{formatCurrency(filtered.reduce((s, e) => s + e.amount, 0))}</span>
    </div>
  </div>

  <div class="card mt-4 overflow-hidden">
    <div class="overflow-x-auto">
      <table class="data-table min-w-[760px]">
        <thead>
          <tr>
            <th class="px-4 py-3 font-semibold">Date</th>
            <th class="px-4 py-3 font-semibold">Customer</th>
            <th class="px-4 py-3 font-semibold">Reference</th>
            <th class="px-4 py-3 font-semibold">Method</th>
            <th class="px-4 py-3 text-right font-semibold">Amount</th>
          </tr>
        </thead>
        <tbody>
          {#each filtered as entry (entry.id)}
            <tr class="border-b border-edge/60 last:border-b-0 transition-colors hover:bg-hover">
              <td class="px-4 py-3">
                <div class="text-ink-secondary">{formatDateTime(entry.dateEpochMillis)}</div>
                <div class="text-2xs text-ink-muted">{formatRelative(entry.dateEpochMillis)}</div>
              </td>
              <td class="px-4 py-3">
                <div class="text-ink-primary">{entry.customerName}</div>
                <div class="font-mono text-2xs text-ink-muted">{entry.imei}</div>
              </td>
              <td class="px-4 py-3 font-mono text-2xs text-ink-muted">{entry.reference}</td>
              <td class="px-4 py-3">
                <span class={methodStyles[entry.method].chip}>
                  <span class="h-1.5 w-1.5 rounded-full" style="background: {methodStyles[entry.method].color};"></span>
                  {methodStyles[entry.method].label}
                </span>
              </td>
              <td class="px-4 py-3 text-right">
                <div class="font-semibold text-emerald tabular-nums">{formatCurrency(entry.amount)}</div>
              </td>
            </tr>
          {/each}

          {#if !loading && filtered.length === 0}
            <tr>
              <td colspan="5" class="px-4 py-14 text-center">
                <div class="mx-auto flex max-w-xs flex-col items-center gap-2">
                  <span class="flex h-10 w-10 items-center justify-center rounded-full bg-surface-100 text-ink-muted">
                    <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                      <path d="M4 6h16M4 12h16M4 18h10" stroke-linecap="round" />
                    </svg>
                  </span>
                  <p class="text-sm text-ink-secondary">No transactions match this filter.</p>
                </div>
              </td>
            </tr>
          {/if}

          {#if loading}
            <tr>
              <td colspan="5" class="px-4 py-14 text-center text-ink-secondary"> Loading ledger… </td>
            </tr>
          {/if}
        </tbody>
      </table>
    </div>
  </div>
</div>