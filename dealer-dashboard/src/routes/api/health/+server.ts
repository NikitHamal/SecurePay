import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

export const GET: RequestHandler = async ({ platform }) => {
  return json({
    service: 'touch-base-dashboard',
    status: 'ok',
    build: platform?.env?.APP_BUILD_VERSION ?? 'development',
    deviceApiVersion: 2,
    requiredDeviceRoutes: [
      '/api/device/activate',
      '/api/device/provisioned',
      '/api/device/check',
      '/api/device/heartbeat',
      '/api/device/location',
      '/api/device/app-update'
    ],
    serverTime: Date.now()
  }, {
    headers: { 'Cache-Control': 'no-store' }
  });
};
