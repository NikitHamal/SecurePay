<script lang="ts">
  import { onMount } from 'svelte';
  import { apiClient } from '$lib/api/client';
  import Card from '$lib/components/ui/Card.svelte';
  import Badge from '$lib/components/ui/Badge.svelte';
  import KpiCard from '$lib/components/ui/KpiCard.svelte';
  import PageHeader from '$lib/components/ui/PageHeader.svelte';
  import TopBar from '$lib/components/layout/TopBar.svelte';

  interface Sale {
    id: string;
    customerName: string;
    imei: string;
    deviceModel: string;
    planName: string;
    totalLoanAmount: number;
    amountPaid: number;
    remainingBalance: number;
    dailyRate: number;
    nextPaymentDueEpochMillis: number;
    status: string;
    downPayment: number;
    createdAt: number;
  }

  interface SalesData {
    sales: Sale[];
    summary: {
      totalSales: number;
      totalDownPayments: number;
      activeLoans: number;
      totalRevenue: number;
    };
  }

  let data: SalesData | null = null;
  let loading = true;
  let error = '';

  onMount(async () => {
    await fetchSales();
  });

  async function fetchSales() {
    loading = true;
    error = '';
    try {
      const res = await apiClient('/api/my-sales');
      if (!res.ok) throw new Error('Failed to fetch sales');
      data = await res.json();
    } catch (e) {
      error = e instanceof Error ? e.message : 'Unknown error';
    } finally {
      loading = false;
    }
  }

  function formatCurrency(amount: number): string {
    return `GH₵${(amount / 100).toFixed(2)}`;
  }

  function formatDate(timestamp: number): string {
    return new Date(timestamp).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  }

  function statusVariant(status: string): 'active' | 'warning' | 'locked' | 'default' {
    switch (status) {
      case 'ACTIVE': return 'active';
      case 'WARNING': return 'warning';
      case 'LOCKED': return 'locked';
      default: return 'default';
    }
  }
</script>

<div class="page">
  <TopBar showSearch={false} />

  <PageHeader title="My Sales" subtitle="View your sales performance and customer portfolio" />

  {#if loading}
    <div class="flex items-center justify-center py-12">
      <div class="h-8 w-8 animate-spin rounded-full border-2 border-emerald border-t-transparent"></div>
    </div>
  {:else if error}
    <Card>
      <div class="rounded-lg border border-crimson-200/30 bg-crimson-200/10 px-4 py-3 text-sm text-crimson">
        {error}
      </div>
    </Card>
  {:else if data}
    <!-- KPI Cards -->
    <div class="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <KpiCard
        title="Total Sales"
        value={data.summary.totalSales.toString()}
        sublabel="Customers enrolled"
        accent="emerald"
      />
      <KpiCard
        title="Active Loans"
        value={data.summary.activeLoans.toString()}
        sublabel="Currently paying"
        accent="emerald"
      />
      <KpiCard
        title="Down Payments"
        value={formatCurrency(data.summary.totalDownPayments)}
        sublabel="Collected upfront"
        accent="emerald"
      />
      <KpiCard
        title="Total Revenue"
        value={formatCurrency(data.summary.totalRevenue)}
        sublabel="All payments collected"
        accent="emerald"
      />
    </div>

    <!-- Sales List -->
    {#if data.sales.length === 0}
      <Card>
        <div class="flex flex-col items-center justify-center py-12 text-center">
          <svg class="mb-3 h-12 w-12 text-ink-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <p class="text-sm font-medium text-ink-primary">No sales yet</p>
          <p class="mt-1 text-xs text-ink-muted">Start enrolling customers to see your sales here</p>
        </div>
      </Card>
    {:else}
      <div class="grid gap-4">
        {#each data.sales as sale (sale.id)}
          <Card>
            <div class="flex items-start justify-between gap-4">
              <div class="flex-1">
                <div class="flex items-center gap-2">
                  <h3 class="text-base font-semibold text-ink-primary">{sale.customerName}</h3>
                  <Badge variant={statusVariant(sale.status)}>{sale.status}</Badge>
                </div>
                <div class="mt-2 grid gap-2 text-sm sm:grid-cols-2 lg:grid-cols-4">
                  <div>
                    <p class="text-xs text-ink-muted">Device</p>
                    <p class="text-ink-secondary">{sale.deviceModel}</p>
                    <p class="text-xs text-ink-muted">{sale.imei}</p>
                  </div>
                  <div>
                    <p class="text-xs text-ink-muted">Plan</p>
                    <p class="text-ink-secondary">{sale.planName}</p>
                    <p class="text-xs text-ink-muted">{formatCurrency(sale.dailyRate)}/day</p>
                  </div>
                  <div>
                    <p class="text-xs text-ink-muted">Loan Amount</p>
                    <p class="text-ink-secondary">{formatCurrency(sale.totalLoanAmount)}</p>
                    <p class="text-xs text-ink-muted">Paid: {formatCurrency(sale.amountPaid)}</p>
                  </div>
                  <div>
                    <p class="text-xs text-ink-muted">Remaining</p>
                    <p class="text-ink-secondary">{formatCurrency(sale.remainingBalance)}</p>
                    <p class="text-xs text-ink-muted">Due: {formatDate(sale.nextPaymentDueEpochMillis)}</p>
                  </div>
                </div>
                <p class="mt-2 text-xs text-ink-muted">
                  Enrolled on {formatDate(sale.createdAt)} · Down payment: {formatCurrency(sale.downPayment)}
                </p>
              </div>
            </div>
          </Card>
        {/each}
      </div>
    {/if}
  {/if}
</div>
