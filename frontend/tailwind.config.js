/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx,js,jsx}'],
  theme: {
    extend: {
      colors: {
        yt: {
          bg: '#0F0F0F',
          surface: '#1F1F1F',
          chip: '#272727',
          hover: '#3F3F3F',
          text: '#F1F1F1',
          muted: '#AAAAAA',
          red: '#FF0000',
        },
      },
      fontFamily: {
        sans: ['Roboto', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'sans-serif'],
      },
    },
  },
  plugins: [],
};
