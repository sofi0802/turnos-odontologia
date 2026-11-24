import { afterEach, describe, expect, it, vi } from 'vitest';
import { crearCampo, crearEntrada } from '../src/shared/ui/campos.ts';
import { abrirDialogo } from '../src/shared/ui/dialogo.ts';

// C-39, etapa B: todos los diálogos usan el mismo molde (D8).

const FUENTES = import.meta.glob<string>('../src/**/*.ts', { query: '?raw', import: 'default', eager: true });

afterEach(() => {
  document.body.replaceChildren();
});

function abrirDeEjemplo(extra: { peligro?: boolean; resumen?: string; alCerrar?: () => void } = {}): ReturnType<typeof abrirDialogo> {
  const paciente = crearCampo('Paciente', crearEntrada('text', 'paciente'));
  return abrirDialogo({
    titulo: 'Nuevo turno',
    contenido: [paciente.contenedor],
    textoDescartar: 'Cancelar',
    textoPrincipal: 'Crear turno',
    ...extra,
  });
}

describe('Diálogos uniformes', () => {
  it('Orden de los botones en los diálogos', () => {
    const dialogo = abrirDeEjemplo();

    const botones = [...dialogo.dialogo.querySelectorAll<HTMLButtonElement>('.dialogo-acciones button')];
    expect(botones.map((b) => b.textContent)).toEqual(['Cancelar', 'Crear turno']);
    expect(botones[0]?.classList.contains('btn')).toBe(true);
    expect(botones[1]?.classList.contains('btn-principal')).toBe(true);
    expect(botones[1]?.type).toBe('submit');
    expect(botones[0]?.type).toBe('button');

    // Todos los diálogos de la aplicación se arman con este molde: ningún otro archivo crea un <dialog>.
    const archivosConDialogo = Object.entries(FUENTES)
      .filter(([, texto]) => texto.includes("createElement('dialog')"))
      .map(([ruta]) => ruta);
    expect(archivosConDialogo).toEqual(['../src/shared/ui/dialogo.ts']);
  });

  it('Lugar del aviso en los diálogos', () => {
    const dialogo = abrirDeEjemplo({ resumen: 'Martes 1 de diciembre' });

    const hijos = [...dialogo.dialogo.children].map((h) => h.tagName);
    expect(hijos).toEqual(['H2', 'P', 'FORM']);
    const primero = dialogo.formulario.firstElementChild;
    expect(primero).toBe(dialogo.general);
    expect(dialogo.general.getAttribute('role')).toBe('alert');
    const campo = dialogo.formulario.querySelector('.campo');
    expect(dialogo.general.compareDocumentPosition(campo as Node) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('el botón principal de una acción destructiva es de peligro y el foco empieza en descartar', () => {
    const dialogo = abrirDialogo({
      titulo: '¿Cancelar el turno?',
      resumen: 'Martes 1 de diciembre a las 10:00.',
      textoDescartar: 'Volver',
      textoPrincipal: 'Sí, cancelar turno',
      peligro: true,
    });

    expect(dialogo.principal.classList.contains('btn-peligro')).toBe(true);
    expect(document.activeElement).toBe(dialogo.descartar);
  });

  it('el foco empieza en el primer campo y el diálogo se nombra con su título', () => {
    const dialogo = abrirDeEjemplo();

    expect(document.activeElement?.tagName).toBe('INPUT');
    const titulo = dialogo.dialogo.querySelector('h2');
    expect(dialogo.dialogo.getAttribute('aria-labelledby')).toBe(titulo?.id);
  });

  it('descartar cierra y quita el diálogo, y avisa con alCerrar', async () => {
    const alCerrar = vi.fn();
    const dialogo = abrirDeEjemplo({ alCerrar });

    dialogo.descartar.click();

    await vi.waitFor(() => expect(document.querySelector('dialog')).toBeNull());
    expect(alCerrar).toHaveBeenCalledTimes(1);
  });
});
