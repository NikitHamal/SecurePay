<script lang="ts">
  import { onMount } from 'svelte';
  import { apiClient } from '$lib/api/client';
  import Card from '$lib/components/ui/Card.svelte';
  import Badge from '$lib/components/ui/Badge.svelte';
  import PageHeader from '$lib/components/ui/PageHeader.svelte';
  import TopBar from '$lib/components/layout/TopBar.svelte';

  interface Branch {
    id: string;
    name: string;
    agencyId: string;
    agencyName?: string;
    adminId?: string;
    adminName?: string;
    address?: string;
    phone?: string;
    agentCount: number;
    isActive: boolean;
    createdAt: number;
  }

  interface Agency {
    id: string;
    name: string;
  }

  let branches: Branch[] = [];
  let agencies: Agency[] = [];
  let loading = true;
  let error = '';
  let formError = '';
  let showCreateForm = false;
  let creating = false;
  let searchQuery = '';

  let newBranch = {
    name: '',
    address: '',
    phone: '',
    agencyId: ''
  };

  onMount(async () => {
    await Promise.all([fetchBranches(), fetchAgencies()]);
  });

  async function fetchAgencies() {
    try {
      const res = await apiClient('/api/agencies');
      if (res.ok) {
        agencies = (await res.json()).map((a: { id: string; name: string }) => ({ id: a.id, name: a.name }));
        if (!newBranch.agencyId && agencies.length === 1) {
          newBranch.agencyId = agencies[0].id;
        }
      }
    } catch {
      /* dropdown stays empty */
    }
  }

  async function fetchBranches() {
    loading = true;
    error = '';
    try {
      const res = await apiClient('/api/branches');
      if (!res.ok) throw new Error('Failed to fetch branches');
      branches = await res.json();
    } catch (e) {
      error = e instanceof Error ? e.message : 'Unknown error';
    } finally {
      loading = false;
    }
  }

  function toggleForm() {
    showCreateForm = !showCreateForm;
    formError = '';
    if (!showCreateForm) {
      newBranch = {
        name: '',
        address: '',
        phone: '',
        agencyId: agencies.length === 1 ? agencies[0].id : ''
      };
    }
  }

  async function createBranch() {
    if (!newBranch.name.trim() || !newBranch.agencyId) {
      formError = 'Branch name and agency selection are required.';
      return;
    }

    creating = true;
    formError = '';
    try {
      const res = await apiClient('/api/branches', {
        method: 'POST',
        body: JSON.stringify(newBranch)
      });
      if (!res.ok) {
        const data = await res.json();
        throw new Error(data.error || 'Failed to create branch');
      }
      showCreateForm = false;
      newBranch = {
        name: '',
        address: '',
        phone: '',
        agencyId: agencies.length === 1 ? agencies[0].id : ''
      };
      await fetchBranches();
    } catch (e) {
      formError = e instanceof Error ? e.message : 'Failed to create branch';
    } finally {
      creating = false;
    }
  }

  $: filteredBranches = branches.filter((b) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    return (
      b.name.toLowerCase().includes(q) ||
      (b.agencyName && b.agencyName.toLowerCase().includes(q)) ||
      (b.address && b.address.toLowerCase().includes(q)) ||
      (b.phone && b.phone.toLowerCase().includes(q))
    );
  });

  $: totalAgents = branches.reduce((sum, b) => sum + (b.agentCount || 0), 0);
  $: uniqueAgencies = new Set(branches.map((b) => b.agencyId)).size;
</script>

<svelte:head>
  <title>Branches · Touch Base</title>
</svelte:head>

