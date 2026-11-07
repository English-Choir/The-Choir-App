import {defineConfig} from 'vite';
export default defineConfig({build:{rollupOptions:{output:{manualChunks(id){
  if(id.includes('@firebase/firestore')||id.includes('/firebase/firestore/'))return 'firestore';
  if(id.includes('node_modules'))return 'firebase';
}}}}});
