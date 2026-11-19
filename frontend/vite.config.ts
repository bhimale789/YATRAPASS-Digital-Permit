import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the Vite server proxies /api to the Spring Boot backend so the
// browser talks to a single origin (same as production behind nginx).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_DEV_API_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    sourcemap: false,
  },
});
