/// <reference types="vitest/config" />
import { defineConfig } from 'vite';

export default defineConfig({
  test: {
    environment: 'jsdom',
    include: ['test/**/*.test.ts'],
    setupFiles: ['test/setup.ts'],
    // Los tests de diseño responsivo leen styles.css con `?raw` (por defecto Vitest lo deja vacío).
    css: { include: [/styles\.css/] },
  },
});
