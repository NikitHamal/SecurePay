import { writable } from 'svelte/store';
import { browser } from '$app/environment';

export type Theme = 'light' | 'dark' | 'system';

function getInitialTheme(): Theme {
  if (!browser) return 'system';
  const stored = localStorage.getItem('sp-theme');
  if (stored === 'light' || stored === 'dark' || stored === 'system') return stored;
  return 'system';
}

function resolveTheme(theme: Theme): 'light' | 'dark' {
  if (theme !== 'system') return theme;
  if (!browser) return 'dark';
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export const themePreference = writable<Theme>(getInitialTheme());
export const resolvedTheme = writable<'light' | 'dark'>('dark');

if (browser) {
  themePreference.subscribe((pref) => {
    localStorage.setItem('sp-theme', pref);
    const resolved = resolveTheme(pref);
    resolvedTheme.set(resolved);
    document.documentElement.setAttribute('data-theme', resolved);
    document.documentElement.classList.toggle('dark', resolved === 'dark');
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', resolved === 'dark' ? '#0A0C10' : '#FFFFFF');
  });

  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    themePreference.update((pref) => {
      const resolved = resolveTheme(pref);
      resolvedTheme.set(resolved);
      document.documentElement.setAttribute('data-theme', resolved);
      document.documentElement.classList.toggle('dark', resolved === 'dark');
      return pref;
    });
  });

  const initial = getInitialTheme();
  const resolved = resolveTheme(initial);
  resolvedTheme.set(resolved);
  document.documentElement.setAttribute('data-theme', resolved);
  document.documentElement.classList.toggle('dark', resolved === 'dark');
}

export function setTheme(theme: Theme): void {
  themePreference.set(theme);
}

export function cycleTheme(): void {
  themePreference.update((current) => {
    if (current === 'dark') return 'light';
    if (current === 'light') return 'system';
    return 'dark';
  });
}