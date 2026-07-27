/**
 * Full Touch Base Device Financing Agreement — generated from the application
 * data. Shown in the enroll wizard before signing, stored on the server at
 * enrolment, and re-rendered on customer detail (printable) exactly as signed.
 * Mirrors the Kotlin AgreementText builder in the agent app line-for-line.
 */

export interface AgreementParties {
  firstName?: string;
  surname?: string;
  idType?: string;
  idNumber?: string;
  phone?: string;
  otherPhone?: string;
  dateOfBirth?: string;
  gender?: string;
  maritalStatus?: string;
  employmentStatus?: string;
  region?: string;
  district?: string;
  physicalAddress?: string;
  preferredLanguage?: string;
  customerName?: string;
  deviceModel?: string;
  imei?: string;
  planName?: string;
  totalLoanAmountCents?: number;
  downPaymentCents?: number;
  dailyRateCents?: number;
  termDays?: number;
  kinName?: string;
  kinRelation?: string;
  kinPhone?: string;
  refereeName?: string;
  refereePhone?: string;
  guarantorName?: string;
  guarantorRelation?: string;
  guarantorPhone?: string;
  guarantorId?: string;
}

export function agreementMoney(cents: number): string {
  return 'GH₵ ' + (cents / 100).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function agreementToday(): string {
  const d = new Date();
  const dd = String(d.getDate()).padStart(2, '0');
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  return `${dd}/${mm}/${d.getFullYear()}`;
}

export function buildAgreement(p: AgreementParties, date: string = agreementToday()): string {
  const customer = [p.firstName || '', p.surname || ''].map((s) => s.trim()).filter(Boolean).join(' ')
    || (p.customerName?.trim() ?? '')
    || '__________________';
  const idType = (p.idType || '').trim();
  const idNumber = (p.idNumber || '').trim();
  const idDesc = idType && idNumber ? `${idType} number ${idNumber}` : idNumber ? `ID number ${idNumber}` : 'the national ID on file';
  const total = p.totalLoanAmountCents ?? 0;
  const down = p.downPaymentCents ?? 0;
  const daily = p.dailyRateCents ?? 0;
  const term = p.termDays ?? 0;
  const outstanding = Math.max(0, total - down);

  const row = (label: string, value?: string) => `${label}: ${value && value.trim() ? value : '—'}`;
  const lines: string[] = [];

  lines.push('TOUCH BASE');
  lines.push('DEVICE FINANCING AGREEMENT');
  lines.push('');
  lines.push('I. CUSTOMER ID');
  lines.push(row('First Name', p.firstName) + '        ' + row('Last Name', p.surname));
  lines.push(row('ID Type', p.idType) + '        ' + row('ID Number', p.idNumber));
  lines.push(row('Mobile #', p.phone) + '        ' + row('Other #', p.otherPhone));
  lines.push(row('Date of Birth', p.dateOfBirth) + '        ' + row('Gender', p.gender));
  lines.push(row('Marital Status', p.maritalStatus) + '        ' + row('Employment', p.employmentStatus));
  lines.push(row('Region', p.region) + '        ' + row('District', p.district));
  lines.push(row('Physical Address', p.physicalAddress));
  lines.push(row('Preferred Language', p.preferredLanguage));
  lines.push('');
  lines.push('II. PARTIES AND PURPOSE');
  lines.push(`1. This Device Financing Agreement ("the Agreement") is entered into on ${date} between Touch Base ("the Company", represented by its authorised agent) and ${customer}, holder of ${idDesc} ("the Customer").`);
  lines.push('2. The Company agrees to sell the Product described in Section III to the Customer on a financed, pay-as-you-go basis, and the Customer agrees to pay for the Product in the instalments described in Sections IV and V.');
  lines.push('');
  lines.push('III. PRODUCT AND OFFER');
  lines.push(row('1. Product', p.deviceModel));
  lines.push(row('2. Device IMEI', p.imei));
  lines.push(`3. Offer: ${p.planName?.trim() ? p.planName : 'Custom terms'}`);
  lines.push(`4. The Customer acknowledges that the Product carries the Company's device-management software, which protects the Company's interest until the loan is fully repaid.`);
  lines.push('');
  lines.push('IV. LOAN DETAILS');
  lines.push(row('1. Total loan amount', agreementMoney(total)));
  lines.push(row('2. Initial payment (deposit)', agreementMoney(down)) + ', payable on signing this Agreement.');
  lines.push(row('3. Daily repayment rate', agreementMoney(daily)) + ` every day for ${term} days.`);
  lines.push(`4. Repayment period: ${term} days, starting on ${date}.`);
  lines.push(row('5. Outstanding balance at signing', agreementMoney(outstanding)));
  lines.push('');
  lines.push('V. MONEY HANDLING AND PAYMENTS');
  lines.push(`1. The Customer shall pay each instalment through the Company's approved payment channels: MTN Mobile Money, Telecel Cash, the Touch Base customer app, or cash paid to an authorised agent of the Company against an official receipt.`);
  lines.push('2. Payments are due every day without demand. A payment counts as made only when confirmed in the Company\'s systems.');
  lines.push('3. The Customer may pay more than the daily rate, or settle the full outstanding balance, at any time without penalty.');
  lines.push('4. Ownership of the Product remains with the Company until the total loan amount is paid in full. The Customer shall not sell, pawn, gift, or otherwise transfer the Product while this Agreement is active.');
  lines.push('');
  lines.push('VI. DEVICE SECURITY, LOCKING AND TRACKING');
  lines.push(`1. The Product is protected by the Company's device-management software. The Customer shall not remove, disable, or attempt to bypass it.`);
  lines.push('2. If a payment is overdue, the Product locks automatically and unlocks once the overdue amount is paid. Continued default may bring further restrictions (calls, data and app access).');
  lines.push(`3. The Customer consents to the Company recording the Product's location for fraud prevention, theft recovery and financing protection.`);
  lines.push('4. After full payment of the total loan amount, the lock is permanently removed and full ownership passes to the Customer.');
  lines.push('');
  lines.push('VII. REFEREES, NEXT OF KIN AND GUARANTOR');
  lines.push(row('Next of Kin', p.kinName) + (p.kinRelation?.trim() ? ` (${p.kinRelation})` : '') + (p.kinPhone?.trim() ? ` — ${p.kinPhone}` : ''));
  lines.push(row('Referee', p.refereeName) + (p.refereePhone?.trim() ? ` — ${p.refereePhone}` : ''));
  lines.push(row('Guarantor (co-signer)', p.guarantorName) + (p.guarantorRelation?.trim() ? ` (${p.guarantorRelation})` : '') + (p.guarantorPhone?.trim() ? ` — ${p.guarantorPhone}` : '') + (p.guarantorId?.trim() ? `, ID ${p.guarantorId}` : ''));
  lines.push(`1. The Guarantor co-signs this Agreement and guarantees the Customer's obligations. If the Customer defaults or cannot be reached, the Guarantor accepts responsibility for helping the Company contact the Customer and for settling outstanding amounts.`);
  lines.push('2. The Customer confirms that the next of kin, referee and guarantor named above have agreed to be contacted by the Company in relation to this Agreement.');
  lines.push('');
  lines.push('VIII. CONSENT FOR COLLECTION AND PROCESSING OF PERSONAL DATA AND PRODUCT INFORMATION');
  lines.push(`1. Privacy Policy. The Customer acknowledges receiving (or being able to access) the Company's Customer Privacy Policy, which explains how the Company collects, uses, stores, processes, transfers and shares personal and Product information in furtherance of this Agreement and under applicable law, including the Data Protection Act, 2012 (Act 843) of the Republic of Ghana.`);
  lines.push(`2. Where the Product is a phone, the Customer understands that the Company shall have access to information on the phone, including the applications run on the phone, the SIM/ICCID number, the phone's IMEI number, software crash reports, status and history (collectively "Phone Information"). Phone Information may be shared with mobile network operators and phone manufacturers to troubleshoot, detect fraud, run analytics and improve the quality of services provided to phone customers.`);
  lines.push('3. Data Transfer. The Customer consents to personal data and Product information being collected by, used by, stored with, processed by, transferred to, or shared with entities affiliated with the Company; business partners, suppliers and sub-contractors engaged for the performance of this Agreement; professional advisers, auditors, insurers and service providers; and any party where required by law, regulation, court order or other court proceedings.');
  lines.push(`4. Credit Checks. The Customer consents to the Company retrieving, analysing and processing the Customer's credit history, credit scoring and similar personal information from third parties such as credit reference bureaus and mobile network providers, in order to assess the Customer's credit profile and score, and to the Company reporting the Customer's repayment performance to such bodies.`);
  lines.push('');
  lines.push('IX. DEFAULT AND RECOVERY');
  lines.push('1. If the Customer remains in default, the Company may demand immediate payment of the full outstanding balance, repossess the Product, and report the default to credit reference bureaus.');
  lines.push('2. On default the Customer shall, on demand, return the Product to the Company in good working condition.');
  lines.push('');
  lines.push('X. DECLARATIONS');
  lines.push('1. The Customer declares that all information provided in connection with this Agreement is true and accurate.');
  lines.push('2. The Customer confirms that this Agreement, the privacy policy and the terms and conditions were read over and explained in a language the Customer understands best, and the Customer agrees to the terms and conditions contained herein in relation to the Product.');
  lines.push('');
  lines.push('XI. GOVERNING LAW');
  lines.push('1. This Agreement is governed by the laws of the Republic of Ghana.');
  lines.push('');
  lines.push('XII. SIGNATURES');
  lines.push(`Customer: ${customer}    Date: ${date}`);
  lines.push('Signature: (signed digitally in the Touch Base agent app)');
  lines.push(`Guarantor: ${p.guarantorName?.trim() ? p.guarantorName : '—'}    Date: ${date}`);
  lines.push(`For the Company: Touch Base authorised agent    Date: ${date}`);

  return lines.join('\n');
}
