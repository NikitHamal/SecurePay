import type { Config } from 'tailwindcss';

export default {
  content: ['./src/**/*.{html,js,svelte,ts}'],
  theme: {
    extend: {
      colors: {
        charcoal: {
          950: '#101416',
          900: '#171D20',
          800: '#233035',
          700: '#304046'
        },
        emerald: {
          active: '#14B878',
          container: '#0F3D31'
        },
        crimson: {
          vivid: '#E21B3C',
          container: '#4C111D'
        },
        amber: {
          warning: '#FFB84D'
        }
      },
      boxShadow: {
        m3: '0 8px 24px rgba(0, 0, 0, 0.24)'
      }
    }
  },
  plugins: []
} satisfies Config;
