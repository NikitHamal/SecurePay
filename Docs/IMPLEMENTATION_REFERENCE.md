# ============================================================
# SecurePay Implementation Reference
# Key API endpoint implementations for multi-tenant features
# Copy these into your dealer-dashboard project
# ============================================================

# ============================================================
# FILE: dealer-dashboard/src/lib/auth.ts (UPDATE)
# ============================================================
# The JWT payload must now include role, agency_id, branch_id

"""
import { createHmac } from 'crypto'; // Only in Node.js env, use Web Crypto in Workers

// JWT sign — include role in payload
export function signToken(dealer: { id: string; name: string; role: string; agencyId?: string; branchId?: string }, secret: string): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = btoa(JSON.stringify({
    sub: dealer.id,
    name: dealer.name,
    role: dealer.role,
    agencyId: dealer.agencyId,
    branchId: dealer.branchId,
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + (8 * 60 * 60) // 8 hours, NOT 30 days
  }));
  const signature = btoa(
    // Use Web Crypto in Workers:
    // const key = await crypto.subtle.importKey(...)
    // For now, keep existing implementation but update payload
    hmacSha256(`${header}.${payload}`, secret)
  );
  return `${header}.${payload}.${signature}`;
}

// JWT verify — extract role
export function verifyToken(token: string, secret: string): { sub: string; name: string; role: string; agencyId?: string; branchId?: string } | null {
  // ... existing verification logic ...
  // Just make sure to decode and return role, agencyId, branchId from payload
}
"""

# ============================================================
# FILE: dealer-dashboard/src/hooks.server.ts (UPDATE)
# ============================================================
# Add role to locals

"""
// After JWT verification:
if (dealer) {
  event.locals.dealer = {
    id: dealer.sub,
    name: dealer.name,
    role: dealer.role,
    agencyId: dealer.agencyId,
    branchId: dealer.branchId
  };
}

// Add a scope helper:
export function getScopeFilter(dealer: any): { where: string; params: string[] } {
  switch (dealer.role) {
    case 'SUPER_ADMIN':
      return { where: '1=1', params: [] };
    case 'AGENCY_OWNER':
      return { where: 'agency_id = ?', params: [dealer.agencyId] };
    case 'BRANCH_ADMIN':
      return { where: 'branch_id = ?', params: [dealer.branchId] };
    case 'AGENT':
      return { where: 'enrolled_by = ?', params: [dealer.id] };
    default:
      return { where: '1=0', params: [] }; // deny all
  }
}
"""

# ============================================================
# FILE: dealer-dashboard/src/routes/api/auth/register-agent/+server.ts (NEW)
# ============================================================

"""
import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse, generateToken } from '$lib/api/server';
import bcrypt from 'bcryptjs';

export const POST: RequestHandler = async ({ request, platform }) => {
  const body = await request.json();
  const { fullName, email, phone, password, requestedBranchId } = body;

  if (!fullName || !email || !phone || !password) {
    return errorResponse('Missing required fields', 400);
  }

  if (password.length < 8) {
    return errorResponse('Password must be at least 8 characters', 400);
  }

  const db = getDb({ platform });

  // Check if email already exists (in dealers or agent_requests)
  const existingDealer = await db.prepare('SELECT id FROM dealers WHERE email = ?').bind(email).first();
  if (existingDealer) {
    return errorResponse('Email already registered', 409);
  }

  const existingRequest = await db.prepare('SELECT id FROM agent_requests WHERE email = ?').bind(email).first();
  if (existingRequest) {
    return errorResponse('Registration request already pending', 409);
  }

  const hashedPassword = await bcrypt.hash(password, 10);
  const requestId = `AREQ-${generateToken(4)}`;

  await db.prepare(`
    INSERT INTO agent_requests (id, full_name, email, phone, password, requested_branch_id, status)
    VALUES (?, ?, ?, ?, ?, ?, 'PENDING')
  `).bind(requestId, fullName, email, phone, hashedPassword, requestedBranchId || null).run();

  // TODO: Notify SUPER_ADMIN and relevant BRANCH_ADMIN about new request

  return json({
    message: 'Registration submitted. An admin will review your request.',
    requestId
  }, { status: 201 });
};
"""

# ============================================================
# FILE: dealer-dashboard/src/routes/api/auth/approve-agent/+server.ts (NEW)
# ============================================================

