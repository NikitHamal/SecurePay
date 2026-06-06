import type { Handle } from '@sveltejs/kit';
import { verifyToken } from '$lib/auth';

export const handle: Handle = async ({ event, resolve }) => {
  const authHeader = event.request.headers.get('authorization');
  const token = authHeader?.replace('Bearer ', '');

  if (token && event.platform?.env?.JWT_SECRET) {
    const dealer = verifyToken(token, event.platform.env.JWT_SECRET);
    if (dealer) {
      event.locals.dealer = { id: dealer.sub, name: dealer.name };
    }
  }

  return resolve(event);
};