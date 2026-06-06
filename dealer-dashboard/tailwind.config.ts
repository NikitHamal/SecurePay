import type { Config } from 'tailwindcss';

export default {
  content: ['./src/**/*.{html,js,svelte,ts}'],
  theme: {
    extend: {
      colors: {
        charcoal: {
          950: '#0E1114',
          900: '#12171B',
          850: '#181D21',
          800: '#22282E',
          700: '#2D353B'
        },
        secure: {
          emerald: '#00A86B',
          crimson: '#C81E3A',
          amber: '#FFB84D',
          cyan: '#54C6D8'
        }
      },
      borderRadius: {
        m3: '8px'
      },
      boxShadow: {
        m3: '0 8px 24px rgba(0, 0, 0, 0.24)'
      }
    }
  },
  plugins: []
} satisfies Config;