"""
import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse, generateToken } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  // Only BRANCH_ADMIN+ can approve agents
  const allowedRoles = ['SUPER_ADMIN', 'AGENCY_OWNER', 'BRANCH_ADMIN'];
  if (!allowedRoles.includes(locals.dealer.role)) {
    return errorResponse('Insufficient permissions', 403);
  }

  const { requestId, branchId } = await request.json();
  if (!requestId) return errorResponse('requestId required', 400);

  const db = getDb({ platform });

  const req = await db.prepare('SELECT * FROM agent_requests WHERE id = ? AND status = ?')
    .bind(requestId, 'PENDING').first();

  if (!req) return errorResponse('Request not found or already processed', 404);

  const now = Math.floor(Date.now() / 1000);
  const assignedBranch = branchId || req.requested_branch_id || locals.dealer.branchId;

  // Get the branch's agency
  const branch = await db.prepare('SELECT agency_id FROM branches WHERE id = ?')
    .bind(assignedBranch).first();
  const agencyId = branch?.agency_id || locals.dealer.agencyId;

  // Create the dealer (agent) record
  const dealerId = uuidv4();
  await db.prepare(`
    INSERT INTO dealers (id, name, email, phone, password, role, agency_id, branch_id, is_approved, approved_by, approved_at)
    VALUES (?, ?, ?, ?, ?, 'AGENT', ?, ?, 1, ?, ?)
  `).bind(dealerId, req.full_name, req.email, req.phone, req.password,
    agencyId, assignedBranch, locals.dealer.id, now).run();

  // Update request status
  await db.prepare('UPDATE agent_requests SET status = ?, reviewed_by = ?, reviewed_at = ? WHERE id = ?')
    .bind('APPROVED', locals.dealer.id, now, requestId).run();

  // Create notification for the super admin
  await db.prepare(`
    INSERT INTO notifications (id, recipient_id, type, title, message, related_entity_type, related_entity_id)
    VALUES (?, ?, 'AGENT_APPROVED', ?, ?, 'dealer', ?)
  `).bind(
    uuidv4(),
    // Notify all SUPER_ADMIN users
    (await db.prepare("SELECT id FROM dealers WHERE role = 'SUPER_ADMIN' LIMIT 1").first())?.id,
    'New Agent Approved',
    `Agent ${req.full_name} has been approved and assigned to branch ${assignedBranch}`,
    dealerId
  ).run();

  return json({ message: 'Agent approved successfully', dealerId });
};
"""

# ============================================================
# FILE: dealer-dashboard/src/routes/api/auth/reject-agent/+server.ts (NEW)
# ============================================================

"""
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const allowedRoles = ['SUPER_ADMIN', 'AGENCY_OWNER', 'BRANCH_ADMIN'];
  if (!allowedRoles.includes(locals.dealer.role)) {
    return errorResponse('Insufficient permissions', 403);
  }

  const { requestId, reason } = await request.json();
  if (!requestId) return errorResponse('requestId required', 400);

  const db = getDb({ platform });

  const req = await db.prepare('SELECT * FROM agent_requests WHERE id = ? AND status = ?')
    .bind(requestId, 'PENDING').first();

  if (!req) return errorResponse('Request not found or already processed', 404);

  const now = Math.floor(Date.now() / 1000);
  await db.prepare('UPDATE agent_requests SET status = ?, reviewed_by = ?, reviewed_at = ? WHERE id = ?')
    .bind('REJECTED', locals.dealer.id, now, requestId).run();

  return { message: 'Agent request rejected' };
};
"""

# ============================================================
# FILE: dealer-dashboard/src/routes/api/agent-requests/+server.ts (NEW)
# ============================================================

"""
import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

export const GET: RequestHandler = async ({ locals, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const db = getDb({ platform });

  // Filter based on role
  let query: string;
  let params: any[];

  switch (locals.dealer.role) {
    case 'SUPER_ADMIN':
      query = 'SELECT id, full_name, email, phone, status, requested_branch_id, created_at FROM agent_requests ORDER BY created_at DESC';
      params = [];
      break;
    case 'AGENCY_OWNER':
      query = `SELECT ar.id, ar.full_name, ar.email, ar.phone, ar.status, ar.requested_branch_id, ar.created_at
        FROM agent_requests ar
        LEFT JOIN branches b ON ar.requested_branch_id = b.id
        WHERE b.agency_id = ? OR ar.requested_branch_id IS NULL
        ORDER BY ar.created_at DESC`;
      params = [locals.dealer.agencyId];
      break;
    case 'BRANCH_ADMIN':
      query = `SELECT id, full_name, email, phone, status, requested_branch_id, created_at
        FROM agent_requests
        WHERE requested_branch_id = ? OR requested_branch_id IS NULL
        ORDER BY created_at DESC`;
      params = [locals.dealer.branchId];
      break;
    default:
      return errorResponse('Insufficient permissions', 403);
  }

  const result = await db.prepare(query).bind(...params).all();
  return json(result.results);
};
"""

# ============================================================
# FILE: dealer-dashboard/src/routes/api/notifications/+server.ts (NEW)
# ============================================================

"""
import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

export const GET: RequestHandler = async ({ locals, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const db = getDb({ platform });
  const result = await db.prepare(`
    SELECT * FROM notifications
    WHERE recipient_id = ?
    ORDER BY created_at DESC
    LIMIT 50
  `).bind(locals.dealer.id).all();

  return json(result.results);
};

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const { ids } = await request.json();
  if (!Array.isArray(ids)) return errorResponse('ids must be an array', 400);

  const db = getDb({ platform });
  for (const id of ids) {
    await db.prepare('UPDATE notifications SET is_read = 1 WHERE id = ? AND recipient_id = ?')
      .bind(id, locals.dealer.id).run();
  }

  return { message: 'Marked as read' };
};
"""

