<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import Modal from '$lib/components/ui/Modal.svelte';
  import { apiClient } from '$lib/api/client';
  import { formatCurrency } from '$lib/utils/format';

  export let open = false;
  export let accountId = '';
  export let customerName = '';
  export let customerPhone = '';
  export let remainingBalance = 0; // pesewas

  const dispatch = createEventDispatcher();

  type Step = 'form' | 'otp' | 'processing' | 'success' | 'failed';
  let step: Step = 'form';
  let loading = false;
  let error = '';

  // form
  let amountGhs = '';
  let phone = '';
  let provider = 'mtn';
  let reference = '';

  // otp
  let otp = '';

  // result
  let statusMessage = '';

  $: amountPesewas = Math.round(Number(amountGhs || 0) * 100);
  $: amountValid = amountPesewas > 0 && amountPesewas <= remainingBalance;

  function reset() {
    step = 'form'; loading = false; error = ''; otp = ''; reference = ''; statusMessage = '';
  }

  function close() {
    reset();
    dispatch('close');
  }

  $: if (open && customerPhone && !phone) {
    phone = customerPhone.replace(/^\+233/, '0');
  }
  $: if (open && remainingBalance > 0 && !amountGhs) {
    amountGhs = ((remainingBalance > 0 ? Math.min(remainingBalance, 2000 * 100) : 0) / 100).toFixed(2);
  }

  async function initialize() {
    error = '';
    if (!amountValid) return error = 'Enter a valid amount up to the remaining balance.';
    if (!phone.trim()) return error = 'Enter the customer phone number.';

    loading = true;
    step = 'processing';
    try {
      const res = await apiClient('/api/paystack/initialize', {
        method: 'POST',
        body: JSON.stringify({
          accountId,
          amount: Number(amountGhs),
          phone,
          provider,
          channel: 'mobile_money'
        })
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.error || `Failed to initialize (${res.status})`);
      }
      const data = await res.json();
      reference = data.reference;
      statusMessage = data.displayText || 'Approve the payment prompt on your phone.';
      step = data.otpRequired ? 'otp' : 'processing';
      if (!data.otpRequired) {
        pollForSuccess();
      }
    } catch (e: any) {
      error = e.message || 'Failed to start payment';
      step = 'form';
    } finally {
      loading = false;
    }
  }

  async function submitOtp() {
    error = '';
    if (!/^\d{4,8}$/.test(otp)) return error = 'Enter the 4-8 digit OTP.';
    loading = true;
    try {
      const res = await apiClient('/api/paystack/otp', {
        method: 'POST',
        body: JSON.stringify({ reference, otp })
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.error || 'OTP failed');
      if (data.status === 'success') {
        step = 'success';
        statusMessage = 'Payment successful — the device will unlock automatically.';
        dispatch('paid');
      } else if (data.otpRequired) {
        error = data.message || 'Another OTP is required.';
      } else {
        step = 'processing';
        pollForSuccess();
      }
    } catch (e: any) {
      error = e.message || 'OTP failed';
    } finally {
      loading = false;
    }
  }

  let pollTimer: ReturnType<typeof setTimeout> | null = null;
  function stopPolling() { if (pollTimer) { clearTimeout(pollTimer); pollTimer = null; } }

  async function pollForSuccess() {
    stopPolling();
    let attempts = 0;
    const tick = async () => {
      attempts++;
      try {
        const res = await apiClient(`/api/paystack/verify/${encodeURIComponent(reference)}`);
        const data = await res.json().catch(() => null);
        if (data?.status === 'success' || data?.applied) {
          step = 'success';
          statusMessage = 'Payment successful — the device will unlock automatically.';
          dispatch('paid');
          return;
        }
        if (data?.status === 'failed' || data?.status === 'abandoned') {
          step = 'failed';
          statusMessage = data.gatewayResponse || 'Payment failed or was abandoned.';
          return;
        }
      } catch { /* ignore */ }
      if (attempts < 20) {
        pollTimer = setTimeout(tick, 3000);
      } else {
        step = 'failed';
        statusMessage = 'Timed out waiting for payment confirmation. Use "Verify" later.';
      }
    };
    pollTimer = setTimeout(tick, 2500);
  }

  function done() {
    stopPolling();
    close();
  }
</script>

<Modal open={open} title="Pay with Mobile Money" on:close={close} size="md">
  {#if step === 'form'}
    <div class="space-y-4">
      <div class="rounded-lg border border-edge bg-surface-100 px-3 py-2 text-sm">
        <p class="text-ink-muted text-xs">Customer</p>
        <p class="text-ink-primary font-medium">{customerName}</p>
        <p class="text-ink-muted text-xs mt-0.5">Remaining: <span class="text-ink-primary font-medium">{formatCurrency(remainingBalance)}</span></p>
      </div>
      {#if error}
        <div class="rounded-lg border border-crimson/20 bg-crimson/10 px-3 py-2 text-sm text-crimson">{error}</div>
      {/if}
      <div>
        <label class="label" for="pm-amount">Amount (GH₵)</label>
        <input id="pm-amount" type="number" step="0.01" min="0.01" class="input" bind:value={amountGhs} placeholder="e.g. 5.00" />
        <div class="mt-1 flex gap-2">
          <button type="button" class="chip hover:bg-hover" on:click={() => amountGhs = (remainingBalance / 100).toFixed(2)}>Pay full balance</button>
        </div>
      </div>
      <div>
        <label class="label" for="pm-phone">Mobile money number</label>
        <input id="pm-phone" type="tel" class="input" bind:value={phone} placeholder="055xxxxxxx" />
      </div>
      <div>
        <label class="label">Network</label>
        <div class="grid grid-cols-3 gap-2">
          {#each [
            { id: 'mtn', label: 'MTN MoMo' },
            { id: 'vod', label: 'Telecel' },
            { id: 'tgo', label: 'AirtelTigo' },
          ] as item, i}
            <button
              type="button"
              on:click={() => provider = item.id}
              class="rounded-lg border px-3 py-2 text-xs font-medium transition
                     {provider === item.id ? 'border-emerald bg-emerald/10 text-emerald' : 'border-edge bg-surface-100 text-ink-secondary hover:bg-hover'}"
            >{item.label}</button>
          {/each}
        </div>
      </div>
    </div>
  {:else if step === 'processing'}
    <div class="flex flex-col items-center justify-center py-8 text-center">
      <div class="mb-3 h-10 w-10 animate-spin rounded-full border-2 border-emerald border-t-transparent"></div>
      <p class="text-sm font-medium text-ink-primary">Waiting for confirmation…</p>
      <p class="mt-1 max-w-xs text-xs text-ink-muted">{statusMessage || 'Check the phone to approve the payment.'}</p>
      <p class="mt-3 text-[11px] text-ink-muted font-mono">Ref: {reference}</p>
    </div>
  {:else if step === 'otp'}
    <div class="space-y-4">
      {#if error}<div class="rounded-lg border border-crimson/20 bg-crimson/10 px-3 py-2 text-sm text-crimson">{error}</div>{/if}
      <div class="rounded-lg border border-amber/20 bg-amber/10 px-3 py-2 text-sm text-amber">
        {statusMessage || 'Enter the OTP sent to the customer phone.'}
      </div>
      <div>
        <label class="label" for="pm-otp">OTP</label>
        <input id="pm-otp" type="text" inputmode="numeric" maxlength="8" class="input text-center text-lg tracking-[0.5em] font-mono" bind:value={otp} placeholder="••••" />
      </div>
      <p class="text-[11px] text-ink-muted font-mono">Ref: {reference}</p>
    </div>
  {:else if step === 'success'}
    <div class="flex flex-col items-center justify-center py-6 text-center">
      <div class="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-emerald/10 text-emerald">
        <svg class="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M5 13l4 4L19 7" stroke-linecap="round" stroke-linejoin="round"/></svg>
      </div>
      <p class="text-base font-semibold text-ink-primary">Payment confirmed</p>
      <p class="mt-1 max-w-xs text-sm text-ink-muted">{statusMessage}</p>
    </div>
  {:else if step === 'failed'}
    <div class="flex flex-col items-center justify-center py-6 text-center">
      <div class="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-crimson/10 text-crimson">
        <svg class="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M6 6l12 12M6 18L18 6" stroke-linecap="round"/></svg>
      </div>
      <p class="text-base font-semibold text-ink-primary">Payment not completed</p>
      <p class="mt-1 max-w-xs text-sm text-ink-muted">{statusMessage}</p>
    </div>
  {/if}

  <svelte:fragment slot="footer">
    {#if step === 'form'}
      <button class="btn-outline" on:click={close} disabled={loading}>Cancel</button>
      <button class="btn-primary" on:click={initialize} disabled={loading || !amountValid}>
        {#if loading}<span class="h-4 w-4 animate-spin rounded-full border-2 border-[color:var(--avatar-text)] border-t-transparent"></span>{/if}
        Request payment
      </button>
    {:else if step === 'otp'}
      <button class="btn-outline" on:click={() => { step = 'form'; error = ''; }}>Change</button>
      <button class="btn-primary" on:click={submitOtp} disabled={loading || !otp}>Submit OTP</button>
    {:else if step === 'processing'}
      <button class="btn-outline" on:click={() => { stopPolling(); step = 'form'; }}>Cancel</button>
      <button class="btn-primary" on:click={pollForSuccess}>Refresh status</button>
    {:else}
      <button class="btn-primary" on:click={done}>Done</button>
    {/if}
  </svelte:fragment>
</Modal>
