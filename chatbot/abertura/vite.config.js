import { defineConfig } from 'vite';
import { resolve } from 'node:path';
export default defineConfig({ base:'/abertura/', build:{ outDir:'../src/main/resources/static/abertura', emptyOutDir:true, rollupOptions:{ input:{main:resolve(import.meta.dirname,'index.html'), particles:resolve(import.meta.dirname,'src/login-particles.jsx')}, output:{entryFileNames:chunk=>chunk.name==='particles'?'assets/login-particles.js':'assets/[name]-[hash].js'} } } });