# ============================================================
# FILE: dealer-dashboard/src/routes/api/webhooks/didit/+server.ts (NEW)
# ============================================================

"""
import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb } from '$lib/api/server';

export const POST: RequestHandler = async ({ request, platform }) => {
  const db = getDb({ platform });
  const signature = request.headers.get('x-signature-v2');
  const body = await request.text();

  // Verify webhook signature
  const webhookSecret = platform?.env?.DIDIT_WEBHOOK_SECRET;
  if (!webhookSecret || !signature) {
    return new Response('Missing signature', { status: 401 });
  }

  const isValid = await verifyDiditSignature(body, signature, webhookSecret);
  if (!isValid) {
    return new Response('Invalid signature', { status: 401 });
  }

  const payload = JSON.parse(body);
  const sessionId = payload.data?.session_id || payload.session_id;
  const status = payload.data?.status || payload.status;
  const vendorData = payload.data?.vendor_data || payload.vendor_data;

  if (!sessionId || !vendorData) {
    return new Response('Missing required fields', { status: 400 });
  }

  // vendor_data is the account ID we passed when creating the session
  const accountId = vendorData;

  // Update account with verification result
  const now = Math.floor(Date.now() / 1000);
  const isVerified = status === 'Approved' ? 1 : 0;

  await db.prepare(`
    UPDATE accounts
    SET ghana_card_verified = ?,
        ghana_card_status = ?,
        ghana_card_verified_at = CASE WHEN ? = 1 THEN ? ELSE ghana_card_verified_at END,
        didit_session_id = ?
    WHERE id = ?
  `).bind(isVerified, status, isVerified, now, sessionId, accountId).run();

  return json({ received: true });
};

async function verifyDiditSignature(body: string, signature: string, secret: string): Promise<boolean> {
  const encoder = new TextEncoder();

  // Parse, sort keys recursively, shorten floats, stringify
  const parsed = JSON.parse(body);
  const sorted = sortKeysRecursive(parsed);
  const canonical = JSON.stringify(sorted);

  const key = await crypto.subtle.importKey(
    'raw',
    encoder.encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['verify']
  );

  const sigBytes = hexToUint8Array(signature);
  const dataBytes = encoder.encode(canonical);

  return await crypto.subtle.verify('HMAC', key, sigBytes, dataBytes);
}

function sortKeysRecursive(obj: any): any {
  if (obj === null || typeof obj !== 'object') return obj;
  if (Array.isArray(obj)) return obj.map(sortKeysRecursive);
  return Object.keys(obj).sort().reduce((acc: any, key: string) => {
    acc[key] = sortKeysRecursive(obj[key]);
    return acc;
  }, {});
}

function hexToUint8Array(hex: string): Uint8Array {
  const bytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < hex.length; i += 2) {
    bytes[i / 2] = parseInt(hex.substr(i, 2), 16);
  }
  return bytes;
}
"""

# ============================================================
# FILE: dealer-dashboard/src/routes/api/my-sales/+server.ts (NEW)
# ============================================================
# For agents to see only their own sales

"""
import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, computeStatus, errorResponse, releaseFields } from '$lib/api/server';

export const GET: RequestHandler = async ({ locals, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  // Agents can only see their own sales
  if (locals.dealer.role !== 'AGENT') {
    return errorResponse('This endpoint is for agents only', 403);
  }

  const db = getDb({ platform });
  const result = await db.prepare(`
    SELECT a.*, d.imei, d.model as device_model, COALESCE(p.name, 'Custom') as plan_name
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    LEFT JOIN plans p ON a.plan_id = p.id
    WHERE a.enrolled_by = ?
    ORDER BY a.created_at DESC
  `).bind(locals.dealer.id).all();

  const sales = result.results.map((row) => {
    const nextPaymentDue = Number(row.next_payment_due);
    const status = row.release_approved === 1
      ? 'ACTIVE'
      : (row.is_stolen === 1 ? 'STOLEN' : (row.locked_by_dealer === 1 ? 'LOCKED' : computeStatus(nextPaymentDue)));

    return {
      id: row.id,
      customerName: row.customer_name,
      imei: row.imei,
      deviceModel: row.device_model,
      planName: row.plan_name,
      totalLoanAmount: Number(row.total_loan_amount),
      amountPaid: Number(row.amount_paid),
      remainingBalance: Math.max(0, Number(row.total_loan_amount) - Number(row.amount_paid)),
      dailyRate: Number(row.daily_rate),
      nextPaymentDueEpochMillis: nextPaymentDue,
      status,
      downPayment: Number(row.down_payment),
      ...releaseFields(row as Record<string, any>)
    };
  });

  return json(sales);
};
"""
