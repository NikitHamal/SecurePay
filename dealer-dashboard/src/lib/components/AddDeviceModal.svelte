<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import Modal from '$lib/components/ui/Modal.svelte';
  import { addDevice, listProductModels, type ProductModel } from '$lib/api/client';
  import { onMount } from 'svelte';

  export let open = false;

  const dispatch = createEventDispatcher();

  let submitting = false;
  let error = '';
  let imei = '';
  let model = '';
  let productModels: ProductModel[] = [];
  let selectedProdId: string = '';
  let totalGhs = '';
  let downGhs = '';
  let dailyGhs = '';
  let termDays = '';
  let assignNow = '';
  let agents: { id: string; name: string }[] = [];
  let added: { imei: string; model: string } | null = null;

  let selectedProd: ProductModel | null = null;
  $: if (open && productModels.length === 0) {
    listProductModels().then(p => productModels = p).catch(()=>{});
    fetch('/api/agents').then(r => r.ok ? r.json() : []).then(a => agents = Array.isArray(a) ? a : []).catch(()=>{});
  }
  $: selectedProd = productModels.find(p => p.id === selectedProdId) || null;

  function reset() {
    imei = ''; model = ''; error = ''; submitting = false; added = null;
    selectedProdId=''; totalGhs=''; downGhs=''; dailyGhs=''; termDays=''; assignNow='';
  }

  async function submit() {
    error = '';
    if (!/^\d{15}$/.test(imei.trim())) return error = 'IMEI must be exactly 15 digits';
    if (!model.trim()) return error = 'Device model is required';
    // Pricing: either product or manual
    let opts: any = {};
    if (selectedProdId) {
      opts.productModelId = selectedProdId;
    } else {
      const total = Math.round(parseFloat(totalGhs || '0')*100);
      const down = Math.round(parseFloat(downGhs || '0')*100);
      const daily = Math.round(parseFloat(dailyGhs || '0')*100);
      const term = parseInt(termDays || '0',10);
      if (!total || !daily || !term) return error = 'Set pricing via product or manual Total/Daily/Term';
      if (down > total) return error = 'Down payment cannot exceed total';
      opts.totalAmount = total; opts.downPayment = down; opts.dailyRate = daily; opts.termDays = term;
    }
    if (assignNow) opts.assignedTo = assignNow;
    submitting = true;
    try {
      const res = await addDevice(imei.trim(), model.trim(), opts);
      added = { imei: res.imei, model: res.model };
    } catch (e) {
      error = e instanceof Error ? e.message : 'Failed to add device';
    } finally {
      submitting = false;
    }
  }

  function close() {
    reset();
    dispatch('close');
  }

  function enrollNow() {
    const prefill = added ? { imei: added.imei, deviceModel: added.model } : {};
    reset();
    dispatch('close');
    dispatch('enroll', prefill);
  }
</script>

<Modal open={open} title={added ? 'Device added to inventory' : 'Add Device to Inventory'} on:close={close} size="sm">
  {#if added}
    <div class="space-y-4 text-center">
      <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-full" style="background: var(--success-soft, rgba(16,185,129,0.14)); color: var(--success, #10B981);">
        <svg class="h-7 w-7" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
          <path d="M5 13l4 4L19 7" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
      <div>
        <p class="text-base font-semibold text-ink-primary">{added.model}</p>
        <p class="font-mono text-sm text-ink-muted">{added.imei}</p>
      </div>
      <p class="text-sm text-ink-secondary">Device is now in stock. Enroll it to a customer to start the financing and provisioning flow.</p>
    </div>
  {:else}
    {#if error}
      <div class="mb-4 rounded-lg border border-crimson/20 bg-crimson/10 px-3 py-2 text-sm text-crimson">{error}</div>
    {/if}
    <div class="space-y-3">
      <div>
        <label class="label" for="ad-imei">IMEI (15 digits)</label>
        <input id="ad-imei" class="input font-mono" bind:value={imei} maxlength={15} placeholder="35xxxxxxxxxxxxx" />
      </div>
      <div>
        <label class="label" for="ad-model">Device model</label>
        <input id="ad-model" class="input" bind:value={model} placeholder="e.g. Samsung A05s" />
      </div>
      <div class="rounded-lg border border-edge bg-surface-100 p-3 space-y-2">
        <p class="text-xs font-bold text-ink-primary">Pricing — admin sets the plan</p>
        <label class="label" for="ad-prod">Link to product catalog (recommended)</label>
        <select id="ad-prod" class="input !py-1.5 text-xs" bind:value={selectedProdId}>
          <option value="">— Use manual pricing below —</option>
          {#each productModels as pm}<option value={pm.id}>{pm.name} — GH₵ {(pm.totalAmount/100).toFixed(0)} down {(pm.downPayment/100).toFixed(0)} {pm.termDays}d @ {(pm.dailyRate/100).toFixed(2)}/d</option>{/each}
        </select>
        {#if selectedProd}
          <p class="text-xs text-emerald">→ Locked to {selectedProd.name}: GH₵ {(selectedProd.totalAmount/100).toFixed(2)} · down {(selectedProd.downPayment/100).toFixed(2)} · {selectedProd.termDays}d @ {(selectedProd.dailyRate/100).toFixed(2)}/d</p>
        {:else}
          <div class="grid grid-cols-2 gap-2">
            <div><label class="label" for="ad-total">Total GH₵</label><input id="ad-total" class="input !py-1 text-xs" type="number" bind:value={totalGhs} placeholder="1500" /></div>
            <div><label class="label" for="ad-down">Down GH₵</label><input id="ad-down" class="input !py-1 text-xs" type="number" bind:value={downGhs} placeholder="300" /></div>
            <div><label class="label" for="ad-daily">Daily GH₵</label><input id="ad-daily" class="input !py-1 text-xs" type="number" bind:value={dailyGhs} placeholder="10" /></div>
            <div><label class="label" for="ad-term">Term (days)</label><input id="ad-term" class="input !py-1 text-xs" type="number" bind:value={termDays} placeholder="120" /></div>
          </div>
        {/if}
      </div>
      <div>
        <label class="label" for="ad-assign">Assign to agent immediately (optional)</label>
        <select id="ad-assign" class="input !py-1.5 text-xs" bind:value={assignNow}>
          <option value="">— Keep in admin stock —</option>
          {#each agents as ag}<option value={ag.id}>{ag.name}</option>{/each}
        </select>
        <p class="text-[11px] text-ink-muted mt-1">Assigned IMEIs appear only in that agent's inventory.</p>
      </div>
    </div>
  {/if}
  <svelte:fragment slot="footer">
    {#if added}
      <button class="btn-outline" on:click={close}>Close</button>
      <button class="btn-primary" on:click={enrollNow}>Enroll this device</button>
    {:else}
      <button class="btn-outline" on:click={close} disabled={submitting}>Cancel</button>
      <button class="btn-primary" on:click={submit} disabled={submitting}>
        {#if submitting}
          <span class="h-4 w-4 animate-spin rounded-full border-2 border-[color:var(--avatar-text)] border-t-transparent"></span>
          Adding…
        {:else}Add device{/if}
      </button>
    {/if}
  </svelte:fragment>
</Modal>
