<script lang="ts">
  import { onMount } from 'svelte';
  import { apiClient } from '$lib/api/client';
  import PageHeader from '$lib/components/ui/PageHeader.svelte';
  import TopBar from '$lib/components/layout/TopBar.svelte';

  interface Agent {
    id: string;
    name: string;
    email: string;
    phone: string;
    branchId?: string;
    branchName?: string;
    agencyId?: string;
    agencyName?: string;
    createdAt: number;
    salesCount: number;
    totalRevenue: number;
  }

  let agents: Agent[] = [];
  let loading = true;
  let error = '';
  let view: 'grid' | 'table' = 'grid';
  let search = '';

  onMount(fetchAgents);

  async function fetchAgents() {
    loading = true;
    error = '';
    try {
      const res = await apiClient('/api/agents');
      if (!res.ok) throw new Error('Failed to fetch agents');
      agents = await res.json();
    } catch (e) {
      error = e instanceof Error ? e.message : 'Unknown error';
    } finally {
      loading = false;
    }
  }

  function formatCurrency(amount: number): string {
    return `GH₵${(amount / 100).toFixed(2)}`;
  }

  function initials(name: string): string {
    return (name || 'A').split(' ').map((n) => n[0]).join('').slice(0, 2).toUpperCase();
  }

  $: filteredAgents = agents.filter(a => {
    if (!search.trim()) return true;
    const q = search.toLowerCase();
    return (a.name || '').toLowerCase().includes(q)
      || (a.email || '').toLowerCase().includes(q)
      || (a.phone || '').includes(q);
  });

  $: totalRevenue = agents.reduce((s, a) => s + (a.totalRevenue || 0), 0);
</script>

<svelte:head><title>Agents · Touch Base</title></svelte:head>

