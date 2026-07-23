<script lang="ts">
  import { page } from '$app/stores';
  import { goto } from '$app/navigation';
  import { dealer, logout } from '$lib/stores/auth';
  import ThemeToggle from '$lib/components/ui/ThemeToggle.svelte';
  import { sidebarOpen } from '$lib/stores/ui';
  import { apiClient } from '$lib/api/client';
  import { onMount } from 'svelte';

  interface NavItem {
    label: string;
    href: string;
    icon: string;
    badge?: string;
    badgeTone?: 'emerald' | 'amber' | 'crimson';
    roles?: string[];
    badgeCount?: () => number;
  }

  interface NavGroup {
    label: string;
    items: NavItem[];
  }

  let pendingAgentRequests = 0;

  async function fetchPendingCount() {
    try {
      const res = await apiClient('/api/agent-requests?status=PENDING');
      if (res.ok) {
        const data = await res.json();
        pendingAgentRequests = Array.isArray(data) ? data.length : 0;
      }
    } catch {
      /* silent */
    }
  }

  onMount(() => {
    fetchPendingCount();
    const interval = setInterval(fetchPendingCount, 60000);
    return () => clearInterval(interval);
  });

  const groups: NavGroup[] = [
    {
      label: 'Operations',
      items: [
        { label: 'Overview', href: '/', icon: 'M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6' },
        { label: 'Customers', href: '/customers', icon: 'M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z' },
        { label: 'My Sales', href: '/my-sales', icon: 'M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z', roles: ['AGENT'] },
        { label: 'Device Logs', href: '/logs', icon: 'M9 17V7m0 10a2 2 0 01-2 2H5a2 2 0 01-2-2V7a2 2 0 012-2h2a2 2 0 012 2m0 10a2 2 0 002 2h2a2 2 0 002-2M9 7a2 2 0 012-2h2a2 2 0 012 2m0 10V7m0 10a2 2 0 002 2h2a2 2 0 002-2V7a2 2 0 00-2-2h-2a2 2 0 00-2 2' }
      ]
    },
    {
      label: 'Finance',
      items: [
        { label: 'Payment Ledger', href: '/ledger', icon: 'M9 7h6m0 10v-3m-3 3h.01M9 17h.01M9 14h.01M12 14h.01M15 11h.01M12 11h.01M9 11h.01M7 21h10a2 2 0 002-2V5a2 2 0 00-2-2H7a2 2 0 00-2 2v14a2 2 0 002 2z' },
        { label: 'Inventory', href: '/inventory', icon: 'M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4' }
      ]
    },
    {
      label: 'Marketing',
      items: [
        { label: 'Ads', href: '/ads', icon: 'M11 5.882V19.24a1.76 1.76 0 01-3.417.592l-2.147-6.15M18 13a3 3 0 100-6M5.436 13.683A4.001 4.001 0 017 6h1.832c4.1 0 7.625-1.234 9.168-3v14c-1.543-1.766-5.067-3-9.168-3H7a3.988 3.988 0 01-1.564-.317z', roles: ['SUPER_ADMIN'] }
      ]
    },
    {
      label: 'Organization',
      items: [
        {
          label: 'Agent Requests',
          href: '/agent-requests',
          icon: 'M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z',
          roles: ['SUPER_ADMIN', 'AGENCY_OWNER', 'BRANCH_ADMIN']
        },
        { label: 'Agents', href: '/agents', icon: 'M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z', roles: ['SUPER_ADMIN', 'AGENCY_OWNER', 'BRANCH_ADMIN'] },
        { label: 'Branches', href: '/branches', icon: 'M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4', roles: ['SUPER_ADMIN', 'AGENCY_OWNER', 'BRANCH_ADMIN'] },
        { label: 'Notifications', href: '/notifications', icon: 'M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9' }
      ]
    }
  ];

  function isActive(href: string, pathname: string): boolean {
    if (href === '/') return pathname === '/';
    return pathname === href || pathname.startsWith(`${href}/`);
  }

  function canAccessItem(item: NavItem, role: string | undefined): boolean {
    if (!item.roles) return true;
    return role ? item.roles.includes(role) : false;
  }

  function getInitials(name: string): string {
    return (name || 'DD').split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase();
  }

  $: pathname = $page.url.pathname;
  $: userRole = $dealer?.role || 'SUPER_ADMIN';
  $: canViewRequests = userRole === 'SUPER_ADMIN' || userRole === 'AGENCY_OWNER' || userRole === 'BRANCH_ADMIN';
