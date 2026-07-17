<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import Modal from '$lib/components/ui/Modal.svelte';
  import { addDevice, createAccount, listDevices, listPlans } from '$lib/api/client';
  import { load } from '$lib/stores/customers';

  export let open = false;

  const dispatch = createEventDispatcher();

  let step: 'form' | 'success' = 'form';
  let submitting = false;
  let error = '';

  // form fields
  let customerName = '';
  let nationalId = '';
  let phoneNumber = '';
  let imei = '';
  let deviceModel = '';
  let addImeiToInventory = true;
  let planId = '';
  let dailyRate = '';
  let totalAmount = '';
  let termDays = '365';
  let downPayment = '0';

  // Plans from server
  let plans: { id: string; name: string; termDays: number; totalAmount: number; dailyRate: number; minDownPayment: number }[] = [];
  let loadingPlans = false;

  // Enrollment result
  let result: { accountNumber: string; temporaryPin: string; customerId: string; deviceImei: string } | null = null;

  $: if (open && plans.length === 0 && !loadingPlans) {
    loadingPlans = true;
    listPlans().then(p => { plans = p; }).catch(() => {}).finally(() => { loadingPlans = false; });
  }

  function pickPlan(id: string) {
    planId = id;
    const p = plans.find(pp => pp.id === id);
    if (p) {
      dailyRate = (p.dailyRate / 100).toFixed(2);
      totalAmount = (p.totalAmount / 100).toFixed(2);
      termDays = String(p.termDays);
      downPayment = (p.minDownPayment / 100).toFixed(2);
    }
  }

  function reset() {
    customerName = ''; nationalId = ''; phoneNumber = ''; imei = ''; deviceModel = '';
    planId = ''; dailyRate = ''; totalAmount = ''; termDays = '365'; downPayment = '0';
    addImeiToInventory = true; error = ''; submitting = false; step = 'form'; result = null;
  }

  async function submit() {
    error = '';
    if (!customerName.trim()) return error = 'Customer name is required';
    if (!phoneNumber.trim()) return error = 'Phone number is required';
    if (!/^\d{15}$/.test(imei.trim())) return error = 'IMEI must be exactly 15 digits';
    if (!deviceModel.trim()) return error = 'Device model is required';
    if (!dailyRate || Number(dailyRate) <= 0) return error = 'Valid daily rate is required';
    if (!totalAmount || Number(totalAmount) <= 0) return error = 'Valid total loan amount is required';
    if (!termDays || Number(termDays) <= 0) return error = 'Valid term in days is required';

    submitting = true;
    try {
      // Ensure device exists in inventory
      if (addImeiToInventory) {
        try { await addDevice(imei.trim(), deviceModel.trim()); }
        catch (e) {
          // If already exists, ignore (unique constraint); rethrow otherwise
          const msg = e instanceof Error ? e.message : String(e);
          if (!/already|exists|duplicate/i.test(msg)) throw e;
        }
      }

      const customer = await createAccount({
        customerName: customerName.trim(),
        nationalId: nationalId.trim(),
        phoneNumber: phoneNumber.trim(),
        imei: imei.trim(),
        planId: planId || undefined,
        dailyRate: Math.round(Number(dailyRate) * 100),
        totalAmount: Math.round(Number(totalAmount) * 100),
        termDays: Number(termDays),
        downPayment: Math.round(Number(downPayment || 0) * 100)
      });

      result = {
        accountNumber: customer.initialCredentials?.accountNumber || phoneNumber.trim(),
        temporaryPin: customer.initialCredentials?.temporaryPin || '',
        customerId: customer.id,
        deviceImei: imei.trim()
      };
      step = 'success';
      await load(); // refresh customers store
    } catch (e) {
      error = e instanceof Error ? e.message : 'Failed to create loan';
    } finally {
      submitting = false;
    }
  }

  function close() {
    reset();
    dispatch('close');
  }

  function goToProvision() {
    dispatch('provision', { imei: imei.trim(), customerId: result?.customerId });
    close();
  }
</script>