<div class="page">
  <TopBar showSearch={false} />

  <PageHeader title="Agents" subtitle="View all agents and their performance">
    <svelte:fragment slot="actions">
      <button class="btn-outline" on:click={fetchAgents} disabled={loading}>
        <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M4 4v6h6M20 20v-6h-6M4 10a8 8 0 0114-3m2 7a8 8 0 01-14 3" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        Refresh
      </button>
    </svelte:fragment>
  </PageHeader>

  <!-- Summary KPIs -->
  <div class="mb-5 grid grid-cols-2 gap-3 sm:grid-cols-3">
    <div class="card p-4">
      <p class="text-xs text-ink-muted font-medium">Total agents</p>
      <p class="mt-1 text-2xl font-semibold tabular-nums text-ink-primary">{agents.length}</p>
    </div>
    <div class="card p-4">
      <p class="text-xs text-ink-muted font-medium">Total sales</p>
      <p class="mt-1 text-2xl font-semibold tabular-nums text-ink-primary">
        {agents.reduce((s, a) => s + (a.salesCount || 0), 0)}
      </p>
    </div>
    <div class="card p-4 col-span-2 sm:col-span-1">
      <p class="text-xs text-ink-muted font-medium">Total revenue</p>
      <p class="mt-1 text-2xl font-semibold tabular-nums text-emerald">{formatCurrency(totalRevenue)}</p>
    </div>
  </div>

  <!-- Toolbar -->
  <div class="card mb-4 flex flex-col gap-3 p-3 sm:flex-row sm:items-center">
    <label class="relative flex-1">
      <svg class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5" stroke-linecap="round"/>
      </svg>
      <input type="search" class="input pl-9" placeholder="Search agents by name, email or phone…" bind:value={search} />
    </label>
    <div class="flex items-center gap-1 rounded-lg border border-edge bg-surface-100 p-1">
      <button
        type="button"
        on:click={() => (view = 'grid')}
        class="rounded-md px-3 py-1.5 text-xs font-medium transition-colors
               {view === 'grid' ? 'bg-surface-200 text-ink-primary shadow-sm' : 'text-ink-secondary hover:text-ink-primary'}"
      >Grid</button>
      <button
        type="button"
        on:click={() => (view = 'table')}
        class="rounded-md px-3 py-1.5 text-xs font-medium transition-colors
               {view === 'table' ? 'bg-surface-200 text-ink-primary shadow-sm' : 'text-ink-secondary hover:text-ink-primary'}"
      >Table</button>
    </div>
  </div>

  {#if loading}
    <div class="flex items-center justify-center py-12">
      <div class="h-6 w-6 animate-spin rounded-full border-2 border-emerald border-t-transparent"></div>
    </div>
  {:else if error}
    <div class="rounded-lg border border-crimson/20 bg-crimson/10 px-4 py-3 text-sm text-crimson">{error}</div>
  {:else if filteredAgents.length === 0}
    <div class="card py-16 text-center">
      <svg class="mx-auto mb-3 h-12 w-12 text-ink-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
        <path stroke-linecap="round" stroke-linejoin="round" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
      </svg>
      <p class="text-sm font-medium text-ink-primary">{agents.length === 0 ? 'No agents yet' : 'No matching agents'}</p>
      <p class="mt-1 text-xs text-ink-muted">
        {agents.length === 0 ? 'Approved agents will appear here.' : 'Try a different search.'}
      </p>
    </div>
  {:else if view === 'grid'}
    <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      {#each filteredAgents as agent (agent.id)}
        <div class="card card-hover p-4">
          <div class="flex items-start gap-3">
            <span class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-sm font-semibold text-white" style="background-color: var(--brand);">
              {initials(agent.name)}
            </span>
            <div class="min-w-0 flex-1">
              <h3 class="truncate text-sm font-semibold text-ink-primary">{agent.name || 'Unknown Agent'}</h3>
              <p class="truncate text-xs text-ink-secondary">{agent.email || ''}</p>
              <p class="text-xs text-ink-muted">{agent.phone || ''}</p>
              {#if agent.branchName || agent.agencyName}
                <p class="mt-1.5 truncate text-xs font-medium text-emerald">
                  {[agent.agencyName, agent.branchName].filter(Boolean).join(' · ')}
                </p>
              {/if}
            </div>
          </div>
          <div class="mt-3 grid grid-cols-2 gap-3 border-t border-edge pt-3">
            <div>
              <p class="text-[11px] uppercase tracking-wider text-ink-muted">Sales</p>
              <p class="text-lg font-semibold tabular-nums text-ink-primary">{agent.salesCount}</p>
            </div>
            <div>
              <p class="text-[11px] uppercase tracking-wider text-ink-muted">Revenue</p>
              <p class="text-lg font-semibold tabular-nums text-emerald">{formatCurrency(agent.totalRevenue)}</p>
            </div>
          </div>
        </div>
      {/each}
    </div>
  {:else}
    <div class="card overflow-hidden">
      <div class="overflow-x-auto">
        <table class="data-table min-w-[720px]">
          <thead>
            <tr>
              <th>Agent</th>
              <th>Phone</th>
              <th>Branch / Agency</th>
              <th class="text-right">Sales</th>
              <th class="text-right">Revenue</th>
            </tr>
          </thead>
          <tbody>
            {#each filteredAgents as agent (agent.id)}
              <tr class="transition-colors hover:bg-hover">
                <td>
                  <div class="flex items-center gap-3">
                    <span class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-xs font-semibold text-white" style="background-color: var(--brand);">
                      {initials(agent.name)}
                    </span>
                    <div class="min-w-0">
                      <p class="truncate text-sm font-medium text-ink-primary">{agent.name}</p>
                      <p class="truncate text-xs text-ink-muted">{agent.email}</p>
                    </div>
                  </div>
                </td>
                <td class="text-sm text-ink-secondary">{agent.phone || '—'}</td>
                <td class="text-sm text-ink-secondary">{[agent.agencyName, agent.branchName].filter(Boolean).join(' · ') || '—'}</td>
                <td class="text-right text-sm font-medium text-ink-primary tabular-nums">{agent.salesCount}</td>
                <td class="text-right text-sm font-semibold text-emerald tabular-nums">{formatCurrency(agent.totalRevenue)}</td>
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
    </div>
  {/if}
</div>
