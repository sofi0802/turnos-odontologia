import type { Fecha } from '../fecha/zona.ts';
import type { Campo } from './campos.ts';
import { crearCalendario } from './calendario.ts';

/** Selector de fecha de la aplicación (C-39): el calendario de C-40 en un desplegable, en lugar del nativo del navegador. */
export interface OpcionesSelectorFecha {
  /** Primer día elegible; por defecto el `min` del campo, o cualquier día si no tiene. */
  readonly minimo?: Fecha | null;
  /** Agrega "Borrar" para dejar el campo sin fecha. */
  readonly opcional?: boolean;
}

const SIN_FECHA = 'Elegí un día';
const SVG = 'http://www.w3.org/2000/svg';

let contador = 0;

function formatear(fecha: Fecha): string {
  const [anio, mes, dia] = fecha.split('-');
  return `${dia}/${mes}/${anio}`;
}

function icono(): SVGElement {
  const svg = document.createElementNS(SVG, 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('width', '20');
  svg.setAttribute('height', '20');
  svg.setAttribute('fill', 'none');
  svg.setAttribute('stroke', 'currentColor');
  svg.setAttribute('stroke-width', '2');
  svg.setAttribute('stroke-linecap', 'round');
  svg.setAttribute('stroke-linejoin', 'round');
  svg.setAttribute('aria-hidden', 'true');
  svg.classList.add('selector-fecha-icono');
  const trazo = document.createElementNS(SVG, 'path');
  trazo.setAttribute('d', 'M8 3v4M16 3v4M4 9h16M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z');
  svg.append(trazo);
  return svg;
}

/**
 * Cambia el campo `<input type="date">` por un botón que abre el calendario. El campo sigue en la página
 * (oculto) con el mismo valor `AAAA-MM-DD`, así que quien lo usa lo lee y lo escucha como antes.
 */
export function usarSelectorFecha(campo: Campo<HTMLInputElement>, opciones: OpcionesSelectorFecha = {}): void {
  const entrada = campo.control;
  contador += 1;
  const idPanel = `selector-fecha-panel-${contador}`;
  const etiqueta = campo.contenedor.querySelector('label')?.textContent ?? 'Día';

  const minimo = (): Fecha | null =>
    opciones.minimo !== undefined ? opciones.minimo : entrada.min === '' ? null : entrada.min;

  entrada.classList.add('fecha-nativa');
  entrada.tabIndex = -1;

  const boton = document.createElement('button');
  boton.type = 'button';
  boton.className = 'selector-fecha-boton control';
  boton.setAttribute('aria-haspopup', 'dialog');
  boton.setAttribute('aria-expanded', 'false');
  boton.setAttribute('aria-controls', idPanel);
  const texto = document.createElement('span');
  texto.className = 'selector-fecha-texto';
  boton.append(texto, icono());

  const panel = document.createElement('div');
  panel.id = idPanel;
  panel.className = 'selector-fecha-panel';
  panel.hidden = true;
  panel.setAttribute('role', 'dialog');
  panel.setAttribute('aria-label', `Elegir ${etiqueta.toLowerCase()}`);

  const calendario = crearCalendario({
    leyenda: etiqueta,
    minimo: minimo(),
    sinAyuda: true,
    alElegir(fecha) {
      entrada.value = fecha;
      entrada.dispatchEvent(new Event('change', { bubbles: true }));
      sincronizar();
      cerrar(true);
    },
  });
  calendario.contenedor.classList.add('selector-fecha-calendario');
  const leyenda = calendario.contenedor.querySelector('legend');
  leyenda?.classList.add('solo-lectores');
  panel.append(calendario.contenedor);

  if (opciones.opcional === true) {
    const borrar = document.createElement('button');
    borrar.type = 'button';
    borrar.className = 'btn btn-fantasma selector-fecha-borrar';
    borrar.textContent = 'Borrar';
    borrar.addEventListener('click', () => {
      entrada.value = '';
      entrada.dispatchEvent(new Event('change', { bubbles: true }));
      sincronizar();
      cerrar(true);
    });
    panel.append(borrar);
  }

  function sincronizar(): void {
    texto.textContent = entrada.value === '' ? SIN_FECHA : formatear(entrada.value);
    texto.classList.toggle('vacio', entrada.value === '');
    boton.setAttribute('aria-label', `${etiqueta}: ${entrada.value === '' ? 'sin elegir' : formatear(entrada.value)}`);
    boton.disabled = entrada.disabled;
    boton.classList.toggle('con-error', entrada.getAttribute('aria-invalid') === 'true');
    if (entrada.getAttribute('aria-invalid') === 'true') {
      boton.setAttribute('aria-invalid', 'true');
    } else {
      boton.removeAttribute('aria-invalid');
    }
  }

  function abrir(): void {
    calendario.fijar(entrada.value === '' ? null : entrada.value);
    panel.hidden = false;
    boton.setAttribute('aria-expanded', 'true');
    document.addEventListener('pointerdown', alTocarAfuera, true);
  }

  function cerrar(devolverFoco: boolean): void {
    panel.hidden = true;
    boton.setAttribute('aria-expanded', 'false');
    document.removeEventListener('pointerdown', alTocarAfuera, true);
    if (devolverFoco) {
      boton.focus();
    }
  }

  function alTocarAfuera(evento: Event): void {
    const destino = evento.target;
    if (destino instanceof Node && (panel.contains(destino) || boton.contains(destino))) {
      return;
    }
    cerrar(false);
  }

  boton.addEventListener('click', () => {
    if (panel.hidden) {
      abrir();
    } else {
      cerrar(false);
    }
  });
  panel.addEventListener('keydown', (evento) => {
    if (evento.key === 'Escape' && !panel.hidden) {
      evento.preventDefault();
      evento.stopPropagation();
      cerrar(true);
    }
  });
  // La etiqueta enfoca el campo oculto: el foco pasa al botón.
  entrada.addEventListener('focus', () => boton.focus());
  // Otro código puede cambiar el valor (por ejemplo, volver al mínimo): se lee ya terminado el cambio.
  entrada.addEventListener('change', () => queueMicrotask(sincronizar));

  const marco = document.createElement('div');
  marco.className = 'selector-fecha';
  entrada.before(marco);
  marco.append(entrada, boton, panel);
  sincronizar();
  new MutationObserver(sincronizar).observe(entrada, {
    attributes: true,
    attributeFilter: ['disabled', 'aria-invalid'],
  });
}
