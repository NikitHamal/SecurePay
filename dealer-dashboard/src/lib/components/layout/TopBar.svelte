<script lang="ts">
  import { page } from '$app/stores';
  import { onMount } from 'svelte';
  import { apiClient } from '$lib/api/client';
  import { customers } from '$lib/stores/customers';
  import ThemeToggle from '$lib/components/ui/ThemeToggle.svelte';
  import { formatRelative } from '$lib/utils/format';
  import { sidebarOpen } from '$lib/stores/ui';

  export let searchPlaceholder: string = 'Search customers, IMEI, references…';
  export let showSearch: boolean = true;

  interface ApiNotification {
    id: string;
    type: string;
    title: string;
    message: string;
    isRead: boolean;
    createdAt: number;
  }

  let notifications: ApiNotification[] = [];
  let notificationsOpen = false;

  onMount(async () => {
    await fetchNotifications();
    // Poll every 30 seconds
    const interval = setInterval(fetchNotifications, 30000);
    return () => clearInterval(interval);
  });

  async function fetchNotifications() {
    try {
      const res = await apiClient('/api/notifications');
      if (res.ok) {
        notifications = await res.json();
      }
    } catch (e) {
      // Silent fail for notifications
    }
  }

  $: pathname = $page.url.pathname;
  $: crumbs = pathname === '/' ? [{ label: 'Overview' }] : pathname.split('/').filter(Boolean).map((part) => ({ label: prettify(part) }));

  function prettify(segment: string): string {
    return segment
      .replace(/-/g, ' ')
      .replace(/\b\w/g, (m) => m.toUpperCase());
  }

  $: warnings = $customers
    .filter((c) => c.status === 'WARNING' || c.status === 'LOCKED')
    .sort((a, b) => a.nextPaymentDueEpochMillis - b.nextPaymentDueEpochMillis)
    .slice(0, 5)
    .map((c) => ({
      id: c.id,
      title: c.status === 'LOCKED'
        ? `${c.customerName} overdue`
        : `${c.customerName} due in ${formatRelative(c.nextPaymentDueEpochMillis - Date.now())}`,
      time: c.status === 'LOCKED' ? 'overdue' : 'soon',
      tone: c.status === 'LOCKED' ? 'crimson' as const : 'amber' as const
    }));

  $: unreadCount = notifications.filter(n => !n.isRead).length;

  function formatNotificationTime(timestamp: number): string {
    const now = Date.now();
    const diff = now - timestamp;
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);
    
    if (minutes < 1) return 'Just now';
    if (minutes < 60) return `${minutes}m ago`;
    if (hours < 24) return `${hours}h ago`;
    if (days < 7) return `${days}d ago`;
    
    return new Date(timestamp).toLocaleDateString();
  }
</script>

<header class="sticky top-0 z-20 -mx-6 mb-6 px-6 py-3 md:-mx-8 md:px-8 bg-surface-200/80 backdrop-blur-xl border-b border-edge">
  <div class="flex items-center gap-3">
    <div class="flex min-w-0 items-center gap-2 text-sm">
      <button
        type="button"
        class="inline-flex h-9 w-9 items-center justify-center rounded-lg text-ink-muted hover:bg-hover hover:text-ink-primary md:hidden"
        on:click={() => sidebarOpen.set(true)}
        aria-label="Open sidebar"
      >
        <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M4 6h16M4 12h16M4 18h16" />
        </svg>
      </button>
      <span class="text-ink-muted">SecurePay</span>
      <svg class="h-3.5 w-3.5 text-ink-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
        <path d="M9 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
      {#each crumbs as c, i (i)}
        <span class={i === crumbs.length - 1 ? 'font-semibold text-ink-primary' : 'text-ink-secondary'}>
          {c.label}
        </span>
        {#if i < crumbs.length - 1}
          <svg class="h-3.5 w-3.5 text-ink-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M9 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        {/if}
      {/each}
    </div>

    <div class="ml-auto flex items-center gap-2">
      {#if showSearch}
        <label class="relative hidden md:flex items-center">
          <svg class="absolute left-3 h-4 w-4 text-ink-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <circle cx="11" cy="11" r="7" />
            <path d="M20 20l-3.5-3.5" stroke-linecap="round" />
          </svg>
          <input
            type="search"
            class="input w-72 pl-9 pr-12"
            placeholder={searchPlaceholder}
          />
          <kbd class="pointer-events-none absolute right-2.5 hidden h-5 select-none items-center rounded border border-edge bg-surface-100 px-1.5 font-mono text-2xs text-ink-muted md:flex">
            ⌘ K
          </kbd>
        </label>
      {/if}

      <ThemeToggle />

      <div class="relative">
        <button
          type="button"
          class="btn-ghost relative h-9 w-9 !p-0"
          aria-label="Notifications"
          on:click={() => (notificationsOpen = !notificationsOpen)}
        >
          <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M6 8a6 6 0 0112 0c0 7 3 9 3 9H3s3-2 3-9M10 21a2 2 0 004 0" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          {#if unreadCount > 0}
            <span class="absolute -right-0.5 -top-0.5 flex h-4 w-4 items-center justify-center rounded-full bg-crimson text-2xs font-semibold text-white ring-2 ring-surface-200">
              {unreadCount > 9 ? '9+' : unreadCount}
            </span>
          {/if}
        </button>
        {#if notificationsOpen}
          <div
            class="absolute right-0 mt-2 w-80 rounded-xl border border-edge bg-surface-200 shadow-card-hover animate-fade-in"
            role="menu"
          >
            <div class="flex items-center justify-between border-b border-edge px-4 py-3">
              <p class="text-sm font-semibold text-ink-primary">Notifications</p>
              {#if notifications.length > 0}
                <a href="/notifications" class="text-2xs text-emerald hover:underline" on:click={() => notificationsOpen = false}>
                  View all
                </a>
              {/if}
            </div>
            {#if notifications.length === 0}
              <div class="px-4 py-8 text-center">
                <svg class="mx-auto mb-2 h-8 w-8 text-ink-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
                </svg>
                <p class="text-sm text-ink-muted">No notifications yet</p>
              </div>
            {:else}
              <ul class="max-h-96 overflow-y-auto">
                {#each notifications.slice(0, 10) as n (n.id)}
                  <li class="flex items-start gap-3 border-b border-edge px-4 py-3 hover:bg-hover last:border-b-0">
                    {#if !n.isRead}
                      <span class="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-emerald"></span>
                    {:else}
                      <span class="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-ink-muted/30"></span>
                    {/if}
                    <div class="flex-1 min-w-0">
                      <p class="text-sm font-medium text-ink-primary">{n.title}</p>
                      <p class="mt-0.5 text-xs text-ink-secondary line-clamp-2">{n.message}</p>
                      <p class="mt-1 text-2xs text-ink-muted">{formatNotificationTime(n.createdAt)}</p>
                    </div>
                  </li>
                {/each}
              </ul>
            {/if}
          </div>
        {/if}
      </div>

      <button type="button" class="btn-primary hidden sm:inline-flex">
        <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4">
          <path d="M12 5v14M5 12h14" stroke-linecap="round" />
        </svg>
        New loan
      </button>
    </div>
  </div>
</header>