import { crearAviso, crearBoton } from '../shared/ui/campos.ts';
import {
  ESTADOS_PIEZA,
  MAXIMO_CAMBIOS,
  type CambioOdontograma,
  type Cara,
  type EstadoPieza,
  type RegistroOdontograma,
} from './api.ts';
import {
  carasDe,
  estadoDeCara,
  esLadoDerecho,
  ESTADOS_DE_PIEZA,
  esSuperior,
  LETRA_ESTADO,
  marcaDePieza,
  MITADES,
  NOMBRE_ESTADO,
  nombreAccesible,
  nombreDeCara,
  ubicacion,
  vigentePorPieza,
  type Denticion,
  type Mitad,
} from './odontograma-modelo.ts';

/**
 * Odontograma de la ficha (C-44, design D4 y D5): dos SVG (lado derecho e izquierdo del paciente), que en pantallas
 * anchas quedan lado a lado; leyenda; lista en texto del estado vigente; y, para el odontólogo, el panel para sumar
 * cambios y guardarlos juntos.
 */

const NS = 'http://www.w3.org/2000/svg';
const T = 40;
const SEP = 6;
const A = 12;
const B = 28;
const ALTO = 140;

export const MENSAJE_LIMITE_CAMBIOS = 'Guardá los cambios antes de sumar más (máximo 32).';
export const MENSAJE_ODONTOGRAMA_VACIO = 'Sin registros en el odontograma.';
export const MENSAJE_ODONTOGRAMA_GUARDADO = 'Odontograma guardado.';

export interface OpcionesOdontograma {
  readonly registros: readonly RegistroOdontograma[];
  readonly editable: boolean;
  /** Envía los cambios; devuelve el estado vigente nuevo o lanza el error del servidor. */
  guardar?(cambios: readonly CambioOdontograma[]): Promise<readonly RegistroOdontograma[]>;
  /** Mensaje en español para un rechazo al guardar. */
  mensajeDeError?(causa: unknown): string;
  /** Avisa que se guardó (para recargar el historial). */
  alGuardar?(): void;
}

export interface Odontograma {
  readonly elemento: HTMLElement;
  /** Cantidad de cambios sin guardar. */
  pendientes(): number;
}

type Eleccion = { readonly pieza: number; readonly cara: Cara | null };

function svg<K extends keyof SVGElementTagNameMap>(nombre: K, atributos: Record<string, string | number>): SVGElementTagNameMap[K] {
  const elemento = document.createElementNS(NS, nombre);
  for (const [clave, valor] of Object.entries(atributos)) {
    elemento.setAttribute(clave, String(valor));
  }
  return elemento;
}

function puntos(lista: ReadonlyArray<readonly [number, number]>): string {
  return lista.map(([x, y]) => `${x},${y}`).join(' ');
}

/** Polígonos de las cinco caras de una pieza en (x, y): mesial del lado de la línea media. */
function geometria(pieza: number, x: number, y: number): ReadonlyArray<readonly [Cara, string]> {
  const superior = esSuperior(pieza);
  const derecha = esLadoDerecho(pieza);
  return [
    [superior ? 'V' : 'L', puntos([[x, y], [x + T, y], [x + B, y + A], [x + A, y + A]])],
    [superior ? 'P' : 'V', puntos([[x + A, y + B], [x + B, y + B], [x + T, y + T], [x, y + T]])],
    [derecha ? 'D' : 'M', puntos([[x, y], [x + A, y + A], [x + A, y + B], [x, y + T]])],
    [derecha ? 'M' : 'D', puntos([[x + T, y], [x + T, y + T], [x + B, y + B], [x + B, y + A]])],
    ['O', puntos([[x + A, y + A], [x + B, y + A], [x + B, y + B], [x + A, y + B]])],
  ];
}

function muestra(estado: EstadoPieza): HTMLSpanElement {
  const span = document.createElement('span');
  span.className = 'muestra';
  span.dataset['estado'] = estado;
  span.setAttribute('aria-hidden', 'true');
  span.textContent = LETRA_ESTADO[estado];
  return span;
}

function claveDe(cambio: Eleccion): string {
  return `${cambio.pieza}:${cambio.cara ?? 'completa'}`;
}

