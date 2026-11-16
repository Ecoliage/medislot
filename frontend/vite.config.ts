import {defineConfig} from 'vite';
import vue from '@vitejs/plugin-vue';
const backend = process.env.API_TARGET || 'http://127.0.0.1:18080';
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {target: backend, changeOrigin: true},
      '/oauth2': {target: backend},
      '/login/oauth2': {target: backend},
    },
  },
});
