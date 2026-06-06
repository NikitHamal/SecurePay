<script lang="ts">
  import { nodes, kpis } from '$lib/stores/nodes';
  import { formatKES } from '$lib/types';
  import StatusBadge from '$lib/components/StatusBadge.svelte';
</script>

<svelte:head>
  <title>Payment Ledger · SecurePay Dealer</title>
</svelte:head>

<section class="px-4 py-6 md:px-8">
  <header class="mb-6">
    <h1 class="text-2xl font-semibold text-brand-text">Payment Ledger</h1>
    <p class="mt-1 text-sm text-brand-textMuted">
      Collections and outstanding balances across the financed fleet.
    </p>
  </header>

  <div class="m3-card overflow-hidden">
    <div class="overflow-x-auto">
      <table class="w-full min-w-[760px] border-collapse text-left text-sm">
        <thead>
          <tr
            class="border-b border-brand-border text-xs uppercase tracking-wider text-brand-textMuted"
          >
            <th class="px-5 py-3 font-medium">Customer</th>
            <th class="px-5 py-3 font-medium">Device</th>
            <th class="px-5 py-3 text-right font-medium">Financed</th>
            <th class="px-5 py-3 text-right font-medium">Paid</th>
            <th class="px-5 py-3 text-right font-medium">Outstanding</th>
            <th class="px-5 py-3 text-right font-medium">Daily Installment</th>
            <th class="px-5 py-3 font-medium">Status</th>
          </tr>
        </thead>
        <tbody>
          {#if $nodes.length === 0}
            <tr>
              <td colspan="7" class="px-5 py-10 text-center text-brand-textMuted">
                No ledger entries to display.
              </td>
            </tr>
          {:else}
            {#each $nodes as node (node.customerId)}
              <tr
                class="border-b border-brand-border/60 transition-colors hover:bg-brand-surfaceVariant/40"
              >
                <td class="px-5 py-3.5">
                  <div class="font-medium text-brand-text">{node.customerName}</div>
                  <div class="text-xs text-brand-textMuted">{node.customerId}</div>
                </td>
                <td class="px-5 py-3.5 text-brand-text">{node.deviceModel}</td>
                <td class="px-5 py-3.5 text-right tabular-nums text-brand-text">
                  {formatKES(node.totalLoanAmount)}
                </td>
                <td class="px-5 py-3.5 text-right tabular-nums text-brand-emerald">
                  {formatKES(node.amountPaid)}
                </td>
                <td class="px-5 py-3.5 text-right tabular-nums text-brand-amber">
                  {formatKES(node.remainingBalance)}
                </td>
                <td class="px-5 py-3.5 text-right tabular-nums text-brand-text">
                  {formatKES(node.dailyInstallment)}
                </td>
                <td class="px-5 py-3.5">
                  <StatusBadge status={node.status} />
                </td>
              </tr>
            {/each}
          {/if}
        </tbody>
        {#if $nodes.length > 0}
          <tfoot>
            <tr class="border-t border-brand-border bg-brand-surfaceVariant/40 font-semibold">
              <td class="px-5 py-3.5 text-brand-text" colspan="2">Totals</td>
              <td class="px-5 py-3.5 text-right tabular-nums text-brand-text">
                {formatKES($kpis.totalFinanced)}
              </td>
              <td class="px-5 py-3.5 text-right tabular-nums text-brand-emerald">
                {formatKES($kpis.totalCollected)}
              </td>
              <td class="px-5 py-3.5 text-right tabular-nums text-brand-amber">
                {formatKES($kpis.totalOutstanding)}
              </td>
              <td class="px-5 py-3.5"></td>
              <td class="px-5 py-3.5"></td>
            </tr>
          </tfoot>
        {/if}
      </table>
    </div>
  </div>
</section>
