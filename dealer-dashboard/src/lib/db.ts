import type { D1Database } from '@cloudflare/workers-types';

export function getDb(platform: App.Platform): D1Database {
  return platform.env.DB;
}