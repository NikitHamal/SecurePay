declare global {
  namespace App {
    interface Locals {
      dealer: { id: string; name: string } | null;
      hmacVerified: boolean;
    }
    interface Platform {
      env: {
        DB: D1Database;
        R2: R2Bucket;
        JWT_SECRET: string;
        HMAC_SECRET: string;
      };
    }
  }
}

export {};
