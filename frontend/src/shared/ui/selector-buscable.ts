/**
 * Selector desplegable con buscador (combobox accesible, ARIA 1.2 con lista), para elegir una opción entre
 * varias que ya están en memoria (design D1 de C-38). Filtra en el navegador, sin pedidos.
 */

export interface OpcionBuscable {
  readonly id: string;
  readonly nombre: string;
  /** Texto secundario de la opción (por ejemplo, "30 minutos"). */
  readonly detalle: string;
}

export interface OpcionesSelectorBuscable {
  readonly etiqueta: string;
  /** Texto de ayuda del campo vacío. */
  readonly marcador: string;
  readonly opciones: readonly OpcionBuscable[];
  /** Opción ya elegida (al volver a un paso), o `null`. */
  readonly elegida: string | null;
  /** Mensaje cuando el texto no coincide con ninguna opción. */
  sinCoincidencias(texto: string): string;
  /** Se llama con el id de la opción elegida, o con `null` cuando el texto cambia y la elección se quita. */
  alCambiar(id: string | null): void;
}

export interface SelectorBuscable {
  readonly contenedor: HTMLElement;
  readonly entrada: HTMLInputElement;
}

const NS = 'http://www.w3.org/2000/svg';

let contador = 0;

/** Minúsculas y sin marcas de acento: "Extracción" coincide con "extraccion". */
export function normalizarTexto(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .trim();
}

