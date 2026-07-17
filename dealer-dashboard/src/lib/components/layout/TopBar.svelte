<script lang="ts">
  import { page } from '$app/stores';
  import { onMount } from 'svelte';
  import { apiClient } from '$lib/api/client';
  import { customers } from '$lib/stores/customers';
  import ThemeToggle from '$lib/components/ui/ThemeToggle.svelte';
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

  onMount(() => {
    fetchNotifications();
    const interval = setInterval(fetchNotifications, 60000);
    return () => clearInterval(interval);
  });

  async function fetchNotifications() {
    try {
      const res = await apiClient('/api/notifications');
      if (res.ok) notifications = await res.json();
    } catch { /* silent */ }
  }

  $: pathname = $page.url.pathname;
  $: crumbs = pathname === '/' ? [{ label: 'Overview' }]
    : pathname.split('/').filter(Boolean).map((part) => ({ label: prettify(part) }));

  function prettify(segment: string): string {
    return segment.replace(/-/g, ' ').replace(/\b\w/g, (m) => m.toUpperCase());
  }

  $: unreadCount = notifications.filter(n => !n.isRead).length;
</script>

<header class="sticky top-0 z-20 border-b border-edge bg-surface-200/80 backdrop-blur supports-[backdrop-filter]:bg-surface-200/70">
  <div class="flex items-center gap-2 px-4 py-3 sm:px-6 lg:px-8">
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

    <nav class="hidden min-w-0 items-center gap-1.5 text-sm md:flex" aria-label="Breadcrumb">
      <span class="text-ink-muted">SecurePay</span>
      <svg class="h-3.5 w-3.5 text-ink-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M9 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
      {#each crumbs as c, i (i)}
        <span class={i === crumbs.length - 1 ? 'font-semibold text-ink-primary' : 'text-ink-secondary'}>
          {c.label}
        </span>
        {#if i < crumbs.length - 1}
          <svg class="h-3.5 w-3.5 text-ink-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        {/if}
      {/each}
    </nav>

    <h1 class="md:hidden text-base font-semibold text-ink-primary truncate">
      {crumbs[crumbs.length - 1]?.label}
    </h1>

    <div class="ml-auto flex items-center gap-2">
      {#if showSearch}
        <label class="relative hidden md:flex items-center">
          <svg class="absolute left-3 h-4 w-4 text-ink-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <circle cx="11" cy="11" r="7" />
            <path d="M20 20l-3.5-3.5" stroke-linecap="round" />
          </svg>
          <input type="search" class="input w-64 pl-9" placeholder={searchPlaceholder} />
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
          <svg class="h-[18px] w-[18px]" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M6 8a6 6 0 0112 0c0 7 3 9 3 9H3s3-2 3-9M10 21a2 2 0 004 0" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          {#if unreadCount > 0}
            <span class="absolute -right-0.5 -top-0.5 flex h-4 w-4 items-center justify-center rounded-full bg-crimson text-[10px] font-semibold text-white ring-2 ring-surface-200">
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
              <a href="/notifications" class="text-xs text-emerald hover:underline" on:click={() => notificationsOpen = false}>View all</a>
            </div>
            {#if notifications.length === 0}
              <div class="px-4 py-8 text-center">
                <svg class="mx-auto mb-2 h-8 w-8 text-ink-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
                </svg>
                <p class="text-sm text-ink-muted">No notifications</p>
              </div>
            {:else}
              <ul class="max-h-80 overflow-y-auto">
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
                    </div>
                  </li>
                {/each}
              </ul>
            {/if}
          </div>
        {/if}
      </div>
    </div>
  </div>
</header>
