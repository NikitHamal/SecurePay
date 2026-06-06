import { createClient } from '@libsql/client';
import { TURSO_CONNECTION_URL, TURSO_AUTH_TOKEN } from '$env/static/private';

export const db = createClient({
  url: TURSO_CONNECTION_URL,
  authToken: TURSO_AUTH_TOKEN
});