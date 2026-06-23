<script lang="ts">
  import PageHeader from '$lib/components/ui/PageHeader.svelte';
  import TopBar from '$lib/components/layout/TopBar.svelte';
  import { listDeviceLogs, clearDeviceLogs, type DeviceLog } from '$lib/api/client';
  import { onMount, onDestroy } from 'svelte';

  let logs: DeviceLog[] = [];
  let loading = false;
  let error: string | null = null;
  let searchQuery = '';
  let levelFilter: 'ALL' | 'ERROR' | 'WARN' | 'INFO' = 'ALL';
  let autoRefresh = true;
  let intervalId: any;

  async function loadLogs(silent = false) {
    if (!silent) {
      loading = true;
    }
    error = null;
    try {
      logs = await listDeviceLogs();
    } catch (err: any) {
      error = err.message || 'Failed to fetch logs';
    } finally {
      if (!silent) {
        loading = false;
      }
    }
  }

  async function handleClearLogs() {
    const ok = window.confirm('Are you sure you want to clear all device logs? This cannot be undone.');
    if (!ok) return;
    loading = true;
    error = null;
    try {
      await clearDeviceLogs();
      logs = [];
    } catch (err: any) {
      error = err.message || 'Failed to clear logs';
    } finally {
      loading = false;
    }
  }

  function startAutoRefresh() {
    stopAutoRefresh();
    intervalId = setInterval(() => {
      loadLogs(true);
    }, 3000);
  }

  function stopAutoRefresh() {
    if (intervalId) {
      clearInterval(intervalId);
      intervalId = null;
    }
  }

  $: if (autoRefresh) {
    startAutoRefresh();
  } else {
    stopAutoRefresh();
  }

  onMount(async () => {
    await loadLogs();
    if (autoRefresh) {
      startAutoRefresh();
    }
  });

  onDestroy(() => {
    stopAutoRefresh();
  });

  $: filteredLogs = logs.filter(log => {
    const matchesSearch = 
      log.tag.toLowerCase().includes(searchQuery.toLowerCase()) || 
      log.message.toLowerCase().includes(searchQuery.toLowerCase());
    
    const matchesLevel = levelFilter === 'ALL' || log.level === levelFilter;
    
    return matchesSearch && matchesLevel;
  });

  function getLevelClass(level: string): string {
    switch (level) {
      case 'ERROR': return 'chip-crimson';
      case 'WARN': return 'chip-amber';
      case 'INFO': return 'chip-sky';
      default: return 'chip';
    }
  }
</script>

<svelte:head>
  <title>Device Logs · SecurePay Dealer Console</title>
</svelte:head>

<div class="page">
  <TopBar showSearch={false} />

  <PageHeader
    eyebrow="Diagnostics"
    title="Device Logs"
    subtitle="Real-time log stream received from financing devices during setup and runtime."
  >
    <div slot="actions" class="flex items-center gap-2">
      <button 
        type="button" 
        class="btn-outline" 
        on:click={() => loadLogs()} 
        disabled={loading}
      >
        {loading ? 'Refreshing...' : 'Refresh'}
      </button>
      <button 
        type="button" 
        class="btn-outline text-crimson hover:bg-crimson/10 border-crimson/20" 
        on:click={handleClearLogs}
        disabled={loading}
      >
        Clear Logs
      </button>
    </div>
  </PageHeader>

  <div class="card p-4 mb-6">
    <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
      <div class="flex flex-wrap items-center gap-2">
        <button
          type="button"
          class="rounded-md px-3 py-1.5 text-xs font-medium transition-colors
                 {levelFilter === 'ALL' ? 'bg-emerald/15 text-emerald' : 'bg-surface-100 text-ink-secondary hover:text-ink-primary'}"
          on:click={() => levelFilter = 'ALL'}
        >
          All Logs
        </button>
        <button
          type="button"
          class="rounded-md px-3 py-1.5 text-xs font-medium transition-colors
                 {levelFilter === 'ERROR' ? 'bg-crimson/15 text-crimson' : 'bg-surface-100 text-ink-secondary hover:text-ink-primary'}"
          on:click={() => levelFilter = 'ERROR'}
        >
          Errors
        </button>
        <button
          type="button"
          class="rounded-md px-3 py-1.5 text-xs font-medium transition-colors
                 {levelFilter === 'WARN' ? 'bg-amber/15 text-amber' : 'bg-surface-100 text-ink-secondary hover:text-ink-primary'}"
          on:click={() => levelFilter = 'WARN'}
        >
          Warnings
        </button>
        <button
          type="button"
          class="rounded-md px-3 py-1.5 text-xs font-medium transition-colors
                 {levelFilter === 'INFO' ? 'bg-sky/15 text-sky' : 'bg-surface-100 text-ink-secondary hover:text-ink-primary'}"
          on:click={() => levelFilter = 'INFO'}
        >
          Info
        </button>
      </div>

      <div class="flex items-center gap-4">
        <label class="flex items-center gap-2 text-xs font-medium text-ink-secondary cursor-pointer select-none">
          <input 
            type="checkbox" 
            bind:checked={autoRefresh} 
            class="h-4 w-4 rounded border-edge text-emerald focus:ring-emerald"
          />
          Auto-refresh (3s)
        </label>

        <input
          type="text"
          placeholder="Filter logs (tag, message)..."
          bind:value={searchQuery}
          class="input max-w-xs py-1 px-3 text-xs"
        />
      </div>
    </div>
  </div>

  {#if error}
    <div class="mb-6 rounded-lg border border-crimson-200/30 bg-crimson-200/10 px-4 py-3 text-sm text-crimson">
      {error}
    </div>
  {/if}

  <div class="card overflow-hidden">
    <div class="overflow-x-auto">
      <table class="data-table min-w-full">
        <thead>
          <tr>
            <th class="w-40">Time</th>
            <th class="w-24">Level</th>
            <th class="w-64">Tag</th>
            <th>Message</th>
          </tr>
        </thead>
        <tbody>
          {#if filteredLogs.length === 0}
            <tr>
              <td colspan="4" class="py-16 text-center text-ink-muted">
                {#if loading}
                  Streaming device logs...
                {:else if searchQuery || levelFilter !== 'ALL'}
                  No logs matching active filters.
                {:else}
                  No logs recorded yet. Once a device with the new client app starts provisioning, its diagnostic logs will appear here in real-time.
                {/if}
              </td>
            </tr>
          {:else}
            {#each filteredLogs as log (log.id)}
              <tr class="transition-colors hover:bg-hover align-top border-b border-edge/60 last:border-b-0">
                <td class="font-mono text-2xs text-ink-secondary whitespace-nowrap pt-3">
                  {log.time}
                </td>
                <td class="pt-2">
                  <span class={getLevelClass(log.level)}>{log.level}</span>
                </td>
                <td class="font-mono text-2xs font-semibold text-ink-primary pt-3 break-all">
                  {log.tag}
                </td>
                <td class="font-mono text-xs text-ink-secondary pt-3 pb-3 whitespace-pre-wrap break-all select-text selection:bg-emerald/30 selection:text-ink-primary">
                  {log.message}
                </td>
              </tr>
            {/each}
          {/if}
        </tbody>
      </table>
    </div>
  </div>
</div>
