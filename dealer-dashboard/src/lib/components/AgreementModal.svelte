<script lang="ts">
  /**
   * Signed Device Financing Agreement reader. Shows the exact text stored at
   * enrolment (falls back to a regenerated copy client-side), the captured
   * signature, and prints a clean document via the browser.
   */
  import type { Customer } from '$lib/types';
  import { buildAgreement, agreementToday } from '$lib/utils/agreement';

  export let open = false;
  export let customer: Customer;
  export let signatureDataUrl: string | null = null; // fresh signature right after enrolment
  export let onClose: () => void = () => {};

  $: date = customer?.consentAt
    ? new Date(customer.consentAt < 1_000_000_000_000 ? customer.consentAt * 1000 : customer.consentAt)
        .toLocaleDateString('en-GB', { day: '2-digit', month: '2-digit', year: 'numeric' })
    : agreementToday();

  $: regenerated = !customer?.agreementText?.trim();
  $: agreementText = customer?.agreementText?.trim()
    ? customer.agreementText
    : buildAgreement({
        surname: customer?.surname ?? '',
        idType: customer?.idType ?? '',
        idNumber: customer?.nationalId ?? '',
        phone: customer?.phoneNumber ?? '',
        otherPhone: customer?.otherPhone ?? '',
        dateOfBirth: customer?.dateOfBirth ?? '',
        gender: customer?.gender ?? '',
        maritalStatus: customer?.maritalStatus ?? '',
        employmentStatus: customer?.employmentStatus ?? '',
        region: customer?.region ?? '',
        district: customer?.district ?? '',
        physicalAddress: customer?.physicalAddress ?? '',
        preferredLanguage: customer?.preferredLanguage ?? '',
        customerName: customer?.customerName ?? '',
        deviceModel: customer?.deviceModel ?? '',
        imei: customer?.imei ?? '',
        planName: customer?.planName ?? '',
        totalLoanAmountCents: customer?.totalLoanAmount ?? 0,
        downPaymentCents: customer?.downPayment ?? 0,
        dailyRateCents: customer?.dailyRate ?? 0,
        termDays: customer?.termDays ?? 0,
        kinName: customer?.nextOfKinName ?? '',
        kinRelation: customer?.nextOfKinRelation ?? '',
        kinPhone: customer?.nextOfKinPhone ?? '',
        refereeName: customer?.refereeName ?? '',
        refereePhone: customer?.refereePhone ?? '',
        guarantorName: customer?.guarantorName ?? '',
        guarantorRelation: customer?.guarantorRelation ?? '',
        guarantorPhone: customer?.guarantorPhone ?? '',
        guarantorId: customer?.guarantorIdNumber ?? ''
      }, date);

  $: signatureSrc = signatureDataUrl
    || (customer?.customerSignaturePath ? `/api/accounts/${customer.id}/photos/signature` : null);

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
      (signatureSrc ? `<h4>Customer signature — ${date}</h4><img src="${signatureSrc}" alt="signature" />` : '') +
      `</body></html>`);
    win.document.close();
    win.focus();
    win.print();
  }
</script>

{#if open}
  <div class="fixed inset-0 z-[70] flex flex-col bg-surface-200/98 backdrop-blur-sm">
    <div class="flex flex-wrap items-center gap-3 border-b border-edge px-4 py-3">
      <button type="button" class="btn-ghost !px-2 !py-1 text-sm" on:click={onClose}>← Close</button>
      <div>
        <p class="text-sm font-bold text-ink-primary">Device Financing Agreement — {customer?.customerName ?? ''}</p>
        <p class="text-2xs text-ink-muted">
          Signed {date}{#if regenerated} · regenerated from account data{/if}
        </p>
      </div>
      <div class="ml-auto">
        <button type="button" class="btn-outline !py-1 !px-3 text-xs" on:click={printAgreement}>Print / save PDF</button>
      </div>
    </div>
    <div class="flex-1 overflow-y-auto">
      <pre class="mx-auto max-w-3xl whitespace-pre-wrap px-6 py-5 font-sans text-sm leading-relaxed text-ink-primary">{agreementText}</pre>
      {#if signatureSrc}
        <div class="mx-auto max-w-3xl px-6 pb-8">
          <p class="text-2xs font-bold uppercase tracking-wide text-ink-muted">Customer signature — {date}</p>
          <img src={signatureSrc} alt="Customer signature" class="mt-2 max-h-40 rounded border border-edge bg-white p-2" />
        </div>
      {/if}
    </div>
  </div>
{/if}
