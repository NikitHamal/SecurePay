<script lang="ts">
  import { onMount } from 'svelte';
  import { page } from '$app/stores';
  import { formatCurrency, formatDate, formatPhone } from '$lib/utils/format';

  interface AccountDetails {
    id: string;
    customerName: string;
    accountNumber: string;
    phone: string;
    deviceModel: string;
    totalLoanAmount: number; // pesewas
    amountPaid: number; // pesewas
    remainingBalance: number; // pesewas
    dailyRate: number; // pesewas
    nextPaymentDue: number; // seconds or ms
    status: string;
  }

  let searchQuery = '';
  let searching = false;
  let searchError = '';
  let account: AccountDetails | null = null;

  // Payment form state
  let amountGhs = '';
  let channel: 'mobile_money' | 'card' = 'mobile_money';
  let phone = '';
  let provider: 'mtn' | 'vod' | 'atl' = 'mtn';
  let email = '';

  let submitting = false;
  let paymentError = '';
  let paymentReference = '';
  let paymentStep: 'form' | 'waiting' | 'success' | 'failed' = 'form';
  let pollTimer: any = null;
  let pollAttempts = 0;
  let statusMessage = '';

  $: remainingGhs = account ? (account.remainingBalance / 100).toFixed(2) : '0.00';
  $: dailyRateGhs = account ? (account.dailyRate / 100).toFixed(2) : '0.00';
  $: amountPesewas = Math.round(Number(amountGhs || 0) * 100);
  $: amountValid = account && amountPesewas > 0 && amountPesewas <= account.remainingBalance;

  onMount(() => {
    const q = $page.url.searchParams.get('account') || $page.url.searchParams.get('acc') || $page.url.searchParams.get('phone');
    if (q) {
      searchQuery = q.trim();
      lookupAccount();
    }
  });

  function detectProvider(p: string): 'mtn' | 'vod' | 'atl' {
    const clean = p.replace(/\D/g, '');
    const prefix = clean.startsWith('233') ? '0' + clean.slice(3, 5) : clean.slice(0, 3);
    if (['024', '054', '055', '059', '053', '025'].includes(prefix)) return 'mtn';
    if (['020', '050'].includes(prefix)) return 'vod';
    if (['026', '056', '027', '057'].includes(prefix)) return 'atl';
    return 'mtn';
  }

  async function lookupAccount() {
    if (!searchQuery.trim()) return;
    searching = true;
    searchError = '';
    account = null;
    paymentStep = 'form';

    try {
      const res = await fetch(`/api/pay/lookup?account=${encodeURIComponent(searchQuery.trim())}`);
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Account not found');
      account = data;
      phone = account?.phone ? account.phone.replace(/^\+233/, '0') : '';
      provider = detectProvider(phone);
      // Default amount to daily rate or remaining balance
      if (account) {
        const defaultAmt = account.dailyRate > 0 && account.dailyRate <= account.remainingBalance
          ? account.dailyRate
          : account.remainingBalance;
        amountGhs = (defaultAmt / 100).toFixed(2);
      }
    } catch (e: any) {
      searchError = e.message || 'Could not find account';
    } finally {
      searching = false;
    }
  }

  function setAmount(ghs: number) {
    if (!account) return;
    const clamped = Math.min(ghs, account.remainingBalance / 100);
    amountGhs = clamped.toFixed(2);
  }

  async function startPayment() {
    if (!account || !amountValid) return;
    paymentError = '';
    submitting = true;

    try {
      const res = await fetch('/api/pay/initialize', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          accountId: account.id,
          amount: Number(amountGhs),
          channel,
          phone,
          provider,
          email
        })
      });

      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Payment initiation failed');

      if (data.mode === 'redirect' && data.authorizationUrl) {
        window.location.href = data.authorizationUrl;
        return;
      }

      paymentReference = data.reference;
      statusMessage = data.displayText || 'Payment request sent! Please enter your PIN on your phone.';
      paymentStep = 'waiting';
      pollAttempts = 0;
      startPolling(paymentReference);
    } catch (e: any) {
      paymentError = e.message || 'Failed to start payment';
      paymentStep = 'form';
    } finally {
      submitting = false;
    }
  }

  function startPolling(ref: string) {
    if (pollTimer) clearInterval(pollTimer);
    pollTimer = setInterval(async () => {
      pollAttempts++;
      if (pollAttempts > 45) { // 90 seconds timeout
        clearInterval(pollTimer);
        paymentStep = 'failed';
        paymentError = 'Payment request timed out. If you already approved with PIN, your account will update automatically within 1-2 minutes.';
        return;
      }

      try {
        const res = await fetch(`/api/pay/verify/${encodeURIComponent(ref)}`);
        const data = await res.json();
        if (data.status === 'success') {
          clearInterval(pollTimer);
          paymentStep = 'success';
          if (account) {
            account.amountPaid += amountPesewas;
            account.remainingBalance = Math.max(0, account.remainingBalance - amountPesewas);
            if (account.status === 'LOCKED' && account.remainingBalance > 0) {
              account.status = 'ACTIVE';
            }
          }
        } else if (data.status === 'failed') {
          clearInterval(pollTimer);
          paymentStep = 'failed';
          paymentError = data.gatewayResponse || 'Payment was declined or cancelled.';
        }
      } catch (e) {
        // Ignore network glitch during poll
      }
    }, 2000);
  }