</script>

{#if $sidebarOpen}
  <div
    class="fixed inset-0 z-40 bg-black/50 md:hidden"
    on:click={() => sidebarOpen.set(false)}
    role="presentation"
  ></div>
{/if}

<aside
  class="fixed inset-y-0 left-0 z-50 flex w-64 shrink-0 flex-col border-r border-edge transition-transform duration-200 ease-out md:sticky md:top-0 md:h-screen md:w-64 md:translate-x-0
         {$sidebarOpen ? 'translate-x-0' : '-translate-x-full'}"
  style="background-color: var(--bg-sidebar);"
  aria-label="Primary navigation"
>
  <!-- Logo / brand -->
  <div class="flex items-center gap-3 px-5 pt-5 pb-4">
    <img
      src="/branding/touchbase-mark-64.png"
      alt="Touch Base"
      class="h-10 w-10 rounded-lg object-contain p-1"
      style="background: linear-gradient(135deg, #D4AF37 0%, #B8941F 100%);"
      loading="eager"
    />
    <div class="leading-tight">
      <p class="text-[15px] font-semibold text-ink-primary">Touch Base</p>
      <p class="text-[11px] text-ink-muted">Dealer Console</p>
    </div>
  </div>

  <nav class="flex flex-1 flex-col gap-5 overflow-y-auto px-3 pb-3 pt-2">
    {#each groups as group (group.label)}
      {@const visibleItems = group.items.filter(item => canAccessItem(item, userRole))}
      {#if visibleItems.length > 0}
        <div class="flex flex-col gap-1">
          <p class="px-3 text-[11px] font-semibold uppercase tracking-wider text-ink-muted mb-1">{group.label}</p>
          {#each visibleItems as item (item.href)}
            {@const active = isActive(item.href, pathname)}
            {@const showPendingBadge = item.href === '/agent-requests' && canViewRequests && pendingAgentRequests > 0}
            <a
              href={item.href}
              on:click={() => sidebarOpen.set(false)}
              aria-current={active ? 'page' : undefined}
              class="group relative flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors
                     {active ? '' : 'text-ink-secondary hover:bg-hover hover:text-ink-primary'}"
              style={active ? 'background-color: var(--brand-soft); color: var(--brand);' : ''}
            >
              {#if active}
                <span class="nav-active-indicator" aria-hidden="true"></span>
              {/if}
              <span
                class="flex h-7 w-7 items-center justify-center rounded-md
                       {active ? '' : 'text-ink-muted group-hover:text-ink-secondary'}"
                style={active ? 'background-color: var(--brand-soft); color: var(--brand);' : ''}
              >
                <svg
                  class="h-[18px] w-[18px]"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  aria-hidden="true"
                >
                  <path d={item.icon} />
                </svg>
              </span>
              <span class="flex-1 truncate">{item.label}</span>
              {#if showPendingBadge}
                <span class="inline-flex min-w-[20px] items-center justify-center rounded-md bg-amber/10 px-1.5 py-0.5 text-[10px] font-semibold text-amber border border-amber/20 tabular-nums">
                  {pendingAgentRequests}
                </span>
              {/if}
            </a>
          {/each}
        </div>
      {/if}
    {/each}
  </nav>

  <div class="border-t border-edge px-3 py-3">
    <button
      type="button"
      on:click={async () => { await logout(); goto('/login'); }}
      class="flex w-full items-center gap-3 rounded-lg px-2 py-2 text-left transition-colors hover:bg-hover"
    >
      <span class="flex h-9 w-9 items-center justify-center rounded-lg text-sm font-semibold text-white" style="background-color: var(--brand);">
        {$dealer ? getInitials($dealer.name || 'DD') : 'DD'}
      </span>
      <span class="flex-1 min-w-0">
        <span class="block text-sm font-medium text-ink-primary truncate">{$dealer?.name || 'Demo Dealer'}</span>
        <span class="block text-[11px] text-ink-muted truncate">{$dealer?.email || 'dealer@touchbase.app'}</span>

      </span>
      <svg class="h-4 w-4 text-ink-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
        <path d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a2 2 0 01-2 2H5a2 2 0 01-2-2V7a2 2 0 012-2h6a2 2 0 012 2v1" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </button>
  </div>
</aside>
