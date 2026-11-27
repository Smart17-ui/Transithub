import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Where the dev server forwards API calls. Override when 8080 is taken,
// e.g. `$env:API_PROXY_TARGET='http://localhost:8081'; npm.cmd run dev`.
const apiTarget = process.env.API_PROXY_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': apiTarget,
      '/v3': apiTarget,
      '/swagger-ui': apiTarget,
    },
  },
})
