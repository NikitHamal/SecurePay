<script lang="ts">
  import '../app.css';
  import { onMount } from 'svelte';
  import Sidebar from '$lib/components/Sidebar.svelte';
  import { error, load } from '$lib/stores/customers';

  onMount(() => {
    void load();
  });
</script>

<div class="flex min-h-screen flex-col md:flex-row">
  <Sidebar />

  <main class="flex-1 overflow-x-hidden">
    {#if $error}
      <div class="m-6 rounded-xl border border-crimson/30 bg-crimson/10 px-4 py-3 text-sm text-crimson">
        {$error}
      </div>
    {/if}
    <slot />
  </main>
</div>
