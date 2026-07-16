<script lang="ts">
  import { onMount } from 'svelte';
  import { apiClient } from '$lib/api/client';
  import Card from '$lib/components/ui/Card.svelte';
  import Badge from '$lib/components/ui/Badge.svelte';
  import PageHeader from '$lib/components/ui/PageHeader.svelte';
  import TopBar from '$lib/components/layout/TopBar.svelte';

  interface Agency {
    id: string;
    name: string;
    ownerId: string;
    ownerName?: string;
    phone?: string;
    region?: string;
    branchCount: number;
    agentCount: number;
    isActive: boolean;
    createdAt: number;
  }

  let agencies: Agency[] = [];
  let loading = true;
  let error = '';
  let showCreateForm = false;
  let creating = false;
  
  let newAgency = {
    name: '',
    phone: '',
    region: ''
  };

  onMount(async () => {
    await fetchAgencies();
  });

  async function fetchAgencies() {
    loading = true;
    error = '';
    try {
      const res = await apiClient('/api/agencies');
      if (!res.ok) throw new Error('Failed to fetch agencies');
      agencies = await res.json();
    } catch (e) {
      error = e instanceof Error ? e.message : 'Unknown error';
    } finally {
      loading = false;
    }
  }

  async function createAgency() {
    if (!newAgency.name) {
      alert('Agency name is required');
      return;
    }
    
    creating = true;
    try {
      const res = await apiClient('/api/agencies', {
        method: 'POST',
        body: JSON.stringify(newAgency)
      });
      if (!res.ok) {
        const data = await res.json();
        throw new Error(data.error || 'Failed to create agency');
      }
      showCreateForm = false;
      newAgency = { name: '', phone: '', region: '' };
      await fetchAgencies();
    } catch (e) {
      alert(e instanceof Error ? e.message : 'Failed to create agency');
    } finally {
      creating = false;
    }
  }
</script>

<div class="page">
  <TopBar showSearch={false} />

  <PageHeader title="Agencies" subtitle="Manage DSL agencies and regional leaders">
    <button slot="actions" type="button" class={showCreateForm ? 'btn-outline' : 'btn-primary'} on:click={() => (showCreateForm = !showCreateForm)}>
      {showCreateForm ? 'Cancel' : '+ New Agency'}
    </button>
  </PageHeader>

  {#if showCreateForm}
    <Card>
      <form on:submit|preventDefault={createAgency} class="space-y-4">
        <h3 class="text-base font-semibold text-ink-primary">Create New Agency</h3>
        <div class="grid gap-4 md:grid-cols-3">
          <div>
            <label for="name" class="mb-1 block text-sm font-medium text-ink-secondary">Agency Name *</label>
            <input
              id="name"
              type="text"
              bind:value={newAgency.name}
              placeholder="e.g., Greater Accra DSL"
              required
              class="input w-full"
            />
          </div>
          <div>
            <label for="region" class="mb-1 block text-sm font-medium text-ink-secondary">Region</label>
            <input
              id="region"
              type="text"
              bind:value={newAgency.region}
              placeholder="e.g., Greater Accra"
              class="input w-full"
            />
          </div>
          <div>
            <label for="phone" class="mb-1 block text-sm font-medium text-ink-secondary">Phone</label>
            <input
              id="phone"
              type="tel"
              bind:value={newAgency.phone}
              placeholder="+233 XX XXX XXXX"
              class="input w-full"
            />
          </div>
        </div>
        <button type="submit" class="btn-primary" disabled={creating}>
          {creating ? 'Creating...' : 'Create Agency'}
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
  {:else if agencies.length === 0}
    <Card>
      <div class="flex flex-col items-center justify-center py-12 text-center">
        <svg class="mb-3 h-12 w-12 text-ink-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M8 14v3m4-3v3m4-3v3M3 21h18M3 10h18M3 7l9-4 9 4M4 10h16v11H4V10z" />
        </svg>
        <p class="text-sm font-medium text-ink-primary">No agencies yet</p>
        <p class="mt-1 text-xs text-ink-muted">Create your first agency to organize branches</p>
      </div>
    </Card>
  {:else}
    <div class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      {#each agencies as agency (agency.id)}
        <Card>
          <div class="flex items-start justify-between">
            <div class="flex-1">
              <div class="flex items-center gap-2">
                <h3 class="text-base font-semibold text-ink-primary">{agency.name}</h3>
                {#if agency.isActive}
                  <Badge variant="active">Active</Badge>
                {:else}
                  <Badge variant="locked">Inactive</Badge>
                {/if}
              </div>
              {#if agency.ownerName}
                <p class="mt-1 text-xs text-ink-muted">Owner: {agency.ownerName}</p>
              {/if}
              <div class="mt-3 space-y-1 text-sm">
                {#if agency.region}
                  <p class="text-ink-secondary">
                    <span class="font-medium">Region:</span> {agency.region}
                  </p>
                {/if}
                {#if agency.phone}
                  <p class="text-ink-secondary">
                    <span class="font-medium">Phone:</span> {agency.phone}
                  </p>
                {/if}
                <div class="mt-2 grid grid-cols-2 gap-2">
                  <div>
                    <p class="text-xs text-ink-muted">Branches</p>
                    <p class="text-sm font-semibold text-ink-primary">{agency.branchCount}</p>
                  </div>
                  <div>
                    <p class="text-xs text-ink-muted">Agents</p>
                    <p class="text-sm font-semibold text-ink-primary">{agency.agentCount}</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </Card>
      {/each}
    </div>
  {/if}
</div>
