<script lang="ts">
  import { onMount } from 'svelte';
  import PageHeader from '$lib/components/ui/PageHeader.svelte';
  import TopBar from '$lib/components/layout/TopBar.svelte';
  import Donut from '$lib/components/charts/Donut.svelte';
  import BarChart from '$lib/components/charts/BarChart.svelte';
  import { listLedger, listCustomers } from '$lib/api/client';
  import type { LedgerEntry, PaymentMethod, Customer } from '$lib/types';
  import { formatCurrency, formatDateTime, formatRelative } from '$lib/utils/format';

  // One rolled-up row in the "Collections by customer" table. Grouping is done
  // client-side from the flat /api/ledger response (same approach as the agent
  // app) and joined with /api/accounts for the phone number + repayment progress.
  interface CustomerGroup {
    customerId: string;
    customerName: string;
    phone: string;
    total: number;
    count: number;
    lastEpoch: number;
    amountPaid: number;
    totalLoan: number;
    hasAccount: boolean;
    percent: number;
    wa: string;
    reminderEncoded: string;
  }

  // wa.me expects the international number without a leading '+'. Stored numbers
  // may begin with '0' (local) or already carry the 233 country code.
  function waDigits(phone: string): string {
    const digits = phone.replace(/\D/g, '');
    if (digits.startsWith('233')) return digits;
    if (digits.startsWith('0')) return '233' + digits.slice(1);
    return digits;
  }

  // Warm, non-threatening payment reminder (mirrors the agent app's copy).
  function reminderText(c: Customer): string {
    const who = c.customerName?.trim() || 'valued customer';
    let due: string;
    if (c.remainingBalance <= 0) {
      due = 'Your loan is fully settled — thank you for being an excellent customer!';
    } else if (!c.nextPaymentDueEpochMillis) {
      due = 'your next instalment is due.';
    } else if (c.nextPaymentDueEpochMillis < Date.now()) {
      due = `your instalment of ${formatCurrency(c.dailyRate)} is now due. A quick payment today keeps your phone fully unlocked.`;
    } else {
      due = `your instalment of ${formatCurrency(c.dailyRate)} is due on ${formatDateTime(c.nextPaymentDueEpochMillis)}. Staying on track keeps your phone fully unlocked.`;
    }
    return `Hello ${who}, this is Touch Base. A friendly reminder that ${due} You're doing great — every payment brings you closer to owning your device outright. Reply or call us if you need any help. Thank you for being a valued customer!`;
  }

  let entries: LedgerEntry[] = [];
  let customers: Customer[] = [];
  let loading = true;
  let loadError: string | null = null;
  let methodFilter: PaymentMethod | 'ALL' = 'ALL';
  let expanded: string | null = null;

  const paymentMethods: PaymentMethod[] = ['MOBILE_MONEY', 'CARD', 'BANK', 'CASH'];

  const methodStyles: Record<PaymentMethod, { chip: string; color: string; label: string }> = {
    MOBILE_MONEY: { chip: 'chip-emerald', color: '#10B981', label: 'Mobile Money' },
    CARD:     { chip: 'chip-amber',   color: '#F59E0B', label: 'Card' },
    BANK:     { chip: 'chip-sky',     color: '#38BDF8', label: 'Bank' },
    CASH:     { chip: 'chip-violet',  color: '#A78BFA', label: 'Cash' }
  };

  $: total = entries.reduce((sum, entry) => sum + entry.amount, 0);
  $: filtered = methodFilter === 'ALL' ? entries : entries.filter((e) => e.method === methodFilter);
  $: methodBreakdown = paymentMethods.map((m) => ({
    label: methodStyles[m].label,
    value: entries.filter((e) => e.method === m).reduce((s, e) => s + e.amount, 0),
    color: methodStyles[m].color
  }));
  $: topEntry = entries[0];
  $: averageAmount = entries.length > 0 ? total / entries.length : 0;

  $: dailyTotals = (() => {
    const map = new Map<string, number>();
    for (const e of entries) {
      const d = new Date(e.dateEpochMillis);
      const key = d.toLocaleDateString('en-GH', { day: '2-digit', month: 'short' });
      map.set(key, (map.get(key) ?? 0) + e.amount);
    }
    return [...map.entries()].map(([label, value]) => ({ label, value }));
  })();

  $: customerById = new Map(customers.map((c) => [c.id, c] as const));

  // Group the (method-filtered) ledger by customer, mirroring the agent app.
  $: customerGroups = (() => {
    const map = new Map<string, CustomerGroup>();
    for (const e of filtered) {
      const existing = map.get(e.customerId);
      if (existing) {
        existing.total += e.amount;
        existing.count += 1;
        if (e.dateEpochMillis > existing.lastEpoch) existing.lastEpoch = e.dateEpochMillis;
        continue;
      }
      const account = customerById.get(e.customerId);
      const amountPaid = account?.amountPaid ?? 0;
      const totalLoan = account?.totalLoanAmount ?? 0;
      const progress = totalLoan > 0 ? Math.min(1, amountPaid / totalLoan) : 0;
      const phone = account?.phoneNumber ?? '';
      map.set(e.customerId, {
        customerId: e.customerId,
        customerName: (account?.customerName || e.customerName || 'Unknown customer').trim(),
        phone,
        total: e.amount,
        count: 1,
        lastEpoch: e.dateEpochMillis,
        amountPaid,
        totalLoan,
        hasAccount: !!account,
        percent: totalLoan > 0 ? Math.round(progress * 100) : 0,
        wa: waDigits(phone),
        reminderEncoded: account ? encodeURIComponent(reminderText(account)) : ''
      });
    }
    return [...map.values()].sort((a, b) => b.lastEpoch - a.lastEpoch);
  })();

  onMount(async () => {
    try {
      const [ledger, accts] = await Promise.all([listLedger(), listCustomers()]);
      entries = ledger;
      customers = accts;
    } catch (err) {
      loadError = err instanceof Error ? err.message : 'Failed to load ledger';
    } finally {
      loading = false;
    }
  });
