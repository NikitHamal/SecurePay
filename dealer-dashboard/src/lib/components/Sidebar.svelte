<script lang="ts">
  import { page } from '$app/stores';

  interface NavItem {
    label: string;
    href: string;
    icon: string;
  }

  const navItems: NavItem[] = [
    { label: 'Overview', href: '/', icon: 'M3 12l9-9 9 9M5 10v10h14V10' },
    {
      label: 'Inventory',
      href: '/inventory',
      icon: 'M3 7l9-4 9 4-9 4-9-4zm0 0v10l9 4 9-4V7'
    },
    {
      label: 'Customers',
      href: '/customers',
      icon: 'M16 14a4 4 0 10-8 0M12 7a3 3 0 100 6 3 3 0 000-6zM4 20a8 8 0 0116 0'
    },
    {
      label: 'Payment Ledger',
      href: '/ledger',
      icon: 'M4 6h16M4 12h16M4 18h10'
    }
  ];

  function isActive(href: string, pathname: string): boolean {
    if (href === '/') {
      return pathname === '/';
    }
    return pathname === href || pathname.startsWith(`${href}/`);
  }

  $: pathname = $page.url.pathname;
</script>

<aside
  class="flex w-full shrink-0 flex-col border-b border-white/5 bg-surface md:h-screen md:w-64 md:border-b-0 md:border-r"
>
  <div class="flex items-center gap-3 px-6 py-5">
    <div
      class="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald/15 text-emerald"
      aria-hidden="true"
    >
      <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M12 2l8 4v6c0 5-3.5 8-8 10-4.5-2-8-5-8-10V6l8-4z" stroke-linejoin="round" />
        <path d="M9 12l2 2 4-4" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </div>
    <div class="leading-tight">
      <p class="text-sm font-semibold text-text-primary">SecurePay</p>
      <p class="text-xs text-text-secondary">Dealer Console</p>
    </div>
  </div>

  <nav class="flex flex-1 flex-col gap-1 px-3 pb-4" aria-label="Primary">
    {#each navItems as item (item.href)}
      <a
        href={item.href}
        aria-current={isActive(item.href, pathname) ? 'page' : undefined}
        class="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors {isActive(
          item.href,
          pathname
        )
          ? 'bg-emerald/15 text-emerald'
          : 'text-text-secondary hover:bg-white/5 hover:text-text-primary'}"
      >
        <svg
          class="h-5 w-5 shrink-0"
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
        <span>{item.label}</span>
      </a>
    {/each}
  </nav>

  <div class="hidden border-t border-white/5 px-6 py-4 md:block">
    <p class="text-xs text-text-secondary">Region: Nairobi Hub</p>
    <p class="text-xs text-text-secondary">Operator: Dealer #4471</p>
  </div>
</aside>
