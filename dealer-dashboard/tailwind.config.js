/** @type {import('tailwindcss').Config} */
export default {
  content: ['./src/**/*.{html,js,svelte,ts}'],
  theme: {
    extend: {
      colors: {
        brand: {
          bg: '#121212',
          surface: '#1E1E1E',
          surfaceVariant: '#242424',
          border: '#2E2E2E',
          emerald: '#10B981',
          crimson: '#DC2626',
          amber: '#F59E0B',
          text: '#ECECEC',
          textMuted: '#A0A0A0'
        }
      },
      fontFamily: {
        sans: [
          'Inter',
          'Roboto',
          'ui-sans-serif',
          'system-ui',
          '-apple-system',
          'Segoe UI',
          'Helvetica Neue',
          'Arial',
          'sans-serif'
        ],
        mono: ['Roboto Mono', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace']
      }
    }
  },
  plugins: []
};
