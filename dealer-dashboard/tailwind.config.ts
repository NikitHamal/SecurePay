import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./src/**/*.{html,js,svelte,ts}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        surface: {
          DEFAULT: 'var(--bg-surface)',
          50: 'var(--bg-surface-50)',
          100: 'var(--bg-surface-100)',
          200: 'var(--bg-surface-200)',
          300: 'var(--bg-surface-300)',
          elevated: 'var(--bg-elevated)',
        },
        edge: {
          DEFAULT: 'var(--border-default)',
          strong: 'var(--border-strong)',
          subtle: 'var(--border-subtle)',
        },
        emerald: {
          DEFAULT: '#10B981',
          50: '#34D399',
          100: '#10B981',
          200: '#059669',
          300: '#047857',
          400: '#065F46',
        },
        crimson: {
          DEFAULT: '#EF4444',
          50: '#F87171',
          100: '#EF4444',
          200: '#DC2626',
          300: '#B91C1C',
        },
        amber: {
          DEFAULT: '#F59E0B',
          50: '#FBBF24',
          100: '#F59E0B',
          200: '#D97706',
        },
        sky: {
          DEFAULT: '#38BDF8',
        },
        violet: {
          DEFAULT: '#A78BFA',
        },
        ink: {
          primary: 'var(--text-primary)',
          secondary: 'var(--text-secondary)',
          muted: 'var(--text-muted)',
          dim: 'var(--text-dim)',
        },
        'text-primary': 'var(--text-primary)',
        'text-secondary': 'var(--text-secondary)',
      },
      fontFamily: {
        sans: [
          'Poppins',
          'ui-sans-serif',
          'system-ui',
          '-apple-system',
          'Segoe UI',
          'Roboto',
          'Helvetica Neue',
          'Arial',
          'sans-serif',
        ],
        display: [
          'Poppins',
          'ui-sans-serif',
          'system-ui',
          'sans-serif',
        ],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'Monaco', 'Consolas', 'monospace'],
      },
      fontSize: {
        '2xs': ['0.6875rem', { lineHeight: '1rem' }],
      },
      borderRadius: {
        xl: '0.875rem',
        '2xl': '1.125rem',
        '3xl': '1.5rem',
      },
      boxShadow: {
        card: 'var(--shadow-card)',
        'card-hover': 'var(--shadow-card-hover)',
      },
      keyframes: {
        'fade-in': {
          from: { opacity: '0', transform: 'translateY(4px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        'pulse-soft': {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.55' },
        },
        shimmer: {
          '0%': { backgroundPosition: '-200% 0' },
          '100%': { backgroundPosition: '200% 0' },
        },
        'spin-slow': {
          to: { transform: 'rotate(360deg)' },
        },
      },
      animation: {
        'fade-in': 'fade-in 0.25s ease-out both',
        'pulse-soft': 'pulse-soft 2.4s ease-in-out infinite',
        shimmer: 'shimmer 2.2s linear infinite',
        'spin-slow': 'spin-slow 12s linear infinite',
      },
    },
  },
  plugins: [],
};

export default config;