export function crearOdontograma(opciones: OpcionesOdontograma): Odontograma {
  let registros: readonly RegistroOdontograma[] = opciones.registros;
  let denticion: Denticion = registros.some((r) => r.pieza >= 51) && !registros.some((r) => r.pieza < 51) ? 'temporal' : 'permanente';
  let eleccion: Eleccion | null = null;
  let pendientes: CambioOdontograma[] = [];
  let foco: string | null = null;

  const seccion = document.createElement('section');
  seccion.className = 'panel ficha-seccion';
  seccion.setAttribute('aria-labelledby', 'titulo-odontograma');

  const cabecera = document.createElement('div');
  cabecera.className = 'ficha-seccion-cabecera';
  const titulo = document.createElement('h2');
  titulo.id = 'titulo-odontograma';
  titulo.textContent = 'Odontograma';
  const grupo = document.createElement('div');
  grupo.className = 'grupo-botones';
  grupo.setAttribute('role', 'group');
  grupo.setAttribute('aria-label', 'Dentición');
  const botonPermanentes = crearBoton('Permanentes');
  const botonTemporales = crearBoton('Temporales');
  grupo.append(botonPermanentes, botonTemporales);
  cabecera.append(titulo, grupo);

  const contenedor = document.createElement('div');
  contenedor.className = 'odontograma';
  if (!opciones.editable) {
    contenedor.dataset['soloLectura'] = '';
  }
  const dibujo = document.createElement('div');
  dibujo.className = 'odontograma-dibujo';
  const vacio = document.createElement('p');
  vacio.className = 'firma';
  vacio.textContent = MENSAJE_ODONTOGRAMA_VACIO;
  const leyenda = document.createElement('ul');
  leyenda.className = 'leyenda';
  leyenda.setAttribute('aria-label', 'Estados');
  for (const estado of ESTADOS_PIEZA) {
    const item = document.createElement('li');
    item.append(muestra(estado), NOMBRE_ESTADO[estado]);
    leyenda.append(item);
  }
  contenedor.append(dibujo, vacio, leyenda);

  const textoVigente = document.createElement('details');
  textoVigente.className = 'ficha-desplegable';
  const resumenVigente = document.createElement('summary');
  const listaVigente = document.createElement('ul');
  listaVigente.className = 'lista-ficha';
  textoVigente.append(resumenVigente, listaVigente);

  const editor = document.createElement('div');
  editor.className = 'editor-pieza';
  const exito = crearAviso('status');
  const error = crearAviso('alert');

  seccion.append(cabecera, contenedor);
  if (opciones.editable) {
    seccion.append(editor);
  }
  seccion.append(exito, error, textoVigente);

  /** Estado vigente con los cambios pendientes encima (como registros más nuevos que todos). */
  function vigenteConPendientes(): ReturnType<typeof vigentePorPieza> {
    const futuro = new Date(8.64e15).toISOString();
    const sinteticos: RegistroOdontograma[] = pendientes.map((c, i) => ({
      id: -1 - i,
      pieza: c.pieza,
      cara: c.cara,
      estado: c.estado,
      profesional: '',
      registradoEn: futuro,
    }));
    return vigentePorPieza([...registros, ...sinteticos]);
  }

  function esPendiente(pieza: number, cara: Cara): boolean {
    return pendientes.some((c) => c.pieza === pieza && (c.cara === cara || c.cara === null));
  }

  function dibujarMitad(mitad: Mitad, vigente: ReturnType<typeof vigentePorPieza>): HTMLElement {
    const figura = document.createElement('div');
    figura.className = 'odontograma-mitad';
    const rotulo = document.createElement('p');
    rotulo.className = 'odontograma-lado';
    rotulo.textContent = mitad.titulo;
    const ancho = mitad.arriba.length * (T + SEP) - SEP;
    const lienzo = svg('svg', { viewBox: `-4 0 ${ancho + 8} ${ALTO}`, role: 'group', 'aria-label': mitad.titulo });
    lienzo.style.maxWidth = `${Math.round((ancho + 8) * 1.4)}px`;
    const filas: ReadonlyArray<readonly [readonly number[], number, number]> = [
      [mitad.arriba, 20, 13],
      [mitad.abajo, 80, ALTO - 4],
    ];
    for (const [fila, y, yNumero] of filas) {
      fila.forEach((pieza, i) => {
        const x = i * (T + SEP);
        const datos = vigente.get(pieza);
        const g = svg('g', { role: 'group', 'aria-label': `Pieza ${pieza}` });
        const numero = svg('text', { class: 'numero', x: x + T / 2, y: yNumero });
        if (eleccion?.pieza === pieza) {
          numero.classList.add('elegida');
        }
        numero.textContent = String(pieza);
        g.append(numero);
        for (const [cara, pts] of geometria(pieza, x, y)) {
          const estado = estadoDeCara(datos, cara);
          const poligono = svg('polygon', { class: 'cara', points: pts, tabindex: -1 });
          poligono.setAttribute('role', opciones.editable ? 'button' : 'img');
          poligono.setAttribute('aria-label', nombreAccesible(pieza, cara, estado));
          poligono.dataset['pieza'] = String(pieza);
          poligono.dataset['cara'] = cara;
          if (estado !== null) {
            poligono.dataset['estado'] = estado;
          }
          if (esPendiente(pieza, cara)) {
            poligono.classList.add('pendiente');
          }
          if (eleccion !== null && eleccion.pieza === pieza && (eleccion.cara === cara || eleccion.cara === null)) {
            poligono.classList.add('elegida');
            if (opciones.editable) {
              poligono.setAttribute('aria-pressed', 'true');
            }
          }
          g.append(poligono);
          if (cara === 'O' && estado !== null && marcaDePieza(datos) === null) {
            const letra = svg('text', { class: 'letra', x: x + T / 2, y: y + T / 2 });
            letra.setAttribute('aria-hidden', 'true');
            letra.textContent = LETRA_ESTADO[estado];
            g.append(letra);
          }
        }
        const marca = marcaDePieza(datos);
        if (marca === 'AUSENTE') {
          g.append(svg('line', { class: 'marca-ausente', x1: x + 3, y1: y + 3, x2: x + T - 3, y2: y + T - 3, 'aria-hidden': 'true' }));
          g.append(svg('line', { class: 'marca-ausente', x1: x + T - 3, y1: y + 3, x2: x + 3, y2: y + T - 3, 'aria-hidden': 'true' }));
        } else if (marca === 'CORONA') {
          g.append(svg('rect', { class: 'marca-corona', x: x - 2, y: y - 2, width: T + 4, height: T + 4, rx: 6, 'aria-hidden': 'true' }));
        } else if (marca === 'ENDODONCIA') {
          g.append(svg('line', { class: 'marca-endodoncia', x1: x + T / 2, y1: y - 3, x2: x + T / 2, y2: y + T + 3, 'aria-hidden': 'true' }));
        }
        lienzo.append(g);
      });
    }
    figura.append(rotulo, lienzo);
    return figura;
  }

  function caras(): SVGPolygonElement[] {
    return [...dibujo.querySelectorAll<SVGPolygonElement>('polygon.cara')];
  }

  function dibujar(): void {
    const vigente = vigenteConPendientes();
    dibujo.replaceChildren(...MITADES[denticion].map((m) => dibujarMitad(m, vigente)));
    botonPermanentes.setAttribute('aria-pressed', String(denticion === 'permanente'));
    botonTemporales.setAttribute('aria-pressed', String(denticion === 'temporal'));
    // Foco itinerante: una sola cara en el orden de tabulación.
    const todas = caras();
    const enfocable = todas.find((c) => `${c.dataset['pieza']}:${c.dataset['cara']}` === foco) ?? todas[0];
    if (enfocable !== undefined) {
      enfocable.setAttribute('tabindex', '0');
    }
    vacio.hidden = registros.length > 0 || pendientes.length > 0;
    dibujarTexto(vigenteConPendientes());
    if (opciones.editable) {
      dibujarEditor();
    }
  }

  function dibujarTexto(vigente: ReturnType<typeof vigentePorPieza>): void {
    const filas: HTMLLIElement[] = [];
    for (const pieza of [...vigente.keys()].sort((a, b) => a - b)) {
      const datos = vigente.get(pieza);
      if (datos === undefined) {
        continue;
      }
      const marcas: Array<[string, EstadoPieza]> = [];
      if (datos.completa !== null) {
        marcas.push(['pieza completa', datos.completa.estado]);
      }
      for (const cara of carasDe(pieza)) {
        const propia = datos.caras.get(cara);
        if (propia !== undefined && (datos.completa === null || propia.en > datos.completa.en)) {
          marcas.push([nombreDeCara(cara), propia.estado]);
        }
      }
      for (const [donde, estado] of marcas) {
        const item = document.createElement('li');
        const izquierda = document.createElement('span');
        izquierda.textContent = `Pieza ${pieza} · ${donde}`;
        const derecha = document.createElement('span');
        derecha.textContent = NOMBRE_ESTADO[estado];
        item.append(izquierda, derecha);
        filas.push(item);
      }
    }
    const conRegistros = new Set(registros.map((r) => r.pieza)).size;
    resumenVigente.textContent = `Estado vigente en texto (${conRegistros} ${conRegistros === 1 ? 'pieza' : 'piezas'} con registros)`;
    listaVigente.replaceChildren(...filas);
  }

  let estadoElegido: EstadoPieza | null = null;

  function radio(nombre: string, texto: string, marcado: boolean, alElegir: () => void, inicio?: HTMLElement): HTMLLabelElement {
    const etiqueta = document.createElement('label');
    etiqueta.className = 'opcion';
    const entrada = document.createElement('input');
    entrada.type = 'radio';
    entrada.name = nombre;
    entrada.checked = marcado;
    entrada.addEventListener('change', alElegir);
    etiqueta.append(entrada);
    if (inicio !== undefined) {
      etiqueta.append(inicio);
    }
    etiqueta.append(texto);
    return etiqueta;
  }

  function dibujarEditor(): void {
    editor.replaceChildren();
    if (eleccion === null) {
      const ayuda = document.createElement('p');
      ayuda.className = 'firma';
      ayuda.textContent = 'Elegí una cara de una pieza para registrar un cambio.';
      editor.append(ayuda);
    } else {
      const actual = eleccion;
      const encabezado = document.createElement('h3');
      encabezado.textContent = `Pieza ${actual.pieza} · ${ubicacion(actual.pieza)}`;
      const fila = document.createElement('div');
      fila.className = 'editor-fila';

      const grupoCaras = document.createElement('fieldset');
      grupoCaras.className = 'opciones';
      const leyendaCaras = document.createElement('legend');
      leyendaCaras.textContent = 'Cara';
      const listaCaras = document.createElement('div');
      listaCaras.className = 'opciones-caras';
      for (const cara of [...carasDe(actual.pieza), null]) {
        const texto = cara === null ? 'Pieza completa' : capitalizar(nombreDeCara(cara));
        listaCaras.append(
          radio('cara', texto, actual.cara === cara, () => {
            eleccion = { pieza: actual.pieza, cara };
            if (cara !== null && estadoElegido !== null && ESTADOS_DE_PIEZA.has(estadoElegido)) {
              estadoElegido = null;
            }
            dibujar();
          }),
        );
      }
      grupoCaras.append(leyendaCaras, listaCaras);

      const grupoEstados = document.createElement('fieldset');
      grupoEstados.className = 'opciones';
      const leyendaEstados = document.createElement('legend');
      leyendaEstados.textContent = 'Estado';
      const listaEstados = document.createElement('div');
      listaEstados.className = 'opciones-estados';
      for (const estado of ESTADOS_PIEZA) {
        listaEstados.append(
          radio(
            'estado',
            NOMBRE_ESTADO[estado],
            estadoElegido === estado,
            () => {
              estadoElegido = estado;
              // Ausente, corona y endodoncia son de toda la pieza: la cara pasa sola a "Pieza completa".
              if (ESTADOS_DE_PIEZA.has(estado) && actual.cara !== null) {
                eleccion = { pieza: actual.pieza, cara: null };
                dibujar();
              }
            },
            muestra(estado),
          ),
        );
      }
      grupoEstados.append(leyendaEstados, listaEstados);
      fila.append(grupoCaras, grupoEstados);

      const acciones = document.createElement('div');
      acciones.className = 'acciones-odontograma';
      const sumar = crearBoton('Sumar cambio');
      sumar.className = 'btn';
      sumar.addEventListener('click', () => sumarCambio());
      acciones.append(sumar);
      editor.append(encabezado, fila, acciones);
    }

    if (pendientes.length > 0) {
      const tituloPendientes = document.createElement('h3');
      tituloPendientes.textContent = `Cambios sin guardar (${pendientes.length})`;
      const lista = document.createElement('ul');
      lista.className = 'pendientes';
      pendientes.forEach((cambio, i) => {
        const item = document.createElement('li');
        const descripcion = document.createElement('span');
        descripcion.append(muestra(cambio.estado), `Pieza ${cambio.pieza} · ${nombreDeCara(cambio.cara)} → ${NOMBRE_ESTADO[cambio.estado]}`);
        const quitar = crearBoton('Quitar');
        quitar.className = 'btn btn-fantasma btn-chico';
        quitar.setAttribute('aria-label', `Quitar el cambio de la pieza ${cambio.pieza}, ${nombreDeCara(cambio.cara)}`);
        quitar.addEventListener('click', () => {
          pendientes = pendientes.filter((_, j) => j !== i);
          dibujar();
        });
        item.append(descripcion, quitar);
        lista.append(item);
      });
      const acciones = document.createElement('div');
      acciones.className = 'acciones-odontograma';
      const descartar = crearBoton('Descartar');
      descartar.className = 'btn';
      descartar.addEventListener('click', () => {
        pendientes = [];
        dibujar();
      });
      const guardar = crearBoton(`Guardar cambios (${pendientes.length})`);
      guardar.className = 'btn btn-principal';
      guardar.addEventListener('click', () => void guardarCambios(guardar));
      acciones.append(descartar, guardar);
      editor.append(tituloPendientes, lista, acciones);
    }
  }

  function sumarCambio(): void {
    exito.textContent = '';
    if (eleccion === null) {
      return;
    }
    if (estadoElegido === null) {
      error.textContent = 'Elegí un estado.';
      return;
    }
    const cara = ESTADOS_DE_PIEZA.has(estadoElegido) ? null : eleccion.cara;
    const nuevo: CambioOdontograma = { pieza: eleccion.pieza, cara, estado: estadoElegido };
    const existente = pendientes.findIndex((c) => claveDe(c) === claveDe(nuevo));
    if (existente >= 0) {
      pendientes = pendientes.map((c, i) => (i === existente ? nuevo : c));
    } else if (pendientes.length >= MAXIMO_CAMBIOS) {
      error.textContent = MENSAJE_LIMITE_CAMBIOS;
      return;
    } else {
      pendientes = [...pendientes, nuevo];
    }
    error.textContent = '';
    dibujar();
  }

  async function guardarCambios(boton: HTMLButtonElement): Promise<void> {
    if (opciones.guardar === undefined || pendientes.length === 0) {
      return;
    }
    boton.disabled = true;
    exito.textContent = '';
    error.textContent = '';
    try {
      registros = await opciones.guardar(pendientes);
      pendientes = [];
      exito.textContent = MENSAJE_ODONTOGRAMA_GUARDADO;
      opciones.alGuardar?.();
    } catch (causa) {
      error.textContent = opciones.mensajeDeError?.(causa) ?? '';
    } finally {
      boton.disabled = false;
    }
    dibujar();
  }

  function elegir(poligono: SVGElement): void {
    const pieza = Number(poligono.dataset['pieza']);
    const cara = poligono.dataset['cara'] as Cara;
    foco = `${pieza}:${cara}`;
    if (!opciones.editable) {
      return;
    }
    eleccion = { pieza, cara: estadoElegido !== null && ESTADOS_DE_PIEZA.has(estadoElegido) ? null : cara };
    dibujar();
    caras()
      .find((c) => `${c.dataset['pieza']}:${c.dataset['cara']}` === foco)
      ?.focus();
  }

  dibujo.addEventListener('click', (evento) => {
    const objetivo = evento.target;
    if (objetivo instanceof SVGElement && objetivo.classList.contains('cara')) {
      elegir(objetivo);
    }
  });

  dibujo.addEventListener('keydown', (evento) => {
    const objetivo = evento.target;
    if (!(objetivo instanceof SVGElement) || !objetivo.classList.contains('cara')) {
      return;
    }
    const todas = caras();
    const indice = todas.findIndex((c) => c === objetivo);
    let siguiente: SVGPolygonElement | undefined;
    switch (evento.key) {
      case 'ArrowRight':
      case 'ArrowDown':
        siguiente = todas[(indice + 1) % todas.length];
        break;
      case 'ArrowLeft':
      case 'ArrowUp':
        siguiente = todas[(indice - 1 + todas.length) % todas.length];
        break;
      case 'Enter':
      case ' ':
        evento.preventDefault();
        elegir(objetivo);
        return;
      default:
        return;
    }
    evento.preventDefault();
    if (siguiente !== undefined) {
      objetivo.setAttribute('tabindex', '-1');
      siguiente.setAttribute('tabindex', '0');
      foco = `${siguiente.dataset['pieza']}:${siguiente.dataset['cara']}`;
      siguiente.focus();
    }
  });

  botonPermanentes.addEventListener('click', () => {
    denticion = 'permanente';
    dibujar();
  });
  botonTemporales.addEventListener('click', () => {
    denticion = 'temporal';
    dibujar();
  });

  dibujar();

  return {
    elemento: seccion,
    pendientes: () => pendientes.length,
  };
}

function capitalizar(texto: string): string {
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}
