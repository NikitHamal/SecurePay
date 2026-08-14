<script lang="ts">
  import PageHeader from '$lib/components/ui/PageHeader.svelte';
  import TopBar from '$lib/components/layout/TopBar.svelte';
  import { listActivity, type ActivityRow } from '$lib/api/client';
  import { onMount, onDestroy } from 'svelte';

  const FILTERS: { label: string; value: string }[] = [
    { label: 'All activity', value: '' },
    { label: 'Logins', value: 'LOGIN' },
    { label: 'Customers', value: 'CUSTOMER_CREATED' },
    { label: 'Payments', value: 'PAYMENT_RECORDED' },
    { label: 'Devices', value: 'DEVICE_REGISTERED' },
    { label: 'Edits & deletions', value: 'EDITS' },
    { label: 'Applications', value: 'APPLICATIONS' }
  ];

  let rows: ActivityRow[] = [];
  let loading = true;
  let error: string | null = null;
  let filter = '';
  let searchQuery = '';
  let intervalId: ReturnType<typeof setInterval> | undefined;

  async function load(silent = false) {
    if (!silent) loading = true;
    error = null;
    try {
      const serverAction = filter === 'EDITS' || filter === 'APPLICATIONS' ? '' : filter;
      const data = await listActivity({ limit: 300, action: serverAction || undefined, q: searchQuery || undefined });
      rows = filter === 'EDITS'
        ? data.filter((r) => r.action === 'CUSTOMER_EDITED' || r.action === 'CUSTOMER_DELETED')
        : filter === 'APPLICATIONS'
          ? data.filter((r) => r.action === 'AGENT_APPROVED' || r.action === 'AGENT_REJECTED')
          : data;
    } catch (err) {
      error = err instanceof Error ? err.message : 'Failed to load activity';
    } finally {
      if (!silent) loading = false;
    }
  }

  onMount(() => {
    load();
    intervalId = setInterval(() => load(true), 15000);
  });

  onDestroy(() => {
    if (intervalId) clearInterval(intervalId);
  });

  function setFilter(value: string) {
    filter = value;
    load();
  }

  let searchTimer: ReturnType<typeof setTimeout> | undefined;
  function onSearchInput() {
    if (searchTimer) clearTimeout(searchTimer);
    searchTimer = setTimeout(() => load(), 300);
  }

  function formatTime(ts: number): string {
    const ms = ts < 1e11 ? ts * 1000 : ts;
    return new Date(ms).toLocaleString('en-GB', {
      day: '2-digit',
      month: 'long',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    });
  }

  function actionLabel(action: string): string {
    switch (action) {
      case 'LOGIN': return 'Signed in';
      case 'CUSTOMER_CREATED': return 'Customer created';
      case 'CUSTOMER_EDITED': return 'Customer edited';
      case 'CUSTOMER_DELETED': return 'Customer deleted';
      case 'PAYMENT_RECORDED': return 'Payment recorded';
      case 'DEVICE_REGISTERED': return 'Device registered';
      case 'AGENT_APPROVED': return 'Application approved';
      case 'AGENT_REJECTED': return 'Application rejected';
      default: return action;
    }
  }

  function actionTone(action: string): string {
    switch (action) {
      case 'CUSTOMER_CREATED': return 'bg-emerald/15 text-emerald';
      case 'PAYMENT_RECORDED': return 'bg-amber/15 text-amber';
      case 'DEVICE_REGISTERED': return 'bg-sky/15 text-sky';
      case 'LOGIN': return 'bg-surface-100 text-ink-secondary';
      case 'AGENT_APPROVED': return 'bg-emerald/15 text-emerald';
      case 'AGENT_REJECTED':
      case 'CUSTOMER_DELETED': return 'bg-crimson/15 text-crimson';
      default: return 'bg-surface-100 text-ink-secondary';
    }
  }

  function mapsUrl(lat: number | null, lng: number | null): string | null {
    if (lat == null || lng == null) return null;
    return `https://www.google.com/maps?q=${lat},${lng}`;
  }
</script>

<svelte:head>
  <title>Agent Activity · Touch Base</title>
</svelte:head>

