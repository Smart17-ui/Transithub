import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Where the dev server forwards API calls. Override when 8080 is taken,
// e.g. `$env:API_PROXY_TARGET='http://localhost:8081'; npm.cmd run dev`.
const apiTarget = process.env.API_PROXY_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Sign-in only accepts http://localhost:5173, so never drift to 5174:
    // fail loudly instead when a dev server is already running.
    strictPort: true,
    proxy: {
      '/api': apiTarget,
      '/v3': apiTarget,
      '/swagger-ui': apiTarget,
    },
  },
})
