<script lang="ts">
  import { nodes, kpis, nodesState, refresh } from '$lib/stores/nodes';
  import { formatKES } from '$lib/types';
  import KpiCard from '$lib/components/KpiCard.svelte';
  import LiveTable from '$lib/components/LiveTable.svelte';
</script>

<svelte:head>
  <title>Overview · SecurePay Dealer</title>
</svelte:head>

<section class="px-4 py-6 md:px-8">
  <header class="mb-6 flex flex-wrap items-center justify-between gap-4">
    <div>
      <h1 class="text-2xl font-semibold text-brand-text">Fleet Overview</h1>
      <p class="mt-1 text-sm text-brand-textMuted">
        Live device-financing status across your portfolio.
      </p>
    </div>
    <button
      type="button"
      class="rounded-lg border border-brand-border bg-brand-surface px-4 py-2 text-sm font-medium text-brand-text transition-colors hover:bg-brand-surfaceVariant disabled:opacity-50"
      disabled={$nodesState.loading}
      on:click={() => refresh()}
    >
      {$nodesState.loading ? 'Refreshing…' : 'Refresh'}
    </button>
  </header>

  {#if $nodesState.error}
    <div
      class="mb-6 rounded-xl border border-brand-crimson/40 bg-brand-crimson/10 px-4 py-3 text-sm text-brand-crimson"
    >
      {$nodesState.error}
    </div>
  {/if}

  <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
    <KpiCard
      label="Active Nodes"
      value={$kpis.activeNodes}
      accent="emerald"
      hint="Devices in good standing"
    />
    <KpiCard
      label="Warning Nodes"
      value={$kpis.warningNodes}
      accent="amber"
      hint="Due within 24 hours"
    />
    <KpiCard
      label="Locked Nodes"
      value={$kpis.lockedNodes}
      accent="crimson"
      hint="Currently restricted"
    />
    <KpiCard
      label="Defaults Tracked"
      value={$kpis.defaultCount}
      accent="crimson"
      hint="Locked with balance owed"
    />
  </div>

  <div class="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-3">
    <KpiCard label="Total Financed" value={formatKES($kpis.totalFinanced)} accent="neutral" />
    <KpiCard label="Total Collected" value={formatKES($kpis.totalCollected)} accent="emerald" />
    <KpiCard label="Total Outstanding" value={formatKES($kpis.totalOutstanding)} accent="amber" />
  </div>

  <div class="mt-8">
    <h2 class="mb-3 text-lg font-semibold text-brand-text">Live Device Table</h2>
    <LiveTable rows={$nodes} />
  </div>
</section>
