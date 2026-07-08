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
    address?: string;
    phone?: string;
    agentCount: number;
    isActive: boolean;
    createdAt: number;
  }

  let branches: Branch[] = [];
  let loading = true;
  let error = '';
  let showCreateForm = false;
  let creating = false;
  
  let newBranch = {
    name: '',
    address: '',
    phone: '',
    agencyId: ''
  };

  onMount(async () => {
    await fetchBranches();
  });

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

  async function createBranch() {
    if (!newBranch.name || !newBranch.agencyId) {
      alert('Branch name and agency are required');
      return;
    }
    
    creating = true;
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
      newBranch = { name: '', address: '', phone: '', agencyId: '' };
      await fetchBranches();
    } catch (e) {
      alert(e instanceof Error ? e.message : 'Failed to create branch');
    } finally {
      creating = false;
    }
  }
</script>

<div class="page">
  <TopBar showSearch={false} />

  <PageHeader title="Branches" subtitle="Manage physical branch locations">
    <button slot="actions" type="button" class={showCreateForm ? 'btn-outline' : 'btn-primary'} on:click={() => (showCreateForm = !showCreateForm)}>
      {showCreateForm ? 'Cancel' : '+ New Branch'}
    </button>
  </PageHeader>

  {#if showCreateForm}
    <Card>
      <form on:submit|preventDefault={createBranch} class="space-y-4">
        <h3 class="text-base font-semibold text-ink-primary">Create New Branch</h3>
        <div class="grid gap-4 md:grid-cols-2">
          <div>
            <label for="name" class="mb-1 block text-sm font-medium text-ink-secondary">Branch Name *</label>
            <input
              id="name"
              type="text"
              bind:value={newBranch.name}
              placeholder="e.g., Accra Central"
              required
              class="input w-full"
            />
          </div>
          <div>
            <label for="agencyId" class="mb-1 block text-sm font-medium text-ink-secondary">Agency ID *</label>
            <input
              id="agencyId"
              type="text"
              bind:value={newBranch.agencyId}
              placeholder="AGY-XXXXXXXX"
              required
              class="input w-full"
            />
          </div>
          <div>
            <label for="address" class="mb-1 block text-sm font-medium text-ink-secondary">Address</label>
            <input
              id="address"
              type="text"
              bind:value={newBranch.address}
              placeholder="Street address"
              class="input w-full"
            />
          </div>
          <div>
            <label for="phone" class="mb-1 block text-sm font-medium text-ink-secondary">Phone</label>
            <input
              id="phone"
              type="tel"
              bind:value={newBranch.phone}
              placeholder="+233 XX XXX XXXX"
              class="input w-full"
            />
          </div>
        </div>
        <button type="submit" class="btn-primary" disabled={creating}>
          {creating ? 'Creating...' : 'Create Branch'}
        </button>
      </form>
    </Card>
  {/if}

  {#if loading}
    <div class="flex items-center justify-center py-12">
      <div class="h-8 w-8 animate-spin rounded-full border-2 border-emerald border-t-transparent"></div>
    </div>
  {:else if error}
    <Card>
      <div class="rounded-lg border border-crimson-200/30 bg-crimson-200/10 px-4 py-3 text-sm text-crimson">
        {error}
      </div>
    </Card>
  {:else if branches.length === 0}
    <Card>
      <div class="flex flex-col items-center justify-center py-12 text-center">
        <svg class="mb-3 h-12 w-12 text-ink-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
        </svg>
        <p class="text-sm font-medium text-ink-primary">No branches yet</p>
        <p class="mt-1 text-xs text-ink-muted">Create your first branch to get started</p>
      </div>
    </Card>
  {:else}
    <div class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      {#each branches as branch (branch.id)}
        <Card>
          <div class="flex items-start justify-between">
            <div class="flex-1">
              <div class="flex items-center gap-2">
                <h3 class="text-base font-semibold text-ink-primary">{branch.name}</h3>
                {#if branch.isActive}
                  <Badge variant="active">Active</Badge>
                {:else}
                  <Badge variant="locked">Inactive</Badge>
                {/if}
              </div>
              {#if branch.agencyName}
                <p class="mt-1 text-xs text-ink-muted">Agency: {branch.agencyName}</p>
              {/if}
              <div class="mt-3 space-y-1 text-sm">
                {#if branch.address}
                  <p class="text-ink-secondary">
                    <span class="font-medium">Address:</span> {branch.address}
                  </p>
                {/if}
                {#if branch.phone}
                  <p class="text-ink-secondary">
                    <span class="font-medium">Phone:</span> {branch.phone}
                  </p>
                {/if}
                <p class="text-ink-secondary">
                  <span class="font-medium">Agents:</span> {branch.agentCount}
                </p>
              </div>
            </div>
          </div>
        </Card>
      {/each}
    </div>
  {/if}
</div>
