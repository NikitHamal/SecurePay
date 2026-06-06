<script lang="ts">
  import { page } from '$app/stores';

  interface NavLink {
    href: string;
    label: string;
    description: string;
  }

  const links: NavLink[] = [
    { href: '/', label: 'Overview', description: 'KPIs & live fleet' },
    { href: '/inventory', label: 'Inventory', description: 'IMEI Matrix' },
    { href: '/customers', label: 'Customers', description: 'Borrower roster' },
    { href: '/ledger', label: 'Payment Ledger', description: 'Collections' }
  ];

  function isActive(href: string, pathname: string): boolean {
    if (href === '/') {
      return pathname === '/';
    }
    return pathname === href || pathname.startsWith(`${href}/`);
  }
</script>

<aside
  class="flex w-16 flex-col border-r border-brand-border bg-brand-surface md:w-64"
  aria-label="Primary navigation"
>
  <div class="flex h-16 items-center gap-3 border-b border-brand-border px-4 md:px-6">
    <span
      class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-brand-emerald font-bold text-brand-bg"
    >
      S
    </span>
    <div class="hidden md:block">
      <p class="text-base font-semibold leading-none text-brand-text">SecurePay</p>
      <p class="mt-1 text-[11px] uppercase tracking-wider text-brand-textMuted">Dealer Console</p>
    </div>
  </div>

  <nav class="flex-1 space-y-1 px-2 py-4 md:px-3">
    {#each links as link (link.href)}
      {@const active = isActive(link.href, $page.url.pathname)}
      <a
        href={link.href}
        class="group flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition-colors {active
          ? 'bg-brand-surfaceVariant text-brand-text'
          : 'text-brand-textMuted hover:bg-brand-surfaceVariant/60 hover:text-brand-text'}"
        aria-current={active ? 'page' : undefined}
        title={link.label}
      >
        <span
          class="h-2 w-2 shrink-0 rounded-full {active
            ? 'bg-brand-emerald'
            : 'bg-brand-border group-hover:bg-brand-textMuted'}"
        ></span>
        <span class="hidden flex-col md:flex">
          <span class="font-medium leading-none">{link.label}</span>
          <span class="mt-1 text-[11px] text-brand-textMuted">{link.description}</span>
        </span>
      </a>
    {/each}
  </nav>

  <div class="hidden border-t border-brand-border px-6 py-4 md:block">
    <p class="text-[11px] leading-relaxed text-brand-textMuted">
      SecurePay device-financing platform. Authorized dealer access only.
    </p>
  </div>
</aside>
