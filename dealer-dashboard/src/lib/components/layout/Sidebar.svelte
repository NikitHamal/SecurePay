<script lang="ts">
  import { page } from '$app/stores';
  import { goto } from '$app/navigation';
  import { dealer, logout } from '$lib/stores/auth';
  import ThemeToggle from '$lib/components/ui/ThemeToggle.svelte';
  import { sidebarOpen } from '$lib/stores/ui';

  interface NavItem {
    label: string;
    href: string;
    icon: string;
    badge?: string;
    roles?: string[];
  }

  interface NavGroup {
    label: string;
    items: NavItem[];
  }

  const groups: NavGroup[] = [
    {
      label: 'Operations',
      items: [
        { label: 'Overview', href: '/', icon: 'M3 12l9-9 9 9M5 10v10h14V10' },
        { label: 'Customers', href: '/customers', icon: 'M16 14a4 4 0 10-8 0M12 7a3 3 0 100 6 3 3 0 000-6zM4 20a8 8 0 0116 0' },
        { label: 'My Sales', href: '/my-sales', icon: 'M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z', roles: ['AGENT'] },
        { label: 'Device Logs', href: '/logs', icon: 'M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z' }
      ]
    },
    {
      label: 'Finance',
      items: [
        { label: 'Payment Ledger', href: '/ledger', icon: 'M4 6h16M4 12h16M4 18h10', badge: 'New' },
        { label: 'Inventory', href: '/inventory', icon: 'M3 7l9-4 9 4-9 4-9-4zm0 0v10l9 4 9-4V7' }
      ]
    },
    {
      label: 'Organization',
      items: [
        { label: 'Agent Requests', href: '/agent-requests', icon: 'M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z', badge: 'Pending', roles: ['SUPER_ADMIN', 'AGENCY_OWNER', 'BRANCH_ADMIN'] },
        { label: 'Agents', href: '/agents', icon: 'M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z', roles: ['SUPER_ADMIN', 'AGENCY_OWNER', 'BRANCH_ADMIN'] },
        { label: 'Branches', href: '/branches', icon: 'M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4', roles: ['SUPER_ADMIN', 'AGENCY_OWNER', 'BRANCH_ADMIN'] },
        { label: 'Agencies', href: '/agencies', icon: 'M8 14v3m4-3v3m4-3v3M3 21h18M3 10h18M3 7l9-4 9 4M4 10h16v11H4V10z', roles: ['SUPER_ADMIN', 'AGENCY_OWNER'] },
        { label: 'Notifications', href: '/notifications', icon: 'M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9' },
        { label: 'Push Messages', href: '/admin/push', icon: 'M7 8l5-5 5 5m-5 11V3', roles: ['SUPER_ADMIN'] }
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

  $: pathname = $page.url.pathname;
  $: userRole = $dealer?.role || 'SUPER_ADMIN';
</script>

{#if $sidebarOpen}
  <div
    class="fixed inset-0 z-40 bg-black/60 md:hidden"
    on:click={() => sidebarOpen.set(false)}
    role="presentation"
  ></div>
{/if}

<aside
  class="fixed inset-y-0 left-0 z-50 flex w-64 shrink-0 flex-col border-r border-edge bg-surface-300 transition-transform duration-300 ease-in-out md:sticky md:top-0 md:h-screen md:w-64 md:translate-x-0
         {$sidebarOpen ? 'translate-x-0' : '-translate-x-full'}"
  aria-label="Primary"
>
  <div class="flex items-center gap-3 px-5 pt-5 pb-3">
    <div
      class="relative flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-emerald-300 to-emerald text-white"
      aria-hidden="true"
    >
      <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4">
        <path d="M12 2l8 4v6c0 5-3.5 8-8 10-4.5-2-8-5-8-10V6l8-4z" stroke-linejoin="round" />
        <path d="M9 12l2 2 4-4" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
      <span class="absolute -bottom-0.5 -right-0.5 h-3 w-3 rounded-full bg-emerald ring-2 ring-surface-300"></span>
    </div>
    <div class="leading-tight">
      <p class="text-sm font-semibold text-ink-primary">SecurePay</p>
      <p class="text-2xs text-ink-muted">Dealer Console · v1.0</p>
    </div>
  </div>

  <div class="mx-4 mb-4 mt-1 rounded-xl border border-edge bg-surface-100/60 px-3 py-2">
    <div class="flex items-center justify-between">
      <span class="section-title">Status</span>
      <span class="inline-flex items-center gap-1.5 text-2xs text-emerald">
        <span class="relative flex h-1.5 w-1.5">
          <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald/60"></span>
          <span class="relative inline-flex h-1.5 w-1.5 rounded-full bg-emerald"></span>
        </span>
        Live
      </span>
    </div>
    <p class="mt-1 text-2xs text-ink-muted">Nairobi Hub · Operator #4471</p>
  </div>

  <nav class="flex flex-1 flex-col gap-5 overflow-y-auto px-3 pb-4">
    {#each groups as group (group.label)}
      {@const visibleItems = group.items.filter(item => canAccessItem(item, userRole))}
      {#if visibleItems.length > 0}
        <div class="flex flex-col gap-1.5">
          <p class="px-3 section-title">{group.label}</p>
          {#each visibleItems as item (item.href)}
            {@const active = isActive(item.href, pathname)}
            <a
              href={item.href}
              on:click={() => sidebarOpen.set(false)}
              aria-current={active ? 'page' : undefined}
              class="group relative flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-all
                     {active
                ? 'bg-emerald/10 text-emerald'
                : 'text-ink-secondary hover:bg-hover hover:text-ink-primary'}"
            >
              {#if active}
                <span
                  class="absolute left-0 top-1/2 h-5 w-0.5 -translate-y-1/2 rounded-r-full bg-emerald"
                  aria-hidden="true"
                ></span>
              {/if}
              <span
                class="flex h-7 w-7 items-center justify-center rounded-md
                       {active ? 'bg-emerald/15 text-emerald' : 'text-ink-muted group-hover:text-ink-secondary'}"
              >
                <svg
                  class="h-4 w-4"
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
              <span class="flex-1">{item.label}</span>
              {#if item.badge}
                <span class="chip-emerald text-2xs">{item.badge}</span>
              {/if}
            </a>
          {/each}
        </div>
      {/if}
    {/each}
  </nav>

  <div class="border-t border-edge px-4 py-4">
    <button
      type="button"
      on:click={async () => { await logout(); goto('/login'); }}
      class="flex w-full items-center gap-3 rounded-lg px-2 py-2 text-left hover:bg-hover transition-colors"
    >
      <span
        class="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-violet to-sky text-white text-sm font-semibold"
      >
        {$dealer ? $dealer.name.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase() : '??'}
      </span>
      <span class="flex-1 min-w-0">
        <span class="block text-sm font-medium text-ink-primary truncate">{$dealer?.name || 'Unknown'}</span>
        <span class="block text-2xs text-ink-muted truncate">{$dealer?.email || ''}</span>
      </span>
      <svg class="h-4 w-4 text-ink-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
        <path d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a2 2 0 01-2 2H5a2 2 0 01-2-2V7a2 2 0 012-2h6a2 2 0 012 2v1" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </button>
  </div>
</aside>