<script lang="ts">
  import { onMount } from 'svelte';
  import PageHeader from '$lib/components/PageHeader.svelte';
  import { listLedger } from '$lib/api/mockApi';
  import type { LedgerEntry, PaymentMethod } from '$lib/types';
  import { formatCurrency, formatDateTime } from '$lib/utils/format';

  let entries: LedgerEntry[] = [];
  let loading = true;
  let loadError: string | null = null;

  const methodStyles: Record<PaymentMethod, string> = {
    'M-PESA': 'bg-emerald/15 text-emerald',
    CARD: 'bg-amber/15 text-amber',
    BANK: 'bg-white/10 text-text-primary',
    CASH: 'bg-white/10 text-text-secondary'
  };

  $: total = entries.reduce((sum, entry) => sum + entry.amount, 0);

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

<section class="p-6">
  <PageHeader
    title="Payment Ledger"
    subtitle="Recorded installment collections across all financed accounts"
  />

  {#if loadError}
    <div class="mb-4 rounded-xl border border-crimson/30 bg-crimson/10 px-4 py-3 text-sm text-crimson">
      {loadError}
    </div>
  {/if}

  <div class="card overflow-hidden">
    <div class="flex items-center justify-between border-b border-white/5 px-4 py-3">
      <span class="text-sm text-text-secondary">
        {entries.length} transaction{entries.length === 1 ? '' : 's'}
      </span>
      <span class="text-sm font-medium text-text-primary">
        Total collected: {formatCurrency(total)}
      </span>
    </div>

    <div class="overflow-x-auto">
      <table class="w-full min-w-[760px] border-collapse text-left text-sm">
        <thead>
          <tr class="border-b border-white/5 text-xs uppercase tracking-wide text-text-secondary">
            <th class="px-4 py-3 font-medium">Date</th>
            <th class="px-4 py-3 font-medium">Customer</th>
            <th class="px-4 py-3 font-medium">Reference</th>
            <th class="px-4 py-3 font-medium">Method</th>
            <th class="px-4 py-3 text-right font-medium">Amount</th>
          </tr>
        </thead>
        <tbody>
          {#each entries as entry (entry.id)}
            <tr class="border-b border-white/5 transition-colors last:border-b-0 hover:bg-white/[0.03]">
              <td class="px-4 py-3 text-text-secondary">{formatDateTime(entry.dateEpochMillis)}</td>
              <td class="px-4 py-3">
                <div class="text-text-primary">{entry.customerName}</div>
                <div class="font-mono text-xs text-text-secondary">{entry.imei}</div>
              </td>
              <td class="px-4 py-3 font-mono text-xs text-text-secondary">{entry.reference}</td>
              <td class="px-4 py-3">
                <span class="rounded-full px-2.5 py-1 text-xs font-medium {methodStyles[entry.method]}">
                  {entry.method}
                </span>
              </td>
              <td class="px-4 py-3 text-right font-medium text-emerald">
                {formatCurrency(entry.amount)}
              </td>
            </tr>
          {/each}

          {#if !loading && entries.length === 0}
            <tr>
              <td colspan="5" class="px-4 py-10 text-center text-text-secondary">
                No payment transactions recorded.
              </td>
            </tr>
          {/if}

          {#if loading}
            <tr>
              <td colspan="5" class="px-4 py-10 text-center text-text-secondary"> Loading ledger… </td>
            </tr>
          {/if}
        </tbody>
      </table>
    </div>
  </div>
</section>
