<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import Modal from '$lib/components/ui/Modal.svelte';
  import { addDevice, createAccount, listDevices } from '$lib/api/client';
  import { load } from '$lib/stores/customers';
  import { newLoanPrefill } from '$lib/stores/ui';
  import { buildAgreement, agreementMoney, agreementToday } from '$lib/utils/agreement';
  import {
    REGIONS, REGION_NAMES, LANGUAGES, MARITAL_STATUSES, EMPLOYMENT_STATUSES,
    ID_TYPES, RELATIONS, GENDERS
  } from '$lib/utils/ghana';
  import { formatCurrency } from '$lib/utils/format';

  export let open = false;

  const dispatch = createEventDispatcher();

  // ---- wizard state ----
  const STEPS = [
    'intro', 'customer', 'details', 'refs', 'identity', 'location',
    'product', 'offers', 'loan', 'verify', 'consent'
  ] as const;
  type StepKey = typeof STEPS[number];
  const STEP_DOT: number[] = [0, 0, 0, 0, 1, 2, 3, 3, 3, 4, 5]; // 6 M-KOPA dots
  const STEP_TITLES: Record<StepKey, string> = {
    intro: 'Customer information',
    customer: 'Customer information',
    details: 'Customer information',
    refs: 'Personal references',
    identity: 'Identity verification',
    location: 'Customer location details',
    product: 'Product information',
    offers: 'Product information',
    loan: 'Product information',
    verify: 'Identity verification result',
    consent: 'Customer consent for collection and processing of their personal data and product information.'
  };

  let stepIndex = 0;
  let submitting = false;
  let error = '';
  let phase: 'wizard' | 'success' = 'wizard';

  // ---- customer information ----
  let firstName = '';
  let surname = '';
  let idType = '';
  let nationalId = '';
  let phoneNumber = '';
  let otherPhone = '';
  let dateOfBirth = ''; // dd/mm/yyyy (converted from the date input)
  let dateOfBirthIso = '';
  let maritalStatus = '';
  let employmentStatus = '';
  let gender = '';
  let isCustomerUser: '' | 'yes' | 'no' = '';

  // ---- references ----
  let kinName = ''; let kinRelation = ''; let kinPhone = '';
  let refereeName = ''; let refereePhone = '';
  let guarantorName = ''; let guarantorRelation = ''; let guarantorPhone = ''; let guarantorId = '';

  // ---- identity photos ----
  let idFrontData = ''; let idBackData = ''; let selfieData = '';

  // ---- location ----
  let region = ''; let district = ''; let physicalAddress = ''; let preferredLanguage = '';

  // ---- product ----
  let imei = ''; let deviceModel = '';
  let manualSerial = false;
  let addImeiToInventory = false;
  let selectedStockId = '';
  let inStockDevices: { id: string; imei: string; model: string; createdAt?: number }[] = [];
  let loadingDevices = false;

  // ---- pricing (always set by the admin/dealer for this specific sale) ----
  let totalAmount = '';
  let dailyRate = '';
  let termDays = '';
  let downPayment = '';

  // ---- consent ----
  let consentChecked = false;
  let signatureDataUrl = '';
  let sigCanvas: HTMLCanvasElement | null = null;
  let sigDrawing = false;
  let sigDirty = false;
  let agreementOpen = false;

  // ---- success ----
  let result: { accountNumber: string; temporaryPin: string; customerId: string; deviceImei: string } | null = null;

  $: step = STEPS[stepIndex];
  $: dotIndex = STEP_DOT[stepIndex];

  // ---- prefill + one-time loads ----
  $: if (open) {
    const pf = $newLoanPrefill || {};
    if (pf.customerName && !firstName) {
      const parts = pf.customerName.trim().split(/\s+/);
      firstName = parts[0] ?? '';
      surname = parts.slice(1).join(' ');
    }
    if (pf.phone && !phoneNumber) phoneNumber = pf.phone;
    if (pf.imei && !imei) {
      imei = pf.imei;
      deviceModel = pf.deviceModel ?? '';
      manualSerial = true;
    }
    if (inStockDevices.length === 0 && !loadingDevices) {
      loadingDevices = true;
      listDevices().then((ds) => {
        inStockDevices = (ds || []).filter((d: { status?: string; customerName?: string | null }) => d.status === 'in_stock' && !d.customerName);
      }).catch(() => {}).finally(() => { loadingDevices = false; });
    }
  }

  function digits(s: string): string { return s.replace(/\D/g, ''); }
  function validPhone(s: string): boolean { const d = digits(s); return d.length >= 9 && d.length <= 15; }

  // ---- per-step validation (mirrors the agent app) ----
  $: isoDobOk = /^\d{4}-\d{2}-\d{2}$/.test(dateOfBirthIso) && (() => {
    const y = Number(dateOfBirthIso.slice(0, 4));
    return y >= 1930 && y <= 2012;
  })();
  $: dateOfBirth = isoDobOk
    ? `${dateOfBirthIso.slice(8, 10)}/${dateOfBirthIso.slice(5, 7)}/${dateOfBirthIso.slice(0, 4)}`
    : '';

  $: totalCents = Math.round((parseFloat(totalAmount) || 0) * 100);
  $: dailyCents = Math.round((parseFloat(dailyRate) || 0) * 100);
  $: termDaysValue = parseInt(termDays || '0', 10) || 0;
  $: planName = deviceModel.trim() || 'Custom terms';
  $: downCents = Math.round((parseFloat(downPayment) || 0) * 100);
  // Suggested daily rate so the balance clears exactly over the chosen term.
  $: suggestedDaily = termDaysValue > 0
    ? Math.ceil(Math.max(totalCents - downCents, 0) / termDaysValue)
    : 0;

  $: stepValid = (() => {
    switch (step) {
      case 'intro':
        return true;
      case 'customer':
        return firstName.trim().length >= 2 && surname.trim().length >= 2 && idType !== '' &&
          nationalId.trim().length >= 6 && nationalId.trim().length <= 20 && validPhone(phoneNumber) &&
          (otherPhone.trim() === '' || (validPhone(otherPhone) && digits(otherPhone) !== digits(phoneNumber)));
      case 'details':
        return isoDobOk && maritalStatus !== '' && employmentStatus !== '' && gender !== '' && isCustomerUser !== '';
      case 'refs':
        return kinName.trim().length >= 3 && kinRelation !== '' && validPhone(kinPhone) && digits(kinPhone) !== digits(phoneNumber) &&
          refereeName.trim().length >= 3 && validPhone(refereePhone) && digits(refereePhone) !== digits(phoneNumber) && digits(refereePhone) !== digits(kinPhone) &&
          guarantorName.trim().length >= 3 && guarantorRelation !== '' && validPhone(guarantorPhone) && digits(guarantorPhone) !== digits(phoneNumber) &&
          guarantorId.trim().length >= 4 && guarantorId.trim().length <= 24;
      case 'identity':
        return idFrontData !== '' && idBackData !== '' && selfieData !== '';
      case 'location':
        return region !== '' && district !== '' && physicalAddress.trim().length >= 2 && preferredLanguage !== '';
      case 'product':
        return /^\d{15}$/.test(imei.trim()) && deviceModel.trim() !== '';
      case 'offers':
        return totalCents > 0 && dailyCents > 0 && termDaysValue > 0;
      case 'loan':
        // Any deposit from zero up to the full price — the dealer decides.
        return downPayment !== '' && downCents >= 0 && downCents <= Math.max(totalCents, 1);
      case 'verify':
        return idFrontData !== '' && idBackData !== '' && selfieData !== '';
      case 'consent':
        return consentChecked && signatureDataUrl !== '';
      default:
        return false;
    }
  })();

  function next() { if (stepValid && stepIndex < STEPS.length - 1) { error = ''; stepIndex += 1; } }
  function back() { if (stepIndex > 0) { error = ''; stepIndex -= 1; } }

  function pickStockDevice(d: { id: string; imei: string; model: string }) {
    selectedStockId = d.id;
    imei = d.imei;
    deviceModel = d.model;
    manualSerial = false;
    addImeiToInventory = false;
  }

  function useSuggestedDaily() {
    if (suggestedDaily > 0) dailyRate = (suggestedDaily / 100).toFixed(2);
  }

  async function pickImage(kind: 'front' | 'back' | 'selfie', e: Event) {
    const input = e.currentTarget as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    const dataUrl = await downscaleImage(file);
    if (kind === 'front') idFrontData = dataUrl;
    else if (kind === 'back') idBackData = dataUrl;
    else selfieData = dataUrl;
  }

  function downscaleImage(file: File, maxDim = 900, quality = 0.8): Promise<string> {
    return new Promise((resolve, reject) => {
      const img = new Image();
      const url = URL.createObjectURL(file);
      img.onload = () => {
        URL.revokeObjectURL(url);
        const scale = Math.min(1, maxDim / Math.max(img.width, img.height));
        const w = Math.max(1, Math.round(img.width * scale));
        const h = Math.max(1, Math.round(img.height * scale));
        const c = document.createElement('canvas');
        c.width = w; c.height = h;
        const ctx = c.getContext('2d');
        if (!ctx) return reject(new Error('Canvas unavailable'));
        ctx.drawImage(img, 0, 0, w, h);
        resolve(c.toDataURL('image/jpeg', quality));
      };
      img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Could not read image')); };
      img.src = url;
    });
  }

  // ---- signature pad ----
  function sigPoint(e: PointerEvent): { x: number; y: number } | null {
    if (!sigCanvas) return null;
    const rect = sigCanvas.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return null;
    return {
      x: ((e.clientX - rect.left) / rect.width) * sigCanvas.width,
      y: ((e.clientY - rect.top) / rect.height) * sigCanvas.height
    };
  }

  function sigStart(e: PointerEvent) {
    if (!sigCanvas) return;
    sigDrawing = true;
    sigCanvas.setPointerCapture(e.pointerId);
    const ctx = sigCanvas.getContext('2d');
    const p = sigPoint(e);
    if (ctx && p) { ctx.beginPath(); ctx.moveTo(p.x, p.y); }
  }

  function sigMove(e: PointerEvent) {
    if (!sigDrawing || !sigCanvas) return;
    const ctx = sigCanvas.getContext('2d');
    const p = sigPoint(e);
    if (ctx && p) {
      ctx.lineTo(p.x, p.y);
      ctx.strokeStyle = '#111827';
      ctx.lineWidth = 2.4;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      ctx.stroke();
      sigDirty = true;
    }
  }

  function sigEnd() {
    if (!sigDrawing || !sigCanvas) return;
    sigDrawing = false;
    if (sigDirty) signatureDataUrl = sigCanvas.toDataURL('image/png');
  }

  function sigClear() {
    if (!sigCanvas) return;
    const ctx = sigCanvas.getContext('2d');
    ctx?.clearRect(0, 0, sigCanvas.width, sigCanvas.height);
    sigDirty = false;
    signatureDataUrl = '';
  }

  // ---- agreement ----
  $: customerName = [firstName.trim(), surname.trim()].filter(Boolean).join(' ');
  $: agreementText = buildAgreement({
    firstName: firstName.trim(),
    surname: surname.trim(),
    idType,
    idNumber: nationalId.trim(),
    phone: phoneNumber.trim(),
    otherPhone: otherPhone.trim(),
    dateOfBirth,
    gender,
    maritalStatus,
    employmentStatus,
    region,
    district,
    physicalAddress: physicalAddress.trim(),
    preferredLanguage,
    customerName,
    deviceModel: deviceModel.trim(),
    imei: imei.trim(),
    planName,
    totalLoanAmountCents: totalCents,
    downPaymentCents: downCents,
    dailyRateCents: dailyCents,
    termDays: termDaysValue,
    kinName: kinName.trim(),
    kinRelation,
    kinPhone: kinPhone.trim(),
    refereeName: refereeName.trim(),
    refereePhone: refereePhone.trim(),
    guarantorName: guarantorName.trim(),
    guarantorRelation,
    guarantorPhone: guarantorPhone.trim(),
    guarantorId: guarantorId.trim()
  });

  function printAgreement() {
    const win = window.open('', '_blank', 'width=820,height=1000');
    if (!win) return;
    const escaped = agreementText
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    win.document.write(`<!doctype html><html><head><title>Touch Base — Device Financing Agreement</title>
      <style>body{font-family:ui-sans-serif,system-ui,sans-serif;color:#111;padding:32px;max-width:760px;margin:auto;}
      pre{white-space:pre-wrap;font-size:13px;line-height:1.55;font-family:inherit;}
      img{display:block;margin:12px 0;border:1px solid #ccc;padding:8px;max-width:340px;background:#fff;}</style>
      </head><body><pre>${escaped}</pre>` +
      (signatureDataUrl ? `<h4>Customer signature — ${agreementToday()}</h4><img src="${signatureDataUrl}" alt="signature" />` : '') +
      `</body></html>`);
    win.document.close();
    win.focus();
    win.print();
  }

  function stepTo(key: StepKey) {
    const i = STEPS.indexOf(key);
    if (i >= 0) stepIndex = i;
  }

  function reset() {
    stepIndex = 0; submitting = false; error = ''; phase = 'wizard';
    firstName = ''; surname = ''; idType = ''; nationalId = ''; phoneNumber = ''; otherPhone = '';
    dateOfBirth = ''; dateOfBirthIso = ''; maritalStatus = ''; employmentStatus = ''; gender = ''; isCustomerUser = '';
    kinName = ''; kinRelation = ''; kinPhone = ''; refereeName = ''; refereePhone = '';
    guarantorName = ''; guarantorRelation = ''; guarantorPhone = ''; guarantorId = '';
    idFrontData = ''; idBackData = ''; selfieData = '';
    region = ''; district = ''; physicalAddress = ''; preferredLanguage = '';
    imei = ''; deviceModel = ''; manualSerial = false; addImeiToInventory = false; selectedStockId = '';
    totalAmount = ''; dailyRate = ''; termDays = ''; downPayment = '';
    consentChecked = false; signatureDataUrl = ''; sigDirty = false; agreementOpen = false;
    result = null;
    newLoanPrefill.set({});
  }

  async function submit() {
    if (submitting) return;
    error = '';
    if (!stepValid) { error = 'Please complete this step first'; return; }

    submitting = true;
    try {
      if ((manualSerial || addImeiToInventory) && !selectedStockId) {
        try { await addDevice(imei.trim(), deviceModel.trim()); }
        catch (e) {
          const msg = e instanceof Error ? e.message : String(e);
          if (!/already|exists|duplicate/i.test(msg)) throw e;
        }
      }

      const customer = await createAccount({
        customerName,
        nationalId: nationalId.trim(),
        phoneNumber: phoneNumber.trim(),
        imei: imei.trim(),
        dailyRate: dailyCents > 0 ? dailyCents : undefined,
        totalAmount: totalCents > 0 ? totalCents : undefined,
        termDays: termDaysValue > 0 ? termDaysValue : undefined,
        downPayment: downCents > 0 ? downCents : undefined,
        customerPhoto: selfieData || undefined,
        nationalIdFront: idFrontData || undefined,
        nationalIdBack: idBackData || undefined,
        idType: idType || undefined,
        nextOfKinName: kinName.trim() || undefined,
        nextOfKinPhone: kinPhone.trim() || undefined,
        nextOfKinRelation: kinRelation || undefined,
        refereeName: refereeName.trim() || undefined,
        refereePhone: refereePhone.trim() || undefined,
        guarantorName: guarantorName.trim() || undefined,
        guarantorPhone: guarantorPhone.trim() || undefined,
        guarantorIdNumber: guarantorId.trim() || undefined,
        guarantorRelation: guarantorRelation || undefined,
        consentTerms: consentChecked,
        consentData: consentChecked,
        customerSignature: signatureDataUrl || undefined,
        surname: surname.trim() || undefined,
        otherPhone: otherPhone.trim() || undefined,
        dateOfBirth: dateOfBirth || undefined,
        maritalStatus: maritalStatus || undefined,
        employmentStatus: employmentStatus || undefined,
        gender: gender || undefined,
        isCustomerUser: isCustomerUser === '' ? undefined : isCustomerUser === 'yes',
        region: region || undefined,
        district: district || undefined,
        physicalAddress: physicalAddress.trim() || undefined,
        preferredLanguage: preferredLanguage || undefined,
        agreementText
      });

      result = {
        accountNumber: customer.initialCredentials?.accountNumber || phoneNumber.trim(),
        temporaryPin: customer.initialCredentials?.temporaryPin || '',
        customerId: customer.id,
        deviceImei: imei.trim()
      };
      phase = 'success';
      await load();
      listDevices().then((ds) => {
        inStockDevices = (ds || []).filter((d: { status?: string; customerName?: string | null }) => d.status === 'in_stock' && !d.customerName);
      }).catch(() => {});
    } catch (e) {
      error = e instanceof Error ? e.message : 'Failed to create application';
    } finally {
      submitting = false;
    }
  }

  function close() {
    reset();
    dispatch('close');
  }

  function goToProvision() {
    dispatch('provision', { imei: result?.deviceImei ?? '', customerId: result?.customerId });
    close();
  }

  $: serialGroups = inStockDevices.reduce<Record<string, typeof inStockDevices>>((acc, d) => {
    const key = d.model?.trim() || 'Other';
    (acc[key] = acc[key] || []).push(d);
    return acc;
  }, {});
