<script lang="ts">
  import { nodes } from '$lib/stores/nodes';
  import StatusBadge from '$lib/components/StatusBadge.svelte';
  import { formatKES } from '$lib/types';
</script>

<svelte:head>
  <title>Inventory · SecurePay Dealer</title>
</svelte:head>

<section class="px-4 py-6 md:px-8">
  <header class="mb-6">
    <h1 class="text-2xl font-semibold text-brand-text">IMEI Matrix</h1>
    <p class="mt-1 text-sm text-brand-textMuted">
      Every financed handset keyed by IMEI, with its current lock status.
    </p>
  </header>

  {#if $nodes.length === 0}
    <div class="m3-card p-10 text-center text-brand-textMuted">No devices in inventory.</div>
  {:else}
    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {#each $nodes as node (node.imei)}
        <article class="m3-card p-5">
          <div class="flex items-start justify-between gap-3">
            <div>
              <h2 class="text-sm font-semibold text-brand-text">{node.deviceModel}</h2>
              <p class="mt-1 font-mono text-xs text-brand-textMuted">IMEI {node.imei}</p>
            </div>
            <StatusBadge status={node.status} />
          </div>
          <dl class="mt-4 grid grid-cols-2 gap-3 text-sm">
            <div>
              <dt class="text-xs uppercase tracking-wider text-brand-textMuted">Customer</dt>
              <dd class="mt-0.5 text-brand-text">{node.customerName}</dd>
            </div>
            <div>
              <dt class="text-xs uppercase tracking-wider text-brand-textMuted">Account</dt>
              <dd class="mt-0.5 font-mono text-xs text-brand-text">{node.customerId}</dd>
            </div>
            <div>
              <dt class="text-xs uppercase tracking-wider text-brand-textMuted">Financed</dt>
              <dd class="mt-0.5 tabular-nums text-brand-text">{formatKES(node.totalLoanAmount)}</dd>
            </div>
            <div>
              <dt class="text-xs uppercase tracking-wider text-brand-textMuted">Remaining</dt>
              <dd class="mt-0.5 tabular-nums text-brand-text">{formatKES(node.remainingBalance)}</dd>
            </div>
          </dl>
        </article>
      {/each}
    </div>
  {/if}
</section>
