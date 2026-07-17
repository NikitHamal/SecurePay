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

  function reset() {
    imei = ''; model = ''; error = ''; submitting = false;
  }

  async function submit() {
    error = '';
    if (!/^\d{15}$/.test(imei.trim())) return error = 'IMEI must be exactly 15 digits';
    if (!model.trim()) return error = 'Device model is required';
    submitting = true;
    try {
      await addDevice(imei.trim(), model.trim());
      reset();
      dispatch('close', { added: true });
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
</script>

<Modal open={open} title="Add Device to Inventory" on:close={close} size="sm">
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
  <svelte:fragment slot="footer">
    <button class="btn-outline" on:click={close} disabled={submitting}>Cancel</button>
    <button class="btn-primary" on:click={submit} disabled={submitting}>
      {#if submitting}
        <span class="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></span>
        Adding…
      {:else}Add device{/if}
    </button>
  </svelte:fragment>
</Modal>
