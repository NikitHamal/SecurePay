import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { getAccountScopeFilter } from '$lib/auth';

type DiditSessionResponse = {
  session_id?: string;
  session_token?: string;
  url?: string;
};

export const POST: RequestHandler = async ({ locals, params, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const db = getDb({ platform });
  const apiKey = platform?.env?.DIDIT_API_KEY;
  const workflowId = platform?.env?.DIDIT_WORKFLOW_ID;
  const callback = platform?.env?.DIDIT_CALLBACK_URL;

  if (!apiKey || !workflowId) {
    return errorResponse('Didit is not configured. Set DIDIT_API_KEY and DIDIT_WORKFLOW_ID.', 503);
  }

  const scope = getAccountScopeFilter(locals.dealer, 'a');
  const account = await db.prepare(`
    SELECT a.id, a.customer_name, a.phone_number
    FROM accounts a
    WHERE a.id = ? AND ${scope.where}
  `).bind(params.id, ...scope.params).first<{ id: string; customer_name: string; phone_number: string }>();

  if (!account) return errorResponse('Account not found', 404);

  const payload: Record<string, unknown> = {
    workflow_id: workflowId,
    vendor_data: account.id,
    metadata: {
      account_id: account.id,
      customer_name: account.customer_name,
      phone_number: account.phone_number
    }
  };
  if (callback) payload.callback = callback;

  try {
    const response = await fetch('https://verification.didit.me/v3/session/', {
      method: 'POST',
      headers: {
        'x-api-key': apiKey,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    });

    const raw = await response.text();
    if (!response.ok) {
      console.error('Didit session creation failed', response.status, raw.slice(0, 500));
      return errorResponse('Failed to create identity verification session', 502);
    }

    const data = JSON.parse(raw) as DiditSessionResponse;
    if (!data.session_id || !data.url) {
      console.error('Didit returned an incomplete session payload');
      return errorResponse('Identity provider returned an incomplete session', 502);
    }

    await db.prepare(`
      UPDATE accounts
      SET didit_session_id = ?, ghana_card_status = 'In Progress', updated_at = ?
      WHERE id = ?
    `).bind(data.session_id, Math.floor(Date.now() / 1000), account.id).run();

    return json({
      sessionId: data.session_id,
      sessionToken: data.session_token ?? null,
      sessionUrl: data.url,
      status: 'In Progress'
    });
  } catch (err) {
    console.error('Didit API error:', err);
    return errorResponse('Failed to connect to identity verification provider', 502);
  }
};
