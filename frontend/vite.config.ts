import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// En Docker Compose el backend es http://backend:8080; fuera de Docker, localhost.
// En producción este papel lo hace nginx (nginx.conf).
const apiTarget = process.env.API_PROXY_TARGET ?? 'http://localhost:8080'
// Con el código montado en Docker sobre Windows/macOS no llegan los eventos de cambio de ficheros: hay
// que sondear para que la recarga en caliente funcione (docker-compose.override.yml lo activa).
const polling = process.env.WATCH_POLLING === 'true'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    strictPort: true,
    watch: polling ? { usePolling: true, interval: 300 } : undefined,
    proxy: {
      '/api': apiTarget,
      '/actuator/health': apiTarget,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
