import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Use './' by default for Tauri (.exe) & Capacitor (.apk) local assets,
// and '/' automatically on Vercel so refreshing /watch/:id or /shorts never fails asset lookup.
const resolvedBase =
  process.env.VERCEL || process.env.VITE_BASE_PATH === '/' ? '/' : './';

export default defineConfig({
  base: resolvedBase,
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    strictPort: true,
  },
  build: {
    target: 'es2020',
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/hls.js')) return 'hls';
          if (
            id.includes('node_modules/react') ||
            id.includes('node_modules/react-dom') ||
            id.includes('node_modules/react-router-dom') ||
            id.includes('node_modules/axios') ||
            id.includes('node_modules/lucide-react')
          ) {
            return 'vendor';
          }
        },
      },
    },
  },
});
