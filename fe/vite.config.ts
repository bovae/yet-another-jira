/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Dev server proxies /api to the backend. Override with VITE_API_PROXY_TARGET
// (localhost:8080 locally, be:8080 inside docker compose).
const apiProxyTarget = process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [tailwindcss(), react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: true,
    // Reset any vi.stubGlobal (e.g. the `fetch` stubs in the API tests) between tests so a stub
    // can't leak into the next one.
    unstubGlobals: true,
    exclude: ['e2e/**', 'node_modules/**'],
    reporters: ['default', ['junit', { outputFile: './test-results/vitest-junit.xml' }]],
  },
})
