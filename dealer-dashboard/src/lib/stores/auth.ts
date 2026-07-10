import { writable, derived, type Writable, type Readable } from 'svelte/store';
import { login as apiLogin, logout as apiLogout, getToken } from '$lib/api/client';

export interface Dealer {
  id: string;
  name: string;
  email: string;
  role: string;
  agencyId?: string | null;
  branchId?: string | null;
}

const initialDealer = typeof window !== 'undefined'
  ? (() => {
      // The JWT lives only in an HttpOnly cookie. This non-sensitive profile cache
      // is used to render navigation until the first authenticated API request.
      const stored = localStorage.getItem('securepay_dealer');
      if (stored) {
        try {
          return JSON.parse(stored);
        } catch {
          return null;
        }
      }
      return null;
    })()
  : null;

export const dealer: Writable<Dealer | null> = writable(initialDealer);
export const isAuthenticated: Readable<boolean> = derived(dealer, ($dealer) => $dealer !== null);
export const authError: Writable<string | null> = writable(null);

export async function login(email: string, password: string): Promise<boolean> {
  authError.set(null);
  try {
    const result = await apiLogin(email, password);
    dealer.set(result.dealer);
    return true;
  } catch (err) {
    authError.set(err instanceof Error ? err.message : 'Login failed');
    return false;
  }
}

export async function logout(): Promise<void> {
  await apiLogout();
  dealer.set(null);
}

export function initAuth(): void {
  // Synchronous initialization complete
}

dealer.subscribe((d) => {
  if (typeof window !== 'undefined') {
    if (d) {
      localStorage.setItem('securepay_dealer', JSON.stringify(d));
    } else {
      localStorage.removeItem('securepay_dealer');
    }
  }
});