function icono(clase: string, trazos: readonly string[]): SVGElement {
  const svg = document.createElementNS(NS, 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('fill', 'none');
  svg.setAttribute('stroke', 'currentColor');
  svg.setAttribute('stroke-width', '2');
  svg.setAttribute('stroke-linecap', 'round');
  svg.setAttribute('stroke-linejoin', 'round');
  svg.setAttribute('aria-hidden', 'true');
  svg.setAttribute('focusable', 'false');
  if (clase !== '') {
    svg.setAttribute('class', clase);
  }
  for (const d of trazos) {
    const trazo = document.createElementNS(NS, 'path');
    trazo.setAttribute('d', d);
    svg.append(trazo);
  }
  return svg;
}

function textoSoloLectores(texto: string): HTMLSpanElement {
  const span = document.createElement('span');
  span.className = 'solo-lectores';
  span.textContent = texto;
  return span;
}

export function crearSelectorBuscable(config: OpcionesSelectorBuscable): SelectorBuscable {
  contador += 1;
  const base = `selector-${contador}`;
  const porId = new Map(config.opciones.map((o) => [o.id, o]));

  let elegida = config.elegida !== null && porId.has(config.elegida) ? config.elegida : null;
  let abierta = false;
  let activo = -1;
  let visibles: readonly OpcionBuscable[] = config.opciones;
  let ignorarFoco = false;
  // Con el puntero apretado fuera del campo (por ejemplo, sobre "Continuar"), cerrar la lista en ese momento
  // mueve el botón antes del `click` y se pierde el toque: el cierre espera a que se suelte el puntero.
  let punteroAbajo = false;
  let cerrarAlSoltar = false;

  const etiqueta = document.createElement('label');
  etiqueta.htmlFor = `${base}-campo`;
  etiqueta.textContent = config.etiqueta;

  const entrada = document.createElement('input');
  entrada.id = `${base}-campo`;
  entrada.type = 'text';
  entrada.className = 'control';
  entrada.autocomplete = 'off';
  entrada.placeholder = config.marcador;
  entrada.setAttribute('role', 'combobox');
  entrada.setAttribute('aria-autocomplete', 'list');
  entrada.setAttribute('aria-controls', `${base}-lista`);
  entrada.setAttribute('aria-expanded', 'false');
  entrada.value = elegida === null ? '' : (porId.get(elegida)?.nombre ?? '');

  const flecha = document.createElement('button');
  flecha.type = 'button';
  flecha.className = 'selector-flecha';
  flecha.tabIndex = -1;
  flecha.setAttribute('aria-label', 'Mostrar las opciones');
  flecha.setAttribute('aria-controls', `${base}-lista`);
  flecha.setAttribute('aria-expanded', 'false');
  flecha.append(icono('', ['M6 9l6 6 6-6']));

  const campo = document.createElement('div');
  campo.className = 'selector-campo';
  campo.append(icono('lupa', ['M18 11a7 7 0 1 1-14 0 7 7 0 0 1 14 0z', 'M20 20l-3.5-3.5']), entrada, flecha);

  const zonaLista = document.createElement('div');
  const estado = document.createElement('p');
  estado.className = 'solo-lectores';
  estado.setAttribute('role', 'status');
  estado.setAttribute('aria-live', 'polite');

  const contenedor = document.createElement('div');
  contenedor.className = 'campo selector';
  contenedor.append(etiqueta, campo, zonaLista, estado);

  function filtrar(): readonly OpcionBuscable[] {
    // Con una opción elegida el campo muestra su nombre: se ofrecen todas para poder cambiarla.
    if (elegida !== null) {
      return config.opciones;
    }
    const buscado = normalizarTexto(entrada.value);
    return buscado === ''
      ? config.opciones
      : config.opciones.filter((o) => normalizarTexto(o.nombre).includes(buscado));
  }

  function dibujarOpcion(opcion: OpcionBuscable, indice: number): HTMLLIElement {
    const item = document.createElement('li');
    item.id = `${base}-op${indice}`;
    item.className = indice === activo ? 'selector-opcion activa' : 'selector-opcion';
    item.setAttribute('role', 'option');
    item.setAttribute('aria-selected', String(opcion.id === elegida));
    const nombre = document.createElement('span');
    nombre.className = 'nombre';
    nombre.textContent = opcion.nombre;
    const detalle = document.createElement('small');
    detalle.append(textoSoloLectores(' · '), opcion.detalle);
    item.append(nombre, detalle);
    item.addEventListener('click', () => elegir(opcion));
    return item;
  }

  function dibujar(): void {
    visibles = filtrar();
    entrada.setAttribute('aria-expanded', String(abierta));
    flecha.setAttribute('aria-expanded', String(abierta));
    if (activo >= visibles.length) {
      activo = visibles.length - 1;
    }
    if (!abierta) {
      zonaLista.replaceChildren();
      estado.textContent = '';
      entrada.removeAttribute('aria-activedescendant');
      return;
    }
    if (visibles.length === 0) {
      const vacio = document.createElement('p');
      vacio.className = 'selector-vacio';
      vacio.textContent = config.sinCoincidencias(entrada.value.trim());
      zonaLista.replaceChildren(vacio);
      estado.textContent = vacio.textContent;
      entrada.removeAttribute('aria-activedescendant');
      return;
    }
    const lista = document.createElement('ul');
    lista.id = `${base}-lista`;
    lista.className = 'selector-lista';
    lista.setAttribute('role', 'listbox');
    lista.setAttribute('aria-label', config.etiqueta);
    lista.append(...visibles.map(dibujarOpcion));
    // Mantener el foco en el campo al tocar una opción o la barra de desplazamiento.
    lista.addEventListener('mousedown', (evento) => evento.preventDefault());
    zonaLista.replaceChildren(lista);
    estado.textContent = visibles.length === 1 ? '1 resultado' : `${visibles.length} resultados`;
    if (activo >= 0) {
      entrada.setAttribute('aria-activedescendant', `${base}-op${activo}`);
      const activa = document.getElementById(`${base}-op${activo}`);
      if (activa !== null && typeof activa.scrollIntoView === 'function') {
        activa.scrollIntoView({ block: 'nearest' });
      }
    } else {
      entrada.removeAttribute('aria-activedescendant');
    }
  }

  const alApretar = (): void => {
    punteroAbajo = true;
  };
  const alSoltar = (): void => {
    punteroAbajo = false;
    if (cerrarAlSoltar) {
      cerrarAlSoltar = false;
      // Después del `click` del control que se tocó.
      setTimeout(cerrar, 0);
    }
  };

  function abrir(): void {
    if (!abierta) {
      abierta = true;
      activo = -1;
      document.addEventListener('pointerdown', alApretar, true);
      document.addEventListener('pointerup', alSoltar, true);
      document.addEventListener('pointercancel', alSoltar, true);
      dibujar();
    }
  }

  function cerrar(): void {
    document.removeEventListener('pointerdown', alApretar, true);
    document.removeEventListener('pointerup', alSoltar, true);
    document.removeEventListener('pointercancel', alSoltar, true);
    punteroAbajo = false;
    cerrarAlSoltar = false;
    if (abierta) {
      abierta = false;
      activo = -1;
      dibujar();
    }
  }

  function elegir(opcion: OpcionBuscable): void {
    elegida = opcion.id;
    entrada.value = opcion.nombre;
    cerrar();
    config.alCambiar(opcion.id);
  }

  function mover(paso: 1 | -1): void {
    if (!abierta) {
      abrir();
    }
    if (visibles.length === 0) {
      return;
    }
    if (activo === -1) {
      activo = paso === 1 ? 0 : visibles.length - 1;
    } else {
      activo = Math.min(Math.max(activo + paso, 0), visibles.length - 1);
    }
    dibujar();
  }

  entrada.addEventListener('focus', () => {
    if (!ignorarFoco) {
      abrir();
    }
  });
  entrada.addEventListener('click', abrir);
  entrada.addEventListener('input', () => {
    if (elegida !== null) {
      elegida = null;
      config.alCambiar(null);
    }
    abrir();
    activo = -1;
    dibujar();
  });
  entrada.addEventListener('keydown', (evento) => {
    if (evento.key === 'ArrowDown') {
      evento.preventDefault();
      mover(1);
    } else if (evento.key === 'ArrowUp') {
      evento.preventDefault();
      mover(-1);
    } else if (evento.key === 'Enter') {
      const opcion = abierta ? visibles[activo] : undefined;
      if (opcion !== undefined) {
        evento.preventDefault();
        elegir(opcion);
      }
    } else if (evento.key === 'Escape' && abierta) {
      evento.preventDefault();
      cerrar();
    }
  });
  contenedor.addEventListener('focusout', (evento) => {
    const hacia = evento.relatedTarget;
    if (!(hacia instanceof Node) || !contenedor.contains(hacia)) {
      if (punteroAbajo) {
        cerrarAlSoltar = true;
      } else {
        cerrar();
      }
    }
  });
  flecha.addEventListener('mousedown', (evento) => evento.preventDefault());
  flecha.addEventListener('click', () => {
    if (abierta) {
      cerrar();
    } else {
      abrir();
    }
    ignorarFoco = true;
    entrada.focus();
    ignorarFoco = false;
  });

  return { contenedor, entrada };
}
