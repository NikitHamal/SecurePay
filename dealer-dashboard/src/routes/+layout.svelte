<script lang="ts">
  import '../app.css';
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { page } from '$app/stores';
  import Sidebar from '$lib/components/layout/Sidebar.svelte';
  import NewLoanModal from '$lib/components/NewLoanModal.svelte';
  import AddDeviceModal from '$lib/components/AddDeviceModal.svelte';
  import ProvisionModal from '$lib/components/ProvisionModal.svelte';
  import { error, load } from '$lib/stores/customers';
  import { loadPayments } from '$lib/stores/payments';
  import { dealer, isAuthenticated, initAuth } from '$lib/stores/auth';
  import {
    newLoanOpen, addDeviceOpen, provisionOpen, provisionInitialImei,
    openProvision
  } from '$lib/stores/ui';

  let ready = false;
  let loaded = false;

  onMount(() => {
    initAuth();
    ready = true;
  });

  $: if (ready && !$isAuthenticated && $page.url.pathname !== '/login') {
    goto('/login');
  }
  $: if (ready && $isAuthenticated && $page.url.pathname === '/login') {
    goto('/');
  }
  $: if ($isAuthenticated && !loaded) {
    loaded = true;
    load();
    loadPayments();
  }

  function closeLoan() { newLoanOpen.set(false); }
  function closeDevice() { addDeviceOpen.set(false); load(); }
  function closeProvision() { provisionOpen.set(false); provisionInitialImei.set(''); }

  function onLoanProvision(e: CustomEvent) {
    closeLoan();
    if (e?.detail?.imei) openProvision(e.detail.imei);
  }
</script>

{#if $page.url.pathname === '/login' || !$isAuthenticated}
  <slot />
{:else}
  <div class="flex min-h-screen flex-col md:flex-row">
    <Sidebar />

    <main class="min-w-0 flex-1 overflow-x-hidden">
      {#if $error}
        <div class="m-4 rounded-lg border border-crimson/20 bg-crimson/10 px-4 py-3 text-sm text-crimson sm:m-6">
          {$error}
        </div>
      {/if}
      <slot />
    </main>
  </div>

  <NewLoanModal open={$newLoanOpen} on:close={closeLoan} on:provision={onLoanProvision} />
  <AddDeviceModal open={$addDeviceOpen} on:close={closeDevice} />
  <ProvisionModal open={$provisionOpen} initialImei={$provisionInitialImei} on:close={closeProvision} />
{/if}