<div class="page">
  <TopBar showSearch={false} />

  <PageHeader
    eyebrow="Accountability"
    title="Agent Activity"
    subtitle="Every key action your team performs — logins, customer registrations, payments, device registrations, edits and application decisions — with time, branch and GPS context when available."
  >
    <div slot="actions" class="flex items-center gap-2">
      <button type="button" class="btn-outline" on:click={() => load()} disabled={loading}>
        {loading ? 'Refreshing…' : 'Refresh'}
      </button>
    </div>
  </PageHeader>

  <div class="card p-4 mb-6">
    <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
      <div class="flex flex-wrap items-center gap-2">
        {#each FILTERS as item (item.value)}
          <button
            type="button"
            class="rounded-md px-3 py-1.5 text-xs font-medium transition-colors
                   {filter === item.value ? 'bg-amber/15 text-amber' : 'bg-surface-100 text-ink-secondary hover:text-ink-primary'}"
            on:click={() => setFilter(item.value)}
          >
            {item.label}
          </button>
        {/each}
      </div>
      <input
        type="text"
        placeholder="Search agent, customer, IMEI…"
        bind:value={searchQuery}
        on:input={onSearchInput}
        class="input max-w-xs py-1 px-3 text-xs"
      />
    </div>
  </div>

  <div class="card overflow-hidden">
    {#if loading && rows.length === 0}
      <div class="px-5 py-16 text-center text-sm text-ink-muted">Loading activity…</div>
    {:else if error}
      <div class="px-5 py-16 text-center text-sm text-crimson">{error}</div>
    {:else if rows.length === 0}
      <div class="px-5 py-16 text-center">
        <p class="text-sm font-medium text-ink-primary">No activity yet</p>
        <p class="mt-1 text-xs text-ink-muted">Actions performed by agents will appear here as they happen.</p>
      </div>
    {:else}
      <!-- Header row (desktop) -->
      <div class="hidden md:grid md:grid-cols-[1.1fr_0.9fr_1.6fr_1fr_0.8fr] gap-3 border-b border-edge bg-surface-50 px-5 py-3 text-[11px] font-semibold uppercase tracking-wider text-ink-muted">
        <span>Agent</span>
        <span>Action</span>
        <span>Details</span>
        <span>Customer</span>
        <span>Time · Location</span>
      </div>
      <ul class="divide-y divide-edge">
        {#each rows as row (row.id)}
          <li class="grid gap-2 px-5 py-4 md:grid-cols-[1.1fr_0.9fr_1.6fr_1fr_0.8fr] md:gap-3 md:items-start">
            <div class="min-w-0">
              <p class="truncate text-sm font-semibold text-ink-primary">{row.actorName}</p>
              <p class="text-[11px] uppercase tracking-wide text-ink-muted">{row.actorRole.replace(/_/g, ' ')}</p>
            </div>
            <div>
              <span class="inline-flex rounded-md px-2 py-1 text-[11px] font-semibold {actionTone(row.action)}">
                {actionLabel(row.action)}
              </span>
            </div>
            <div class="min-w-0">
              <p class="text-sm text-ink-primary break-words">{row.details}</p>
              {#if row.imei}
                <p class="mt-0.5 font-mono text-[11px] text-ink-muted">IMEI {row.imei}</p>
              {/if}
            </div>
            <div class="min-w-0">
              {#if row.customerName}
                <p class="truncate text-sm text-ink-primary">{row.customerName}</p>
              {:else}
                <p class="text-sm text-ink-muted">—</p>
              {/if}
            </div>
            <div>
              <p class="text-xs text-ink-secondary">{formatTime(row.createdAt)}</p>
              <p class="mt-0.5 flex flex-wrap items-center gap-1 text-[11px] text-ink-muted">
                <span>{row.branchName || 'No branch'}</span>
                {#if mapsUrl(row.latitude, row.longitude)}
                  ·
                  <a
                    href={mapsUrl(row.latitude, row.longitude)}
                    target="_blank"
                    rel="noreferrer"
                    class="inline-flex items-center gap-0.5 text-sky hover:underline"
                  >
                    <svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                      <path d="M12 21s-7-5.1-7-11a7 7 0 1114 0c0 5.9-7 11-7 11z" />
                      <circle cx="12" cy="10" r="2.6" />
                    </svg>
                    GPS
                  </a>
                {/if}
              </p>
            </div>
          </li>
        {/each}
      </ul>
    {/if}
  </div>
</div>
