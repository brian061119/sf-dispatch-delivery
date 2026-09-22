import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
// Dev server proxies /api to the backend so the frontend never deals with CORS.
// Override the backend origin with VITE_API_PROXY_TARGET (backend alignment).
export default defineConfig({
    plugins: [react()],
    server: {
        port: 3000,
        proxy: {
            '/api': {
                target: process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
                changeOrigin: true,
            },
        },
    },
});
