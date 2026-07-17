<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import Modal from '$lib/components/ui/Modal.svelte';
  import { addDevice } from '$lib/api/client';

  export let open = false;

  const dispatch = createEventDispatcher();

  let submitting = false;
  let error = '';
  let imei = '';
  let model = '';
  let added: { imei: string; model: string } | null = null;

  function reset() {
    imei = ''; model = ''; error = ''; submitting = false; added = null;
  }

  async function submit() {
    error = '';
    if (!/^\d{15}$/.test(imei.trim())) return error = 'IMEI must be exactly 15 digits';
    if (!model.trim()) return error = 'Device model is required';
    submitting = true;
    try {
      const res = await addDevice(imei.trim(), model.trim());
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
      <p class="text-xs text-ink-muted">Devices must be added to inventory before they can be financed and provisioned.</p>
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