</script>

<svelte:head>
  <title>Payment Ledger · Touch Base</title>
</svelte:head>

<div class="page">
  <TopBar searchPlaceholder="Search transactions…" />

  <PageHeader
    eyebrow="Finance"
    title="Payment Ledger"
    subtitle="Recorded installment collections across all financed accounts."
  >
    <div slot="actions" class="flex items-center gap-2">
      <button type="button" class="btn-outline">
        <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 5v14M5 12h14" stroke-linecap="round" />
        </svg>
        Export
      </button>
    </div>
  </PageHeader>

  {#if loadError}
    <div class="mb-4 rounded-xl border border-crimson-200/30 bg-crimson-200/10 px-4 py-3 text-sm text-crimson">
      {loadError}
    </div>
  {/if}

  <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
    <div class="card p-5">
      <p class="section-title">Total collected</p>
      <p class="mt-2 text-3xl font-semibold text-emerald tabular-nums">{formatCurrency(total)}</p>
      <p class="mt-1 text-xs text-ink-secondary">Across {entries.length} transactions</p>
    </div>
    <div class="card p-5">
      <p class="section-title">Average ticket</p>
      <p class="mt-2 text-3xl font-semibold text-sky tabular-nums">{formatCurrency(averageAmount)}</p>
      <p class="mt-1 text-xs text-ink-secondary">Per installment</p>
    </div>
    <div class="card p-5">
      <p class="section-title">Last payment</p>
      {#if topEntry}
        <p class="mt-2 text-2xl font-semibold text-ink-primary tabular-nums">{formatCurrency(topEntry.amount)}</p>
        <p class="mt-1 text-xs text-ink-secondary">
          {topEntry.customerName} · {formatRelative(topEntry.dateEpochMillis)}
        </p>
      {:else}
        <p class="mt-2 text-2xl font-semibold text-ink-muted">—</p>
      {/if}
    </div>
  </div>

  <div class="mt-6 grid grid-cols-1 gap-4 lg:grid-cols-3">
    <div class="card p-6">
      <p class="section-title">Method mix</p>
      <p class="mt-1 text-sm text-ink-secondary">By value</p>
      <div class="mt-4">
        <Donut
          segments={methodBreakdown.map((m) => ({ label: m.label, value: m.value, color: m.color }))}
          size={170}
          stroke={20}
          gap={3}
          centerTitle={formatCurrency(total)}
          centerSubtitle="total"
          legendValues
        />
      </div>
    </div>

    <div class="card p-6 lg:col-span-2">
      <p class="section-title">Daily collection totals</p>
      <p class="mt-1 text-sm text-ink-secondary">Sum of all transactions per day</p>
      <div class="mt-4">
        {#if loading}
          <div class="skeleton h-[220px] w-full rounded-lg"></div>
        {:else if dailyTotals.length > 0}
          <BarChart
            values={dailyTotals.map((d) => ({
              label: d.label,
              value: d.value,
              color: '#10B981'
            }))}
            color="#10B981"
            height={220}
            yTicks={3}
            xLabelRotation={-25}
            formatY={(n) => n >= 1000 ? `${(n / 1000).toFixed(0)}k` : n.toString()}
            showValues
          />
        {:else}
          <div class="flex h-[220px] items-center justify-center text-sm text-ink-muted">
            No collection data yet.
          </div>
        {/if}
      </div>
    </div>
  </div>

  <div class="card mt-6 flex flex-wrap items-center gap-3 p-4">
    <div class="flex flex-wrap items-center gap-2">
      <button
        type="button"
        on:click={() => (methodFilter = 'ALL')}
        class="rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors
               {methodFilter === 'ALL'
          ? 'border-emerald-300/30 bg-emerald-300/10 text-emerald'
          : 'border-edge bg-surface-100/40 text-ink-secondary hover:text-ink-primary hover:bg-hover'}"
      >
        All methods
        <span class="ml-1.5 rounded-md bg-surface-100 px-1.5 py-0.5 text-2xs text-ink-muted tabular-nums">{entries.length}</span>
      </button>
      {#each paymentMethods as m (m)}
        {@const count = entries.filter((e) => e.method === m).length}
        <button
          type="button"
          on:click={() => (methodFilter = m)}
          class="rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors
                 {methodFilter === m
            ? 'border-emerald-300/30 bg-emerald-300/10 text-emerald'
            : 'border-edge bg-surface-100/40 text-ink-secondary hover:text-ink-primary hover:bg-hover'}"
        >
          <span class="inline-flex items-center gap-1.5">
            <span class="h-1.5 w-1.5 rounded-full" style="background: {methodStyles[m].color};"></span>
            {methodStyles[m].label}
          </span>
          <span class="ml-1.5 rounded-md bg-surface-100 px-1.5 py-0.5 text-2xs text-ink-muted tabular-nums">{count}</span>
        </button>
      {/each}
    </div>

    <div class="ml-auto text-sm">
      <span class="text-ink-secondary">Total collected: </span>
      <span class="font-semibold text-emerald tabular-nums">{formatCurrency(filtered.reduce((s, e) => s + e.amount, 0))}</span>
    </div>
  </div>

  <div class="card mt-4 overflow-hidden">
    <div class="flex items-center justify-between px-5 py-4">
      <div>
        <p class="section-title">Collections by customer</p>
        <p class="mt-1 text-sm text-ink-secondary">Open a customer to see their payments and repayment progress, then call or message them to pay.</p>
      </div>
      <span class="rounded-md bg-surface-100 px-2 py-1 text-2xs text-ink-muted tabular-nums">{customerGroups.length} {customerGroups.length === 1 ? 'customer' : 'customers'}</span>
    </div>
    {#if !loading && customerGroups.length === 0}
      <div class="px-5 pb-6 text-sm text-ink-muted">No customer collections match this filter.</div>
    {:else if !loading}
      <div class="overflow-x-auto">
        <table class="data-table min-w-[860px]">
          <thead>
            <tr>
              <th class="px-4 py-3 font-semibold">Customer</th>
              <th class="px-4 py-3 font-semibold">Payments</th>
              <th class="px-4 py-3 text-right font-semibold">Collected</th>
              <th class="px-4 py-3 font-semibold">Repayment progress</th>
              <th class="px-4 py-3 font-semibold">Last payment</th>
              <th class="px-4 py-3 text-right font-semibold">Actions</th>
            </tr>
          </thead>
          <tbody>
            {#each customerGroups as group (group.customerId)}
              {@const isOpen = expanded === group.customerId}
              {@const account = customerById.get(group.customerId)}
              <tr class="border-b border-edge/60">
                <td class="px-4 py-3">
                  <div class="text-ink-primary">{group.customerName}</div>
                  {#if account}<div class="font-mono text-2xs text-ink-muted">{account.imei}</div>{/if}
                </td>
                <td class="px-4 py-3 tabular-nums text-ink-secondary">{group.count}</td>
                <td class="px-4 py-3 text-right font-semibold text-emerald tabular-nums">{formatCurrency(group.total)}</td>
                <td class="px-4 py-3">
                  {#if group.hasAccount}
                    <div class="flex items-center gap-2">
                      <div class="h-1.5 w-24 overflow-hidden rounded-full bg-surface-100">
                        <div class="h-full rounded-full bg-emerald" style="width: {group.percent}%"></div>
                      </div>
                      <span class="text-2xs text-ink-muted tabular-nums">{group.percent}%</span>
                    </div>
                    <div class="text-2xs text-ink-muted tabular-nums">{formatCurrency(group.amountPaid)} of {formatCurrency(group.totalLoan)}</div>
                  {:else}
                    <span class="text-2xs text-ink-muted">—</span>
                  {/if}
                </td>
                <td class="px-4 py-3">
                  <div class="text-ink-secondary">{formatRelative(group.lastEpoch)}</div>
                  <div class="text-2xs text-ink-muted">{formatDateTime(group.lastEpoch)}</div>
                </td>
                <td class="px-4 py-3">
                  <div class="flex flex-wrap justify-end gap-2">
                    <button type="button" class="btn-outline" aria-expanded={isOpen} on:click={() => (expanded = isOpen ? null : group.customerId)}>{isOpen ? 'Hide' : 'View'} payments</button>
                    {#if group.wa}
                      <a href="tel:{group.phone}" class="btn-outline">Call</a>
                      <a href="https://wa.me/{group.wa}?text={group.reminderEncoded}" target="_blank" rel="noopener noreferrer" class="btn-outline">Remind</a>
                    {/if}
                  </div>
                </td>
              </tr>
              {#if isOpen}
                <tr class="border-b border-edge/60 bg-surface-100/40">
                  <td colspan="6" class="px-4 py-3">
                    <div class="space-y-2">
                      {#each filtered.filter((e) => e.customerId === group.customerId) as entry (entry.id)}
                        <div class="flex items-center justify-between rounded-lg border border-edge/60 bg-surface-100/60 px-3 py-2">
                          <div>
                            <div class="text-ink-secondary">{formatDateTime(entry.dateEpochMillis)}</div>
                            <div class="font-mono text-2xs text-ink-muted">{entry.reference || entry.method}</div>
                          </div>
                          <div class="font-semibold text-emerald tabular-nums">{formatCurrency(entry.amount)}</div>
                        </div>
                      {/each}
                    </div>
                  </td>
                </tr>
              {/if}
            {/each}
          </tbody>
        </table>
      </div>
    {/if}
  </div>

  <div class="card mt-4 overflow-hidden">
    <div class="overflow-x-auto">
      <table class="data-table min-w-[760px]">
        <thead>
          <tr>
            <th class="px-4 py-3 font-semibold">Date</th>
            <th class="px-4 py-3 font-semibold">Customer</th>
            <th class="px-4 py-3 font-semibold">Reference</th>
            <th class="px-4 py-3 font-semibold">Method</th>
            <th class="px-4 py-3 text-right font-semibold">Amount</th>
          </tr>
        </thead>
        <tbody>
          {#each filtered as entry (entry.id)}
            <tr class="border-b border-edge/60 last:border-b-0 transition-colors hover:bg-hover">
              <td class="px-4 py-3">
                <div class="text-ink-secondary">{formatDateTime(entry.dateEpochMillis)}</div>
                <div class="text-2xs text-ink-muted">{formatRelative(entry.dateEpochMillis)}</div>
              </td>
              <td class="px-4 py-3">
                <div class="text-ink-primary">{entry.customerName}</div>
                <div class="font-mono text-2xs text-ink-muted">{entry.imei}</div>
              </td>
              <td class="px-4 py-3 font-mono text-2xs text-ink-muted">{entry.reference}</td>
              <td class="px-4 py-3">
                <span class={methodStyles[entry.method].chip}>
                  <span class="h-1.5 w-1.5 rounded-full" style="background: {methodStyles[entry.method].color};"></span>
                  {methodStyles[entry.method].label}
                </span>
              </td>
              <td class="px-4 py-3 text-right">
                <div class="font-semibold text-emerald tabular-nums">{formatCurrency(entry.amount)}</div>
              </td>
            </tr>
          {/each}

          {#if !loading && filtered.length === 0}
            <tr>
              <td colspan="5" class="px-4 py-14 text-center">
                <div class="mx-auto flex max-w-xs flex-col items-center gap-2">
                  <span class="flex h-10 w-10 items-center justify-center rounded-full bg-surface-100 text-ink-muted">
                    <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                      <path d="M4 6h16M4 12h16M4 18h10" stroke-linecap="round" />
                    </svg>
                  </span>
                  <p class="text-sm text-ink-secondary">No transactions match this filter.</p>
                </div>
              </td>
            </tr>
          {/if}

          {#if loading}
            <tr>
              <td colspan="5" class="px-4 py-14 text-center text-ink-secondary"> Loading ledger… </td>
            </tr>
          {/if}
        </tbody>
      </table>
    </div>
  </div>
</div>