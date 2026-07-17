<script lang="ts">
  import { createEventDispatcher, onMount } from 'svelte';
  import Modal from '$lib/components/ui/Modal.svelte';
  import { generateQr, listDevices, type QrProvisionResult } from '$lib/api/client';
  import { load } from '$lib/stores/customers';

  export let open = false;
  export let initialImei = '';

  const dispatch = createEventDispatcher();

  // Form
  let imei = '';
  let wifiSsid = '';
  let wifiPassword = '';
  let devices: { imei: string; model: string; status: string }[] = [];
  let loadingDevices = false;

  // Result
  let result: QrProvisionResult | null = null;
  let submitting = false;
  let error = '';
  let copied = false;
  let qrImgUrl = '';

  $: if (open) {
    imei = initialImei || '';
    if (devices.length === 0) loadDevices();
  }

  async function loadDevices() {
    loadingDevices = true;
    try {
      devices = await listDevices();
    } catch {
      /* ignore */
    } finally {
      loadingDevices = false;
    }
  }

  function reset() {
    imei = initialImei || '';
    wifiSsid = ''; wifiPassword = '';
    result = null; submitting = false; error = ''; copied = false; qrImgUrl = '';
  }

  async function submit() {
    error = '';
    if (!/^\d{15}$/.test(imei.trim())) return error = 'IMEI must be exactly 15 digits';
    submitting = true;
    result = null;
    qrImgUrl = '';
    try {
      result = await generateQr({
        imei: imei.trim(),
        wifiSsid: wifiSsid.trim() || undefined,
        wifiPassword: wifiPassword || undefined
      });
      // Build QR code data URL via QR server? We'll render the QR using a canvas/qr lib-free approach:
      // Use Google Chart API alternative: qrserver.com is external. Better: generate via QR code lib locally.
      // We don't have a QR lib installed. Let's inline a tiny QR encoder or use an SVG.
      // For production, use a QR code render endpoint that returns SVG. We'll generate with a small browser-side approach.
      // For now: render an <img> pointing to a QR generation using the browser's built-in canvas via qrcode-generator.
      // To keep it simple and dependency-free, use a data URI approach via fetching from a public QR service? No external calls.
      // We'll include a tiny QR encoder inline.
      qrImgUrl = await buildQrSvg(result.qrPayload);
      await load();
    } catch (e) {
      error = e instanceof Error ? e.message : 'Failed to generate QR';
    } finally {
      submitting = false;
    }
  }

  // Tiny QR code generator using standalone qrcode generator approach.
  // To avoid adding npm deps, we'll use an inline SVG via google-free API? Actually, let's use QRServer only if allowed.
  // Better: install `qrcode` package later. For now provide the JSON clearly and a visible note to scan from device;
  // and use the browser's QR code via canvas drawing using an embedded library.
  //
  // Simpler: render an iframe-less SVG using a pure-JS QR. We'll load the popular "qrcode-generator" inline? Let me just add it via CDN-free import.
  // To keep bundle tight we will import "qrcode" from npm. But build is offline — let me check if it's already in node_modules.
  // Actually I realize — we can just render via an SVG using a tiny QR we write? Too complex.
  // Pragmatic approach: Use the `qrcode` npm package. Let me install it.
  // For now, buildQrSvg will be populated after we install qrcode.
  // Placeholder:
  async function buildQrSvg(text: string): Promise<string> {
    try {
      if (typeof window === 'undefined') return '';
      // @ts-ignore
      const QRCode = (await import('qrcode')).default;
      return await QRCode.toDataURL(text, { margin: 1, width: 260, color: { dark: '#111827', light: '#ffffff' } });
    } catch {
      return '';
    }
  }

  async function copyJson() {
    if (!result) return;
    try {
      await navigator.clipboard.writeText(result.qrPayload);
      copied = true;
      setTimeout(() => copied = false, 1800);
    } catch {
      // fallback: select text in textarea
      const ta = document.getElementById('qr-json') as HTMLTextAreaElement | null;
      if (ta) { ta.select(); document.execCommand('copy'); }
    }
  }

  function close() {
    reset();
    dispatch('close');
  }

  function formatExpiry(ts: number) {
    return new Date(ts).toLocaleString();
  }

  $: prettyJson = result ? JSON.stringify(JSON.parse(result.qrPayload), null, 2) : '';
  $: adminExtras = result ? (JSON.parse(result.qrPayload)['android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE'] || {}) : {};
</script>