</script>

<Modal
  open={open}
  title={phase === 'wizard' ? 'Start Application' : 'Customer Enrolled'}
  on:close={close}
  size="xl"
>
  {#if phase === 'wizard'}
    <!-- M-KOPA progress dots (6 sections) -->
    <div class="mb-4 flex items-center justify-center gap-0" aria-hidden="true">
      {#each Array(6) as _, i}
        <span
          class="h-2.5 w-2.5 rounded-full transition-colors {i <= dotIndex ? '' : ''}"
          style="background: {i <= dotIndex ? 'var(--brand)' : 'var(--edge, rgba(255,255,255,0.14))'};{i === dotIndex ? 'transform:scale(1.25);' : ''}"
        ></span>
        {#if i < 5}
          <span class="mx-0.5 h-0.5 w-5 rounded" style="background: {i < dotIndex ? 'var(--brand)' : 'var(--edge, rgba(255,255,255,0.14))'}"></span>
        {/if}
      {/each}
    </div>

    <!-- Section header -->
    <div class="mb-4 flex items-start gap-2.5">
      <svg class="mt-0.5 h-5 w-5 shrink-0" style="color: var(--brand);" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        {#if step === 'identity' || step === 'verify'}
          <path d="M12 11a3 3 0 100 6 3 3 0 000-6z"/><path d="M12 2a9 9 0 019 9c0 2.5-1 4.2-2.4 5.6M3.4 9.2A9 9 0 0112 2m-7.2 13A9 9 0 003 11c0-1.2.3-2.3.8-3.3M7 20.5A9 9 0 0012 22" stroke-linecap="round"/>
        {:else if step === 'location'}
          <path d="M12 21s7-5.5 7-11a7 7 0 10-14 0c0 5.5 7 11 7 11z"/><circle cx="12" cy="10" r="2.5"/>
        {:else if step === 'product' || step === 'offers' || step === 'loan'}
          <rect x="7" y="2" width="10" height="20" rx="2"/><path d="M11 18h2" stroke-linecap="round"/>
        {:else if step === 'consent'}
          <path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 013 3L7 19l-4 1 1-4L16.5 3.5z" stroke-linecap="round" stroke-linejoin="round"/>
        {:else if step === 'refs'}
          <circle cx="9" cy="8" r="3"/><path d="M2.5 20a6.5 6.5 0 0113 0"/><circle cx="17.5" cy="9" r="2.2"/><path d="M15.5 20a5.5 5.5 0 016 0" stroke-linecap="round"/>
        {:else}
          <circle cx="12" cy="8" r="3.5"/><path d="M4.5 20.5a7.5 7.5 0 0115 0" stroke-linecap="round"/>
        {/if}
      </svg>
      <p class="text-base font-bold text-ink-primary leading-snug">{STEP_TITLES[step]}</p>
    </div>

    {#if error}
      <div class="mb-3 rounded-lg border border-crimson/20 bg-crimson/10 px-3 py-2 text-sm text-crimson">{error}</div>
    {/if}

    <!-- ============ INTRO ============ -->
    {#if step === 'intro'}
      <div class="flex flex-col items-center py-8 text-center">
        <div class="flex h-36 w-36 items-center justify-center rounded-full" style="background: var(--brand-soft);">
          <div class="flex h-24 w-24 items-center justify-center rounded-full" style="background: var(--brand-soft);">
            <svg class="h-16 w-16" style="color: var(--brand);" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
              <circle cx="12" cy="8" r="3.5"/><path d="M4.5 20.5a7.5 7.5 0 0115 0" stroke-linecap="round"/>
            </svg>
          </div>
        </div>
        <h3 class="mt-8 text-xl font-bold text-ink-primary">You're onboarding a new customer</h3>
        <p class="mt-2 text-sm font-medium text-ink-muted">Let's complete their onboarding.</p>
      </div>
    {/if}

    <!-- ============ CUSTOMER (form A) ============ -->
    {#if step === 'customer'}
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div>
          <label class="label" for="nl-first">First Name</label>
          <input id="nl-first" class="input" bind:value={firstName} placeholder="Daniel" />
        </div>
        <div>
          <label class="label" for="nl-last">Surname</label>
          <input id="nl-last" class="input" bind:value={surname} placeholder="Sem" />
        </div>
        <div>
          <label class="label" for="nl-idtype">ID Type</label>
          <select id="nl-idtype" class="input" bind:value={idType}>
            <option value="" disabled>Select ID type</option>
            {#each ID_TYPES as t}<option value={t}>{t}</option>{/each}
          </select>
        </div>
        <div>
          <label class="label" for="nl-idnum">ID Number</label>
          <input id="nl-idnum" class="input font-mono" bind:value={nationalId} placeholder="GHA-XXXXXXXXX-X" />
        </div>
        <div>
          <label class="label" for="nl-phone">Phone number</label>
          <input id="nl-phone" class="input" bind:value={phoneNumber} placeholder="(+233) XX XXX XXXX" />
        </div>
        <div>
          <label class="label" for="nl-other">Other Number <span class="text-ink-muted text-xs">(optional)</span></label>
          <input id="nl-other" class="input" bind:value={otherPhone} placeholder="(+233) XX XXX XXXX" />
        </div>
      </div>
    {/if}

    <!-- ============ DETAILS (form B) ============ -->
    {#if step === 'details'}
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div>
          <label class="label" for="nl-dob">Date of Birth</label>
          <input id="nl-dob" type="date" class="input" bind:value={dateOfBirthIso} min="1930-01-01" max="2012-12-31" />
        </div>
        <div>
          <label class="label" for="nl-marital">Marital Status</label>
          <select id="nl-marital" class="input" bind:value={maritalStatus}>
            <option value="" disabled>Select</option>
            {#each MARITAL_STATUSES as m}<option value={m}>{m}</option>{/each}
          </select>
        </div>
        <div>
          <label class="label" for="nl-employ">Employment status</label>
          <select id="nl-employ" class="input" bind:value={employmentStatus}>
            <option value="" disabled>Select</option>
            {#each EMPLOYMENT_STATUSES as s}<option value={s}>{s}</option>{/each}
          </select>
        </div>
        <div>
          <p class="label">Gender</p>
          <div class="flex items-center gap-4 pt-1.5">
            {#each GENDERS as g}
              <label class="flex items-center gap-2 text-sm text-ink-primary cursor-pointer">
                <input type="radio" name="nl-gender" value={g} bind:group={gender} class="h-4 w-4" style="accent-color: var(--brand);" /> {g}
              </label>
            {/each}
          </div>
        </div>
        <div class="sm:col-span-2">
          <p class="label">Is the customer the user?</p>
          <div class="flex items-center gap-4 pt-1.5">
            {#each [['yes', 'Yes'], ['no', 'No']] as [v, l]}
              <label class="flex items-center gap-2 text-sm text-ink-primary cursor-pointer">
                <input type="radio" name="nl-user" value={v} bind:group={isCustomerUser} class="h-4 w-4" style="accent-color: var(--brand);" /> {l}
              </label>
            {/each}
          </div>
        </div>
      </div>
    {/if}

    <!-- ============ REFERENCES ============ -->
    {#if step === 'refs'}
      <div class="space-y-4">
        <div>
          <p class="section-title mb-2">Next of kin</p>
          <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <div>
              <label class="label" for="nl-kin-name">Full name</label>
              <input id="nl-kin-name" class="input" bind:value={kinName} placeholder="e.g. Ama Mensah" />
            </div>
            <div>
              <label class="label" for="nl-kin-rel">Relationship</label>
              <select id="nl-kin-rel" class="input" bind:value={kinRelation}>
                <option value="" disabled>Select</option>
                {#each RELATIONS as r}<option value={r}>{r}</option>{/each}
              </select>
            </div>
            <div>
              <label class="label" for="nl-kin-phone">Phone number</label>
              <input id="nl-kin-phone" class="input" bind:value={kinPhone} placeholder="024 xxx xxxx" />
            </div>
          </div>
        </div>
        <div class="border-t border-edge pt-4">
          <p class="section-title mb-2">Referee</p>
          <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <label class="label" for="nl-ref-name">Full name</label>
              <input id="nl-ref-name" class="input" bind:value={refereeName} placeholder="e.g. Kwame Owusu" />
            </div>
            <div>
              <label class="label" for="nl-ref-phone">Phone number</label>
              <input id="nl-ref-phone" class="input" bind:value={refereePhone} placeholder="055 xxx xxxx" />
            </div>
          </div>
        </div>
        <div class="border-t border-edge pt-4">
          <p class="section-title mb-1">Guarantor (co-signer)</p>
          <p class="mb-2 text-xs text-ink-muted">The guarantor co-signs the financing agreement and can be contacted on default.</p>
          <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <label class="label" for="nl-g-name">Full name</label>
              <input id="nl-g-name" class="input" bind:value={guarantorName} placeholder="e.g. Kofi Boateng" />
            </div>
            <div>
              <label class="label" for="nl-g-rel">Relationship</label>
              <select id="nl-g-rel" class="input" bind:value={guarantorRelation}>
                <option value="" disabled>Select</option>
                {#each RELATIONS as r}<option value={r}>{r}</option>{/each}
              </select>
            </div>
            <div>
              <label class="label" for="nl-g-phone">Phone number</label>
              <input id="nl-g-phone" class="input" bind:value={guarantorPhone} placeholder="020 xxx xxxx" />
            </div>
            <div>
              <label class="label" for="nl-g-id">ID number</label>
              <input id="nl-g-id" class="input" bind:value={guarantorId} placeholder="Ghana Card / Voter / Passport" />
            </div>
          </div>
        </div>
      </div>
    {/if}

    <!-- ============ IDENTITY ============ -->
    {#if step === 'identity'}
      <p class="mb-3 text-xs text-ink-muted">Take clear photos of the customer's documents. All three are required.</p>
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
        {#each [
          { key: 'front', label: 'Identity document (front)', data: idFrontData },
          { key: 'back', label: 'Identity document (back)', data: idBackData },
          { key: 'selfie', label: 'Customer photo', data: selfieData }
        ] as item (item.key)}
          <div class="rounded-lg border {item.data ? 'border-emerald/50' : 'border-dashed border-edge'} bg-surface-100 p-2.5">
            <p class="mb-2 text-xs font-semibold text-ink-primary">{item.label}</p>
            <label class="relative flex aspect-[4/3] cursor-pointer items-center justify-center overflow-hidden rounded-md bg-surface-200/60 hover:bg-hover">
              {#if item.data}
                <img src={item.data} alt={item.label} class="absolute inset-0 h-full w-full object-cover" />
                <span class="absolute bottom-1.5 right-1.5 flex items-center gap-1 rounded-md bg-emerald px-1.5 py-0.5 text-2xs font-bold text-white">
                  <svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><path d="M5 13l4 4L19 7" stroke-linecap="round" stroke-linejoin="round"/></svg>Passed
                </span>
              {:else}
                <span class="flex flex-col items-center gap-1 text-ink-muted">
                  <svg class="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 7h3l2-2h6l2 2h3a2 2 0 012 2v9a2 2 0 01-2 2H4a2 2 0 01-2-2V9a2 2 0 012-2z"/><circle cx="12" cy="13" r="3.5"/></svg>
                  <span class="text-2xs font-medium">Required · tap to attach</span>
                </span>
              {/if}
              <input type="file" accept="image/*" capture={item.key === 'selfie' ? 'user' : 'environment'} class="hidden" on:change={(e) => pickImage(item.key as 'front' | 'back' | 'selfie', e)} />
            </label>
            {#if item.data}
              <button type="button" class="mt-2 text-2xs text-crimson hover:underline" on:click={() => { if (item.key === 'front') idFrontData = ''; else if (item.key === 'back') idBackData = ''; else selfieData = ''; }}>Remove</button>
            {/if}
          </div>
        {/each}
      </div>
    {/if}

    <!-- ============ LOCATION ============ -->
    {#if step === 'location'}
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div>
          <label class="label" for="nl-region">Region</label>
          <select id="nl-region" class="input" bind:value={region} on:change={() => (district = '')}>
            <option value="" disabled>Select region</option>
            {#each REGION_NAMES as r}<option value={r}>{r}</option>{/each}
          </select>
        </div>
        <div>
          <label class="label" for="nl-district">District</label>
          <select id="nl-district" class="input" bind:value={district} disabled={region === ''}>
            <option value="" disabled>Select district</option>
            {#each REGIONS[region] ?? [] as d}<option value={d}>{d}</option>{/each}
          </select>
        </div>
        <div>
          <label class="label" for="nl-address">Physical Address</label>
          <input id="nl-address" class="input" bind:value={physicalAddress} placeholder="e.g. Market" />
        </div>
        <div>
          <label class="label" for="nl-lang">Preferred Language</label>
          <select id="nl-lang" class="input" bind:value={preferredLanguage}>
            <option value="" disabled>Select</option>
            {#each LANGUAGES as l}<option value={l}>{l}</option>{/each}
          </select>
        </div>
      </div>
    {/if}

    <!-- ============ PRODUCT SERIAL ============ -->
    {#if step === 'product'}
      <p class="mb-3 text-sm font-bold text-ink-primary">Select the product serial you want to sell.</p>
      {#if loadingDevices}
        <p class="text-xs text-ink-muted">Refreshing stock…</p>
      {/if}
      <div class="max-h-72 space-y-3 overflow-y-auto pr-1">
        {#each Object.entries(serialGroups) as [model, devices] (model)}
          <p class="text-xs font-bold text-ink-primary">{model}</p>
          <div class="space-y-2">
            {#each devices as d (d.id)}
              <button
                type="button"
                on:click={() => pickStockDevice(d)}
                class="flex w-full items-center gap-3 rounded-lg border p-3 text-left transition-colors {selectedStockId === d.id ? 'border-[var(--brand)] bg-[var(--brand-soft)]' : 'border-edge bg-surface-100 hover:bg-hover'}"
              >
                <span class="flex h-4 w-4 items-center justify-center rounded-full border-2 {selectedStockId === d.id ? 'border-[var(--brand)]' : 'border-ink-muted'}">
                  {#if selectedStockId === d.id}<span class="h-2 w-2 rounded-full" style="background: var(--brand);"></span>{/if}
                </span>
                <span>
                  <span class="block text-sm font-bold text-ink-primary">{d.model} ~ {d.imei.slice(-4)}</span>
                  <span class="block font-mono text-2xs text-ink-muted">{d.imei}</span>
                </span>
              </button>
            {/each}
          </div>
        {/each}
        {#if !loadingDevices && inStockDevices.length === 0}
          <p class="text-xs text-ink-muted">No unsold devices in stock — enter the serial below.</p>
        {/if}
      </div>
      <div class="mt-3 rounded-lg border border-edge bg-surface-100 p-3">
        <label class="flex items-center gap-2 text-xs font-medium text-ink-primary cursor-pointer">
          <input type="checkbox" bind:checked={manualSerial} class="h-4 w-4" style="accent-color: var(--brand);" on:change={() => { if (!manualSerial) { selectedStockId = ''; } }} />
          Enter serial manually
        </label>
        {#if manualSerial}
          <div class="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <label class="label" for="nl-imei">IMEI (15 digits)</label>
              <input id="nl-imei" class="input font-mono" bind:value={imei} maxlength={15} placeholder="35xxxxxxxxxxxxx" />
            </div>
            <div>
              <label class="label" for="nl-model">Device model</label>
              <input id="nl-model" class="input" bind:value={deviceModel} placeholder="e.g. Samsung A07 4/64" />
            </div>
            <label class="sm:col-span-2 flex items-center gap-2 text-xs text-ink-secondary cursor-pointer">
              <input type="checkbox" bind:checked={addImeiToInventory} class="h-4 w-4" style="accent-color: var(--brand);" />
              Add this IMEI to inventory if it isn't already
            </label>
          </div>
        {/if}
      </div>
    {/if}

    <!-- ============ OFFERS ============ -->
    {#if step === 'offers'}
      <div class="mb-2 flex items-center justify-between">
        <span class="text-xs text-ink-muted">Device IMEI</span>
        <span class="font-mono text-sm font-bold text-ink-primary">{imei}</span>
      </div>
      <div class="mb-3 flex justify-end">
        <button type="button" class="rounded-full border px-3 py-1 text-xs font-bold" style="border-color: var(--brand); color: var(--brand);" on:click={() => stepTo('product')}>Change product</button>
      </div>
      <p class="mb-1 text-sm font-bold text-ink-primary">Set the price for this sale</p>
      <p class="mb-3 text-xs text-ink-muted">Your prices, your terms — enter the amounts you agreed with this customer.</p>
      <div class="rounded-lg border p-3.5" style="border-color: var(--brand);">
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <div>
            <label class="label" for="nl-total">Total price (GH₵)</label>
            <input id="nl-total" type="number" step="0.01" min="0" class="input" bind:value={totalAmount} placeholder="2277.80" />
          </div>
          <div>
            <label class="label" for="nl-term">Repayment period (days)</label>
            <input id="nl-term" type="number" min="1" class="input" bind:value={termDays} placeholder="119" />
          </div>
          <div>
            <label class="label" for="nl-daily">Daily rate (GH₵)</label>
            <input id="nl-daily" type="number" step="0.01" min="0" class="input" bind:value={dailyRate} placeholder="16.20" />
          </div>
        </div>
        {#if suggestedDaily > 0}
          <div class="mt-3 flex items-center justify-between">
            <p class="text-2xs text-ink-muted">Suggested {formatCurrency(suggestedDaily)} / day to clear the balance in {termDaysValue} days</p>
            <button type="button" class="text-xs font-bold" style="color: var(--brand);" on:click={useSuggestedDaily}>Use</button>
          </div>
        {/if}
      </div>

      {#if totalCents > 0 && dailyCents > 0 && termDaysValue > 0}
        <div class="mt-3 rounded-lg border border-edge bg-surface-100 p-3.5">
          <div class="flex items-center gap-2">
            <svg class="h-4 w-4 text-ink-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="7" y="2" width="10" height="20" rx="2"/></svg>
            <p class="text-sm font-bold text-ink-primary">Your offer</p>
          </div>
          <p class="mt-1.5 flex items-center gap-1.5 text-xs text-ink-muted">
            <svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 018 0v3"/></svg>
            Daily repayment rate {formatCurrency(dailyCents)}
          </p>
          <div class="mt-2.5 flex items-center justify-between">
            <span class="rounded-full bg-surface-200 px-2.5 py-1 text-2xs font-bold text-ink-muted">{termDaysValue} DAYS</span>
            <span class="text-sm font-bold text-ink-primary">Total {formatCurrency(totalCents)}</span>
          </div>
        </div>
      {/if}
    {/if}

    <!-- ============ LOAN DETAILS ============ -->
    {#if step === 'loan'}
      <div class="rounded-lg border border-edge bg-surface-100 p-3.5">
        <p class="text-2xs uppercase tracking-wide text-ink-muted">DEVICE IMEI</p>
        <p class="font-mono text-base font-bold text-ink-primary">{imei}</p>
      </div>
      <p class="mt-4 mb-2 text-sm text-ink-muted">Loan details</p>
      <div class="rounded-lg p-4" style="background: linear-gradient(135deg, var(--brand), var(--brand-dim, #8C701E)); color: #141414;">
        <div class="flex items-center gap-2.5">
          <span class="flex h-8 w-8 items-center justify-center rounded-full bg-white/25">
            <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="7" y="2" width="10" height="20" rx="2"/></svg>
          </span>
          <p class="text-sm font-bold">{planName}</p>
        </div>
        <div class="mt-2.5 space-y-1.5 text-xs font-semibold">
          <p class="flex items-center gap-1.5">
            <svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 018 0v3"/></svg>
            Initial payment {agreementMoney(downCents)}
          </p>
          <p class="flex items-center gap-1.5">
            <svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 018 0v3"/></svg>
            Daily repayment rate {agreementMoney(dailyCents)}
          </p>
          <p class="flex items-center gap-1.5">
            <svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 2v4M15 2v4M5 9h14"/></svg>
            Repayment Period {termDaysValue} Days
          </p>
        </div>
        <p class="mt-3 text-right text-sm font-extrabold">Total loan amount {agreementMoney(totalCents)}</p>
      </div>
      <div class="mt-4">
        <label class="label" for="nl-down">Initial payment (deposit)</label>
        <input id="nl-down" type="number" step="0.01" min="0" class="input" bind:value={downPayment} />
        <p class="mt-1 text-2xs text-ink-muted">Any amount from 0 up to the total price {formatCurrency(totalCents)}</p>
      </div>
    {/if}

    <!-- ============ VERIFICATION RESULT ============ -->
    {#if step === 'verify'}
      <div class="rounded-lg border border-emerald/30 bg-emerald/15 p-4">
        <div class="flex items-center gap-2.5">
          <svg class="h-6 w-6 text-emerald" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><path d="M8 12.5l2.5 2.5L16 9.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
          <div>
            <p class="text-sm font-bold text-emerald">Approved</p>
            <p class="text-xs text-emerald/90">Your identity verification has been approved</p>
          </div>
        </div>
      </div>
      <div class="mt-4 space-y-3">
        <div class="flex items-center gap-3">
          <svg class="h-5 w-5 text-ink-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="8.5" cy="11" r="1.8"/><path d="M5.5 17a3 3 0 016 0M14 9h5M14 12.5h5M14 16h3"/></svg>
          <p class="flex-1 text-sm font-semibold text-ink-primary">Identity document</p>
          <span class="text-xs font-bold text-emerald">✓ Passed</span>
        </div>
        <div class="flex items-center gap-3">
          <svg class="h-5 w-5 text-ink-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="9" r="3"/><path d="M5 20a7 7 0 0114 0" stroke-linecap="round"/></svg>
          <p class="flex-1 text-sm font-semibold text-ink-primary">Customer photo</p>
          <span class="text-xs font-bold text-emerald">✓ Passed</span>
        </div>
      </div>
    {/if}

    <!-- ============ CONSENT + AGREEMENT + SIGNATURE ============ -->
    {#if step === 'consent'}
      <div class="mb-3 flex items-center gap-2.5">
        <span class="text-xl font-black text-ink-primary">Touch Base</span>
        <span class="h-3.5 w-3.5 rounded-full" style="background: var(--brand);"></span>
        <span class="ml-auto text-sm font-bold" style="color: var(--brand);">CUSTOMER CONSENT</span>
      </div>

      <div class="rounded border border-edge bg-surface-100/40">
        <div class="flex items-center justify-between border-b border-edge px-3 py-2">
          <p class="text-2xs font-bold uppercase tracking-wide text-ink-muted">Device Financing Agreement</p>
          <div class="flex items-center gap-3">
            <button type="button" class="text-2xs font-bold" style="color: var(--brand);" on:click={() => (agreementOpen = true)}>Read full screen</button>
            <button type="button" class="text-2xs font-bold" style="color: var(--brand);" on:click={printAgreement}>Print</button>
          </div>
        </div>
        <pre class="max-h-60 overflow-y-auto whitespace-pre-wrap px-3 py-2.5 font-sans text-xs leading-relaxed text-ink-primary">{agreementText}</pre>
      </div>

      <label class="mt-3 flex cursor-pointer items-start gap-2.5">
        <input type="checkbox" bind:checked={consentChecked} class="mt-0.5 h-4 w-4 shrink-0" style="accent-color: var(--brand);" />
        <span class="text-xs font-semibold leading-relaxed text-ink-primary">I affirm that the privacy policy and the terms and conditions were read over and explained to me in a language I understand best and I agree to the terms and conditions contained herein in relation to the Product</span>
      </label>

      <div class="mt-3">
        <div class="flex items-center justify-between">
          <p class="label !mb-0">Customer signature</p>
          <button type="button" class="text-2xs font-bold" style="color: var(--brand);" on:click={sigClear}>CLEAR</button>
        </div>
        {#if signatureDataUrl === ''}
          <canvas
            bind:this={sigCanvas}
            width="680"
            height="200"
            class="mt-1.5 h-40 w-full cursor-crosshair rounded bg-white touch-none"
            on:pointerdown={sigStart}
            on:pointermove={sigMove}
            on:pointerup={sigEnd}
            on:pointerleave={sigEnd}
          ></canvas>
          <p class="mt-1 text-2xs text-ink-muted">Ask the customer to sign with a finger or mouse.</p>
        {:else}
          <div class="relative mt-1.5 rounded border-2 border-emerald bg-white">
            <img src={signatureDataUrl} alt="Customer signature" class="h-40 w-full object-contain" />
            <button
              type="button"
              class="absolute bottom-1.5 right-1.5 rounded bg-surface-200/90 px-2 py-1 text-2xs font-bold text-ink-primary"
              on:click={sigClear}
            >Re-sign</button>
          </div>
        {/if}
      </div>
    {/if}
  {:else if result}
    <!-- ============ SUCCESS ============ -->
    <div class="space-y-4 text-center">
      <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-emerald/15 text-emerald">
        <svg class="h-7 w-7" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M5 13l4 4L19 7" stroke-linecap="round" stroke-linejoin="round"/></svg>
      </div>
      <div>
        <p class="text-lg font-semibold text-ink-primary">Approved — application submitted</p>
        <p class="mt-1 text-sm text-ink-muted">Give these one-time credentials to the customer.</p>
      </div>
      <div class="space-y-2 rounded-lg border border-edge bg-surface-100 p-4 text-left font-mono text-sm">
        <div class="flex justify-between gap-4">
          <span class="text-ink-muted">Account #</span>
          <span class="text-ink-primary">{result.accountNumber}</span>
        </div>
        {#if result.temporaryPin}
          <div class="flex justify-between gap-4">
            <span class="text-ink-muted">Temporary PIN</span>
            <span class="font-semibold tracking-widest text-emerald">{result.temporaryPin}</span>
          </div>
        {/if}
        <div class="flex justify-between gap-4">
          <span class="text-ink-muted">IMEI</span>
          <span class="text-ink-primary">{result.deviceImei}</span>
        </div>
      </div>
      <p class="rounded-lg border border-amber/25 bg-amber/10 p-3 text-left text-xs text-amber">
        <strong>Important:</strong> Save the temporary PIN now — it cannot be retrieved later. Next, provision the device, and keep the signed agreement with your records.
      </p>
    </div>
  {/if}

  <svelte:fragment slot="footer">
    {#if phase === 'wizard'}
      <div class="flex w-full items-center gap-3">
        {#if stepIndex > 0}
          <button
            type="button"
            on:click={back}
            disabled={submitting}
            class="flex h-11 w-11 shrink-0 items-center justify-center rounded-full border border-edge bg-surface-100 text-ink-primary transition-colors hover:bg-hover disabled:opacity-50"
            aria-label="Back"
          >
            <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M15 18l-6-6 6-6" stroke-linecap="round" stroke-linejoin="round"/></svg>
          </button>
        {/if}
        {#if step === 'consent'}
          <button class="btn-primary flex-1 !py-3" on:click={submit} disabled={!stepValid || submitting}>
            {#if submitting}
              <span class="h-4 w-4 animate-spin rounded-full border-2 border-[color:var(--avatar-text)] border-t-transparent"></span>
              Submitting…
            {:else}AGREE & SUBMIT{/if}
          </button>
        {:else}
          <button class="btn-primary flex-1 !py-3" on:click={next} disabled={!stepValid}>
            {step === 'intro' ? 'CONTINUE' : 'NEXT'}
          </button>
        {/if}
      </div>
    {:else}
      <div class="flex w-full flex-wrap items-center justify-end gap-2">
        <button class="btn-outline" on:click={printAgreement}>Print agreement</button>
        <button class="btn-outline" on:click={close}>Done</button>
        <button class="btn-primary" on:click={goToProvision}>
          Generate provisioning QR
          <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M13 5l7 7-7 7" stroke-linecap="round" stroke-linejoin="round"/></svg>
        </button>
      </div>
    {/if}
  </svelte:fragment>
</Modal>

<!-- Full-screen agreement reader -->
{#if agreementOpen}
  <div class="fixed inset-0 z-[70] flex flex-col bg-surface-200/98 backdrop-blur-sm">
    <div class="flex items-center gap-3 border-b border-edge px-4 py-3">
      <button type="button" class="btn-ghost !px-2 !py-1 text-sm" on:click={() => (agreementOpen = false)}>← Close</button>
      <p class="text-sm font-bold text-ink-primary">Device Financing Agreement — {customerName || 'New customer'}</p>
      <div class="ml-auto">
        <button type="button" class="btn-outline !py-1 !px-3 text-xs" on:click={printAgreement}>Print</button>
      </div>
    </div>
    <pre class="flex-1 overflow-y-auto whitespace-pre-wrap px-6 py-4 font-sans text-sm leading-relaxed text-ink-primary">{agreementText}</pre>
  </div>
{/if}
