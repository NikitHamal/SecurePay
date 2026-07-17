<script lang="ts">
  import { onMount } from 'svelte';
  import { apiClient } from '$lib/api/client';
  import PageHeader from '$lib/components/ui/PageHeader.svelte';
  import TopBar from '$lib/components/layout/TopBar.svelte';

  type ReqStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

  interface AgentRequest {
    id: string;
    fullName: string;
    email: string;
    phone: string;
    status: ReqStatus;
    requestedBranchId?: string;
    branchName?: string;
    createdAt: number;
  }

  let requests: AgentRequest[] = [];
  let loading = true;
  let error = '';
  let processing = '';
  let activeTab: ReqStatus | 'ALL' = 'PENDING';
  let pendingCount = 0;
  let approvedCount = 0;
  let rejectedCount = 0;

  onMount(fetchRequests);

  async function fetchRequests() {
    loading = true;
    error = '';
    try {
      const status = activeTab === 'ALL' ? '' : `?status=${activeTab}`;
      const res = await apiClient(`/api/agent-requests${status}`);
      if (!res.ok) throw new Error('Failed to fetch requests');
      requests = await res.json();
      // Also fetch counts for the tabs
      const [p, a, r] = await Promise.all([
        apiClient('/api/agent-requests?status=PENDING').then(r => r.json()),
        apiClient('/api/agent-requests?status=APPROVED').then(r => r.json()),
        apiClient('/api/agent-requests?status=REJECTED').then(r => r.json()),
      ]);
      pendingCount = p.length;
      approvedCount = a.length;
      rejectedCount = r.length;
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
    if (!confirm('Reject this agent request? This cannot be undone.')) return;
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

  function formatDate(ts: number): string {
    return new Date(ts).toLocaleDateString('en-GB', {
      year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
    });
  }

  function initials(name: string): string {
    return name.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase();
  }

  function statusBadgeClass(status: ReqStatus): string {
    if (status === 'PENDING') return 'bg-amber/10 text-amber border border-amber/20';
    if (status === 'APPROVED') return 'bg-emerald/10 text-emerald border border-emerald/20';
    return 'bg-crimson/10 text-crimson border border-crimson/20';
  }

  const tabs: { id: ReqStatus | 'ALL'; label: string; count: () => number }[] = [
    { id: 'PENDING', label: 'Pending', count: () => pendingCount },
    { id: 'APPROVED', label: 'Approved', count: () => approvedCount },
    { id: 'REJECTED', label: 'Rejected', count: () => rejectedCount },
    { id: 'ALL', label: 'All', count: () => pendingCount + approvedCount + rejectedCount },
  ];

  $: if (activeTab) { /* no-op reactive trigger */ }
</script>

<svelte:head>
  <title>Agent Requests · SecurePay</title>
</svelte:head>

<div class="page">
  <TopBar showSearch={false} />

  <PageHeader title="Agent Requests" subtitle="Review and manage agent sign-ups">
    <svelte:fragment slot="actions">
      <button class="btn-outline" on:click={fetchRequests} disabled={loading}>
        <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M4 4v6h6M20 20v-6h-6M4 10a8 8 0 0114-3m2 7a8 8 0 01-14 3" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        Refresh
      </button>
    </svelte:fragment>
  </PageHeader>

  <!-- Tabs -->
  <div class="card mb-4 overflow-hidden">
    <div class="flex border-b border-edge overflow-x-auto">
      {#each tabs as tab (tab.id)}
        <button
          class="relative px-4 py-3 text-sm font-medium transition-colors whitespace-nowrap
                 {activeTab === tab.id ? 'text-emerald' : 'text-ink-secondary hover:text-ink-primary'}"
          on:click={() => { activeTab = tab.id; fetchRequests(); }}
        >
          {tab.label}
          <span class="ml-2 inline-flex items-center justify-center rounded-md bg-surface-100 px-1.5 py-0.5 text-xs font-medium text-ink-muted tabular-nums">
            {tab.count()}
          </span>
          {#if activeTab === tab.id}
            <span class="absolute inset-x-0 bottom-0 h-0.5 bg-emerald" aria-hidden="true"></span>
          {/if}
        </button>
      {/each}
    </div>

    {#if loading}
      <div class="flex items-center justify-center py-12">
        <div class="h-6 w-6 animate-spin rounded-full border-2 border-emerald border-t-transparent"></div>
      </div>
    {:else if error}
      <div class="m-4 rounded-lg border border-crimson/20 bg-crimson/10 px-4 py-3 text-sm text-crimson">{error}</div>
    {:else if requests.length === 0}
      <div class="flex flex-col items-center justify-center py-16 text-center">
        <svg class="mb-3 h-12 w-12 text-ink-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
        </svg>
        <p class="text-sm font-medium text-ink-primary">No {activeTab === 'ALL' ? '' : activeTab.toLowerCase()} requests</p>
        <p class="mt-1 text-xs text-ink-muted">
          {activeTab === 'PENDING' ? 'All caught up — no pending sign-ups.' : 'Nothing to show here yet.'}
        </p>
      </div>
    {:else}
      <div class="overflow-x-auto">
        <table class="data-table min-w-[720px]">
          <thead>
            <tr>
              <th>Agent</th>
              <th>Contact</th>
              <th>Branch</th>
              <th>Submitted</th>
              <th>Status</th>
              <th class="text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {#each requests as req (req.id)}
              <tr class="transition-colors hover:bg-hover">
                <td>
                  <div class="flex items-center gap-3">
                    <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-xs font-semibold text-white" style="background-color: var(--brand);">
                      {initials(req.fullName || 'A')}
                    </span>
                    <div class="min-w-0">
                      <p class="truncate text-sm font-medium text-ink-primary">{req.fullName}</p>
                    </div>
                  </div>
                </td>
                <td>
                  <p class="text-sm text-ink-primary truncate">{req.email}</p>
                  <p class="text-xs text-ink-muted">{req.phone}</p>
                </td>
                <td class="text-sm text-ink-secondary">{req.branchName || '—'}</td>
                <td class="text-xs text-ink-muted whitespace-nowrap">{formatDate(req.createdAt)}</td>
                <td>
                  <span class="inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium {statusBadgeClass(req.status)}">
                    {req.status.charAt(0) + req.status.slice(1).toLowerCase()}
                  </span>
                </td>
                <td class="text-right">
                  {#if req.status === 'PENDING'}
                    <div class="flex justify-end gap-2">
                      <button
                        type="button"
                        class="btn-primary !py-1.5 !px-3 text-xs"
                        disabled={processing === req.id}
                        on:click={() => approveRequest(req.id)}
                      >
                        {#if processing === req.id}
                          <span class="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent"></span>
                        {:else}Approve{/if}
                      </button>
                      <button
                        type="button"
                        class="btn-outline !py-1.5 !px-3 text-xs text-crimson hover:bg-crimson/10 hover:border-crimson/30"
                        disabled={processing === req.id}
                        on:click={() => rejectRequest(req.id)}
                      >Reject</button>
                    </div>
                  {:else}
                    <span class="text-xs text-ink-muted">—</span>
                  {/if}
                </td>
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
    {/if}
  </div>
</div>
