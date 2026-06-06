declare global {
  namespace App {
    interface Locals {
      dealer: { id: string; name: string } | null;
    }
    interface Platform {
      env: {
        DB: D1Database;
        JWT_SECRET: string;
      };
    }
  }
}

export {};