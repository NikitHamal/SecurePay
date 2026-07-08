import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

export const POST: RequestHandler = async ({ locals, params, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const accountId = params.id;
  const db = getDb({ platform });

  const apiKey = platform?.env?.DIDIT_API_KEY;
  const workflowId = platform?.env?.DIDIT_WORKFLOW_ID;

  if (!apiKey || !workflowId) {
    return errorResponse('Didit not configured. Set DIDIT_API_KEY and DIDIT_WORKFLOW_ID.', 503);
  }

  const account = await db.prepare('SELECT id, customer_name, national_id, phone_number FROM accounts WHERE id = ?')
    .bind(accountId).first();

  if (!account) return errorResponse('Account not found', 404);

  try {
    const response = await fetch('https://verification.didit.me/v3/session/', {
      method: 'POST',
      headers: {
        'x-api-key': apiKey,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        workflow_id: workflowId,
        vendor_data: accountId
      })
    });

    if (!response.ok) {
      const errorBody = await response.text();
      console.error('Didit session creation failed:', errorBody);
      return errorResponse('Failed to create verification session', 502);
    }

    const data = await response.json();

    await db.prepare(`
      UPDATE accounts SET didit_session_id = ?, ghana_card_status = 'In Progress'
      WHERE id = ?
    `).bind(data.session_id, accountId).run();

    return json({
      sessionId: data.session_id,
      sessionUrl: data.session_url,
      message: 'Verification session created. Redirect customer to session_url.'
    });
  } catch (err) {
    console.error('Didit API error:', err);
    return errorResponse('Failed to connect to Didit API', 502);
  }
};