<div class="page">
  <TopBar showSearch={false} />

  <PageHeader title="Branches" subtitle="Manage physical branch locations and operational hubs">
    <svelte:fragment slot="actions">
      <button
        type="button"
        class={showCreateForm ? 'btn-outline' : 'btn-primary'}
        on:click={toggleForm}
      >
        <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4">
          {#if showCreateForm}
            <path d="M6 18L18 6M6 6l12 12" stroke-linecap="round" />
          {:else}
            <path d="M12 5v14M5 12h14" stroke-linecap="round" />
          {/if}
        </svg>
        {showCreateForm ? 'Close Form' : 'New Branch'}
      </button>
    </svelte:fragment>
  </PageHeader>

  {#if showCreateForm}
    <div class="mb-6 rounded-xl border border-edge bg-surface-200 p-5 sm:p-6">
      <form on:submit|preventDefault={createBranch} class="space-y-5">
        <div class="flex items-center justify-between border-b border-edge/60 pb-3">
          <div>
            <h3 class="text-base font-semibold text-ink-primary">Create New Branch</h3>
            <p class="text-xs text-ink-muted">Set up a new physical branch location under a registered agency</p>
          </div>
          <button
            type="button"
            class="btn-ghost h-8 w-8 !p-0 text-ink-muted hover:text-ink-primary"
            aria-label="Close form"
            on:click={toggleForm}
          >
            <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M6 18L18 6M6 6l12 12" stroke-linecap="round" />
            </svg>
          </button>
        </div>

        {#if formError}
          <div class="rounded-lg border border-crimson/20 bg-crimson/10 px-4 py-2.5 text-xs text-crimson">
            {formError}
          </div>
        {/if}

        <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <label for="branch-name" class="mb-1.5 block text-xs font-medium text-ink-secondary">
              Branch Name <span class="text-crimson">*</span>
            </label>
            <input
              id="branch-name"
              type="text"
              bind:value={newBranch.name}
              placeholder="e.g., Accra Central"
              required
              class="input w-full text-xs"
            />
          </div>

          <div>
            <label for="branch-agency" class="mb-1.5 block text-xs font-medium text-ink-secondary">
              Agency <span class="text-crimson">*</span>
            </label>
            {#if agencies.length === 0}
              <div class="rounded-lg border border-amber/40 bg-amber/10 px-3 py-2 text-xs text-amber flex items-center justify-between">
                <span>No agencies found</span>
                <a href="/agencies" class="font-semibold underline hover:opacity-80">Create →</a>
              </div>
            {:else}
              <select id="branch-agency" bind:value={newBranch.agencyId} required class="input w-full text-xs">
                <option value="" disabled>Select agency…</option>
                {#each agencies as agency (agency.id)}
                  <option value={agency.id}>{agency.name} ({agency.id})</option>
                {/each}
              </select>
            {/if}
          </div>

          <div>
            <label for="branch-address" class="mb-1.5 block text-xs font-medium text-ink-secondary">
              Address
            </label>
            <input
              id="branch-address"
              type="text"
              bind:value={newBranch.address}
              placeholder="Street address or location"
              class="input w-full text-xs"
            />
          </div>

          <div>
            <label for="branch-phone" class="mb-1.5 block text-xs font-medium text-ink-secondary">
              Phone Number
            </label>
            <input
              id="branch-phone"
              type="tel"
              bind:value={newBranch.phone}
              placeholder="+233 XX XXX XXXX"
              class="input w-full text-xs"
            />
          </div>
        </div>

        <div class="flex items-center justify-end gap-3 border-t border-edge/60 pt-4">
          <button type="button" class="btn-ghost text-xs" on:click={toggleForm}>
            Cancel
          </button>
          <button
            type="submit"
            class="btn-primary text-xs"
            disabled={creating || agencies.length === 0}
          >
            {creating ? 'Creating...' : 'Create Branch'}
          </button>
        </div>
      </form>
    </div>
  {/if}

  {#if error}
    <div class="mb-4 rounded-xl border border-crimson/20 bg-crimson/10 p-4 text-xs text-crimson">
      {error}
    </div>
  {/if}

  <!-- Stats Bar & Search -->
  <div class="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
    <div class="flex flex-wrap items-center gap-3">
      <div class="flex items-center gap-2 rounded-lg border border-edge bg-surface-100/60 px-3 py-1.5 text-xs">
        <span class="font-medium text-ink-muted">Total Branches:</span>
        <span class="font-semibold text-ink-primary">{branches.length}</span>
      </div>
      <div class="flex items-center gap-2 rounded-lg border border-edge bg-surface-100/60 px-3 py-1.5 text-xs">
        <span class="font-medium text-ink-muted">Agencies Represented:</span>
        <span class="font-semibold text-ink-primary">{uniqueAgencies}</span>
      </div>
      <div class="flex items-center gap-2 rounded-lg border border-edge bg-surface-100/60 px-3 py-1.5 text-xs">
        <span class="font-medium text-ink-muted">Total Agents:</span>
        <span class="font-semibold text-ink-primary">{totalAgents}</span>
      </div>
    </div>

    {#if branches.length > 0}
      <div class="relative w-full sm:w-64">
        <svg
          class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-muted"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          stroke-width="2"
        >
          <path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
        </svg>
        <input
          type="text"
          bind:value={searchQuery}
          placeholder="Search branches..."
          class="input w-full !pl-9 text-xs"
        />
      </div>
    {/if}
  </div>

  {#if loading}
    <div class="flex items-center justify-center py-16">
      <div class="h-8 w-8 animate-spin rounded-full border-2 border-emerald border-t-transparent"></div>
    </div>
  {:else if branches.length === 0}
    <Card>
      <div class="flex flex-col items-center justify-center py-14 text-center">
        <div class="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gold-400/10 text-gold-400">
          <svg class="h-7 w-7" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4"
            />
          </svg>
        </div>
        <p class="text-base font-semibold text-ink-primary">No branches yet</p>
        <p class="mt-1 max-w-sm text-xs text-ink-muted">
          Create physical branch locations under an agency to organize your agents and operations.
        </p>
        <button class="btn-primary mt-5" on:click={toggleForm}>
          + Create Branch
        </button>
      </div>
    </Card>
  {:else if filteredBranches.length === 0}
    <Card>
      <div class="py-10 text-center text-xs text-ink-muted">
        No branches match your search query "{searchQuery}".
      </div>
    </Card>
  {:else}
    <div class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      {#each filteredBranches as branch (branch.id)}
        <Card>
          <div class="flex flex-col h-full justify-between gap-4">
            <div>
              <div class="flex items-start justify-between gap-2">
                <div class="flex items-center gap-2.5">
                  <div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-surface-100 text-gold-400 border border-edge">
                    <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
                    </svg>
                  </div>
                  <div>
                    <h3 class="text-base font-semibold text-ink-primary leading-tight">{branch.name}</h3>
                    {#if branch.agencyName}
                      <p class="text-2xs text-ink-muted mt-0.5">Agency: <span class="text-ink-secondary font-medium">{branch.agencyName}</span></p>
                    {/if}
                  </div>
                </div>
                {#if branch.isActive}
                  <Badge variant="active">Active</Badge>
                {:else}
                  <Badge variant="locked">Inactive</Badge>
                {/if}
              </div>

              <div class="mt-4 space-y-2.5 border-t border-edge/60 pt-3 text-xs">
                <div class="flex items-center gap-2 text-ink-secondary">
                  <svg class="h-4 w-4 shrink-0 text-ink-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                    <path stroke-linecap="round" stroke-linejoin="round" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
                  </svg>
                  <span class="truncate">{branch.address || 'No address specified'}</span>
                </div>

                <div class="flex items-center gap-2 text-ink-secondary">
                  <svg class="h-4 w-4 shrink-0 text-ink-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z" />
                  </svg>
                  <span>{branch.phone || 'No phone number'}</span>
                </div>

                {#if branch.adminName}
                  <div class="flex items-center gap-2 text-ink-secondary">
                    <svg class="h-4 w-4 shrink-0 text-ink-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                    </svg>
                    <span>Admin: {branch.adminName}</span>
                  </div>
                {/if}
              </div>
            </div>

            <div class="flex items-center justify-between border-t border-edge/60 pt-3 text-xs">
              <span class="text-ink-muted">Assigned Agents</span>
              <span class="inline-flex items-center gap-1 rounded-full bg-surface-100 px-2.5 py-0.5 font-semibold text-ink-primary border border-edge">
                <svg class="h-3.5 w-3.5 text-emerald" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
                </svg>
                {branch.agentCount}
              </span>
            </div>
          </div>
        </Card>
      {/each}
    </div>
  {/if}
</div>
