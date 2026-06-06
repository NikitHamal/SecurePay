<script lang="ts">
  import '../app.css';
  import { onMount } from 'svelte';
  import Sidebar from '$lib/components/Sidebar.svelte';
  import { load } from '$lib/stores/nodes';

  onMount(() => {
    void load();
  });
</script>

<div class="flex min-h-screen bg-brand-bg text-brand-text">
  <Sidebar />
  <main class="flex-1 overflow-x-hidden">
    <slot />
  </main>
</div>
