import { ErrorNoEncontrado, ErrorPermiso } from '../shared/api/errores.ts';
import { formatearHora } from '../shared/fecha/zona.ts';
import { crearAviso, crearBoton, crearCampo, crearSelector, type Opcion } from '../shared/ui/campos.ts';
import { abrirDialogo } from '../shared/ui/dialogo.ts';
import { crearCargando, crearEstadoVacio } from '../shared/ui/estado-vacio.ts';
import { fechaNumerica, fechaYHora } from './formato.ts';
import { esSuperior } from './odontograma-modelo.ts';
import {
  SITIOS,
  type PedidoPeriodontograma,
  type PiezaPeriodontograma,
  type ResumenPeriodontograma,
  type VersionPeriodontograma,
} from './periodontograma-api.ts';
import {
  ARCADAS,
  armarPedido,
  borradorDe,
  esMultirradicular,
  formaDe,
  iguales,
  LAC,
  lineasDe,
  MM,
  NOMBRE_ESTADO_PERIO,
  nombreDeSitio,
  piezaAusente,
  piezaVacia,
  validar,
  type Errores,
  type EstadoDelMapa,
  type PiezaBorrador,
  type SitioBorrador,
} from './periodontograma-modelo.ts';

/**
 * Sección "Periodontograma" de la ficha (C-46, design D2 a D6): la versión más nueva con el mapa de dientes
 * dibujados, el selector de versiones anteriores en solo lectura y, para el odontólogo, la nueva versión copiada de
 * la última. Se carga aparte del resto de la ficha. Nada de esto se guarda en el navegador (D9 de C-44).
 */

export const MENSAJE_SIN_PERIODONTOGRAMA = 'Todavía no hay periodontogramas de este paciente.';
export const MENSAJE_FALLA_PERIODONTOGRAMA = 'No pudimos cargar el periodontograma. Revisá la conexión e intentá de nuevo.';
export const MENSAJE_PERIODONTOGRAMA_GUARDADO = 'Periodontograma guardado.';
export const MENSAJE_DESCARTAR_PERIODONTOGRAMA = 'Hay un periodontograma sin guardar. ¿Descartarlo?';
export const MENSAJE_ELEGIR_PIEZA = 'Elegí una pieza para cargarla.';
export const MENSAJE_PIEZA_FUERA = 'Esta pieza no está en la versión.';
export const AYUDA_RECESION =
  'Recesión: del límite amelocementario al margen de la encía. Cargala en negativo si el margen está por encima del límite, como en una encía sana (por ejemplo, −2).';

export interface OpcionesPeriodontograma {
  /** Solo el odontólogo carga versiones nuevas (RN-CL-01). */
  readonly editable: boolean;
  listar(): Promise<readonly ResumenPeriodontograma[]>;
  obtener(versionId: number): Promise<VersionPeriodontograma>;
  registrar(pedido: PedidoPeriodontograma): Promise<VersionPeriodontograma>;
  /** Mensaje para un error; vacío si la sesión venció y la página ya se ocupó. */
  mensajeDeError(causa: unknown): string;
  /** `false` cuando la página ya se dibujó de nuevo o se salió de ella: un pedido viejo no dibuja nada. */
  vigente(): boolean;
}

export interface SeccionPeriodontograma {
  readonly contenedor: HTMLElement;
  /** Hay una nueva versión con cambios respecto de la copia inicial. */
  sinGuardar(): boolean;
}

interface Edicion {
  readonly base: VersionPeriodontograma | null;
  readonly inicial: ReadonlyMap<number, PiezaBorrador>;
  readonly borrador: Map<number, PiezaBorrador>;
  errores: Errores | null;
}

type Tres = ReadonlyArray<readonly [number | null, number | null]>;

const NS = 'http://www.w3.org/2000/svg';
let contador = 0;

function svg<K extends keyof SVGElementTagNameMap>(nombre: K, atributos: Record<string, string | number>): SVGElementTagNameMap[K] {
  const elemento = document.createElementNS(NS, nombre);
  for (const [clave, valor] of Object.entries(atributos)) {
    elemento.setAttribute(clave, String(valor));
  }
  return elemento;
}

interface OpcionesDiente {
  readonly ancho: number;
  readonly alto: number;
  readonly grilla?: boolean;
}

