<script lang="ts">
  import { page } from '$app/stores';
  import { goto } from '$app/navigation';
  import { dealer, logout } from '$lib/stores/auth';
  import ThemeToggle from '$lib/components/ui/ThemeToggle.svelte';

  interface NavItem {
    label: string;
    href: string;
    icon: string;
    badge?: string;
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
        { label: 'Customers', href: '/customers', icon: 'M16 14a4 4 0 10-8 0M12 7a3 3 0 100 6 3 3 0 000-6zM4 20a8 8 0 0116 0' }
      ]
    },
    {
      label: 'Finance',
      items: [
        { label: 'Payment Ledger', href: '/ledger', icon: 'M4 6h16M4 12h16M4 18h10', badge: 'New' },
        { label: 'Inventory', href: '/inventory', icon: 'M3 7l9-4 9 4-9 4-9-4zm0 0v10l9 4 9-4V7' }
      ]
    }
  ];

  function isActive(href: string, pathname: string): boolean {
    if (href === '/') return pathname === '/';
    return pathname === href || pathname.startsWith(`${href}/`);
  }

  $: pathname = $page.url.pathname;
</script>

<aside
  class="relative flex w-full shrink-0 flex-col border-r border-edge bg-surface-300 md:sticky md:top-0 md:h-screen md:w-64"
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
      <div class="flex flex-col gap-1.5">
        <p class="px-3 section-title">{group.label}</p>
        {#each group.items as item (item.href)}
          {@const active = isActive(item.href, pathname)}
          <a
            href={item.href}
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