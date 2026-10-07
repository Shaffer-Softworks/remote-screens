import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// Ingress serves the editor under a path of its own (/api/hassio_ingress/<token>/), so every URL is relative.
export default defineConfig({
  base: './',
  plugins: [vue()],
  server: {
    proxy: { '/api': process.env.REMOTE_SCREENS_URL ?? 'http://localhost:18099' },
  },
  test: {
    include: ['src/**/*.test.ts'],
  },
})