/** Dibujo de un diente con la raíz hacia afuera de la boca; decorativo (`aria-hidden`), el dato está en el texto. */
function crearDiente(pieza: number, estado: EstadoDelMapa, tres: Tres | null, opciones: OpcionesDiente): SVGSVGElement {
  const dibujo = svg('svg', { class: 'perio-diente', viewBox: '0 0 40 100', width: opciones.ancho, height: opciones.alto, 'aria-hidden': 'true', focusable: 'false' });
  const grupo = svg('g', {});
  if (!esSuperior(pieza)) {
    grupo.setAttribute('transform', 'translate(0 100) scale(1 -1)');
  }
  if (opciones.grilla === true) {
    for (let mm = -5; mm <= 15; mm += 1) {
      const y = LAC - mm * MM;
      if (y > 0 && y < 100) {
        grupo.append(svg('line', { class: mm % 5 === 0 ? 'perio-grilla fuerte' : 'perio-grilla', x1: 0, y1: y, x2: 40, y2: y }));
      }
    }
  }
  const forma = formaDe(pieza);
  for (const raiz of forma.raices) {
    grupo.append(svg('path', { class: 'perio-raiz', d: raiz }));
  }
  grupo.append(svg('path', { class: 'perio-corona', d: forma.corona }), svg('line', { class: 'perio-lac', x1: 2, y1: LAC, x2: 38, y2: LAC }));
  const lineas = estado === 'AUSENTE' || tres === null ? null : lineasDe(pieza, tres);
  if (lineas !== null) {
    grupo.append(svg('polyline', { class: 'perio-linea-fondo', points: lineas.fondo }), svg('polyline', { class: 'perio-linea-margen', points: lineas.margen }));
  }
  if (estado === 'AUSENTE') {
    grupo.append(svg('path', { class: 'perio-cruz', d: 'M6 20 L34 88 M34 20 L6 88' }));
  }
  dibujo.append(grupo);
  return dibujo;
}

function tresDeVersion(pieza: PiezaPeriodontograma | undefined, desde: number): Tres | null {
  if (pieza === undefined || pieza.ausente || pieza.sitios.length < 6) {
    return null;
  }
  return pieza.sitios.slice(desde, desde + 3).map((s) => [s.profundidadSondaje, s.recesion] as const);
}

function tresDeBorrador(pieza: PiezaBorrador | undefined, desde: number): Tres | null {
  if (pieza === undefined || pieza.ausente) {
    return null;
  }
  return pieza.sitios.slice(desde, desde + 3).map((s) => [s.profundidad, s.recesion] as const);
}

function cantidad(n: number): string {
  return n === 1 ? '1 pieza cargada' : `${n} piezas cargadas`;
}

function enumerar(partes: readonly string[]): string {
  return partes.length <= 1 ? (partes[0] ?? '') : `${partes.slice(0, -1).join(', ')} y ${partes[partes.length - 1] ?? ''}`;
}

function fechaCorta(instante: string): string {
  return `${fechaNumerica(instante)} ${formatearHora(instante)}`;
}

function leerNumero(valor: string): number | null {
  return valor.trim() === '' ? null : Number(valor);
}

function numeroOVacio(valor: number | null): string {
  return valor === null || Number.isNaN(valor) ? '' : String(valor);
}

function opcionesDeGrado(vacio: string): Opcion[] {
  return [{ valor: '', texto: vacio }, ...[0, 1, 2, 3].map((g) => ({ valor: String(g), texto: String(g) }))];
}

