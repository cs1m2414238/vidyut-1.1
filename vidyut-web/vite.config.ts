import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: process.env.VIDYUT_BACKEND_PROXY || 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
      '/x402': {
        target: process.env.VIDYUT_X402_GATEWAY_PROXY || 'http://127.0.0.1:8085',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/x402/, '/api/x402'),
      },
    },
  },
})
