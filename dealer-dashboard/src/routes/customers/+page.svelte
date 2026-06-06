<script lang="ts">
  import { nodes } from '$lib/stores/nodes';
  import LiveTable from '$lib/components/LiveTable.svelte';
</script>

<svelte:head>
  <title>Customers · SecurePay Dealer</title>
</svelte:head>

<section class="px-4 py-6 md:px-8">
  <header class="mb-6">
    <h1 class="text-2xl font-semibold text-brand-text">Customers</h1>
    <p class="mt-1 text-sm text-brand-textMuted">
      Borrower roster with live payment countdowns and remote controls.
    </p>
  </header>

  <LiveTable rows={$nodes} />
</section>
