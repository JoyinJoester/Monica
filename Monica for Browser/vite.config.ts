import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'path'
import fs from 'fs'
import { execSync } from 'child_process'

// Check if building for Firefox
const isFirefox = process.env.BROWSER === 'firefox' || process.env.FIREFOX === 'true'

// Copy appropriate manifest based on target browser
const copyManifest = () => {
  const manifestSource = isFirefox ? 'public/manifest.firefox.json' : 'public/manifest.json'
  const manifestDest = 'dist/manifest.json'
  
  console.log(`[Manifest] isFirefox: ${isFirefox}, copying ${manifestSource}`)
  
  // Ensure source exists
  if (fs.existsSync(manifestSource)) {
    const manifestContent = fs.readFileSync(manifestSource, 'utf-8')
    fs.mkdirSync('dist', { recursive: true })
    fs.writeFileSync(manifestDest, manifestContent)
    console.log(`[Manifest] Copied ${manifestSource} to dist/manifest.json`)
  } else {
    console.error(`[Manifest] Source file not found: ${manifestSource}`)
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    {
      name: 'clean-dist',
      enforce: 'pre',
      buildStart() {
        // Clean dist directory before build
        if (fs.existsSync('dist')) {
          execSync('rm -rf dist', { cwd: resolve(__dirname) })
          console.log('[Build] Cleaned dist directory')
        }
      }
    },
    {
      name: 'copy-manifest',
      writeBundle() {
        copyManifest()
      }
    }
  ],
  base: './', // Important for extension relative paths
  build: {
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
        background: resolve(__dirname, 'src/background.ts'),
        content: resolve(__dirname, 'src/content.ts'),
      },
      output: {
        entryFileNames: (chunkInfo) => {
          // Output background.js and content.js directly to dist root
          if (chunkInfo.name === 'background') {
            return 'background.js';
          }
          if (chunkInfo.name === 'content') {
            return 'content.js';
          }
          return 'assets/[name]-[hash].js';
        },
        // Firefox uses ES module format for better compatibility with multiple entry points
        // Chrome also works with ES modules
        format: 'es',
      },
    },
    // Don't minify for easier debugging
    minify: false,
  },
})


