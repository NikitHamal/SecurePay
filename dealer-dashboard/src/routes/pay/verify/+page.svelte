<script lang="ts">
  import { onMount } from 'svelte';
  import { page } from '$app/stores';
  import { formatCurrency } from '$lib/utils/format';

  let reference = '';
  let status: 'verifying' | 'success' | 'failed' = 'verifying';
  let message = 'Verifying your payment...';
  let amount = 0;

  onMount(async () => {
    reference = $page.url.searchParams.get('reference') || $page.url.searchParams.get('trxref') || '';
    if (!reference) {
      status = 'failed';
      message = 'No payment reference provided.';
      return;
    }

    try {
      const res = await fetch(`/api/pay/verify/${encodeURIComponent(reference)}`);
      const data = await res.json();
      if (data.status === 'success') {
        status = 'success';
        amount = data.amount || 0;
        message = 'Payment completed successfully!';
      } else {
        status = 'failed';
        message = data.gatewayResponse || 'Payment verification failed.';
      }
    } catch (e: any) {
      status = 'failed';
      message = e.message || 'Network error verifying payment.';
    }
  });
</script>

<svelte:head>
  <title>Payment Verification | TouchBase</title>
</svelte:head>

<div class="min-h-screen bg-surface-50 text-ink-primary font-sans antialiased flex flex-col justify-center items-center p-4">
  <div class="max-w-md w-full bg-surface-base rounded-2xl border border-edge p-6 shadow-sm text-center space-y-4">
    {#if status === 'verifying'}
      <div class="h-14 w-14 rounded-full bg-emerald/10 border border-emerald/20 text-emerald flex items-center justify-center mx-auto">
        <div class="h-6 w-6 border-2 border-emerald border-t-transparent rounded-full animate-spin"></div>
      </div>
      <h2 class="text-lg font-bold text-ink-primary">Verifying Payment</h2>
      <p class="text-xs text-ink-muted">Please wait while we confirm your payment with Paystack...</p>
    {:else if status === 'success'}
      <div class="h-16 w-16 rounded-full bg-emerald/10 text-emerald flex items-center justify-center mx-auto">
        <svg class="h-8 w-8" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/></svg>
      </div>
      <h2 class="text-xl font-bold text-ink-primary">Payment Successful!</h2>
      {#if amount > 0}
        <p class="text-sm font-semibold text-emerald">{formatCurrency(amount)} Received</p>
      {/if}
      <p class="text-xs text-ink-secondary">Your account balance and device status have been updated.</p>
      <div class="p-3 bg-surface-50 rounded-xl border border-edge text-2xs font-mono text-ink-muted">
        Ref: {reference}
      </div>
      <a href="/pay" class="inline-block w-full py-3 px-4 rounded-xl bg-emerald text-white font-bold text-sm hover:bg-emerald/90 transition">
        Return to Payments
      </a>
    {:else}
      <div class="h-14 w-14 rounded-full bg-crimson/10 text-crimson flex items-center justify-center mx-auto">
        <svg class="h-7 w-7" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd"/></svg>
      </div>
      <h2 class="text-base font-bold text-ink-primary">Payment Incomplete</h2>
      <p class="text-xs text-crimson">{message}</p>
      <a href="/pay" class="inline-block w-full py-3 px-4 rounded-xl bg-surface-100 text-ink-primary font-semibold text-sm hover:bg-hover transition">
        Back to Pay
      </a>
    {/if}
  </div>
</div>
