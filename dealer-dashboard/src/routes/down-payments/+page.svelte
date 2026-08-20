<script lang="ts">
  import PageHeader from '$lib/components/ui/PageHeader.svelte';
  import TopBar from '$lib/components/layout/TopBar.svelte';
  import { onMount } from 'svelte';
  import { dealer } from '$lib/stores/auth';
  import { listDownPayments, confirmDownPayment, rejectDownPayment, type DownPaymentSubmission } from '$lib/api/client';
  import { formatCurrency } from '$lib/utils/format';

  let subs: DownPaymentSubmission[] = [];
  let loading = false;
  let error: string | null = null;
  let filter: 'all'|'pending'|'confirmed'|'rejected' = 'pending';
  let busyId: string | null = null;
  $: isAdmin = $dealer ? $dealer.role !== 'AGENT' : false;

  onMount(() => load());

  async function load() {
    loading = true; error = null;
    try {
      const status = filter === 'all' ? undefined : filter;
      subs = await listDownPayments(status);
    } catch (e) { error = e instanceof Error ? e.message : 'Failed to load'; }
    finally { loading = false; }
  }
  async function handleConfirm(id: string) {
    if (!confirm('Confirm this cash down payment? The customer balance will be credited and device can be provisioned.')) return;
    busyId = id; error = null;
    try { await confirmDownPayment(id); await load(); } catch (e) { error = e instanceof Error ? e.message : 'Confirm failed'; }
    finally { busyId = null; }
  }
  async function handleReject(id: string) {
    const note = prompt('Reason for rejection?');
    if (note === null) return;
    busyId = id; error = null;
    try { await rejectDownPayment(id, note); await load(); } catch (e) { error = e instanceof Error ? e.message : 'Reject failed'; }
    finally { busyId = null; }
  }
</script>

<svelte:head><title>Down Payments · Touch Base</title></svelte:head>
<div class="page">
  <TopBar searchPlaceholder="Search down payments…" />
  <PageHeader eyebrow="Finance" title="Down Payments" subtitle={isAdmin ? 'Agent cash submissions — confirm to credit the customer and unlock provisioning.' : 'Your down payment submissions — pending admin confirmation.'} />
  <div class="flex gap-2 mb-4">
    {#each ['pending','confirmed','rejected','all'] as f}
      <button class="btn-outline !py-1 !px-3 text-xs {filter===f ? 'bg-surface-200 text-ink-primary' : ''}" on:click={() => { filter = f as any; load(); }}>{f}</button>
    {/each}
    <button class="btn-outline !py-1 !px-3 text-xs ml-auto" on:click={load} disabled={loading}>{loading ? 'Loading…' : 'Refresh'}</button>
  </div>
  {#if error}<div class="mb-3 rounded-lg border border-crimson/20 bg-crimson/10 px-3 py-2 text-sm text-crimson">{error}</div>{/if}
  <div class="card overflow-hidden">
    <div class="overflow-x-auto">
      <table class="data-table min-w-[800px]">
        <thead><tr><th>Customer</th><th>IMEI / Model</th><th>Agent</th><th>Amount</th><th>Status</th><th>Submitted</th><th class="text-right">Actions</th></tr></thead>
        <tbody>
        {#if loading && subs.length===0}<tr><td colspan="7" class="py-10 text-center text-ink-muted">Loading…</td></tr>
        {:else if subs.length===0}<tr><td colspan="7" class="py-12 text-center text-sm text-ink-muted">No {filter !== 'all' ? filter : ''} submissions</td></tr>
        {:else}
          {#each subs as s (s.id)}
          <tr class="hover:bg-hover">
            <td class="text-sm font-medium text-ink-primary">{s.customerName}<span class="block text-[11px] font-mono text-ink-muted">{s.accountId.slice(0,10)}…</span></td>
            <td class="text-xs"><span class="font-mono text-ink-secondary">{s.imei}</span><span class="block text-ink-muted">{s.model}</span></td>
            <td class="text-xs">{s.agentName ?? s.agentId.slice(0,8)}</td>
            <td class="text-sm font-bold tabular-nums text-ink-primary">{formatCurrency(s.amount)}</td>
            <td><span class={s.status==='pending' ? 'chip-amber' : s.status==='confirmed' ? 'chip-emerald' : 'chip-crimson'}>{s.status}</span></td>
            <td class="text-xs text-ink-muted">{new Date(s.submittedAt).toLocaleString()}</td>
            <td class="text-right">
              {#if isAdmin && s.status==='pending'}
                <div class="flex justify-end gap-1">
                  <button class="btn-primary !py-1 !px-2.5 text-xs" disabled={busyId===s.id} on:click={()=>handleConfirm(s.id)}>{busyId===s.id ? '…' : 'Confirm'}</button>
                  <button class="btn-outline !py-1 !px-2.5 text-xs text-crimson" disabled={busyId===s.id} on:click={()=>handleReject(s.id)}>Reject</button>
                </div>
              {:else}
                <span class="text-xs text-ink-muted">—</span>
              {/if}
            </td>
          </tr>
          {/each}
        {/if}
        </tbody>
      </table>
    </div>
  </div>
  <p class="mt-3 text-xs text-ink-muted">{#if isAdmin}Admin confirms after verifying cash hand-over. Until confirmed, provisioning is blocked.{:else}Your device cannot be provisioned until admin confirms your cash payment. {/if}</p>
</div>