<Modal open={open} title={step === 'form' ? 'New Loan · Enroll Customer' : 'Customer Enrolled'} on:close={close} size="lg">
  {#if step === 'form'}
    <div class="space-y-4">
      {#if error}
        <div class="rounded-lg border border-crimson/20 bg-crimson/10 px-3 py-2 text-sm text-crimson">{error}</div>
      {/if}

      <div>
        <p class="section-title mb-3">Customer</p>
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <label class="label" for="nl-name">Full name</label>
            <input id="nl-name" class="input" bind:value={customerName} placeholder="e.g. Kwame Mensah" />
          </div>
          <div>
            <label class="label" for="nl-phone">Phone number</label>
            <input id="nl-phone" class="input" bind:value={phoneNumber} placeholder="024 xxx xxxx" />
          </div>
          <div class="sm:col-span-2">
            <label class="label" for="nl-nid">Ghana Card / National ID</label>
            <input id="nl-nid" class="input" bind:value={nationalId} placeholder="GHA-xxxx-xxxx-xxxx" />
          </div>
        </div>
      </div>

      <div>
        <p class="section-title mb-3">Device</p>
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <label class="label" for="nl-imei">IMEI (15 digits)</label>
            <input id="nl-imei" class="input font-mono" bind:value={imei} maxlength={15} placeholder="35xxxxxxxxxxxxx" />
          </div>
          <div>
            <label class="label" for="nl-model">Device model</label>
            <input id="nl-model" class="input" bind:value={deviceModel} placeholder="e.g. Samsung A05s" />
          </div>
          <label class="sm:col-span-2 flex items-center gap-2 text-sm text-ink-secondary">
            <input type="checkbox" bind:checked={addImeiToInventory} class="h-4 w-4 rounded border-edge text-emerald" />
            Add this IMEI to inventory if it isn't already
          </label>
        </div>
      </div>

      <div>
        <p class="section-title mb-3">Loan plan</p>
        {#if plans.length > 0}
          <div class="mb-3 grid grid-cols-2 gap-2 sm:grid-cols-3">
            {#each plans as p (p.id)}
              <button
                type="button"
                on:click={() => pickPlan(p.id)}
                class="rounded-lg border px-3 py-2 text-left text-xs transition-colors
                       {planId === p.id ? 'border-emerald bg-emerald/10 text-emerald' : 'border-edge bg-surface-100 text-ink-secondary hover:bg-hover'}"
              >
                <p class="font-semibold">{p.name}</p>
                <p class="text-ink-muted">{p.termDays}d · GH₵{(p.totalAmount/100).toFixed(0)}</p>
              </button>
            {/each}
          </div>
        {/if}
        <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <div>
            <label class="label" for="nl-daily">Daily (GH₵)</label>
            <input id="nl-daily" type="number" step="0.01" class="input" bind:value={dailyRate} />
          </div>
          <div>
            <label class="label" for="nl-total">Total (GH₵)</label>
            <input id="nl-total" type="number" step="0.01" class="input" bind:value={totalAmount} />
          </div>
          <div>
            <label class="label" for="nl-term">Term (days)</label>
            <input id="nl-term" type="number" class="input" bind:value={termDays} />
          </div>
          <div>
            <label class="label" for="nl-down">Down (GH₵)</label>
            <input id="nl-down" type="number" step="0.01" class="input" bind:value={downPayment} />
          </div>
        </div>
      </div>
    </div>
  {:else if result}
    <div class="space-y-4 text-center">
      <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-emerald/10 text-emerald">
        <svg class="h-7 w-7" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
          <path d="M5 13l4 4L19 7" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
      <div>
        <p class="text-lg font-semibold text-ink-primary">Customer enrolled successfully</p>
        <p class="mt-1 text-sm text-ink-muted">Give these one-time credentials to the customer.</p>
      </div>
      <div class="rounded-lg border border-edge bg-surface-100 p-4 text-left font-mono text-sm space-y-2">
        <div class="flex justify-between gap-4">
          <span class="text-ink-muted">Account #</span>
          <span class="text-ink-primary">{result.accountNumber}</span>
        </div>
        {#if result.temporaryPin}
          <div class="flex justify-between gap-4">
            <span class="text-ink-muted">Temporary PIN</span>
            <span class="text-emerald font-semibold tracking-widest">{result.temporaryPin}</span>
          </div>
        {/if}
        <div class="flex justify-between gap-4">
          <span class="text-ink-muted">IMEI</span>
          <span class="text-ink-primary">{result.deviceImei}</span>
        </div>
      </div>
      <p class="text-xs text-amber bg-amber/10 border border-amber/20 rounded-lg p-3 text-left">
        <strong>Important:</strong> Save the temporary PIN now. It is shown once and cannot be retrieved. Next, generate a Device Owner QR to provision the phone.
      </p>
    </div>
  {/if}

  <svelte:fragment slot="footer">
    {#if step === 'form'}
      <button class="btn-outline" on:click={close} disabled={submitting}>Cancel</button>
      <button class="btn-primary" on:click={submit} disabled={submitting}>
        {#if submitting}
          <span class="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></span>
          Creating…
        {:else}Create loan{/if}
      </button>
    {:else}
      <button class="btn-outline" on:click={close}>Done</button>
      <button class="btn-primary" on:click={goToProvision}>
        Generate provisioning QR
        <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M5 12h14M13 5l7 7-7 7" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
    {/if}
  </svelte:fragment>
</Modal>
