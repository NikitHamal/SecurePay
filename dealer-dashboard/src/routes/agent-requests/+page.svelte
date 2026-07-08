<script lang="ts">
  import { onMount } from 'svelte';
  import { apiClient } from '$lib/api/client';
  import Card from '$lib/components/ui/Card.svelte';
  import Badge from '$lib/components/ui/Badge.svelte';
  import PageHeader from '$lib/components/ui/PageHeader.svelte';

  interface AgentRequest {
    id: string;
    fullName: string;
    email: string;
    phone: string;
    status: string;
    requestedBranchId?: string;
    createdAt: number;
  }

  let requests: AgentRequest[] = [];
  let loading = true;
  let error = '';
  let processing = '';

  onMount(async () => {
    await fetchRequests();
  });

  async function fetchRequests() {
    loading = true;
    error = '';
    try {
      const res = await apiClient('/api/agent-requests?status=PENDING');
      if (!res.ok) throw new Error('Failed to fetch requests');
      requests = await res.json();
    } catch (e) {
      error = e instanceof Error ? e.message : 'Unknown error';
    } finally {
      loading = false;
    }
  }

  async function approveRequest(requestId: string) {
    processing = requestId;
    try {
      const res = await apiClient('/api/auth/approve-agent', {
        method: 'POST',
        body: JSON.stringify({ requestId })
      });
      if (!res.ok) {
        const data = await res.json();
        throw new Error(data.error || 'Failed to approve');
      }
      await fetchRequests();
    } catch (e) {
      alert(e instanceof Error ? e.message : 'Failed to approve agent');
    } finally {
      processing = '';
    }
  }

  async function rejectRequest(requestId: string) {
    if (!confirm('Are you sure you want to reject this agent request?')) return;
    processing = requestId;
    try {
      const res = await apiClient('/api/auth/reject-agent', {
        method: 'POST',
        body: JSON.stringify({ requestId })
      });
      if (!res.ok) {
        const data = await res.json();
        throw new Error(data.error || 'Failed to reject');
      }
      await fetchRequests();
    } catch (e) {
      alert(e instanceof Error ? e.message : 'Failed to reject agent');
    } finally {
      processing = '';
    }
  }

  function formatDate(timestamp: number): string {
    return new Date(timestamp).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }
</script>

<PageHeader title="Agent Requests" subtitle="Review and approve agent registration requests" />

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
{:else if requests.length === 0}
  <Card>
    <div class="flex flex-col items-center justify-center py-12 text-center">
      <svg class="mb-3 h-12 w-12 text-ink-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
        <path stroke-linecap="round" stroke-linejoin="round" d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z" />
      </svg>
      <p class="text-sm font-medium text-ink-primary">No pending requests</p>
      <p class="mt-1 text-xs text-ink-muted">All agent requests have been processed</p>
    </div>
  </Card>
{:else}
  <div class="grid gap-4">
    {#each requests as request (request.id)}
      <Card>
        <div class="flex items-start justify-between gap-4">
          <div class="flex-1">
            <div class="flex items-center gap-2">
              <h3 class="text-base font-semibold text-ink-primary">{request.fullName}</h3>
              <Badge variant="warning">Pending</Badge>
            </div>
            <div class="mt-2 space-y-1 text-sm">
              <p class="text-ink-secondary">
                <span class="font-medium">Email:</span> {request.email}
              </p>
              <p class="text-ink-secondary">
                <span class="font-medium">Phone:</span> {request.phone}
              </p>
              {#if request.requestedBranchId}
                <p class="text-ink-secondary">
                  <span class="font-medium">Requested Branch:</span> {request.requestedBranchId}
                </p>
              {/if}
              <p class="text-xs text-ink-muted">
                Submitted {formatDate(request.createdAt)}
              </p>
            </div>
          </div>
          <div class="flex gap-2">
            <button
              type="button"
              class="btn-primary"
              disabled={processing === request.id}
              on:click={() => approveRequest(request.id)}
            >
              {#if processing === request.id}
                <span class="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></span>
              {:else}
                Approve
              {/if}
            </button>
            <button
              type="button"
              class="btn-ghost border border-crimson-200/30 text-crimson hover:bg-crimson-200/10"
              disabled={processing === request.id}
              on:click={() => rejectRequest(request.id)}
            >
              Reject
            </button>
          </div>
        </div>
      </Card>
    {/each}
  </div>
{/if}
