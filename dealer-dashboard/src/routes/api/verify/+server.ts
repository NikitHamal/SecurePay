import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { errorResponse } from '$lib/api/server';

const WORKFLOW_ID = '12f9eba5-5d23-4990-8c61-927ec82f46d3';

export const POST: RequestHandler = async ({ request, platform }) => {
  const apiKey = platform?.env?.DIDIT_API_KEY;
  if (!apiKey) {
    return errorResponse('DIDIT_API_KEY not configured', 500);
  }

  const { vendorData } = await request.json().catch(() => ({ vendorData: undefined }));
  if (!vendorData) {
    return errorResponse('vendorData is required', 400);
  }

  const res = await fetch('https://verification.didit.me/v3/session/', {
    method: 'POST',
    headers: {
      'x-api-key': apiKey,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      workflow_id: WORKFLOW_ID,
      vendor_data: vendorData,
      callback: 'https://securepay-dashboard.pages.dev/api/webhooks/didit',
    }),
  });

  if (!res.ok) {
    const detail = await res.text();
    return errorResponse(`Didit session creation failed: ${detail}`, 502);
  }

  const session = await res.json();
  return json({ url: session.url, session_id: session.session_id, session_token: session.session_token });
};
