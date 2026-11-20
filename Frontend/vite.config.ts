import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const PRODUCTION_API_URL = 'https://ilussionesboutique-production.up.railway.app'
const TEST_API_URL = 'https://ilussionesboutique-test.up.railway.app'

// Production builds (Vercel production / branch `main`) use the production API;
// every other branch, preview and local build uses the test API.
const isProductionBuild =
  process.env.VERCEL_ENV === 'production' || process.env.VERCEL_GIT_COMMIT_REF === 'main'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  define: {
    __DEFAULT_API_BASE_URL__: JSON.stringify(isProductionBuild ? PRODUCTION_API_URL : TEST_API_URL),
  },
  server: {
    host: true,
    port: 5173
  }
})