</script>

<svelte:head>
  <title>Pay TouchBase Installment | TouchBase Phones</title>
</svelte:head>

<div class="min-h-screen bg-surface-50 text-ink-primary font-sans antialiased flex flex-col justify-between">
  <!-- Top Navigation Header -->
  <header class="border-b border-edge bg-surface-base/80 backdrop-blur sticky top-0 z-30">
    <div class="max-w-xl mx-auto px-4 py-3.5 flex items-center justify-between">
      <div class="flex items-center gap-2.5">
        <div class="h-9 w-9 rounded-xl bg-emerald flex items-center justify-center shadow-sm">
          <svg class="h-5 w-5 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 2v20M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6" />
          </svg>
        </div>
        <div>
          <span class="font-bold text-base tracking-tight text-ink-primary">TouchBase</span>
          <span class="ml-1 text-2xs uppercase tracking-wider font-semibold px-1.5 py-0.5 rounded bg-emerald/10 text-emerald">Pay</span>
        </div>
      </div>
      <div class="text-xs text-ink-muted flex items-center gap-1.5">
        <span class="h-2 w-2 rounded-full bg-emerald animate-pulse"></span>
        <span>Secure Paystack Gateway</span>
      </div>
    </div>
  </header>

  <!-- Main Content Body -->
  <main class="flex-1 max-w-xl w-full mx-auto p-4 sm:p-6 space-y-5">
    {#if !account}
      <!-- Step 1: Lookup Form -->
      <div class="bg-surface-base rounded-2xl border border-edge p-6 shadow-sm space-y-5">
        <div class="text-center space-y-1.5">
          <h1 class="text-xl font-bold text-ink-primary">Pay Your Device Installment</h1>
          <p class="text-sm text-ink-secondary">Enter your TouchBase account number or registered phone number</p>
        </div>

        <form on:submit|preventDefault={lookupAccount} class="space-y-4">
          <div>
            <label for="searchQuery" class="block text-xs font-semibold uppercase tracking-wider text-ink-muted mb-1.5">
              Account or Phone Number
            </label>
            <div class="relative">
              <input
                id="searchQuery"
                type="text"
                bind:value={searchQuery}
                placeholder="e.g. 0537995936 or ACC-..."
                class="w-full px-4 py-3 rounded-xl border border-edge bg-surface-50 text-ink-primary placeholder:text-ink-muted focus:outline-none focus:ring-2 focus:ring-emerald focus:border-transparent text-base"
                required
              />
            </div>
          </div>

          {#if searchError}
            <div class="p-3 rounded-xl bg-crimson/10 border border-crimson/20 text-crimson text-xs flex items-center gap-2">
              <svg class="h-4 w-4 shrink-0" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/></svg>
              <span>{searchError}</span>
            </div>
          {/if}

          <button
            type="submit"
            disabled={searching || !searchQuery.trim()}
            class="w-full py-3.5 px-4 rounded-xl bg-emerald hover:bg-emerald/90 active:scale-[0.99] text-white font-semibold text-sm transition shadow-sm disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
          >
            {#if searching}
              <div class="h-4 w-4 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
              <span>Searching Account...</span>
            {:else}
              <span>Find Account & Pay</span>
              <svg class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10.293 3.293a1 1 0 011.414 0l6 6a1 1 0 010 1.414l-6 6a1 1 0 01-1.414-1.414L14.586 11H3a1 1 0 110-2h11.586l-4.293-4.293a1 1 0 010-1.414z" clip-rule="evenodd"/></svg>
            {/if}
          </button>
        </form>

        <div class="pt-4 border-t border-edge flex items-center justify-center gap-2 text-2xs text-ink-muted text-center">
          <svg class="h-4 w-4 text-emerald" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
          <span>No internet? You can also dial <strong>*920*264#</strong> on any phone to pay!</span>
        </div>
      </div>
    {:else}
      <!-- Step 2: Account Details & Payment Section -->
      <div class="space-y-4">
        <!-- Customer Info Card -->
        <div class="bg-surface-base rounded-2xl border border-edge p-5 shadow-sm space-y-4">
          <div class="flex items-start justify-between">
            <div>
              <span class="text-xs text-ink-muted font-medium">Customer Account</span>
              <h2 class="text-lg font-bold text-ink-primary">{account.customerName}</h2>
              <p class="text-xs text-ink-secondary">{formatPhone(account.phone)} · {account.deviceModel}</p>
            </div>
            <div>
              {#if account.status === 'LOCKED'}
                <span class="px-2.5 py-1 rounded-full text-xs font-semibold bg-crimson/10 text-crimson border border-crimson/20">LOCKED</span>
              {:else if account.status === 'COMPLETED'}
                <span class="px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald/10 text-emerald border border-emerald/20">PAID OFF</span>
              {:else}
                <span class="px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald/10 text-emerald border border-emerald/20">ACTIVE</span>
              {/if}
            </div>
          </div>

          <!-- Balance progress -->
          <div class="space-y-2 pt-2 border-t border-edge">
            <div class="flex justify-between text-xs">
              <span class="text-ink-muted">Paid: <strong class="text-ink-primary">{formatCurrency(account.amountPaid)}</strong></span>
              <span class="text-ink-muted">Remaining: <strong class="text-emerald">{formatCurrency(account.remainingBalance)}</strong></span>
            </div>
            <div class="h-2 w-full bg-surface-100 rounded-full overflow-hidden">
              <div
                class="h-full bg-emerald transition-all duration-500 rounded-full"
                style="width: {Math.min(100, Math.round((account.amountPaid / Math.max(1, account.totalLoanAmount)) * 100))}%"
              ></div>
            </div>
            <div class="flex justify-between text-2xs text-ink-muted">
              <span>Daily Rate: {formatCurrency(account.dailyRate)}/day</span>
              {#if account.nextPaymentDue > 0}
                <span>Due: {formatDate(account.nextPaymentDue)}</span>
              {/if}
            </div>
          </div>
        </div>

        {#if paymentStep === 'form'}
          <!-- Payment Selection Form -->
          <div class="bg-surface-base rounded-2xl border border-edge p-5 shadow-sm space-y-5">
            <h3 class="text-sm font-bold text-ink-primary uppercase tracking-wider">Make a Payment</h3>

            <!-- Amount presets -->
            <div class="space-y-2">
              <span class="text-xs font-medium text-ink-secondary">Select Amount (GH₵)</span>
              <div class="grid grid-cols-3 gap-2">
                {#if account.dailyRate > 0 && account.dailyRate <= account.remainingBalance}
                  <button
                    type="button"
                    class="py-2.5 px-3 rounded-xl border text-xs font-semibold transition {amountPesewas === account.dailyRate ? 'border-emerald bg-emerald/10 text-emerald' : 'border-edge bg-surface-50 text-ink-secondary hover:bg-hover'}"
                    on:click={() => setAmount(account.dailyRate / 100)}
                  >
                    1 Day<br/><span class="text-2xs font-normal">GH₵ {(account.dailyRate / 100).toFixed(2)}</span>
                  </button>
                {/if}
                {#if account.dailyRate * 7 > 0 && account.dailyRate * 7 <= account.remainingBalance}
                  <button
                    type="button"
                    class="py-2.5 px-3 rounded-xl border text-xs font-semibold transition {amountPesewas === account.dailyRate * 7 ? 'border-emerald bg-emerald/10 text-emerald' : 'border-edge bg-surface-50 text-ink-secondary hover:bg-hover'}"
                    on:click={() => setAmount((account.dailyRate * 7) / 100)}
                  >
                    1 Week<br/><span class="text-2xs font-normal">GH₵ {((account.dailyRate * 7) / 100).toFixed(2)}</span>
                  </button>
                {/if}
                <button
                  type="button"
                  class="py-2.5 px-3 rounded-xl border text-xs font-semibold transition {amountPesewas === account.remainingBalance ? 'border-emerald bg-emerald/10 text-emerald' : 'border-edge bg-surface-50 text-ink-secondary hover:bg-hover'}"
                  on:click={() => setAmount(account.remainingBalance / 100)}
                >
                  Pay in Full<br/><span class="text-2xs font-normal">GH₵ {remainingGhs}</span>
                </button>
              </div>

              <!-- Custom amount input -->
              <div class="pt-2">
                <div class="relative">
                  <span class="absolute left-4 top-1/2 -translate-y-1/2 text-sm font-semibold text-ink-muted">GH₵</span>
                  <input
                    type="number"
                    step="0.5"
                    min="1"
                    max={remainingGhs}
                    bind:value={amountGhs}
                    placeholder="Enter custom amount"
                    class="w-full pl-14 pr-4 py-3 rounded-xl border border-edge bg-surface-50 text-ink-primary text-base font-semibold focus:outline-none focus:ring-2 focus:ring-emerald focus:border-transparent"
                  />
                </div>
              </div>
            </div>

            <!-- Payment Method Tabs -->
            <div class="space-y-3 pt-2">
              <span class="text-xs font-medium text-ink-secondary">Payment Method</span>
              <div class="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  class="p-3 rounded-xl border text-left transition flex items-center gap-2.5 {channel === 'mobile_money' ? 'border-emerald bg-emerald/10 text-emerald' : 'border-edge bg-surface-50 text-ink-secondary hover:bg-hover'}"
                  on:click={() => channel = 'mobile_money'}
                >
                  <svg class="h-5 w-5 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="5" y="2" width="14" height="20" rx="2"/><path d="M12 18h.01"/></svg>
                  <div>
                    <p class="text-xs font-bold leading-none">Mobile Money</p>
                    <p class="text-2xs text-ink-muted mt-0.5">MTN / Telecel / AT</p>
                  </div>
                </button>
                <button
                  type="button"
                  class="p-3 rounded-xl border text-left transition flex items-center gap-2.5 {channel === 'card' ? 'border-emerald bg-emerald/10 text-emerald' : 'border-edge bg-surface-50 text-ink-secondary hover:bg-hover'}"
                  on:click={() => channel = 'card'}
                >
                  <svg class="h-5 w-5 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="1" y="4" width="22" height="16" rx="2"/><line x1="1" y1="10" x2="23" y2="10"/></svg>
                  <div>
                    <p class="text-xs font-bold leading-none">Card / Checkout</p>
                    <p class="text-2xs text-ink-muted mt-0.5">Visa / Mastercard</p>
                  </div>
                </button>
              </div>

              {#if channel === 'mobile_money'}
                <div class="space-y-3 pt-2">
                  <div>
                    <label for="momoPhone" class="block text-xs font-medium text-ink-secondary mb-1">MoMo Wallet Phone Number</label>
                    <input
                      id="momoPhone"
                      type="tel"
                      bind:value={phone}
                      on:input={() => provider = detectProvider(phone)}
                      placeholder="e.g. 055xxxxxxx"
                      class="w-full px-4 py-2.5 rounded-xl border border-edge bg-surface-50 text-ink-primary text-sm focus:outline-none focus:ring-2 focus:ring-emerald focus:border-transparent"
                    />
                  </div>

                  <div>
                    <span class="block text-xs font-medium text-ink-secondary mb-1">Network Provider</span>
                    <div class="grid grid-cols-3 gap-2">
                      <button
                        type="button"
                        class="py-2 px-3 rounded-xl border text-xs font-bold text-center {provider === 'mtn' ? 'border-amber bg-amber/10 text-amber' : 'border-edge bg-surface-50 text-ink-muted'}"
                        on:click={() => provider = 'mtn'}
                      >
                        MTN MoMo
                      </button>
                      <button
                        type="button"
                        class="py-2 px-3 rounded-xl border text-xs font-bold text-center {provider === 'vod' ? 'border-crimson bg-crimson/10 text-crimson' : 'border-edge bg-surface-50 text-ink-muted'}"
                        on:click={() => provider = 'vod'}
                      >
                        Telecel Cash
                      </button>
                      <button
                        type="button"
                        class="py-2 px-3 rounded-xl border text-xs font-bold text-center {provider === 'atl' ? 'border-sky bg-sky/10 text-sky' : 'border-edge bg-surface-50 text-ink-muted'}"
                        on:click={() => provider = 'atl'}
                      >
                        AirtelTigo
                      </button>
                    </div>
                  </div>
                </div>
              {/if}
            </div>

            {#if paymentError}
              <div class="p-3 rounded-xl bg-crimson/10 border border-crimson/20 text-crimson text-xs flex items-center gap-2">
                <svg class="h-4 w-4 shrink-0" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/></svg>
                <span>{paymentError}</span>
              </div>
            {/if}

            <button
              type="button"
              disabled={submitting || !amountValid}
              on:click={startPayment}
              class="w-full py-3.5 px-4 rounded-xl bg-emerald hover:bg-emerald/90 active:scale-[0.99] text-white font-bold text-sm transition shadow-sm disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
            >
              {#if submitting}
                <div class="h-4 w-4 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
                <span>Connecting to Gateway...</span>
              {:else}
                <span>Authorize GH₵ {Number(amountGhs || 0).toFixed(2)} Payment</span>
              {/if}
            </button>

            <button
              type="button"
              class="w-full text-center text-xs text-ink-muted hover:text-ink-primary transition"
              on:click={() => { account = null; searchQuery = ''; }}
            >
              ← Search a different account
            </button>
          </div>
        {:else if paymentStep === 'waiting'}
          <!-- Waiting for MoMo PIN Approval -->
          <div class="bg-surface-base rounded-2xl border border-edge p-6 shadow-sm text-center space-y-4">
            <div class="h-14 w-14 rounded-full bg-emerald/10 border border-emerald/20 text-emerald flex items-center justify-center mx-auto animate-pulse">
              <svg class="h-7 w-7" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
            </div>
            <div class="space-y-1">
              <h3 class="text-base font-bold text-ink-primary">Approve on Your Phone</h3>
              <p class="text-sm text-ink-secondary">{statusMessage}</p>
            </div>
            <div class="p-3.5 rounded-xl bg-surface-50 border border-edge text-xs text-ink-muted space-y-1 text-left">
              <p class="font-semibold text-ink-primary">Didn't see the prompt?</p>
              {#if provider === 'mtn'}
                <p>Dial <strong>*170#</strong> &gt; <strong>Wallet</strong> &gt; <strong>3. My Approvals</strong> to authorize.</p>
              {:else if provider === 'vod'}
                <p>Dial <strong>*110#</strong> &gt; <strong>My Account</strong> &gt; <strong>Approvals</strong>.</p>
              {:else}
                <p>Check your pending transaction approvals on your mobile money menu.</p>
              {/if}
            </div>
            <div class="flex items-center justify-center gap-2 text-xs text-ink-muted">
              <div class="h-3 w-3 border-2 border-emerald border-t-transparent rounded-full animate-spin"></div>
              <span>Waiting for confirmation... ({45 - pollAttempts}s)</span>
            </div>
          </div>
        {:else if paymentStep === 'success'}
          <!-- Payment Success Confirmation -->
          <div class="bg-surface-base rounded-2xl border border-edge p-6 shadow-sm text-center space-y-5">
            <div class="h-16 w-16 rounded-full bg-emerald/10 text-emerald flex items-center justify-center mx-auto">
              <svg class="h-8 w-8" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/></svg>
            </div>
            <div class="space-y-1">
              <h3 class="text-xl font-bold text-ink-primary">Payment Successful!</h3>
              <p class="text-sm text-ink-secondary">Thank you! Your payment of <strong>GH₵ {Number(amountGhs).toFixed(2)}</strong> was received.</p>
            </div>

            <div class="p-4 rounded-xl bg-surface-50 border border-edge text-xs space-y-2 text-left">
              <div class="flex justify-between">
                <span class="text-ink-muted">Reference:</span>
                <span class="font-mono text-ink-primary">{paymentReference}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-ink-muted">Remaining Balance:</span>
                <span class="font-bold text-emerald">{formatCurrency(account.remainingBalance)}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-ink-muted">Device Status:</span>
                <span class="font-bold text-emerald">UNLOCKED / ACTIVE</span>
              </div>
            </div>

            <button
              type="button"
              class="w-full py-3 px-4 rounded-xl bg-emerald text-white font-bold text-sm hover:bg-emerald/90 transition"
              on:click={() => { paymentStep = 'form'; }}
            >
              Done
            </button>
          </div>
        {:else if paymentStep === 'failed'}
          <!-- Payment Failed Screen -->
          <div class="bg-surface-base rounded-2xl border border-edge p-6 shadow-sm text-center space-y-4">
            <div class="h-14 w-14 rounded-full bg-crimson/10 text-crimson flex items-center justify-center mx-auto">
              <svg class="h-7 w-7" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd"/></svg>
            </div>
            <div class="space-y-1">
              <h3 class="text-base font-bold text-ink-primary">Payment Incomplete</h3>
              <p class="text-xs text-crimson">{paymentError}</p>
            </div>
            <button
              type="button"
              class="w-full py-3 px-4 rounded-xl bg-surface-100 hover:bg-hover text-ink-primary font-semibold text-sm transition"
              on:click={() => { paymentStep = 'form'; paymentError = ''; }}
            >
              Try Again
            </button>
          </div>
        {/if}
      </div>
    {/if}
  </main>

  <!-- Footer -->
  <footer class="border-t border-edge bg-surface-base/50 py-4 text-center text-xs text-ink-muted">
    <p>© TouchBase Phones · Powered by SecurePay · Secured by Paystack</p>
  </footer>
</div>
