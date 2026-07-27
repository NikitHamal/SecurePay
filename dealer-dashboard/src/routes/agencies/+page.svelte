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
  let formError = '';
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

  function toggleForm() {
    showCreateForm = !showCreateForm;
    formError = '';
    if (!showCreateForm) {
      newAgency = { name: '', phone: '', region: '' };
    }
  }

  async function createAgency() {
    if (!newAgency.name.trim()) {
      formError = 'Agency name is required.';
      return;
    }

    creating = true;
    formError = '';
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
      formError = e instanceof Error ? e.message : 'Failed to create agency';
    } finally {
      creating = false;
    }
  }
</script>

<svelte:head>
  <title>Agencies · Touch Base</title>
</svelte:head>

<div class="page">
  <TopBar showSearch={false} />

  <PageHeader title="Agencies" subtitle="Manage DSL agencies and regional leaders">
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
        {showCreateForm ? 'Close Form' : 'New Agency'}
      </button>
    </svelte:fragment>
  </PageHeader>

  {#if showCreateForm}
    <div class="mb-6 rounded-xl border border-edge bg-surface-200 p-5 sm:p-6">
      <form on:submit|preventDefault={createAgency} class="space-y-5">
        <div class="flex items-center justify-between border-b border-edge/60 pb-3">
          <div>
            <h3 class="text-base font-semibold text-ink-primary">Create New Agency</h3>
            <p class="text-xs text-ink-muted">Add a regional DSL agency to manage branches and agents</p>
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

        <div class="grid gap-4 sm:grid-cols-3">
          <div>
            <label for="agency-name" class="mb-1.5 block text-xs font-medium text-ink-secondary">
              Agency Name <span class="text-crimson">*</span>
            </label>
            <input
              id="agency-name"
              type="text"
              bind:value={newAgency.name}
              placeholder="e.g., Greater Accra DSL"
              required
              class="input w-full text-xs"
            />
          </div>
          <div>
            <label for="agency-region" class="mb-1.5 block text-xs font-medium text-ink-secondary">
              Region
            </label>
            <input
              id="agency-region"
              type="text"
              bind:value={newAgency.region}
              placeholder="e.g., Greater Accra"
              class="input w-full text-xs"
            />
          </div>
          <div>
            <label for="agency-phone" class="mb-1.5 block text-xs font-medium text-ink-secondary">
              Phone Number
            </label>
            <input
              id="agency-phone"
              type="tel"
              bind:value={newAgency.phone}
              placeholder="+233 XX XXX XXXX"
              class="input w-full text-xs"
            />
          </div>
        </div>

        <div class="flex items-center justify-end gap-3 border-t border-edge/60 pt-4">
          <button type="button" class="btn-ghost text-xs" on:click={toggleForm}>
            Cancel
          </button>
          <button type="submit" class="btn-primary text-xs" disabled={creating}>
            {creating ? 'Creating...' : 'Create Agency'}
          </button>
        </div>
      </form>
    </div>
  {/if}

  {#if loading}
    <div class="flex items-center justify-center py-16">
      <div class="h-8 w-8 animate-spin rounded-full border-2 border-emerald border-t-transparent"></div>
    </div>
  {:else if error}
    <div class="rounded-xl border border-crimson/20 bg-crimson/10 p-4 text-xs text-crimson">
      {error}
    </div>
  {:else if agencies.length === 0}
    <Card>
      <div class="flex flex-col items-center justify-center py-14 text-center">
        <div class="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gold-400/10 text-gold-400">
          <svg class="h-7 w-7" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
            <path stroke-linecap="round" stroke-linejoin="round" d="M8 14v3m4-3v3m4-3v3M3 21h18M3 10h18M3 7l9-4 9 4M4 10h16v11H4V10z" />
          </svg>
        </div>
        <p class="text-base font-semibold text-ink-primary">No agencies yet</p>
        <p class="mt-1 max-w-sm text-xs text-ink-muted">
          Create your first agency to organize branches and regional teams.
        </p>
        <button class="btn-primary mt-5" on:click={toggleForm}>
          + Create Agency
        </button>
      </div>
    </Card>
  {:else}
    <div class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      {#each agencies as agency (agency.id)}
        <Card>
          <div class="flex flex-col h-full justify-between gap-4">
            <div>
              <div class="flex items-start justify-between gap-2">
                <div class="flex items-center gap-2.5">
                  <div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-surface-100 text-gold-400 border border-edge">
                    <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M8 14v3m4-3v3m4-3v3M3 21h18M3 10h18M3 7l9-4 9 4M4 10h16v11H4V10z" />
                    </svg>
                  </div>
                  <div>
                    <h3 class="text-base font-semibold text-ink-primary leading-tight">{agency.name}</h3>
                    {#if agency.ownerName}
                      <p class="text-2xs text-ink-muted mt-0.5">Owner: <span class="text-ink-secondary font-medium">{agency.ownerName}</span></p>
                    {/if}
                  </div>
                </div>
                {#if agency.isActive}
                  <Badge variant="active">Active</Badge>
                {:else}
                  <Badge variant="locked">Inactive</Badge>
                {/if}
              </div>

              <div class="mt-4 space-y-2.5 border-t border-edge/60 pt-3 text-xs">
                {#if agency.region}
                  <div class="flex items-center gap-2 text-ink-secondary">
                    <svg class="h-4 w-4 shrink-0 text-ink-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                      <path stroke-linecap="round" stroke-linejoin="round" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
                    </svg>
                    <span>Region: <strong class="font-medium text-ink-primary">{agency.region}</strong></span>
                  </div>
                {/if}

                {#if agency.phone}
                  <div class="flex items-center gap-2 text-ink-secondary">
                    <svg class="h-4 w-4 shrink-0 text-ink-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z" />
                    </svg>
                    <span>{agency.phone}</span>
                  </div>
                {/if}
              </div>
            </div>

            <div class="grid grid-cols-2 gap-2 border-t border-edge/60 pt-3 text-xs">
              <div class="rounded-lg bg-surface-100 p-2 text-center border border-edge">
                <span class="block text-2xs text-ink-muted">Branches</span>
                <span class="text-sm font-semibold text-ink-primary">{agency.branchCount}</span>
              </div>
              <div class="rounded-lg bg-surface-100 p-2 text-center border border-edge">
                <span class="block text-2xs text-ink-muted">Agents</span>
                <span class="text-sm font-semibold text-ink-primary">{agency.agentCount}</span>
              </div>
            </div>
          </div>
        </Card>
      {/each}
    </div>
  {/if}
</div>
