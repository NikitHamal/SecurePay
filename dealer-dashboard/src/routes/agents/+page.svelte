<script lang="ts">
  import { onMount } from 'svelte';
  import { apiClient, updateAgent, deleteAgent, type AgentRow } from '$lib/api/client';
  import PageHeader from '$lib/components/ui/PageHeader.svelte';
  import TopBar from '$lib/components/layout/TopBar.svelte';
  import Modal from '$lib/components/ui/Modal.svelte';
  import { dealer } from '$lib/stores/auth';

  interface Branch { id: string; name: string; agencyName?: string }

  let agents: AgentRow[] = [];
  let branches: Branch[] = [];
  let loading = true;
  let error = '';
  let actionError = '';
  let view: 'grid' | 'table' = 'grid';
  let search = '';
  let busyId = '';
  $: canManage = $dealer ? $dealer.role !== 'AGENT' : false;

  // ---- edit modal ----
  let editOpen = false;
  let editAgent: AgentRow | null = null;
  let editName = '';
  let editPhone = '';
  let editBranchId = '';
  let editSaving = false;
  let editError = '';

  onMount(async () => {
    await Promise.all([fetchAgents(), fetchBranches()]);
  });

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

  async function fetchBranches() {
    try {
      const res = await apiClient('/api/branches');
      if (res.ok) branches = await res.json();
    } catch { /* edit picker stays empty */ }
  }

  function openEdit(agent: AgentRow) {
    editAgent = agent;
    editName = agent.name || '';
    editPhone = agent.phone || '';
    editBranchId = agent.branchId || '';
    editError = '';
    editOpen = true;
  }

  async function saveEdit() {
    if (!editAgent) return;
    if (editName.trim().length < 2) { editError = 'Name must be at least 2 characters'; return; }
    editSaving = true;
    editError = '';
    try {
      await updateAgent(editAgent.id, {
        name: editName.trim(),
        phone: editPhone.trim(),
        ...(editBranchId ? { branchId: editBranchId } : {})
      });
      editOpen = false;
      await fetchAgents();
    } catch (e) {
      editError = e instanceof Error ? e.message : 'Failed to update agent';
    } finally {
      editSaving = false;
    }
  }

  async function toggleBan(agent: AgentRow) {
    const banning = agent.isApproved;
    if (banning && !confirm(`Ban ${agent.name}? They will be locked out of the agent app immediately and cannot log in until reinstated.`)) return;
    busyId = agent.id;
    actionError = '';
    try {
      await updateAgent(agent.id, { isApproved: !banning });
      await fetchAgents();
    } catch (e) {
      actionError = e instanceof Error ? e.message : 'Failed to update agent';
    } finally {
      busyId = '';
    }
  }

  async function removeAgent(agent: AgentRow) {
    if (!confirm(`Permanently delete ${agent.name}? This cannot be undone. Agents with customers or assigned devices cannot be deleted — ban them instead.`)) return;
    busyId = agent.id;
    actionError = '';
    try {
      await deleteAgent(agent.id);
      await fetchAgents();
    } catch (e) {
      actionError = e instanceof Error ? e.message : 'Failed to delete agent';
    } finally {
      busyId = '';
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

  $: activeCount = agents.filter(a => a.isApproved).length;
  $: bannedCount = agents.filter(a => !a.isApproved).length;
  $: totalRevenue = agents.reduce((s, a) => s + (a.totalRevenue || 0), 0);
</script>

<svelte:head><title>Agents · Touch Base</title></svelte:head>

<div class="page">
  <TopBar showSearch={false} />

  <PageHeader title="Agents" subtitle="Manage agents — edit, ban, or remove access">
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
  <div class="mb-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
    <div class="card p-4">
      <p class="text-xs text-ink-muted font-medium">Total agents</p>
      <p class="mt-1 text-2xl font-semibold tabular-nums text-ink-primary">{agents.length}</p>
    </div>
    <div class="card p-4">
      <p class="text-xs text-ink-muted font-medium">Active</p>
      <p class="mt-1 text-2xl font-semibold tabular-nums text-emerald">{activeCount}</p>
    </div>
    <div class="card p-4">
      <p class="text-xs text-ink-muted font-medium">Banned</p>
      <p class="mt-1 text-2xl font-semibold tabular-nums text-crimson">{bannedCount}</p>
    </div>
    <div class="card p-4">
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

  {#if actionError}
    <div class="mb-4 rounded-lg border border-crimson/20 bg-crimson/10 px-4 py-3 text-sm text-crimson">{actionError}</div>
  {/if}

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
        <div class="card card-hover p-4 {agent.isApproved ? '' : 'opacity-75 border-crimson/30'}">
          <div class="flex items-start gap-3">
            <span class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-sm font-semibold text-white" style="background-color: {agent.isApproved ? 'var(--brand)' : '#DC2626'};">
              {initials(agent.name)}
            </span>
            <div class="min-w-0 flex-1">
              <h3 class="truncate text-sm font-semibold text-ink-primary">{agent.name || 'Unknown Agent'}</h3>
              <p class="truncate text-xs text-ink-secondary">{agent.email || ''}</p>
              <p class="text-xs text-ink-muted">{agent.phone || ''}</p>
              <div class="mt-1.5 flex flex-wrap items-center gap-1.5">
                {#if !agent.isApproved}
                  <span class="chip-crimson !text-[10px]">BANNED</span>
                {/if}
                {#if agent.branchName || agent.agencyName}
                  <span class="truncate text-xs font-medium text-emerald">{[agent.agencyName, agent.branchName].filter(Boolean).join(' · ')}</span>
                {/if}
              </div>
            </div>
          </div>
          <div class="mt-3 grid grid-cols-2 gap-3 pt-3">
            <div>
              <p class="text-[11px] uppercase tracking-wider text-ink-muted">Sales</p>
              <p class="text-lg font-semibold tabular-nums text-ink-primary">{agent.salesCount}</p>
            </div>
            <div>
              <p class="text-[11px] uppercase tracking-wider text-ink-muted">Revenue</p>
              <p class="text-lg font-semibold tabular-nums text-emerald">{formatCurrency(agent.totalRevenue)}</p>
            </div>
          </div>
          {#if canManage}
            <div class="mt-3 flex items-center justify-end gap-1.5 border-t border-edge pt-3">
              <button class="btn-outline !py-1 !px-2.5 text-xs" disabled={busyId === agent.id} on:click={() => openEdit(agent)}>Edit</button>
              <button
                class="btn-outline !py-1 !px-2.5 text-xs {agent.isApproved ? 'text-amber hover:bg-amber/10' : 'text-emerald hover:bg-emerald/10'}"
                disabled={busyId === agent.id}
                on:click={() => toggleBan(agent)}
              >{agent.isApproved ? 'Ban' : 'Unban'}</button>
              <button class="btn-outline !py-1 !px-2.5 text-xs text-crimson hover:bg-crimson/10" disabled={busyId === agent.id} on:click={() => removeAgent(agent)}>Delete</button>
            </div>
          {/if}
        </div>
      {/each}
    </div>
  {:else}
    <div class="card overflow-hidden">
      <div class="overflow-x-auto">
        <table class="data-table min-w-[900px]">
          <thead>
            <tr>
              <th>Agent</th>
              <th>Phone</th>
              <th>Branch / Agency</th>
              <th>Status</th>
              <th class="text-right">Sales</th>
              <th class="text-right">Revenue</th>
              {#if canManage}<th class="text-right">Actions</th>{/if}
            </tr>
          </thead>
          <tbody>
            {#each filteredAgents as agent (agent.id)}
              <tr class="transition-colors hover:bg-hover">
                <td>
                  <div class="flex items-center gap-3">
                    <span class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-xs font-semibold text-white" style="background-color: {agent.isApproved ? 'var(--brand)' : '#DC2626'};">
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
                <td>
                  {#if agent.isApproved}
                    <span class="chip-emerald">Active</span>
                  {:else}
                    <span class="chip-crimson">Banned</span>
                  {/if}
                </td>
                <td class="text-right text-sm font-medium text-ink-primary tabular-nums">{agent.salesCount}</td>
                <td class="text-right text-sm font-semibold text-emerald tabular-nums">{formatCurrency(agent.totalRevenue)}</td>
                {#if canManage}
                  <td class="text-right">
                    <div class="flex justify-end gap-1.5">
                      <button class="btn-outline !py-1 !px-2 text-xs" disabled={busyId === agent.id} on:click={() => openEdit(agent)}>Edit</button>
                      <button
                        class="btn-outline !py-1 !px-2 text-xs {agent.isApproved ? 'text-amber hover:bg-amber/10' : 'text-emerald hover:bg-emerald/10'}"
                        disabled={busyId === agent.id}
                        on:click={() => toggleBan(agent)}
                      >{agent.isApproved ? 'Ban' : 'Unban'}</button>
                      <button class="btn-outline !py-1 !px-2 text-xs text-crimson hover:bg-crimson/10" disabled={busyId === agent.id} on:click={() => removeAgent(agent)}>Delete</button>
                    </div>
                  </td>
                {/if}
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
    </div>
  {/if}
</div>

<!-- Edit agent modal -->
<Modal open={editOpen} title="Edit Agent" size="sm" on:close={() => (editOpen = false)}>
  {#if editError}
    <div class="mb-3 rounded-lg border border-crimson/20 bg-crimson/10 px-3 py-2 text-xs text-crimson">{editError}</div>
  {/if}
  <div class="space-y-3">
    <div>
      <label class="label" for="ag-name">Full name</label>
      <input id="ag-name" class="input" bind:value={editName} placeholder="e.g. Prince Osei Boateng" />
    </div>
    <div>
      <label class="label" for="ag-phone">Phone</label>
      <input id="ag-phone" class="input" bind:value={editPhone} placeholder="024 xxx xxxx" />
    </div>
    <div>
      <label class="label" for="ag-email">Email (login — cannot change)</label>
      <input id="ag-email" class="input opacity-60" value={editAgent?.email || ''} disabled />
    </div>
    <div>
      <label class="label" for="ag-branch">Branch</label>
      <select id="ag-branch" class="input" bind:value={editBranchId}>
        <option value="">— Unassigned —</option>
        {#each branches as b (b.id)}
          <option value={b.id}>{b.name}{b.agencyName ? ` — ${b.agencyName}` : ''}</option>
        {/each}
      </select>
      <p class="mt-1 text-2xs text-ink-muted">Reassigning the branch moves the agent's agency scope too.</p>
    </div>
  </div>
  <svelte:fragment slot="footer">
    <button class="btn-outline" on:click={() => (editOpen = false)} disabled={editSaving}>Cancel</button>
    <button class="btn-primary" on:click={saveEdit} disabled={editSaving}>
      {editSaving ? 'Saving…' : 'Save changes'}
    </button>
  </svelte:fragment>
</Modal>