export function crearPeriodontograma(opciones: OpcionesPeriodontograma): SeccionPeriodontograma {
  contador += 1;
  const id = `perio-${contador}`;
  const seccion = document.createElement('section');
  seccion.className = 'panel ficha-seccion perio-seccion';
  seccion.setAttribute('aria-labelledby', `${id}-titulo`);
  const cabecera = document.createElement('div');
  cabecera.className = 'ficha-seccion-cabecera';
  const titulo = document.createElement('h2');
  titulo.id = `${id}-titulo`;
  const acciones = document.createElement('div');
  acciones.className = 'perio-cabecera-acciones';
  cabecera.append(titulo, acciones);
  const exito = crearAviso('status');
  const error = crearAviso('alert');
  const cuerpo = document.createElement('div');
  cuerpo.className = 'perio-cuerpo';
  seccion.append(cabecera, exito, error, cuerpo);

  let versiones: ResumenPeriodontograma[] = [];
  const cargadas = new Map<number, VersionPeriodontograma>();
  let mostrada: VersionPeriodontograma | null = null;
  let elegida: number | null = null;
  let edicion: Edicion | null = null;
  let pedido = 0;

  function avisar(exitoso: string, fallido: string): void {
    exito.textContent = exitoso;
    error.textContent = fallido;
  }

  function botonDePieza(pieza: number): HTMLButtonElement | null {
    return seccion.querySelector<HTMLButtonElement>(`.perio-pieza[data-pieza="${pieza}"]`);
  }

  /** Muestra la falla en la sección, con "Reintentar"; el resto de la ficha sigue visible (design D2). */
  function mostrarFalla(causa: unknown, reintentar: () => void): void {
    const mensaje = opciones.mensajeDeError(causa);
    if (mensaje === '') {
      return;
    }
    avisar('', causa instanceof ErrorPermiso || causa instanceof ErrorNoEncontrado ? mensaje : MENSAJE_FALLA_PERIODONTOGRAMA);
    titulo.textContent = 'Periodontograma';
    acciones.replaceChildren();
    const boton = crearBoton('Reintentar');
    boton.className = 'btn btn-principal';
    boton.addEventListener('click', () => {
      avisar('', '');
      reintentar();
    });
    const caja = document.createElement('div');
    caja.append(boton);
    cuerpo.replaceChildren(caja);
  }

  async function cargar(): Promise<void> {
    pedido += 1;
    const este = pedido;
    titulo.textContent = 'Periodontograma';
    acciones.replaceChildren();
    cuerpo.replaceChildren(crearCargando('Cargando el periodontograma…'));
    try {
      const lista = await opciones.listar();
      const masNueva = lista[0];
      const version = masNueva === undefined ? null : await opciones.obtener(masNueva.id);
      if (este !== pedido || !opciones.vigente()) {
        return;
      }
      versiones = [...lista];
      if (version !== null) {
        cargadas.set(version.id, version);
      }
      mostrada = version;
      elegida = null;
      dibujar();
    } catch (causa) {
      if (este === pedido && opciones.vigente()) {
        mostrarFalla(causa, () => void cargar());
      }
    }
  }

  async function verVersion(versionId: number): Promise<void> {
    pedido += 1;
    const este = pedido;
    avisar('', '');
    const ya = cargadas.get(versionId);
    if (ya !== undefined) {
      mostrada = ya;
      dibujar();
      return;
    }
    cuerpo.replaceChildren(crearCargando('Cargando la versión…'));
    try {
      const version = await opciones.obtener(versionId);
      if (este !== pedido || !opciones.vigente()) {
        return;
      }
      cargadas.set(version.id, version);
      mostrada = version;
      dibujar();
    } catch (causa) {
      if (este === pedido && opciones.vigente()) {
        mostrarFalla(causa, () => void verVersion(versionId));
      }
    }
  }

  function masNueva(): VersionPeriodontograma | null {
    const primera = versiones[0];
    return primera === undefined ? null : (cargadas.get(primera.id) ?? null);
  }

  // ---- Estado de cada pieza en el mapa ----

  function estadoEnVersion(pieza: number): EstadoDelMapa {
    const encontrada = mostrada?.piezas.find((p) => p.pieza === pieza);
    if (encontrada === undefined) {
      return 'SIN_CARGAR';
    }
    return encontrada.ausente ? 'AUSENTE' : (encontrada.estado ?? 'CARGANDO');
  }

  /** En la nueva versión se ve el estado de la versión copiada hasta guardar; una pieza nueva está "en carga". */
  function estadoEnEdicion(actual: Edicion, pieza: number): EstadoDelMapa {
    const cargada = actual.borrador.get(pieza);
    if (cargada === undefined) {
      return 'SIN_CARGAR';
    }
    if (cargada.ausente) {
      return 'AUSENTE';
    }
    const copiada = actual.base?.piezas.find((p) => p.pieza === pieza);
    return copiada === undefined || copiada.ausente || copiada.estado === null ? 'CARGANDO' : copiada.estado;
  }

  function estadoDe(pieza: number): EstadoDelMapa {
    return edicion === null ? estadoEnVersion(pieza) : estadoEnEdicion(edicion, pieza);
  }

  function tresDe(pieza: number, desde: number): Tres | null {
    return edicion === null
      ? tresDeVersion(mostrada?.piezas.find((p) => p.pieza === pieza), desde)
      : tresDeBorrador(edicion.borrador.get(pieza), desde);
  }

  // ---- Mapa y leyenda (design D3) ----

  function crearBotonDePieza(pieza: number): HTMLButtonElement {
    const estado = estadoDe(pieza);
    const nombre = NOMBRE_ESTADO_PERIO[estado];
    const conError = edicion?.errores?.piezas.has(pieza) === true;
    const boton = crearBoton('');
    boton.className = 'perio-pieza';
    boton.dataset['pieza'] = String(pieza);
    boton.dataset['estado'] = estado;
    if (conError) {
      boton.dataset['error'] = '';
    }
    boton.setAttribute('aria-pressed', String(pieza === elegida));
    boton.setAttribute('aria-label', `Pieza ${pieza}: ${nombre}${conError ? ', con errores' : ''}`);
    const numero = document.createElement('span');
    numero.className = 'perio-num';
    numero.textContent = String(pieza);
    const rotulo = document.createElement('span');
    rotulo.className = 'perio-estado';
    rotulo.textContent = nombre;
    const dibujo = crearDiente(pieza, estado, tresDe(pieza, 0), { ancho: 34, alto: 85 });
    if (esSuperior(pieza)) {
      boton.append(dibujo, numero, rotulo);
    } else {
      boton.append(numero, rotulo, dibujo);
    }
    boton.addEventListener('click', () => {
      elegida = pieza;
      dibujar();
      botonDePieza(pieza)?.focus();
    });
    return boton;
  }

  function crearMapa(): HTMLElement {
    const mapa = document.createElement('div');
    mapa.className = 'perio-mapa';
    for (const arcada of ARCADAS) {
      const grupo = document.createElement('div');
      grupo.className = 'perio-arcada';
      grupo.setAttribute('role', 'group');
      grupo.setAttribute('aria-label', arcada.titulo);
      const rotulo = document.createElement('p');
      rotulo.className = 'perio-arcada-titulo';
      rotulo.setAttribute('aria-hidden', 'true');
      rotulo.textContent = `${arcada.titulo} · vista vestibular`;
      grupo.append(rotulo);
      for (const piezas of arcada.cuadrantes) {
        const fila = document.createElement('div');
        fila.className = 'perio-cuadrante';
        fila.append(...piezas.map(crearBotonDePieza));
        grupo.append(fila);
      }
      mapa.append(grupo);
    }
    return mapa;
  }

  function crearLeyenda(): HTMLElement {
    const lista = document.createElement('ul');
    lista.className = 'perio-leyenda';
    lista.setAttribute('aria-label', 'Estados del periodontograma');
    const estados: EstadoDelMapa[] = ['SANO', 'LEVE', 'MODERADO', 'SEVERO', 'AUSENTE', 'SIN_CARGAR'];
    for (const estado of estados) {
      const item = document.createElement('li');
      const muestra = document.createElement('span');
      muestra.className = 'perio-muestra';
      muestra.dataset['estado'] = estado;
      muestra.setAttribute('aria-hidden', 'true');
      muestra.append(crearDiente(11, estado, null, { ancho: 14, alto: 35 }));
      item.append(muestra, NOMBRE_ESTADO_PERIO[estado]);
      lista.append(item);
    }
    for (const [clase, nombre] of [
      ['margen', 'Margen de la encía'],
      ['fondo', 'Fondo de la bolsa'],
      ['lac', 'Límite amelocementario'],
    ] as const) {
      const item = document.createElement('li');
      const trazo = document.createElement('span');
      trazo.className = `perio-trazo ${clase}`;
      trazo.setAttribute('aria-hidden', 'true');
      item.append(trazo, nombre);
      lista.append(item);
    }
    return lista;
  }

  // ---- Detalle de la pieza (design D4) ----

  function crearVistas(pieza: number): HTMLElement {
    const vistas = document.createElement('div');
    vistas.className = 'perio-vistas';
    const estado = estadoDe(pieza);
    for (const [rotulo, desde] of [
      ['Vestibular', 0],
      [esSuperior(pieza) ? 'Palatino' : 'Lingual', 3],
    ] as const) {
      const figura = document.createElement('figure');
      figura.className = 'perio-vista';
      figura.dataset['estado'] = estado;
      const leyenda = document.createElement('figcaption');
      leyenda.textContent = rotulo;
      figura.append(leyenda, crearDiente(pieza, estado, tresDe(pieza, desde), { ancho: 96, alto: 240, grilla: true }));
      vistas.append(figura);
    }
    return vistas;
  }

  function crearCabeceraDetalle(pieza: number, extra: HTMLElement | null): HTMLElement {
    const cabeza = document.createElement('div');
    cabeza.className = 'perio-detalle-cabecera';
    const nombre = document.createElement('h3');
    nombre.id = `${id}-pieza`;
    nombre.textContent = `Pieza ${pieza}`;
    cabeza.append(nombre);
    if (extra !== null) {
      cabeza.append(extra);
    }
    return cabeza;
  }

  function nuevoDetalle(): HTMLElement {
    const detalle = document.createElement('div');
    detalle.className = 'perio-detalle';
    detalle.setAttribute('aria-labelledby', `${id}-pieza`);
    return detalle;
  }

  function parrafo(clase: string, textoDel: string): HTMLParagraphElement {
    const p = document.createElement('p');
    p.className = clase;
    p.textContent = textoDel;
    return p;
  }

  function crearDato(nombre: string, valor: string): HTMLElement {
    const fila = document.createElement('div');
    const dt = document.createElement('dt');
    dt.textContent = nombre;
    const dd = document.createElement('dd');
    dd.textContent = valor;
    fila.append(dt, ' ', dd);
    return fila;
  }

  function crearCabezaDeTabla(): HTMLTableSectionElement {
    const cabeza = document.createElement('thead');
    const fila = document.createElement('tr');
    for (const columna of ['Sitio', 'Profundidad (mm)', 'Recesión (mm)', 'Pérdida de inserción', 'Sangrado', 'Placa']) {
      const th = document.createElement('th');
      th.scope = 'col';
      th.textContent = columna;
      fila.append(th);
    }
    cabeza.append(fila);
    return cabeza;
  }

  function crearRotuloDeSitio(pieza: number, indice: number): HTMLTableCellElement {
    const codigo = SITIOS[indice] ?? 'MV';
    const th = document.createElement('th');
    th.scope = 'row';
    const cara = document.createElement('span');
    cara.className = 'perio-cara';
    cara.textContent = codigo;
    th.append(nombreDeSitio(pieza, codigo), cara);
    return th;
  }

  function celda(rotulo: string, contenido: string | Node): HTMLTableCellElement {
    const td = document.createElement('td');
    td.dataset['rotulo'] = rotulo;
    td.append(contenido);
    return td;
  }

  function crearDetalleConsulta(version: VersionPeriodontograma, pieza: number): HTMLElement {
    const detalle = nuevoDetalle();
    const datos = version.piezas.find((p) => p.pieza === pieza);
    if (datos === undefined) {
      detalle.append(crearCabeceraDetalle(pieza, null), parrafo('perio-vacio-pieza', MENSAJE_PIEZA_FUERA));
      return detalle;
    }
    const estado = estadoEnVersion(pieza);
    const chip = document.createElement('span');
    chip.className = 'perio-chip';
    chip.dataset['estado'] = estado;
    chip.textContent = NOMBRE_ESTADO_PERIO[estado];
    detalle.append(crearCabeceraDetalle(pieza, chip));
    if (datos.ausente) {
      detalle.append(parrafo('perio-vacio-pieza', 'Pieza ausente: no lleva mediciones.'));
      return detalle;
    }
    const lista = document.createElement('dl');
    lista.className = 'perio-datos-pieza';
    lista.append(crearDato('Movilidad', numeroOVacio(datos.movilidad)));
    if (datos.furcacion !== null) {
      lista.append(crearDato('Furcación', String(datos.furcacion)));
    }
    const perdidas = datos.sitios.map((s) => s.profundidadSondaje + s.recesion);
    const maxima = Math.max(...perdidas);
    const donde = datos.sitios.filter((_, i) => perdidas[i] === maxima).map((s) => nombreDeSitio(pieza, s.sitio).toLowerCase());
    if (datos.sitios.length > 0) {
      lista.append(crearDato('Pérdida de inserción máxima', `${maxima} mm (${enumerar(donde)})`));
    }
    const tabla = document.createElement('table');
    tabla.className = 'perio-tabla';
    const filas = document.createElement('tbody');
    datos.sitios.forEach((s, i) => {
      const fila = document.createElement('tr');
      fila.append(
        crearRotuloDeSitio(pieza, i),
        celda('Profundidad', `${s.profundidadSondaje} mm`),
        celda('Recesión', `${s.recesion} mm`),
        celda('Pérdida de inserción', `${s.profundidadSondaje + s.recesion} mm`),
        celda('Sangrado', s.sangrado ? 'Sí' : 'No'),
        celda('Placa', s.placa ? 'Sí' : 'No'),
      );
      filas.append(fila);
    });
    tabla.append(crearCabezaDeTabla(), filas);
    detalle.append(lista, crearVistas(pieza), tabla);
    return detalle;
  }

  // ---- Nueva versión (design D4 a D6) ----

  function cambiarPieza(actual: Edicion, pieza: number, cambio: (p: PiezaBorrador) => PiezaBorrador): void {
    const anterior = actual.borrador.get(pieza);
    if (anterior !== undefined) {
      actual.borrador.set(pieza, cambio(anterior));
    }
  }

  function cambiarSitio(actual: Edicion, pieza: number, indice: number, cambio: Partial<SitioBorrador>): void {
    cambiarPieza(actual, pieza, (p) => ({ ...p, sitios: p.sitios.map((s, i) => (i === indice ? { ...s, ...cambio } : s)) }));
  }

  /** Redibuja el diente del mapa y las vistas grandes sin tocar los campos (así no se pierde el foco). */
  function refrescarDibujos(pieza: number): void {
    botonDePieza(pieza)?.replaceWith(crearBotonDePieza(pieza));
    seccion.querySelector('.perio-vistas')?.replaceWith(crearVistas(pieza));
  }

  function crearCampoNumero(
    actual: Edicion,
    pieza: number,
    indice: number,
    campo: 'profundidad' | 'recesion',
    perdida: HTMLTableCellElement,
    ayuda: string,
  ): HTMLElement {
    const codigo = SITIOS[indice] ?? 'MV';
    const sitio = actual.borrador.get(pieza)?.sitios[indice];
    const entrada = document.createElement('input');
    entrada.type = 'number';
    entrada.inputMode = 'numeric';
    entrada.step = '1';
    entrada.min = campo === 'profundidad' ? '0' : '-5';
    entrada.max = '15';
    entrada.value = numeroOVacio(sitio?.[campo] ?? null);
    entrada.setAttribute(
      'aria-label',
      `Pieza ${pieza}, ${nombreDeSitio(pieza, codigo).toLowerCase()}: ${campo === 'profundidad' ? 'profundidad de sondaje' : 'recesión'} en mm`,
    );
    const describe = campo === 'recesion' ? [ayuda] : [];
    const caja = document.createElement('div');
    caja.append(entrada);
    const mensaje = actual.errores?.piezas.get(pieza)?.campos.get(`${codigo}-${campo}`);
    if (mensaje !== undefined) {
      const textoError = document.createElement('span');
      textoError.className = 'campo-error';
      textoError.id = `${id}-${pieza}-${codigo}-${campo}-error`;
      textoError.textContent = mensaje;
      describe.unshift(textoError.id);
      entrada.setAttribute('aria-invalid', 'true');
      caja.append(textoError);
    }
    if (describe.length > 0) {
      entrada.setAttribute('aria-describedby', describe.join(' '));
    }
    entrada.addEventListener('input', () => {
      cambiarSitio(actual, pieza, indice, { [campo]: leerNumero(entrada.value) });
      const medido = actual.borrador.get(pieza)?.sitios[indice];
      const suma = (medido?.profundidad ?? NaN) + (medido?.recesion ?? NaN);
      perdida.textContent = Number.isFinite(suma) ? `${suma} mm` : '—';
      refrescarDibujos(pieza);
    });
    return caja;
  }

  function crearCasilla(actual: Edicion, pieza: number, indice: number, campo: 'sangrado' | 'placa'): HTMLInputElement {
    const codigo = SITIOS[indice] ?? 'MV';
    const casilla = document.createElement('input');
    casilla.type = 'checkbox';
    casilla.checked = actual.borrador.get(pieza)?.sitios[indice]?.[campo] === true;
    casilla.setAttribute('aria-label', `Pieza ${pieza}, ${nombreDeSitio(pieza, codigo).toLowerCase()}: ${campo}`);
    casilla.addEventListener('change', () => cambiarSitio(actual, pieza, indice, { [campo]: casilla.checked }));
    return casilla;
  }

  function crearTablaEditable(actual: Edicion, pieza: number, ayuda: string): HTMLTableElement {
    const tabla = document.createElement('table');
    tabla.className = 'perio-tabla';
    const filas = document.createElement('tbody');
    const datos = actual.borrador.get(pieza);
    SITIOS.forEach((_, i) => {
      const sitio = datos?.sitios[i];
      const suma = (sitio?.profundidad ?? NaN) + (sitio?.recesion ?? NaN);
      const perdida = celda('Pérdida de inserción', Number.isFinite(suma) ? `${suma} mm` : '—');
      const fila = document.createElement('tr');
      fila.append(
        crearRotuloDeSitio(pieza, i),
        celda('Profundidad (mm)', crearCampoNumero(actual, pieza, i, 'profundidad', perdida, ayuda)),
        celda('Recesión (mm)', crearCampoNumero(actual, pieza, i, 'recesion', perdida, ayuda)),
        perdida,
        celda('Sangrado', crearCasilla(actual, pieza, i, 'sangrado')),
        celda('Placa', crearCasilla(actual, pieza, i, 'placa')),
      );
      filas.append(fila);
    });
    tabla.append(crearCabezaDeTabla(), filas);
    return tabla;
  }

  function crearGrado(actual: Edicion, pieza: number, campo: 'movilidad' | 'furcacion'): HTMLElement {
    const etiqueta = campo === 'movilidad' ? 'Movilidad' : 'Furcación';
    const selector = crearSelector(campo, opcionesDeGrado(campo === 'movilidad' ? 'Sin cargar' : 'Sin registrar'));
    selector.value = numeroOVacio(actual.borrador.get(pieza)?.[campo] ?? null);
    const control = crearCampo(etiqueta, selector);
    const mensaje = actual.errores?.piezas.get(pieza)?.campos.get(campo);
    if (mensaje !== undefined) {
      control.mostrarError(mensaje);
    }
    selector.addEventListener('change', () => cambiarPieza(actual, pieza, (p) => ({ ...p, [campo]: leerNumero(selector.value) })));
    return control.contenedor;
  }

  function crearDetalleEdicion(actual: Edicion, pieza: number): HTMLElement {
    const detalle = nuevoDetalle();
    const datos = actual.borrador.get(pieza);
    if (datos === undefined) {
      const cargarPieza = crearBoton('Cargar pieza');
      cargarPieza.className = 'btn btn-principal';
      cargarPieza.addEventListener('click', () => {
        actual.borrador.set(pieza, piezaVacia(pieza));
        dibujar();
        seccion.querySelector<HTMLInputElement>('.perio-tabla input')?.focus();
      });
      const cargarAusente = crearBoton('Cargar como ausente');
      cargarAusente.className = 'btn';
      cargarAusente.addEventListener('click', () => {
        actual.borrador.set(pieza, piezaAusente(pieza));
        dibujar();
        botonDePieza(pieza)?.focus();
      });
      const botones = document.createElement('div');
      botones.className = 'perio-acciones-version perio-acciones-inicio';
      botones.append(cargarPieza, cargarAusente);
      detalle.append(crearCabeceraDetalle(pieza, null), parrafo('perio-vacio-pieza', MENSAJE_PIEZA_FUERA), botones);
      return detalle;
    }
    const quitar = crearBoton('Quitar de esta versión');
    quitar.className = 'btn btn-fantasma btn-chico';
    quitar.addEventListener('click', () => {
      actual.borrador.delete(pieza);
      dibujar();
      botonDePieza(pieza)?.focus();
    });
    const controles = document.createElement('div');
    controles.className = 'perio-controles-pieza';
    const etiqueta = document.createElement('label');
    etiqueta.className = 'perio-casilla';
    const casilla = document.createElement('input');
    casilla.type = 'checkbox';
    casilla.checked = datos.ausente;
    casilla.addEventListener('change', () => {
      actual.borrador.set(pieza, casilla.checked ? piezaAusente(pieza) : piezaVacia(pieza));
      dibujar();
      seccion.querySelector<HTMLInputElement>('.perio-casilla input')?.focus();
    });
    etiqueta.append(casilla, ' Pieza ausente');
    controles.append(etiqueta);
    detalle.append(crearCabeceraDetalle(pieza, quitar), controles);
    if (datos.ausente) {
      return detalle;
    }
    controles.append(crearGrado(actual, pieza, 'movilidad'));
    if (esMultirradicular(pieza)) {
      controles.append(crearGrado(actual, pieza, 'furcacion'));
    }
    const ayuda = document.createElement('p');
    ayuda.className = 'perio-ayuda';
    ayuda.id = `${id}-ayuda-recesion`;
    const negrita = document.createElement('b');
    negrita.textContent = 'Recesión:';
    ayuda.append(negrita, AYUDA_RECESION.slice('Recesión:'.length));
    detalle.append(crearVistas(pieza), ayuda, crearTablaEditable(actual, pieza, ayuda.id));
    return detalle;
  }

  function empezarVersion(): void {
    const base = masNueva();
    const copia = borradorDe(base);
    edicion = { base, inicial: borradorDe(base), borrador: copia, errores: null };
    elegida = null;
    avisar('', '');
    dibujar();
  }

  function salirDeEdicion(): void {
    edicion = null;
    elegida = null;
    mostrada = masNueva();
    avisar('', '');
    dibujar();
  }

  function cancelar(): void {
    if (!sinGuardar()) {
      salirDeEdicion();
      return;
    }
    confirmarDescarte(salirDeEdicion);
  }

  async function guardar(actual: Edicion, boton: HTMLButtonElement): Promise<void> {
    const errores = validar(actual.borrador);
    if (errores.general !== null) {
      actual.errores = errores;
      avisar('', errores.general);
      const primera = [...errores.piezas.keys()][0];
      if (primera !== undefined) {
        elegida = primera;
      }
      dibujar();
      if (primera !== undefined) {
        botonDePieza(primera)?.focus();
      }
      return;
    }
    actual.errores = null;
    avisar('', '');
    boton.disabled = true;
    try {
      const version = await opciones.registrar(armarPedido(actual.borrador));
      if (!opciones.vigente() || edicion !== actual) {
        return;
      }
      cargadas.set(version.id, version);
      versiones = [
        { id: version.id, profesional: version.profesional, registradoEn: version.registradoEn },
        ...versiones.filter((v) => v.id !== version.id),
      ];
      edicion = null;
      mostrada = version;
      elegida = null;
      dibujar();
      avisar(MENSAJE_PERIODONTOGRAMA_GUARDADO, '');
    } catch (causa) {
      if (!opciones.vigente() || edicion !== actual) {
        return;
      }
      boton.disabled = false;
      const mensaje = opciones.mensajeDeError(causa);
      if (mensaje !== '') {
        avisar('', mensaje);
      }
    }
  }

  // ---- Dibujo de la sección ----

  function dibujarCabecera(): void {
    acciones.replaceChildren();
    if (edicion !== null) {
      titulo.textContent = 'Periodontograma · nueva versión';
      return;
    }
    titulo.textContent = 'Periodontograma';
    if (versiones.length > 0) {
      const selector = crearSelector(
        'version',
        versiones.map((v, i) => ({ valor: String(v.id), texto: `${fechaCorta(v.registradoEn)} · ${v.profesional}${i === 0 ? ' (la más nueva)' : ''}` })),
      );
      selector.value = String(mostrada?.id ?? '');
      selector.addEventListener('change', () => {
        elegida = null;
        void verVersion(Number(selector.value));
      });
      acciones.append(crearCampo('Versión', selector).contenedor);
    }
    if (opciones.editable) {
      const nueva = crearBoton('Nueva versión');
      nueva.className = 'btn btn-principal';
      nueva.addEventListener('click', empezarVersion);
      acciones.append(nueva);
    }
  }

  function dibujarConsulta(version: VersionPeriodontograma): void {
    const anterior = versiones[0]?.id !== version.id;
    const firma = parrafo(
      'perio-firma',
      `Versión del ${fechaYHora(version.registradoEn)} · ${version.profesional} · ${cantidad(version.piezas.length)}${anterior ? ' · es una versión anterior' : ''}`,
    );
    const partes: HTMLElement[] = [firma, crearMapa(), crearLeyenda()];
    if (elegida !== null) {
      partes.push(crearDetalleConsulta(version, elegida));
    }
    partes.push(parrafo('perio-nota', 'El estado es orientativo y no reemplaza el diagnóstico: lo calcula el sistema por la pérdida de inserción del peor sitio.'));
    cuerpo.replaceChildren(...partes);
  }

  function dibujarEdicion(actual: Edicion): void {
    const firma = parrafo(
      'perio-firma',
      actual.base === null
        ? 'Primera versión del periodontograma de este paciente.'
        : `Copiada de la versión del ${fechaYHora(actual.base.registradoEn)}. Se guarda como una versión nueva; las anteriores no cambian.`,
    );
    const resumen = parrafo('perio-resumen-carga', actual.borrador.size === 0 ? MENSAJE_ELEGIR_PIEZA : cantidad(actual.borrador.size));
    const partes: HTMLElement[] = [firma, resumen, crearMapa(), crearLeyenda()];
    if (elegida !== null) {
      partes.push(crearDetalleEdicion(actual, elegida));
    }
    const cancelarBoton = crearBoton('Cancelar');
    cancelarBoton.className = 'btn';
    cancelarBoton.addEventListener('click', cancelar);
    const guardarBoton = crearBoton('Guardar versión');
    guardarBoton.className = 'btn btn-principal';
    guardarBoton.addEventListener('click', () => void guardar(actual, guardarBoton));
    const botones = document.createElement('div');
    botones.className = 'perio-acciones-version';
    botones.append(cancelarBoton, guardarBoton);
    partes.push(botones);
    cuerpo.replaceChildren(...partes);
  }

  function dibujar(): void {
    dibujarCabecera();
    if (edicion !== null) {
      dibujarEdicion(edicion);
    } else if (mostrada !== null) {
      dibujarConsulta(mostrada);
    } else {
      cuerpo.replaceChildren(crearEstadoVacio({ texto: MENSAJE_SIN_PERIODONTOGRAMA, chico: true }));
    }
  }

  function confirmarDescarte(alDescartar: () => void): void {
    const dialogo = abrirDialogo({
      titulo: '¿Descartar el periodontograma?',
      resumen: MENSAJE_DESCARTAR_PERIODONTOGRAMA,
      textoDescartar: 'Seguir editando',
      textoPrincipal: 'Descartar',
      peligro: true,
    });
    dialogo.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      dialogo.cerrar();
      alDescartar();
    });
  }

  function sinGuardar(): boolean {
    return edicion !== null && !iguales(edicion.borrador, edicion.inicial);
  }

  void cargar();
  return { contenedor: seccion, sinGuardar };
}
