import type { DealerRole } from '$lib/auth';

declare global {
  namespace App {
    interface Locals {
      dealer: {
        id: string;
        name: string;
        role: DealerRole;
        agencyId?: string | null;
        branchId?: string | null;
      } | null;
      hmacVerified: boolean;
      hmacScope?: 'global' | 'device';
      deviceId?: string;
      deviceImei?: string;
    }
    interface Platform {
      env: {
        DB: D1Database;
        R2: R2Bucket;
        JWT_SECRET: string;
        HMAC_SECRET: string;
        FRP_ACCOUNT_IDS?: string;
        CUSTOMER_APP_MIN_SUPPORTED_VERSION_CODE?: string;
        ALLOW_DEMO_SEED?: string;
        FCM_SERVICE_ACCOUNT_EMAIL?: string;
        FCM_SERVICE_ACCOUNT_PRIVATE_KEY?: string;
        FCM_PROJECT_ID?: string;
        DIDIT_API_KEY?: string;
        DIDIT_WEBHOOK_SECRET?: string;
        DIDIT_WORKFLOW_ID?: string;
        DIDIT_CALLBACK_URL?: string;
        APP_BUILD_VERSION?: string;
      };
    }
  }
}

export {};
