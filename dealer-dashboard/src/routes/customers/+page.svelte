<script lang="ts">
  import CustomerTable from '$lib/components/CustomerTable.svelte';
  import PageHeader from '$lib/components/PageHeader.svelte';
</script>

<svelte:head>
  <title>Customers · SecurePay Dealer Console</title>
</svelte:head>

<section class="p-6">
  <PageHeader
    title="Customer Accounts"
    subtitle="Manage payment timers and remote device locks for every financed account"
  />

  <CustomerTable />
</section>
