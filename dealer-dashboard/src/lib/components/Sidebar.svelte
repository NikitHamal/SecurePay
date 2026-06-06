<script lang="ts">
  const navItems = ['Overview', 'Inventory', 'Customers', 'Payment Ledger'];
  export let active = 'Overview';
</script>

<aside class="flex h-full w-full flex-col border-r border-white/10 bg-charcoal-900 px-4 py-5 md:w-72">
  <div class="mb-8">
    <p class="text-2xl font-bold tracking-normal text-white">SecurePay</p>
    <p class="mt-1 text-sm text-white/58">Dealer command console</p>
  </div>

  <nav class="flex flex-row gap-2 overflow-x-auto md:flex-col md:overflow-visible">
    {#each navItems as item}
      <button
        class={`rounded-lg px-4 py-3 text-left text-sm font-semibold transition ${
          item === active
            ? 'bg-emerald-container text-emerald-active'
            : 'text-white/70 hover:bg-white/6 hover:text-white'
        }`}
        type="button"
      >
        {item}
      </button>
    {/each}
  </nav>
</aside>
