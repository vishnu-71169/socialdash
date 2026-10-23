import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        // The browser only talks to Vite, so proxied calls are same-origin. Dropping the Origin header keeps the
        // backend's CORS check from rejecting them when Vite runs on another port, 127.0.0.1 or a LAN address.
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'));
        },
      },
    },
  },
});
