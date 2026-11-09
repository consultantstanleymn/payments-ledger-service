import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  base: '/console/',
  plugins: [react()],
  server: {
    port: 26710,
    strictPort: true,
    proxy: {
      '/ops': 'http://127.0.0.1:18080',
      '/payments': 'http://127.0.0.1:18080',
      '/accounts': 'http://127.0.0.1:18080',
      '/reconciliations': 'http://127.0.0.1:18080',
      '/admin': 'http://127.0.0.1:18080'
    }
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    globals: true,
    exclude: ['node_modules/**', 'e2e/**', 'e2e-live/**']
  },
  build: {
    outDir: '../src/main/resources/static/console',
    emptyOutDir: true
  }
});

