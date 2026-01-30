import { defineConfig } from 'vite'
import { resolve } from 'path'

export default defineConfig({
    base: '/Monica/',
    root: '.',

    build: {
        outDir: 'dist',
        rollupOptions: {
            input: {
                main: resolve(__dirname, 'index.html'),
                docs: resolve(__dirname, 'docs.html')
            }
        }
    },
    server: {
        open: true
    }
})
