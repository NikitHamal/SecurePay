/**
 * SecurePay reconciliation cron Worker.
 *
 * Cloudflare Pages Functions don't support scheduled triggers, so this small,
 * separately-deployed Worker runs on a cron schedule and pokes the dashboard's
 * /api/cron/reconcile endpoint, which requeries Paystack for unapplied
 * transactions and credits accounts (auto-confirming down payments). Keeping
 * the sweep inside the dashboard keeps it on the same D1 database; this Worker
 * just wakes it up.
 *
 * Required environment (secrets / vars):
 *   DASHBOARD_URL  https://securepay-dashboard.pages.dev
 *   CRON_SECRET    shared secret, must match the dashboard's CRON_SECRET
 */
/// <reference types="@cloudflare/workers-types" />

export default {
  async scheduled(_controller: ScheduledController, env: Record<string, string | undefined>, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(runOnce(env));
  },

  async fetch(request: Request, env: Record<string, string | undefined>): Promise<Response> {
    if (request.method !== 'POST') return new Response('Method Not Allowed', { status: 405 });
    await runOnce(env);
    return new Response('ok', { status: 200 });
  }
};

async function runOnce(env: Record<string, string | undefined>): Promise<void> {
  const secret = env.CRON_SECRET ?? '';
  if (!secret) {
    console.error('CRON_SECRET is not configured');
    return;
  }

  try {
    const res = await fetch(`${(env.DASHBOARD_URL ?? 'https://securepay-dashboard.pages.dev').replace(/\/$/, '')}/api/cron/reconcile`, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'x-cron-secret': secret
      }
    });
    if (!res.ok) {
      console.error(`reconcile failed: HTTP ${res.status}`, await res.text());
      return;
    }
    console.log('reconcile ok', await res.text());
  } catch (error) {
    console.error('reconcile request error', error);
  }
}