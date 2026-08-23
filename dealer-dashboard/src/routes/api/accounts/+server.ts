import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import {
  getDb,
  computeStatus,
  errorResponse,
  releaseFields,
  releaseApproved,
  getR2,
  generateAccountId,
  generateCustomerPin
} from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';
import type { Customer, Status } from '$lib/types';
import { getScopeFilter, hashPassword } from '$lib/auth';
import { logActivity, normalizeNationalId, NATIONAL_ID_NORM_SQL } from '$lib/audit';

const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
const DAY_MS = 24 * 60 * 60 * 1000;

type ImageMime = 'image/jpeg' | 'image/png' | 'image/webp';

interface EncodedImage {
  bytes: Uint8Array;
  mimeType: ImageMime;
  extension: 'jpg' | 'png' | 'webp';
}

function cleanText(value: unknown): string {
  return String(value ?? '').trim();
}

/** Human readable cedis for error messages (values are stored in pesewas). */
function cedis(pesewas: number): string {
  return `GHS ${(pesewas / 100).toLocaleString('en-GH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function parseSafeInteger(value: unknown, fieldName: string, minimum: number, money = true): number {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed)) {
    throw new Error(`${fieldName} must be a whole number`);
  }
  if (parsed < minimum) {
    throw new Error(`${fieldName} must be at least ${money ? cedis(minimum) : minimum}`);
  }
  return parsed;
}

/**
 * Runtime idempotent guard so a fresh/unmigrated D1 database can not 500 the
 * enrollment endpoints. Mirrors migrations/20260727_application_extras.sql.
 */
async function ensureApplicationColumns(db: ReturnType<typeof getDb>) {
  try {
    const info = await db.prepare('PRAGMA table_info(accounts)').all();
    const existing = new Set(info.results.map((row) => String((row as { name?: unknown }).name ?? '')));
    const defs: Array<[string, string]> = [
      ['id_type', 'TEXT'],
      ['next_of_kin_name', 'TEXT'],
      ['next_of_kin_phone', 'TEXT'],
      ['next_of_kin_relation', 'TEXT'],
      ['referee_name', 'TEXT'],
      ['referee_phone', 'TEXT'],
      ['guarantor_name', 'TEXT'],
      ['guarantor_phone', 'TEXT'],
      ['guarantor_id_number', 'TEXT'],
      ['guarantor_relation', 'TEXT'],
      ['consent_terms', 'INTEGER NOT NULL DEFAULT 0'],
      ['consent_data', 'INTEGER NOT NULL DEFAULT 0'],
      ['consent_at', 'INTEGER'],
      ['customer_signature_path', 'TEXT'],
      // 20260727_customer_profile.sql — M-KOPA style profile + signed agreement
      ['surname', 'TEXT'],
      ['other_phone', 'TEXT'],
      ['date_of_birth', 'TEXT'],
      ['marital_status', 'TEXT'],
      ['employment_status', 'TEXT'],
      ['gender', 'TEXT'],
      ['is_customer_user', 'INTEGER'],
      ['region', 'TEXT'],
      ['district', 'TEXT'],
      ['physical_address', 'TEXT'],
      ['preferred_language', 'TEXT'],
      ['agreement_text', 'TEXT'],
      // 20260801_accountability.sql — GPS captured at enrollment time
      ['enrollment_lat', 'REAL'],
      ['enrollment_lng', 'REAL'],
      ['enrollment_accuracy', 'REAL'],
      // 20260821_admin_pricing_downpayments.sql
      ['down_payment_status', 'TEXT DEFAULT \'unpaid\''],
      ['down_payment_confirmed_by', 'TEXT'],
      ['down_payment_confirmed_at', 'INTEGER']
    ];
    for (const [column, ddl] of defs) {
      if (!existing.has(column)) {
        await db.prepare(`ALTER TABLE accounts ADD COLUMN ${column} ${ddl}`).run();
      }
    }
    // Ensure device pricing/assignment columns exist (idempotent for legacy DBs).
    try {
      const dinfo = await db.prepare('PRAGMA table_info(devices)').all();
      const dexist = new Set(dinfo.results.map((row) => String((row as { name?: unknown }).name ?? '')));
      for (const [column, ddl] of [
        ['product_model_id', 'TEXT'], ['assigned_to', 'TEXT'], ['assigned_at', 'INTEGER'], ['assigned_by', 'TEXT'],
        ['total_amount', 'INTEGER'], ['down_payment', 'INTEGER'], ['daily_rate', 'INTEGER'], ['term_days', 'INTEGER']
      ] as Array<[string, string]>) {
        if (!dexist.has(column)) await db.prepare(`ALTER TABLE devices ADD COLUMN ${column} ${ddl}`).run();
      }
      await db.prepare(`CREATE TABLE IF NOT EXISTS product_models (
        id TEXT PRIMARY KEY, name TEXT NOT NULL UNIQUE, model TEXT NOT NULL, description TEXT,
        total_amount INTEGER NOT NULL, down_payment INTEGER NOT NULL, daily_rate INTEGER NOT NULL, term_days INTEGER NOT NULL,
        created_by TEXT, is_active INTEGER DEFAULT 1, created_at INTEGER DEFAULT (unixepoch()), updated_at INTEGER DEFAULT (unixepoch())
      )`).run();
      await db.prepare(`CREATE TABLE IF NOT EXISTS down_payment_submissions (
        id TEXT PRIMARY KEY, account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
        device_id TEXT NOT NULL REFERENCES devices(id), agent_id TEXT NOT NULL REFERENCES dealers(id),
        amount INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','confirmed','rejected','cancelled')),
        method TEXT NOT NULL DEFAULT 'cash', reference TEXT, submitted_at INTEGER DEFAULT (unixepoch()),
        confirmed_by TEXT, confirmed_at INTEGER, note TEXT, created_at INTEGER DEFAULT (unixepoch()), updated_at INTEGER DEFAULT (unixepoch())
      )`).run();
    } catch { /* ignore — nested ensure is best-effort */ }
  } catch { /* read-only replica — the query below will surface a real problem */ }
}

/**
 * Find accounts (any owner — an ID belongs to a person, not to one dealer)
 * already holding this national ID after normalization. Ghana Cards are the
 * primary target but the check intentionally covers every ID type: the same
 * number must never be enrolled twice.
 */
async function findNationalIdDuplicates(
  db: ReturnType<typeof getDb>,
  nationalId: string,
  excludeAccountId?: string
) {
  const normalized = normalizeNationalId(nationalId);
  if (!normalized) return [];
  const result = await db.prepare(`
    SELECT a.id, a.customer_name, a.amount_paid, a.total_loan_amount, a.created_at,
           COALESCE(a.release_approved, 0) AS release_approved,
           d.model AS device_model, dl.name AS enrolled_by_name
      FROM accounts a
      JOIN devices d ON d.id = a.device_id
      LEFT JOIN dealers dl ON dl.id = a.enrolled_by
     WHERE ${NATIONAL_ID_NORM_SQL} = ?
       ${excludeAccountId ? 'AND a.id != ?' : ''}
     ORDER BY a.created_at DESC
     LIMIT 3
  `).bind(...(excludeAccountId ? [normalized, excludeAccountId] : [normalized])).all<{
    id: string;
    customer_name: string;
    amount_paid: number;
    total_loan_amount: number;
    created_at: number;
    release_approved: number;
    device_model: string;
    enrolled_by_name: string | null;
  }>();
  return result.results;
}

function applicationFields(row: Record<string, unknown>) {
  return {
    idType: (row.id_type ?? null) as string | null,
    nextOfKinName: (row.next_of_kin_name ?? null) as string | null,
    nextOfKinPhone: (row.next_of_kin_phone ?? null) as string | null,
    nextOfKinRelation: (row.next_of_kin_relation ?? null) as string | null,
    refereeName: (row.referee_name ?? null) as string | null,
    refereePhone: (row.referee_phone ?? null) as string | null,
    guarantorName: (row.guarantor_name ?? null) as string | null,
    guarantorPhone: (row.guarantor_phone ?? null) as string | null,
    guarantorIdNumber: (row.guarantor_id_number ?? null) as string | null,
    guarantorRelation: (row.guarantor_relation ?? null) as string | null,
    consentTerms: row.consent_terms === 1,
    consentData: row.consent_data === 1,
    consentAt: (row.consent_at ?? null) as number | null,
    customerSignaturePath: (row.customer_signature_path ?? null) as string | null,
    surname: (row.surname ?? null) as string | null,
    otherPhone: (row.other_phone ?? null) as string | null,
    dateOfBirth: (row.date_of_birth ?? null) as string | null,
    maritalStatus: (row.marital_status ?? null) as string | null,
    employmentStatus: (row.employment_status ?? null) as string | null,
    gender: (row.gender ?? null) as string | null,
    isCustomerUser: row.is_customer_user == null ? null : row.is_customer_user === 1,
    region: (row.region ?? null) as string | null,
    district: (row.district ?? null) as string | null,
    physicalAddress: (row.physical_address ?? null) as string | null,
    preferredLanguage: (row.preferred_language ?? null) as string | null,
    agreementText: (row.agreement_text ?? null) as string | null
  };
}

function cleanOptText(value: unknown, max: number): string | null {
  const text = cleanText(value);
  if (!text) return null;
  return text.slice(0, max);
}

function cleanOptPhone(value: unknown): string | null {
  const text = cleanText(value);
  if (!text) return null;
  if (!/^[0-9+\-()\s]{7,24}$/.test(text)) {
    throw new Error(`Phone numbers must contain 7 to 24 valid phone characters`);
  }
  return text;
}

/** Optional finite coordinate inside [min, max]; anything else is ignored. */
function parseOptionalCoordinate(value: unknown, min: number, max: number): number | null {
  if (value == null || value === '') return null;
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < min || parsed > max) return null;
  return parsed;
}

function boolFlag(value: unknown): boolean {
  return value === true || value === 1 || value === '1' || value === 'true';
}

function decodeImage(value: unknown, fieldName: string): EncodedImage | null {
  const input = cleanText(value);
  if (!input) return null;

  const match = input.match(/^data:image\/(jpeg|jpg|png|webp);base64,([A-Za-z0-9+/=\s]+)$/i);
  const mimeToken = match?.[1]?.toLowerCase() ?? 'jpeg';
  const payload = (match?.[2] ?? input).replace(/\s/g, '');

  if (!/^[A-Za-z0-9+/]*={0,2}$/.test(payload) || payload.length % 4 === 1) {
    throw new Error(`${fieldName} is not valid Base64 image data`);
  }

  let binary: string;
  try {
    binary = atob(payload);
  } catch {
    throw new Error(`${fieldName} is not valid Base64 image data`);
  }

  if (binary.length === 0 || binary.length > MAX_PHOTO_BYTES) {
    throw new Error(`${fieldName} must be between 1 byte and 5MB`);
  }

  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }

  if (mimeToken === 'png') return { bytes, mimeType: 'image/png', extension: 'png' };
  if (mimeToken === 'webp') return { bytes, mimeType: 'image/webp', extension: 'webp' };
  return { bytes, mimeType: 'image/jpeg', extension: 'jpg' };
}

export const GET: RequestHandler = async ({ locals, url, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const statusFilter = url.searchParams.get('status') as Status | null;
  const db = getDb({ platform });
  const scope = getScopeFilter(locals.dealer);

  const result = await db.prepare(`
    SELECT a.*, d.imei, d.model as device_model, COALESCE(p.name, 'Custom') as plan_name
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    LEFT JOIN plans p ON a.plan_id = p.id
    WHERE ${scope.where}
    ORDER BY a.created_at DESC
  `).bind(...scope.params).all();

  const customers: Customer[] = result.results.map((row) => {
    const rawDue = Number(row.next_payment_due) || 0;
    const nextPaymentDue = rawDue > 0 && rawDue < 1e11 ? rawDue * 1000 : rawDue;
    const rawCreated = Number(row.created_at) || 0;
    const createdAt = rawCreated > 0 && rawCreated < 1e11 ? rawCreated * 1000 : rawCreated;
    const amountPaid = Number(row.amount_paid);
    const totalLoanAmount = Number(row.total_loan_amount);
    const status: Status = releaseApproved(row as Record<string, unknown>)
      ? 'ACTIVE'
      : (row.is_stolen === 1 ? 'STOLEN' : (row.locked_by_dealer === 1 ? 'LOCKED' : computeStatus(nextPaymentDue)));

    return {
      id: row.id as string,
      customerName: row.customer_name as string,
      nationalId: row.national_id as string,
      phoneNumber: row.phone_number as string,
      imei: row.imei as string,
      deviceModel: row.device_model as string,
      planName: row.plan_name as string,
      totalLoanAmount,
      amountPaid,
      remainingBalance: Math.max(0, totalLoanAmount - amountPaid),
      dailyRate: Number(row.daily_rate),
      nextPaymentDueEpochMillis: nextPaymentDue,
      status,
      isStolen: row.is_stolen === 1,
      customerPhotoPath: row.customer_photo_path as string | null,
      nationalIdFrontPath: row.national_id_front_path as string | null,
      nationalIdBackPath: row.national_id_back_path as string | null,
      termDays: Number(row.term_days),
      downPayment: Number(row.down_payment),
      downPaymentStatus: (row.down_payment_status as string | null) ?? 'unpaid',
      enrolledBy: row.enrolled_by as string | null,
      ghanaCardVerified: row.ghana_card_verified === 1,
      ghanaCardStatus: row.ghana_card_status as string | null,
      ...applicationFields(row as Record<string, unknown>),
      ...releaseFields(row as Record<string, unknown>)
    };
  });

  return json(statusFilter ? customers.filter((customer) => customer.status === statusFilter) : customers);
};

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  let body: Record<string, unknown>;
  try {
    body = await request.json() as Record<string, unknown>;
  } catch {
    return errorResponse('Request body must be valid JSON', 400);
  }

  const customerName = cleanText(body.customerName);
  const nationalId = cleanText(body.nationalId).toUpperCase();
  const phoneNumber = cleanText(body.phoneNumber);
  const imei = cleanText(body.imei).replace(/\D/g, '');
  const planId = cleanText(body.planId) || null;

  if (customerName.length < 2 || customerName.length > 120) {
    return errorResponse('customerName must be between 2 and 120 characters', 400);
  }
  if (nationalId.length < 4 || nationalId.length > 64) {
    return errorResponse('nationalId must be between 4 and 64 characters', 400);
  }
  if (!/^[0-9+()\-\s]{8,24}$/.test(phoneNumber)) {
    return errorResponse('phoneNumber must contain 8 to 24 valid phone characters', 400);
  }
  if (!/^\d{15}$/.test(imei)) {
    return errorResponse('imei must contain exactly 15 digits', 400);
  }
  if (!planId && body.dailyRate == null) {
    return errorResponse('dailyRate is required (pricing is set per sale)', 400);
  }

  // M-KOPA style application fields: references, guarantor, consent, signature.
  // All optional so older app builds keep working.
  let applicationData: {
    idType: string | null;
    nextOfKinName: string | null;
    nextOfKinPhone: string | null;
    nextOfKinRelation: string | null;
    refereeName: string | null;
    refereePhone: string | null;
    guarantorName: string | null;
    guarantorPhone: string | null;
    guarantorIdNumber: string | null;
    guarantorRelation: string | null;
    consentTerms: boolean;
    consentData: boolean;
    surname: string | null;
    otherPhone: string | null;
    dateOfBirth: string | null;
    maritalStatus: string | null;
    employmentStatus: string | null;
    gender: string | null;
    isCustomerUser: number | null;
    region: string | null;
    district: string | null;
    physicalAddress: string | null;
    preferredLanguage: string | null;
    agreementText: string | null;
  };
  let customerPhoto: EncodedImage | null;
  let nationalIdFront: EncodedImage | null;
  let nationalIdBack: EncodedImage | null;
  let customerSignature: EncodedImage | null;
  try {
    applicationData = {
      idType: cleanOptText(body.idType, 32),
      nextOfKinName: cleanOptText(body.nextOfKinName, 120),
      nextOfKinPhone: cleanOptPhone(body.nextOfKinPhone),
      nextOfKinRelation: cleanOptText(body.nextOfKinRelation, 40),
      refereeName: cleanOptText(body.refereeName, 120),
      refereePhone: cleanOptPhone(body.refereePhone),
      guarantorName: cleanOptText(body.guarantorName, 120),
      guarantorPhone: cleanOptPhone(body.guarantorPhone),
      guarantorIdNumber: cleanOptText(body.guarantorIdNumber, 64),
      guarantorRelation: cleanOptText(body.guarantorRelation, 40),
      consentTerms: boolFlag(body.consentTerms),
      consentData: boolFlag(body.consentData),
      surname: cleanOptText(body.surname, 60),
      otherPhone: cleanOptPhone(body.otherPhone),
      dateOfBirth: cleanOptText(body.dateOfBirth, 16),
      maritalStatus: cleanOptText(body.maritalStatus, 24),
      employmentStatus: cleanOptText(body.employmentStatus, 40),
      gender: cleanOptText(body.gender, 12),
      isCustomerUser: body.isCustomerUser == null ? null : (boolFlag(body.isCustomerUser) ? 1 : 0),
      region: cleanOptText(body.region, 60),
      district: cleanOptText(body.district, 80),
      physicalAddress: cleanOptText(body.physicalAddress, 160),
      preferredLanguage: cleanOptText(body.preferredLanguage, 40),
      agreementText: cleanOptText(body.agreementText, 20000)
    };
    customerPhoto = decodeImage(body.customerPhoto, 'customerPhoto');
    nationalIdFront = decodeImage(body.nationalIdFront, 'nationalIdFront');
    nationalIdBack = decodeImage(body.nationalIdBack, 'nationalIdBack');
    customerSignature = decodeImage(body.customerSignature, 'customerSignature');
  } catch (error) {
    return errorResponse(error instanceof Error ? error.message : 'Invalid application data', 400);
  }

  // GPS fix captured by the agent app when the application was submitted
  // (anti-fraud context — optional so older app builds keep working).
  const enrollmentLat = parseOptionalCoordinate(body.enrollmentLat, -90, 90);
  const enrollmentLng = parseOptionalCoordinate(body.enrollmentLng, -180, 180);
  const enrollmentAccuracy = parseOptionalCoordinate(body.enrollmentAccuracy, 0, 100000);

  const db = getDb({ platform });
  await ensureApplicationColumns(db);

  // Hard guard: the same Ghana Card / ID number can never be enrolled twice.
  const duplicates = await findNationalIdDuplicates(db, nationalId);
  if (duplicates.length > 0) {
    const first = duplicates[0];
    const idLabel = applicationData.idType || 'ID';
    return errorResponse(
      `Duplicate ${idLabel}: ${nationalId} is already registered to ${first.customer_name} (${first.device_model}). The same ID cannot be enrolled twice.`,
      409
    );
  }
  // Device lookup: agents see only their assigned IMEIs; admins see any.
  const isAgent = locals.dealer.role === 'AGENT';
  const device = isAgent
    ? await db.prepare(
        'SELECT id, imei, model, status, dealer_id, assigned_to, product_model_id, total_amount, down_payment, daily_rate, term_days FROM devices WHERE imei = ? AND (assigned_to = ? OR (assigned_to IS NULL AND dealer_id = ?))'
      ).bind(imei, locals.dealer.id, locals.dealer.id).first<{ id: string; imei: string; model: string; status: string; dealer_id: string; assigned_to: string | null; product_model_id: string | null; total_amount: number | null; down_payment: number | null; daily_rate: number | null; term_days: number | null }>()
    : await db.prepare(
        'SELECT id, imei, model, status, dealer_id, assigned_to, product_model_id, total_amount, down_payment, daily_rate, term_days FROM devices WHERE imei = ?'
      ).bind(imei).first<{ id: string; imei: string; model: string; status: string; dealer_id: string; assigned_to: string | null; product_model_id: string | null; total_amount: number | null; down_payment: number | null; daily_rate: number | null; term_days: number | null }>();

  if (!device) return errorResponse(isAgent ? 'Device not found in your assigned inventory' : 'Device not found in inventory', 404);
  if (device.status !== 'in_stock') return errorResponse('Device is not available for sale', 409);
  // If inventory has admin-set pricing, agent must use it — ignore any client-supplied financials.
  const deviceHasPricing = device.total_amount != null && device.daily_rate != null && device.term_days != null;

  const plan = planId
    ? await db.prepare('SELECT id, total_amount, daily_rate, term_days, min_down_payment FROM plans WHERE id = ?')
      .bind(planId)
      .first<{ id: string; total_amount: number; daily_rate: number; term_days: number; min_down_payment: number }>()
    : null;
  if (planId && !plan) return errorResponse('Plan not found', 404);

  let totalLoanAmount: number;
  let dailyRate: number;
  let termDays: number;
  let downPayment: number;
  try {
    if (isAgent && deviceHasPricing) {
      // Admin owns pricing — agent's numbers are ignored.
      totalLoanAmount = parseSafeInteger(device.total_amount, 'device total amount', 1);
      dailyRate = parseSafeInteger(device.daily_rate, 'device daily rate', 1);
      termDays = parseSafeInteger(device.term_days, 'device term', 1, false);
      downPayment = parseSafeInteger(device.down_payment ?? 0, 'device down payment', 0);
      // If agent still sent explicit values that diverge, surface a clear error to catch front-end bugs.
      const clientTotal = body.totalAmount != null ? Number(body.totalAmount) : null;
      const clientDaily = body.dailyRate != null ? Number(body.dailyRate) : null;
      const clientTerm = body.termDays != null ? Number(body.termDays) : null;
      if (clientTotal != null && clientTotal !== totalLoanAmount) console.warn(`[accounts] agent supplied totalAmount ${clientTotal} ignored — device ${device.imei} locked to ${totalLoanAmount}`);
      if (clientDaily != null && clientDaily !== dailyRate) console.warn(`[accounts] agent dailyRate ignored`);
      if (clientTerm != null && clientTerm !== termDays) console.warn(`[accounts] agent termDays ignored`);
    } else if (deviceHasPricing && !plan) {
      // Admin enrolling a device that already has catalog pricing — use it unless admin explicitly overrides via plan.
      // Allow admin to override only if they clearly supply a planId; otherwise respect the device's locked price.
      const useDevicePricing = body.totalAmount == null && body.dailyRate == null && body.termDays == null;
      if (useDevicePricing) {
        totalLoanAmount = parseSafeInteger(device.total_amount, 'device total amount', 1);
        dailyRate = parseSafeInteger(device.daily_rate, 'device daily rate', 1);
        termDays = parseSafeInteger(device.term_days, 'device term', 1, false);
        downPayment = parseSafeInteger(device.down_payment ?? 0, 'device down payment', 0);
      } else {
        const pp: any = plan as any;
        totalLoanAmount = pp
          ? parseSafeInteger(pp.total_amount, 'plan total amount', 1)
          : parseSafeInteger(body.totalAmount, 'totalAmount', 1);
        dailyRate = pp
          ? parseSafeInteger(pp.daily_rate, 'plan daily rate', 1)
          : parseSafeInteger(body.dailyRate, 'dailyRate', 1);
        termDays = pp
          ? parseSafeInteger(pp.term_days, 'plan term', 1, false)
          : parseSafeInteger(body.termDays, 'termDays', 1, false);
        const suggestedDownPayment = pp ? parseSafeInteger(pp.min_down_payment, 'plan minimum down payment', 0) : (device.down_payment ?? 0);
        downPayment = body.downPayment == null || body.downPayment === ''
          ? suggestedDownPayment
          : parseSafeInteger(body.downPayment, 'downPayment', 0);
      }
    } else {
      const p: any = plan as any;
      totalLoanAmount = p
        ? parseSafeInteger(p.total_amount, 'plan total amount', 1)
        : parseSafeInteger(body.totalAmount, 'totalAmount', 1);
      dailyRate = p
        ? parseSafeInteger(p.daily_rate, 'plan daily rate', 1)
        : parseSafeInteger(body.dailyRate, 'dailyRate', 1);
      termDays = p
        ? parseSafeInteger(p.term_days, 'plan term', 1, false)
        : parseSafeInteger(body.termDays, 'termDays', 1, false);
      const suggestedDownPayment = p ? parseSafeInteger(p.min_down_payment, 'plan minimum down payment', 0) : 0;
      downPayment = body.downPayment == null || body.downPayment === ''
        ? suggestedDownPayment
        : parseSafeInteger(body.downPayment, 'downPayment', 0);
    }
  } catch (error) {
    return errorResponse(error instanceof Error ? error.message : 'Invalid loan values', 400);
  }

  if (downPayment > totalLoanAmount) {
    return errorResponse(
      `Initial payment ${cedis(downPayment)} cannot be more than the total price ${cedis(totalLoanAmount)}`,
      400
    );
  }

  const nowMillis = Date.now();
  const nowSeconds = Math.floor(nowMillis / 1000);
  const nextPaymentDue = nowMillis + DAY_MS;
  const accountId = generateAccountId();
  const customerAccountNumber = phoneNumber.replace(/\D/g, '');
  if (customerAccountNumber.length < 8 || customerAccountNumber.length > 15) {
    return errorResponse('phoneNumber must normalize to an 8 to 15 digit account number', 400);
  }
  const temporaryPin = generateCustomerPin();
  const customerPinHash = hashPassword(temporaryPin);
  const r2 = getR2({ platform });
  const uploadedKeys: string[] = [];

  const uploadImage = async (image: EncodedImage | null, label: string): Promise<string | null> => {
    if (!image) return null;
    const key = `kyc/customer_${accountId}_${label}.${image.extension}`;
    await r2.put(key, image.bytes, { httpMetadata: { contentType: image.mimeType } });
    uploadedKeys.push(key);
    return key;
  };

  let customerPhotoPath: string | null;
  let nationalIdFrontPath: string | null;
  let nationalIdBackPath: string | null;
  let customerSignaturePath: string | null;
  try {
    customerPhotoPath = await uploadImage(customerPhoto, 'photo');
    nationalIdFrontPath = await uploadImage(nationalIdFront, 'id_front');
    nationalIdBackPath = await uploadImage(nationalIdBack, 'id_back');
    customerSignaturePath = await uploadImage(customerSignature, 'signature');
  } catch (error) {
    await Promise.allSettled(uploadedKeys.map((key) => r2.delete(key)));
    console.error('KYC upload failed', error);
    return errorResponse('Unable to store KYC images. No customer account was created.', 502);
  }

  // Down-payment settlement: agents' cash goes to pending approval; admins confirm instantly.
  const downPaymentStatus = isAgent ? (downPayment > 0 ? 'pending' : 'unpaid') : (downPayment > 0 ? 'confirmed' : 'unpaid');
  const initialAmountPaid = isAgent ? 0 : downPayment;
  const downPaymentConfirmedBy = !isAgent && downPayment > 0 ? locals.dealer.id : null;
  const downPaymentConfirmedAt = !isAgent && downPayment > 0 ? nowSeconds : null;

  const statements = [
    db.prepare(`
      INSERT INTO accounts (
        id, customer_name, national_id, phone_number, device_id, dealer_id, plan_id,
        total_loan_amount, amount_paid, daily_rate, next_payment_due, status,
        locked_by_dealer, down_payment, term_days, currency_code, customer_photo_path,
        national_id_front_path, national_id_back_path, enrolled_by, branch_id, agency_id,
        customer_account_number, customer_pin_hash, customer_pin_updated_at, created_at, updated_at,
        id_type, next_of_kin_name, next_of_kin_phone, next_of_kin_relation,
        referee_name, referee_phone, guarantor_name, guarantor_phone,
        guarantor_id_number, guarantor_relation, consent_terms, consent_data,
        consent_at, customer_signature_path,
        surname, other_phone, date_of_birth, marital_status, employment_status, gender,
        is_customer_user, region, district, physical_address, preferred_language, agreement_text
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 0, ?, ?, 'GHS', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).bind(
      accountId,
      customerName,
      nationalId,
      phoneNumber,
      device.id,
      locals.dealer.id,
      planId,
      totalLoanAmount,
      initialAmountPaid,
      dailyRate,
      nextPaymentDue,
      downPayment,
      termDays,
      customerPhotoPath,
      nationalIdFrontPath,
      nationalIdBackPath,
      locals.dealer.id,
      locals.dealer.branchId || null,
      locals.dealer.agencyId || null,
      customerAccountNumber,
      customerPinHash,
      nowSeconds,
      nowSeconds,
      nowSeconds,
      applicationData.idType,
      applicationData.nextOfKinName,
      applicationData.nextOfKinPhone,
      applicationData.nextOfKinRelation,
      applicationData.refereeName,
      applicationData.refereePhone,
      applicationData.guarantorName,
      applicationData.guarantorPhone,
      applicationData.guarantorIdNumber,
      applicationData.guarantorRelation,
      applicationData.consentTerms ? 1 : 0,
      applicationData.consentData ? 1 : 0,
      applicationData.consentTerms && applicationData.consentData ? nowSeconds : null,
      customerSignaturePath,
      applicationData.surname,
      applicationData.otherPhone,
      applicationData.dateOfBirth,
      applicationData.maritalStatus,
      applicationData.employmentStatus,
      applicationData.gender,
      applicationData.isCustomerUser,
      applicationData.region,
      applicationData.district,
      applicationData.physicalAddress,
      applicationData.preferredLanguage,
      applicationData.agreementText
    ),
    db.prepare("UPDATE devices SET status = 'sold' WHERE id = ? AND status = 'in_stock'").bind(device.id)
  ];

  // Patch in the new down-payment workflow columns (back-compat earlier insert omits them).
  statements.push(db.prepare(
    'UPDATE accounts SET down_payment_status = ?, down_payment_confirmed_by = ?, down_payment_confirmed_at = ? WHERE id = ?'
  ).bind(downPaymentStatus, downPaymentConfirmedBy, downPaymentConfirmedAt, accountId));

  if (enrollmentLat != null && enrollmentLng != null) {
    statements.push(db.prepare(
      'UPDATE accounts SET enrollment_lat = ?, enrollment_lng = ?, enrollment_accuracy = ? WHERE id = ?'
    ).bind(enrollmentLat, enrollmentLng, enrollmentAccuracy, accountId));
  }

  if (downPayment > 0) {
    if (isAgent) {
      statements.push(db.prepare(`
        INSERT INTO down_payment_submissions (id, account_id, device_id, agent_id, amount, status, method, submitted_at, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, 'pending', 'cash', ?, ?, ?)
      `).bind(uuidv4(), accountId, device.id, locals.dealer.id, downPayment, nowSeconds, nowSeconds, nowSeconds));
    } else {
      statements.push(db.prepare(`
        INSERT INTO payments (id, account_id, amount, method, reference, recorded_by, created_at)
        VALUES (?, ?, ?, 'cash', 'Down payment', ?, ?)
      `).bind(uuidv4(), accountId, downPayment, locals.dealer.id, nowSeconds));
    }
  }

  try {
    await db.batch(statements);
  } catch (error) {
    await Promise.allSettled(uploadedKeys.map((key) => r2.delete(key)));
    console.error('Customer enrollment transaction failed', error);
    const message = String(error).toLowerCase();
    const conflict = message.includes('unique') || message.includes('constraint');
    return errorResponse(
      conflict ? 'This device or customer enrollment already exists' : 'Customer enrollment failed. No sale was recorded.',
      conflict ? 409 : 500
    );
  }

  const adminRecipients = await db.prepare(`
    SELECT id FROM dealers
    WHERE role = 'SUPER_ADMIN' OR (role = 'AGENCY_OWNER' AND agency_id = ?)
  `).bind(locals.dealer.agencyId || '').all<{ id: string }>();

  const notificationStatements = adminRecipients.results
    .filter((admin) => admin.id !== locals.dealer?.id)
    .map((admin) => db.prepare(`
      INSERT INTO notifications (
        id, recipient_id, type, title, message, related_entity_type, related_entity_id, created_at
      ) VALUES (?, ?, 'NEW_SALE', ?, ?, 'account', ?, ?)
    `).bind(
      uuidv4(),
      admin.id,
      'New Sale Recorded',
      `${locals.dealer!.name} enrolled customer ${customerName} for ${imei} (GH₵${(downPayment / 100).toFixed(2)} down payment)`,
      accountId,
      nowSeconds
    ));
  if (notificationStatements.length > 0) await db.batch(notificationStatements);

  await logActivity(db, {
    actor: locals.dealer,
    action: 'CUSTOMER_CREATED',
    details: `Enrolled ${customerName} for a ${device.model} (${imei}) with ${cedis(downPayment)} initial payment`,
    customerName,
    accountId,
    imei,
    latitude: enrollmentLat,
    longitude: enrollmentLng
  });

  const row = await db.prepare(`
    SELECT a.*, d.imei, d.model as device_model, COALESCE(p.name, 'Custom') as plan_name
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    LEFT JOIN plans p ON a.plan_id = p.id
    WHERE a.id = ?
  `).bind(accountId).first();

  if (!row) return errorResponse('Account was created but could not be reloaded', 500);

  const amountPaid = Number(row.amount_paid);
  const total = Number(row.total_loan_amount);
  const status: Status = releaseApproved(row as Record<string, unknown>)
    ? 'ACTIVE'
    : computeStatus(Number(row.next_payment_due));

  const customer: Customer = {
    id: row.id as string,
    customerName: row.customer_name as string,
    nationalId: row.national_id as string,
    phoneNumber: row.phone_number as string,
    imei: row.imei as string,
    deviceModel: row.device_model as string,
    planName: (row.plan_name as string) || 'Custom',
    totalLoanAmount: total,
    amountPaid,
    remainingBalance: Math.max(0, total - amountPaid),
    dailyRate: Number(row.daily_rate),
    nextPaymentDueEpochMillis: Number(row.next_payment_due),
    status,
    isStolen: row.is_stolen === 1,
    customerPhotoPath: row.customer_photo_path as string | null,
    nationalIdFrontPath: row.national_id_front_path as string | null,
    nationalIdBackPath: row.national_id_back_path as string | null,
    termDays: Number(row.term_days),
    downPayment,
    enrolledBy: locals.dealer.id,
    ghanaCardVerified: false,
    ghanaCardStatus: null,
    initialCredentials: {
      accountNumber: customerAccountNumber,
      temporaryPin
    },
    ...releaseFields(row as Record<string, unknown>)
  };

  return json(customer, { status: 201 });
};
