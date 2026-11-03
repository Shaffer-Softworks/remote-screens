import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { mockPreview } from './dev/mockPreview'

// Ingress serves the editor under a path of its own (/api/hassio_ingress/<token>/), so every URL is relative.
export default defineConfig(({ mode }) => ({
  base: './',
  plugins: [vue(), ...(mode === 'mock' ? [mockPreview()] : [])],
  server: {
    proxy: mode === 'mock' ? undefined : {
      '/api': process.env.REMOTE_SCREENS_URL ?? 'http://localhost:18099',
      '/webrtc': {
        target: process.env.REMOTE_SCREENS_URL ?? 'http://localhost:18099',
        ws: true,
      },
    },
  },
  test: {
    include: ['src/**/*.test.ts'],
  },
}))
