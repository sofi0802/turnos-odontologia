import { afterEach, describe, expect, it, vi } from 'vitest';
import { abrirApp, json, ServidorFalso } from './apoyo.ts';
import { estilos, textoDeModulo, textoDeTokens } from './estilos.ts';
import type { Router } from '../src/shared/ui/router.ts';

// C-39, etapa B: encabezado y pie con el color de la marca, y estados de los botones.
// jsdom no aplica :hover ni :focus-visible ni calcula colores: se comprueban las reglas de CSS y los tokens.

let router: Router | undefined;

afterEach(() => {
  router?.detener();
  router = undefined;
});

/** Bloque de una regla por su selector exacto (con su lista de selectores). */
function regla(css: string, selector: string): string {
  const escapado = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`(^|\\n\\s*|,\\s*)${escapado}\\s*(,[^{]*)?\\{([^}]*)\\}`).exec(css)?.[3] ?? '';
}

function valorDeToken(nombre: string): string {
  const coincide = new RegExp(`${nombre}:\\s*(#[0-9a-fA-F]{6})`).exec(textoDeTokens);
  if (coincide?.[1] === undefined) {
    throw new Error(`No se encontró el token ${nombre} con un color #rrggbb`);
  }
  return coincide[1];
}

function luminancia(hex: string): number {
  const canal = (desde: number): number => {
    const c = Number.parseInt(hex.slice(desde, desde + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * canal(1) + 0.7152 * canal(3) + 0.0722 * canal(5);
}

function contraste(a: string, b: string): number {
  const [claro, oscuro] = [luminancia(a), luminancia(b)].sort((x, y) => y - x);
  return ((claro ?? 0) + 0.05) / ((oscuro ?? 0) + 0.05);
}

function abrirEn(hash: string, servidor: ServidorFalso): HTMLElement {
  const app = abrirApp(servidor, hash);
  router = app.router;
  return app.raiz;
}

function sinSesion(): ServidorFalso {
  const servidor = new ServidorFalso();
  servidor.sinSesion();
  servidor.responder('GET /public/prestaciones', () => json(200, []));
  return servidor;
}

function conPaciente(): ServidorFalso {
  const servidor = sinSesion();
  servidor.responder('GET /public/sesion', () => json(200, { autenticado: true, username: 'paciente.uno@example.com', rol: 'PACIENTE' }));
  servidor.responder('GET /paciente/turnos', () => json(200, []));
  return servidor;
}

describe('Encabezado y pie con el color de la marca', () => {
  it('Contraste del encabezado y del pie', () => {
    const fondo = valorDeToken('--ds-acento-500');
    const texto = valorDeToken('--ds-acento-texto');
    expect(contraste(fondo, texto)).toBeGreaterThanOrEqual(4.5);

    const layout = textoDeModulo('13-layout.css');
    for (const selector of ['.cabecera', '.pie']) {
      expect(regla(layout, selector), selector).toMatch(/background:\s*var\(--ds-acento-500\)/);
      expect(regla(layout, selector), selector).toMatch(/color:\s*var\(--ds-acento-texto\)/);
    }
  });

  it('El pie en todas las páginas', async () => {
    const paginas: [string, () => ServidorFalso][] = [
      ['#/inicio', sinSesion],
      ['#/reserva', sinSesion],
      ['#/mi-cuenta', sinSesion],
      ['#/mis-turnos', conPaciente],
      ['#/agenda', () => new ServidorFalso()],
      ['#/configuracion', () => new ServidorFalso()],
    ];
    for (const [hash, servidor] of paginas) {
      router?.detener();
      const raiz = abrirEn(hash, servidor());
      await vi.waitFor(() => expect(raiz.querySelector('main h1')).not.toBeNull());
      const pie = raiz.querySelector('footer.pie');
      expect(pie?.textContent, hash).toBe('Turnos odontología');
      expect(pie?.parentElement?.lastElementChild, hash).toBe(pie);
    }
  });
});

describe('Estados de los botones', () => {
  const estados = textoDeModulo('14-estados.css');

  it('Hover y presionado del botón principal', () => {
    const normal = regla(textoDeModulo('06-componentes.css'), '.btn-principal');
    const hover = regla(estados, '.btn-principal:hover:not(:disabled)');
    const presionado = regla(estados, '.btn-principal:active:not(:disabled)');
    const fondo = (css: string): string => /background:\s*(var\([^)]+\))/.exec(css)?.[1] ?? '';
    expect(fondo(normal)).toBe('var(--ds-acento-500)');
    expect(fondo(hover)).toBe('var(--ds-acento-600)');
    expect(fondo(presionado)).toBe('var(--ds-acento-700)');
    for (const selector of ['.btn-fantasma:hover:not(:disabled)', '.btn-peligro:hover:not(:disabled)', '.chip:hover:not(:disabled)']) {
      expect(regla(estados, selector), selector).toMatch(/background:\s*var\(--ds-/);
    }
  });

  it('Foco visible con el teclado', () => {
    const lista = ['.btn:focus-visible', '.boton-principal:focus-visible', '.boton-secundario:focus-visible', '.chip:focus-visible'];
    for (const selector of lista) {
      expect(regla(estados, selector), selector).toMatch(/outline:\s*var\(--ds-foco-ancho\) solid var\(--ds-foco-color\)/);
    }
    // El botón de peligro se enfoca en rojo, como el botón.
    expect(regla(estados, '.btn-peligro:focus-visible')).toMatch(/outline-color:\s*var\(--ds-error\)/);
    // Ninguna regla quita el anillo sin reemplazarlo.
    expect(estilos).not.toMatch(/outline:\s*(none|0)\b/);
  });

  it('Botón deshabilitado', () => {
    const deshabilitado = regla(textoDeModulo('06-componentes.css'), '.btn:disabled');
    expect(deshabilitado).toMatch(/color:\s*var\(--ds-texto-deshabilitado\)/);
    expect(deshabilitado).toMatch(/cursor:\s*not-allowed/);
    // Ningún hover ni presionado vale para un botón deshabilitado.
    for (const [, selector] of estados.matchAll(/(\.[a-z-]+(?:\[[^\]]+\])?):(?:hover|active)(?![:\w-])/g)) {
      const sinFiltro = new RegExp(`${selector?.replace('.', '\\.')}:(hover|active)\\s*[,{]`);
      if (/^\.(btn|boton|chip)/.test(selector ?? '')) {
        expect(sinFiltro.test(estados), `${selector} debe excluir :disabled`).toBe(false);
      }
    }
  });
});

describe('Movimiento reducido', () => {
  it('Transiciones con movimiento reducido', () => {
    const transiciones = [...estilos.matchAll(/transition:\s*([^;]+);/g)].map((m) => m[1] ?? '');
    expect(transiciones.length).toBeGreaterThan(0);
    for (const valor of transiciones) {
      expect(valor, valor).toMatch(/var\(--ds-duracion(-rapida)?\)|^none/);
    }
    expect(textoDeTokens).toMatch(/@media \(prefers-reduced-motion: reduce\)[\s\S]*--ds-duracion:\s*0ms[\s\S]*transition:\s*none !important/);
  });
});
