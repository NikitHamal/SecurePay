import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./src/**/*.{html,js,svelte,ts}'],
  theme: {
    extend: {
      colors: {
        charcoal: '#121212',
        surface: '#1E1E1E',
        emerald: '#10B981',
        crimson: '#DC2626',
        amber: '#F59E0B',
        'text-primary': '#E5E7EB',
        'text-secondary': '#9CA3AF'
      },
      fontFamily: {
        sans: [
          'Inter',
          'ui-sans-serif',
          'system-ui',
          '-apple-system',
          'Segoe UI',
          'Roboto',
          'Helvetica Neue',
          'Arial',
          'sans-serif'
        ],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'Monaco', 'Consolas', 'monospace']
      },
      borderRadius: {
        xl: '0.875rem'
      }
    }
  },
  plugins: []
};

export default config;
