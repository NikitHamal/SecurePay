<script lang="ts">
  import '../app.css';
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { page } from '$app/stores';
  import Sidebar from '$lib/components/layout/Sidebar.svelte';
  import { error, load } from '$lib/stores/customers';
  import { loadPayments } from '$lib/stores/payments';
  import { dealer, isAuthenticated, initAuth } from '$lib/stores/auth';

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
</script>

{#if $page.url.pathname === '/login' || !$isAuthenticated}
  <slot />
{:else}
  <div class="flex min-h-screen flex-col md:flex-row">
    <Sidebar />

    <main class="min-w-0 flex-1 overflow-x-hidden">
      {#if $error}
        <div class="m-6 rounded-xl border border-crimson-200/30 bg-crimson-200/10 px-4 py-3 text-sm text-crimson">
          {$error}
        </div>
      {/if}
      <slot />
    </main>
  </div>
{/if}