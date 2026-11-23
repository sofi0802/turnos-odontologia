/// <reference types="vitest/config" />
import { defineConfig } from 'vite';

export default defineConfig({
  // Los tests leen design-system/tokens.css con `?raw`; solo esa carpeta se suma a la raíz de frontend/.
  server: { fs: { allow: ['.', '../design-system'] } },
  test: {
    environment: 'jsdom',
    include: ['test/**/*.test.ts'],
    setupFiles: ['test/setup.ts'],
    // Los tests de diseño responsivo leen styles.css, sus módulos de src/styles/ y design-system/tokens.css con `?raw` (por defecto Vitest los deja vacíos).
    css: { include: [/src[\/]styles(\.css|[\/])/, /design-system[\/]tokens\.css/] },
  },
});
