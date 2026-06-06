<script lang="ts">
  import PageHeader from '$lib/components/PageHeader.svelte';
  import StatusBadge from '$lib/components/StatusBadge.svelte';
  import { customers } from '$lib/stores/customers';
</script>

<svelte:head>
  <title>Inventory · SecurePay Dealer Console</title>
</svelte:head>

<section class="p-6">
  <PageHeader
    title="IMEI Matrix"
    subtitle="Hardware inventory mapped to financed accounts and lock status"
  />

  <div class="card overflow-hidden">
    <div class="overflow-x-auto">
      <table class="w-full min-w-[720px] border-collapse text-left text-sm">
        <thead>
          <tr class="border-b border-white/5 text-xs uppercase tracking-wide text-text-secondary">
            <th class="px-4 py-3 font-medium">IMEI</th>
            <th class="px-4 py-3 font-medium">Device Model</th>
            <th class="px-4 py-3 font-medium">Assigned Customer</th>
            <th class="px-4 py-3 font-medium">Status</th>
          </tr>
        </thead>
        <tbody>
          {#each $customers as customer (customer.id)}
            <tr class="border-b border-white/5 transition-colors last:border-b-0 hover:bg-white/[0.03]">
              <td class="px-4 py-3 font-mono text-xs text-text-secondary">{customer.imei}</td>
              <td class="px-4 py-3 text-text-primary">{customer.deviceModel}</td>
              <td class="px-4 py-3">
                <div class="text-text-primary">{customer.customerName}</div>
                <div class="text-xs text-text-secondary">{customer.id}</div>
              </td>
              <td class="px-4 py-3">
                <StatusBadge status={customer.status} />
              </td>
            </tr>
          {/each}

          {#if $customers.length === 0}
            <tr>
              <td colspan="4" class="px-4 py-10 text-center text-text-secondary">
                No inventory records loaded.
              </td>
            </tr>
          {/if}
        </tbody>
      </table>
    </div>
  </div>
</section>
