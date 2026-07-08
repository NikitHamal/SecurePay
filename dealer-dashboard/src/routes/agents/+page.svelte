<script lang="ts">
  import { onMount } from 'svelte';
  import { apiClient } from '$lib/api/client';
  import Card from '$lib/components/ui/Card.svelte';
  import Badge from '$lib/components/ui/Badge.svelte';
  import PageHeader from '$lib/components/ui/PageHeader.svelte';

  interface Agent {
    id: string;
    name: string;
    email: string;
    phone: string;
    branchId?: string;
    agencyId?: string;
    createdAt: number;
    salesCount: number;
    totalRevenue: number;
  }

  let agents: Agent[] = [];
  let loading = true;
  let error = '';

  onMount(async () => {
    await fetchAgents();
  });

  async function fetchAgents() {
    loading = true;
    error = '';
    try {
      // Fetch all customers to get agent stats
      const res = await apiClient('/api/accounts');
      if (!res.ok) throw new Error('Failed to fetch data');
      const customers = await res.json();
      
      // Group by enrolled_by to get agent stats
      const agentMap = new Map<string, Agent>();
      customers.forEach((c: any) => {
        if (c.enrolledBy) {
          if (!agentMap.has(c.enrolledBy)) {
            agentMap.set(c.enrolledBy, {
              id: c.enrolledBy,
              name: 'Agent',
              email: '',
              phone: '',
              createdAt: 0,
              salesCount: 0,
              totalRevenue: 0
            });
          }
          const agent = agentMap.get(c.enrolledBy)!;
          agent.salesCount++;
          agent.totalRevenue += c.amountPaid || 0;
        }
      });
      
      agents = Array.from(agentMap.values()).sort((a, b) => b.salesCount - a.salesCount);
    } catch (e) {
      error = e instanceof Error ? e.message : 'Unknown error';
    } finally {
      loading = false;
    }
  }

  function formatCurrency(amount: number): string {
    return `GH₵${(amount / 100).toFixed(2)}`;
  }
</script>

<PageHeader title="Agents" subtitle="View all agents and their performance" />

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
{:else if agents.length === 0}
  <Card>
    <div class="flex flex-col items-center justify-center py-12 text-center">
      <svg class="mb-3 h-12 w-12 text-ink-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
        <path stroke-linecap="round" stroke-linejoin="round" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
      </svg>
      <p class="text-sm font-medium text-ink-primary">No agents yet</p>
      <p class="mt-1 text-xs text-ink-muted">Agents will appear here once they are approved</p>
    </div>
  </Card>
{:else}
  <div class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
    {#each agents as agent (agent.id)}
      <Card>
        <div class="flex items-start gap-3">
          <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-emerald-300 to-emerald text-white text-sm font-semibold">
            {agent.name.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase()}
          </div>
          <div class="flex-1 min-w-0">
            <h3 class="text-sm font-semibold text-ink-primary truncate">{agent.name}</h3>
            <p class="text-xs text-ink-muted truncate">{agent.email}</p>
            <div class="mt-3 grid grid-cols-2 gap-2">
              <div>
                <p class="text-xs text-ink-muted">Sales</p>
                <p class="text-sm font-semibold text-ink-primary">{agent.salesCount}</p>
              </div>
              <div>
                <p class="text-xs text-ink-muted">Revenue</p>
                <p class="text-sm font-semibold text-emerald">{formatCurrency(agent.totalRevenue)}</p>
              </div>
            </div>
          </div>
        </div>
      </Card>
    {/each}
  </div>
{/if}
