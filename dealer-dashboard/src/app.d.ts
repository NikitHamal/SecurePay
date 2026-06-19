declare global {
  namespace App {
    interface Locals {
      dealer: { id: string; name: string } | null;
      hmacVerified: boolean;
      hmacScope?: 'global' | 'device';
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
      };
    }
  }
}

export {};