<Modal open={open} title={result ? 'Provisioning Ready' : 'Generate Provisioning QR'} on:close={close} size="xl">
  {#if !result}
    <div class="space-y-4">
      {#if error}
        <div class="rounded-lg border border-crimson/20 bg-crimson/10 px-3 py-2 text-sm text-crimson">{error}</div>
      {/if}

      <div class="rounded-lg border border-edge bg-surface-100 p-3 text-xs text-ink-secondary">
        Generates a Device Owner QR code for factory-reset Samsung / Android Enterprise enrollment. The QR encodes the DPC download link, Wi-Fi, and an 8-digit activation code.
      </div>

      <div>
        <label class="label" for="pr-imei">Device IMEI</label>
        <input id="pr-imei" class="input font-mono" bind:value={imei} maxlength={15} placeholder="35xxxxxxxxxxxxx" />
        {#if devices.length > 0}
          <div class="mt-2 flex flex-wrap gap-1.5">
            {#each devices.filter(d => d.status === 'in_stock').slice(0, 8) as d (d.imei)}
              <button
                type="button"
                class="rounded-md border border-edge bg-surface-100 px-2 py-1 text-xs text-ink-secondary hover:bg-hover"
                on:click={() => { imei = d.imei; }}
              >{d.model} · {d.imei.slice(-4)}</button>
            {/each}
          </div>
        {/if}
      </div>

      <div>
        <p class="section-title mb-3">Wi-Fi (optional)</p>
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <label class="label" for="pr-ssid">SSID</label>
            <input id="pr-ssid" class="input" bind:value={wifiSsid} placeholder="Shop Wi-Fi" />
          </div>
          <div>
            <label class="label" for="pr-pw">Password</label>
            <input id="pr-pw" class="input" type="password" bind:value={wifiPassword} placeholder="Leave blank for open" />
          </div>
        </div>
      </div>
    </div>
  {:else}
    <div class="grid grid-cols-1 gap-5 md:grid-cols-5">
      <!-- QR -->
      <div class="md:col-span-2">
        <div class="rounded-xl border border-edge bg-white p-4 text-center">
          {#if qrImgUrl}
            <img src={qrImgUrl} alt="Provisioning QR" class="mx-auto h-56 w-56" />
          {:else}
            <div class="mx-auto flex h-56 w-56 items-center justify-center bg-surface-100 rounded-lg text-sm text-ink-muted">
              Install qrcode lib to render
            </div>
          {/if}
          <p class="mt-3 text-[11px] text-ink-muted">Tap the welcome screen 6 times to start QR enrollment.</p>
        </div>
        <div class="mt-3 space-y-2 text-sm">
          <div class="flex items-center justify-between rounded-lg border border-edge bg-surface-100 px-3 py-2">
            <span class="text-ink-muted text-xs">Activation code</span>
            <span class="font-mono font-semibold tracking-widest text-emerald">{result.activationCode}</span>
          </div>
          <div class="flex items-center justify-between rounded-lg border border-edge bg-surface-100 px-3 py-2">
            <span class="text-ink-muted text-xs">Expires</span>
            <span class="text-xs text-ink-secondary">{formatExpiry(result.expiresAt)}</span>
          </div>
          <div class="flex items-center justify-between rounded-lg border border-edge bg-surface-100 px-3 py-2">
            <span class="text-ink-muted text-xs">Device</span>
            <span class="text-xs text-ink-secondary">{result.device.model} · {result.device.imei}</span>
          </div>
        </div>
      </div>

      <!-- JSON -->
      <div class="md:col-span-3">
        <div class="mb-2 flex items-center justify-between">
          <p class="text-sm font-semibold text-ink-primary">QR payload (JSON)</p>
          <button class="btn-emerald !py-1.5 !px-3 text-xs" on:click={copyJson}>
            {#if copied}
              <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M5 13l4 4L19 7" stroke-linecap="round" stroke-linejoin="round"/></svg>
              Copied
            {:else}
              <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>
              Copy JSON
            {/if}
          </button>
        </div>
        <textarea
          id="qr-json"
          readonly
          class="input h-72 w-full resize-y font-mono text-xs !bg-surface-100"
          value={prettyJson}
        ></textarea>

        <p class="mt-2 text-xs text-ink-muted">
          The values below are what the DPC reads after scanning. Use these when provisioning with your custom tool:
        </p>
        <div class="mt-2 rounded-lg border border-edge bg-surface-100 p-3 font-mono text-xs space-y-1">
          <div><span class="text-ink-muted">provisioningToken:</span> <span class="text-ink-primary">{adminExtras.provisioningToken}</span></div>
          <div><span class="text-ink-muted">activationCode:</span> <span class="text-emerald font-semibold">{adminExtras.activationCode}</span></div>
          <div><span class="text-ink-muted">expectedImei:</span> <span class="text-ink-primary">{adminExtras.expectedImei}</span></div>
          <div><span class="text-ink-muted">accountId:</span> <span class="text-ink-primary">{adminExtras.accountId}</span></div>
          <div><span class="text-ink-muted">deviceId:</span> <span class="text-ink-primary">{adminExtras.deviceId}</span></div>
          <div><span class="text-ink-muted">dealerId:</span> <span class="text-ink-primary">{adminExtras.dealerId}</span></div>
        </div>
      </div>
    </div>
  {/if}

  <svelte:fragment slot="footer">
    {#if !result}
      <button class="btn-outline" on:click={close} disabled={submitting}>Cancel</button>
      <button class="btn-primary" on:click={submit} disabled={submitting}>
        {#if submitting}
          <span class="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></span>
          Generating…
        {:else}Generate QR{/if}
      </button>
    {:else}
      <button class="btn-outline" on:click={() => { result = null; qrImgUrl = ''; }}>Generate another</button>
      <button class="btn-primary" on:click={close}>Done</button>
    {/if}
  </svelte:fragment>
</Modal>


