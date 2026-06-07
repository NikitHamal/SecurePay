<script lang="ts">
  import { page } from '$app/stores';
  import { customers } from '$lib/stores/customers';
  import ThemeToggle from '$lib/components/ui/ThemeToggle.svelte';
  import { formatRelative } from '$lib/utils/format';

  export let searchPlaceholder: string = 'Search customers, IMEI, references…';
  export let showSearch: boolean = true;

  $: pathname = $page.url.pathname;
  $: crumbs = pathname === '/' ? [{ label: 'Overview' }] : pathname.split('/').filter(Boolean).map((part) => ({ label: prettify(part) }));

  function prettify(segment: string): string {
    return segment
      .replace(/-/g, ' ')
      .replace(/\b\w/g, (m) => m.toUpperCase());
  }

  let notificationsOpen = false;

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
</script>

<header class="sticky top-0 z-20 -mx-6 mb-6 px-6 py-3 md:-mx-8 md:px-8 bg-surface-200/80 backdrop-blur-xl border-b border-edge">
  <div class="flex items-center gap-3">
    <div class="flex min-w-0 items-center gap-2 text-sm">
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
            {searchPlaceholder}
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
          <span class="absolute right-1.5 top-1.5 h-2 w-2 rounded-full bg-crimson ring-2 ring-surface-200"></span>
        </button>
        {#if notificationsOpen}
          <div
            class="absolute right-0 mt-2 w-80 rounded-xl border border-edge bg-surface-200 shadow-card-hover p-2 animate-fade-in"
            role="menu"
          >
            <div class="flex items-center justify-between px-3 py-2">
              <p class="text-sm font-semibold text-ink-primary">Notifications</p>
              <button type="button" class="text-2xs text-ink-muted hover:text-ink-primary" on:click={() => (notificationsOpen = false)}>
                Mark all read
              </button>
            </div>
            <ul class="flex flex-col">
              {#each warnings as n (n.id)}
                <li class="flex items-start gap-3 rounded-lg px-3 py-2 hover:bg-hover">
                  <span
                    class="mt-1 h-2 w-2 shrink-0 rounded-full"
                    style="background: {n.tone === 'crimson' ? '#EF4444' : '#F59E0B'}"
                  ></span>
                  <div class="flex-1 min-w-0">
                    <p class="text-sm text-ink-primary">{n.title}</p>
                    <p class="text-2xs text-ink-muted">{n.time}</p>
                  </div>
                </li>
              {/each}
            </ul>
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