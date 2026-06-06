declare global {
  namespace App {
    interface Locals {
      dealer: { id: string; name: string } | null;
    }
  }
}

export {};