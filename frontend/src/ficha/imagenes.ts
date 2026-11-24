import { ErrorNegocio, ErrorNoEncontrado, ErrorPermiso, ErrorValidacion } from '../shared/api/errores.ts';
import { hoy } from '../shared/fecha/zona.ts';
import { crearAviso, crearBoton, crearCampo, crearEntrada, crearSelector } from '../shared/ui/campos.ts';
import { abrirDialogo, type DialogoAbierto } from '../shared/ui/dialogo.ts';
import { crearCargando, crearEstadoVacio } from '../shared/ui/estado-vacio.ts';
import { crearIcono, type NombreIcono } from '../shared/ui/iconos.ts';
import { fechaYHora } from './formato.ts';
import type { CargaImagen, ImagenClinica } from './imagenes-api.ts';
import {
  CATEGORIAS,
  LIMITE_DESCRIPCION,
  LIMITE_MOTIVO,
  validarMotivo,
  MENSAJE_ARCHIVO_GRANDE,
  tipoDeArchivo,
  validarArchivo,
  validarDescripcion,
  validarFechaDeToma,
  validarPieza,
  type Categoria,
  etiquetaDeFormato,
  fechaDeToma,
  formatearPeso,
  nombreAccesible,
  nombreDeArchivo,
  nombreDeBoton,
  nombreDeCategoria,
  puedeVerse,
} from './imagenes-modelo.ts';

/**
 * Sección "Imágenes y estudios" de la ficha (C-47, design D2 a D7): lista las imágenes del paciente sin miniaturas,
 * con filtro por pieza y "Mostrar anuladas". Pide sus datos aparte del resto de la ficha, con su propio error y
 * "Reintentar" (D4). Ningún dato ni archivo queda en la dirección, el almacenamiento del navegador ni la consola.
 */

export const MENSAJE_SIN_IMAGENES = 'Todavía no hay imágenes ni estudios cargados.';
export const MENSAJE_SOLO_ANULADAS = 'No hay imágenes vigentes. Para ver las anuladas, marcá "Mostrar anuladas".';
export const MENSAJE_REVISAR_DATOS = 'Revisá los datos marcados.';
export const MENSAJE_DESCARTAR_IMAGEN = 'Hay una imagen sin cargar. ¿Descartarla?';
export const MENSAJE_IMAGEN_ANULADA = 'Imagen anulada.';
export const MENSAJE_IMAGEN_CARGADA = 'Imagen cargada.';
export const MENSAJE_FALLA_ARCHIVO = 'No pudimos abrir el archivo. Intentá de nuevo más tarde.';
export const MENSAJE_FALLA_DESCARGA = 'No pudimos descargar el archivo. Intentá de nuevo más tarde.';
export const MENSAJE_FALLA_IMAGENES = 'No pudimos cargar las imágenes. Revisá la conexión e intentá de nuevo.';

export interface OpcionesImagenes {
  /** Solo el odontólogo carga y anula (RN-CL-01); el administrador consulta. */
  readonly editable: boolean;
  /** Nombre del paciente, para el nombre accesible de la lista. */
  readonly nombrePaciente: string;
  listar(filtro: { readonly pieza?: number; readonly incluirAnuladas: boolean }): Promise<readonly ImagenClinica[]>;
  /** Carga una imagen; con `201` ya se cargó, aunque la respuesta no se pueda leer. */
  cargar(carga: CargaImagen): Promise<unknown>;
  /** Anula una imagen con su motivo. */
  anular(imagenId: number, motivo: string): Promise<unknown>;
  /** El archivo de una imagen; con `descarga` la API lo entrega como adjunto. */
  obtenerArchivo(imagenId: number, descarga: boolean): Promise<Blob>;
  /** Mensaje para un error; vacío si la sesión venció y la página ya se ocupó. */
  mensajeDeError(causa: unknown): string;
  /** `false` cuando la página ya se dibujó de nuevo o se salió de ella: un pedido viejo no dibuja nada. */
  vigente(): boolean;
}

export interface SeccionImagenes {
  readonly contenedor: HTMLElement;
  /** Hay un archivo elegido o texto escrito en el diálogo de carga. */
  sinGuardar(): boolean;
  /** Cierra los diálogos abiertos y libera los archivos que sigan en memoria. */
  destruir(): void;
}

let contador = 0;

/** Cuánto espera una descarga ya iniciada antes de liberar su dirección temporal. */
const TIEMPO_ANTES_DE_LIBERAR = 100;

function esMasculino(imagen: ImagenClinica): boolean {
  return imagen.categoria === 'ESTUDIO';
}

/** "la radiografía", "la fotografía", "el estudio": para el título del diálogo de anulación. */
function conArticulo(imagen: ImagenClinica): string {
  return `${esMasculino(imagen) ? 'el' : 'la'} ${nombreDeCategoria(imagen.categoria).toLowerCase()}`;
}

interface AreaConContador {
  readonly contenedor: HTMLElement;
  readonly area: HTMLTextAreaElement;
  mostrarError(mensaje: string): void;
}

/** Campo de texto largo con su contador "n/200", como el de la ficha básica (C-44). */
function crearAreaConContador(idBase: string, etiqueta: string, ejemplo: string, maximo: number): AreaConContador {
  const area = document.createElement('textarea');
  area.id = idBase;
  area.rows = 3;
  area.placeholder = ejemplo;
  const label = document.createElement('label');
  label.htmlFor = idBase;
  label.textContent = etiqueta;
  const cuenta = document.createElement('span');
  cuenta.className = 'contador';
  cuenta.id = `${idBase}-contador`;
  const error = document.createElement('p');
  error.className = 'campo-error';
  error.id = `${idBase}-error`;
  area.setAttribute('aria-describedby', `${error.id} ${cuenta.id}`);
  const actualizar = (): void => {
    cuenta.textContent = `${area.value.length}/${maximo}`;
  };
  actualizar();
  area.addEventListener('input', actualizar);
  const cabecera = document.createElement('div');
  cabecera.className = 'campo-suave-cabecera';
  cabecera.append(label, cuenta);
  const contenedor = document.createElement('div');
  contenedor.className = 'campo-suave';
  contenedor.append(cabecera, area, error);
  return {
    contenedor,
    area,
    mostrarError(mensaje) {
      error.textContent = mensaje;
      if (mensaje === '') {
        area.removeAttribute('aria-invalid');
      } else {
        area.setAttribute('aria-invalid', 'true');
      }
    },
  };
}

function crearEtiqueta(texto: string, clase = ''): HTMLSpanElement {
  const etiqueta = document.createElement('span');
  etiqueta.className = clase === '' ? 'etiqueta' : `etiqueta ${clase}`;
  etiqueta.textContent = texto;
  return etiqueta;
}

function conIcono(boton: HTMLButtonElement, icono: NombreIcono): void {
  boton.prepend(crearIcono(icono));
}

function resumenDeVigentes(lista: readonly ImagenClinica[]): string {
  const anuladas = lista.filter((i) => i.anulacion !== null).length;
  const vigentes = lista.length - anuladas;
  const partes = [vigentes === 1 ? '1 vigente' : `${vigentes} vigentes`];
  if (anuladas > 0) {
    partes.push(anuladas === 1 ? '1 anulada' : `${anuladas} anuladas`);
  }
  return partes.join(' · ');
}

export function crearImagenes(opciones: OpcionesImagenes): SeccionImagenes {
  contador += 1;
  const id = `imagenes-${contador}`;
  const seccion = document.createElement('section');
  seccion.className = 'panel ficha-seccion img-seccion';
  seccion.setAttribute('aria-labelledby', `${id}-titulo`);
  const cabecera = document.createElement('div');
  cabecera.className = 'ficha-seccion-cabecera';
  const encabezado = document.createElement('div');
  const titulo = document.createElement('h2');
  titulo.id = `${id}-titulo`;
  titulo.textContent = 'Imágenes y estudios';
  const resumen = document.createElement('p');
  resumen.className = 'img-contador';
  resumen.hidden = true;
  encabezado.append(titulo, resumen);
  const acciones = document.createElement('div');
  acciones.className = 'img-cabecera-acciones';
  cabecera.append(encabezado, acciones);
  if (opciones.editable) {
    const cargarImagen = crearBoton('Cargar imagen');
    cargarImagen.className = 'btn btn-principal';
    conIcono(cargarImagen, 'subir');
    cargarImagen.addEventListener('click', () => abrirCarga(cargarImagen));
    acciones.append(cargarImagen);
  }
  const exito = crearAviso('status');
  const error = crearAviso('alert');
  const filtros = document.createElement('div');
  filtros.className = 'img-filtros';
  filtros.setAttribute('role', 'group');
  filtros.setAttribute('aria-label', 'Filtros de las imágenes');
  const resultados = document.createElement('div');
  resultados.className = 'img-resultados';
  seccion.append(cabecera, exito, error, resultados);

  let pedido = 0;
  let pieza: number | undefined;
  let incluirAnuladas = false;
  let piezasConocidas: number[] = [];
  let conImagenes = false;
  /** El paciente no tiene imágenes vigentes pero sí anuladas: los filtros siguen a mano para poder verlas. */
  let soloAnuladas = false;

  const selector = document.createElement('select');
  selector.id = `${id}-pieza`;
  selector.name = 'pieza';
  const etiquetaPieza = document.createElement('label');
  etiquetaPieza.htmlFor = selector.id;
  etiquetaPieza.textContent = 'Pieza';
  const campoPieza = document.createElement('div');
  campoPieza.className = 'campo';
  campoPieza.append(etiquetaPieza, selector);
  const casilla = document.createElement('input');
  casilla.type = 'checkbox';
  casilla.name = 'incluirAnuladas';
  const etiquetaCasilla = document.createElement('label');
  etiquetaCasilla.className = 'img-check';
  etiquetaCasilla.append(casilla, 'Mostrar anuladas');
  filtros.append(campoPieza, etiquetaCasilla);

  /** Los filtros solo están en la página cuando hay algo que filtrar. */
  function mostrarFiltros(ver: boolean): void {
    if (!ver) {
      filtros.remove();
    } else if (!filtros.isConnected) {
      seccion.insertBefore(filtros, resultados);
    }
  }

  function avisar(exitoso: string, fallido: string): void {
    exito.textContent = exitoso;
    error.textContent = fallido;
  }

  /** Direcciones temporales de los archivos que siguen en memoria; `destruir` revoca las pendientes (design D5). */
  const direcciones = new Set<string>();
  let visor: HTMLDialogElement | null = null;
  let carga: { readonly abierto: DialogoAbierto; hayDatos(): boolean } | null = null;
  let pregunta: DialogoAbierto | null = null;
  let anulacion: DialogoAbierto | null = null;

  function crearDireccion(archivo: Blob): string {
    const direccion = URL.createObjectURL(archivo);
    direcciones.add(direccion);
    return direccion;
  }

  function liberar(direccion: string): void {
    if (direcciones.delete(direccion)) {
      URL.revokeObjectURL(direccion);
    }
  }

  /** Mensaje de un pedido de archivo: el de permiso o inexistente tal cual, y un texto propio para el resto. */
  function mensajeDeArchivo(causa: unknown, generico: string): string {
    const mensaje = opciones.mensajeDeError(causa);
    if (mensaje === '') {
      return '';
    }
    return causa instanceof ErrorPermiso || causa instanceof ErrorNoEncontrado ? mensaje : generico;
  }

  /** Un 413 sin código (el servidor cortó la subida antes del controlador) igual dice que el archivo es muy grande. */
  function mensajeDeCarga(causa: unknown): string {
    if (causa instanceof ErrorValidacion && causa.estado === 413 && causa.codigo === null) {
      return MENSAJE_ARCHIVO_GRANDE;
    }
    return opciones.mensajeDeError(causa);
  }

  function bajar(imagen: ImagenClinica, archivo: Blob): void {
    const direccion = crearDireccion(archivo);
    const enlace = document.createElement('a');
    enlace.href = direccion;
    enlace.download = nombreDeArchivo(imagen.id, imagen.tipoContenido);
    enlace.hidden = true;
    document.body.append(enlace);
    enlace.click();
    enlace.remove();
    // La descarga ya empezó: se libera el archivo de la memoria sin esperar a cerrar nada.
    setTimeout(() => liberar(direccion), TIEMPO_ANTES_DE_LIBERAR);
  }

  async function descargarImagen(imagen: ImagenClinica): Promise<void> {
    avisar('', '');
    try {
      const archivo = await opciones.obtenerArchivo(imagen.id, true);
      if (opciones.vigente()) {
        bajar(imagen, archivo);
      }
    } catch (causa) {
      if (opciones.vigente()) {
        avisar('', mensajeDeArchivo(causa, MENSAJE_FALLA_DESCARGA));
      }
    }
  }

  function cerrarVisor(): void {
    if (visor?.open === true) {
      visor.close();
    }
    visor?.remove();
    visor = null;
  }

  /** Visor de una imagen JPEG o PNG: pide el archivo y lo muestra en un `<img>` con su texto alternativo (design D5). */
  async function abrirVisor(imagen: ImagenClinica, abridor: HTMLElement): Promise<void> {
    cerrarVisor();
    const marco = document.createElement('div');
    marco.className = 'img-visor-marco';
    marco.append(crearCargando('Cargando la imagen…'));
    const pie = document.createElement('p');
    pie.className = 'img-visor-pie';
    pie.textContent = [
      `${esMasculino(imagen) ? 'Tomado' : 'Tomada'} el ${fechaDeToma(imagen.tomadaEl)}`,
      imagen.profesional === '' ? '' : `Cargó ${imagen.profesional}`,
      imagen.descripcion,
    ]
      .filter((t) => t !== '')
      .join(' · ');
    let direccion: string | null = null;
    // El visor usa el molde de los diálogos: "Cerrar" descarta y "Descargar" es la acción principal.
    const abierto = abrirDialogo({
      titulo:
        imagen.pieza === null
          ? nombreDeCategoria(imagen.categoria)
          : `${nombreDeCategoria(imagen.categoria)} · pieza ${imagen.pieza}`,
      contenido: [marco, pie],
      textoDescartar: 'Cerrar',
      textoPrincipal: 'Descargar',
      alCerrar: () => {
        if (direccion !== null) {
          liberar(direccion);
        }
        if (visor === abierto.dialogo) {
          visor = null;
        }
        if (abridor.isConnected) {
          abridor.focus();
        }
      },
    });
    const dialogo = abierto.dialogo;
    dialogo.classList.add('img-visor');
    conIcono(abierto.principal, 'descarga');
    abierto.descartar.focus();
    visor = dialogo;
    abierto.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      void descargarImagen(imagen);
    });
    dialogo.addEventListener('keydown', (evento) => {
      if (evento.key === 'Escape') {
        evento.preventDefault();
        abierto.cerrar();
      }
    });

    try {
      const archivo = await opciones.obtenerArchivo(imagen.id, false);
      if (visor !== dialogo || !opciones.vigente()) {
        return;
      }
      direccion = crearDireccion(archivo);
      const foto = document.createElement('img');
      foto.alt = nombreAccesible(imagen);
      foto.src = direccion;
      marco.replaceChildren(foto);
    } catch (causa) {
      if (visor !== dialogo || !opciones.vigente()) {
        return;
      }
      const falla = crearAviso('alert');
      falla.textContent = mensajeDeArchivo(causa, MENSAJE_FALLA_ARCHIVO);
      marco.replaceChildren(falla);
    }
  }

  function dibujarOpcionesDePieza(): void {
    const piezas = [...piezasConocidas];
    if (pieza !== undefined && !piezas.includes(pieza)) {
      piezas.push(pieza);
    }
    piezas.sort((a, b) => a - b);
    selector.replaceChildren(
      ...[{ valor: '', texto: 'Todas las piezas' }, ...piezas.map((p) => ({ valor: String(p), texto: String(p) }))].map(
        ({ valor, texto }) => {
          const opcion = document.createElement('option');
          opcion.value = valor;
          opcion.textContent = texto;
          return opcion;
        },
      ),
    );
    selector.value = pieza === undefined ? '' : String(pieza);
  }

  function mostrarFalla(causa: unknown): void {
    const mensaje = opciones.mensajeDeError(causa);
    if (mensaje === '') {
      return;
    }
    avisar('', causa instanceof ErrorPermiso || causa instanceof ErrorNoEncontrado ? mensaje : MENSAJE_FALLA_IMAGENES);
    resumen.hidden = true;
    mostrarFiltros(false);
    const reintentar = crearBoton('Reintentar');
    reintentar.className = 'btn btn-principal';
    reintentar.addEventListener('click', () => {
      avisar('', '');
      void recargar();
    });
    const caja = document.createElement('div');
    caja.append(reintentar);
    resultados.replaceChildren(caja);
  }

  /** Diálogo de carga (design D6): el archivo, la categoría, la fecha de la toma, la pieza y la descripción. */
  function abrirCarga(abridor: HTMLElement): void {
    contador += 1;
    const idCarga = `${id}-carga-${contador}`;

    const entradaArchivo = document.createElement('input');
    entradaArchivo.type = 'file';
    entradaArchivo.id = `${idCarga}-archivo`;
    entradaArchivo.name = 'archivo';
    entradaArchivo.accept = 'image/jpeg,image/png,application/pdf';
    entradaArchivo.className = 'solo-lectores';
    const ayudaArchivo = document.createElement('p');
    ayudaArchivo.className = 'img-archivo-ayuda';
    ayudaArchivo.id = `${idCarga}-archivo-ayuda`;
    ayudaArchivo.textContent = 'JPEG, PNG o PDF de hasta 10 MB.';
    const errorArchivo = document.createElement('p');
    errorArchivo.className = 'campo-error';
    errorArchivo.id = `${idCarga}-archivo-error`;
    entradaArchivo.setAttribute('aria-describedby', `${ayudaArchivo.id} ${errorArchivo.id}`);
    const etiquetaArchivo = document.createElement('label');
    etiquetaArchivo.htmlFor = entradaArchivo.id;
    etiquetaArchivo.className = 'btn';
    etiquetaArchivo.textContent = 'Elegir archivo';
    const iconoArchivo = document.createElement('span');
    iconoArchivo.append(crearIcono('subir'));
    const elegido = document.createElement('p');
    elegido.className = 'img-archivo-elegido';
    elegido.hidden = true;
    const zonaArchivo = document.createElement('div');
    zonaArchivo.className = 'img-archivo';
    zonaArchivo.append(iconoArchivo, elegido, etiquetaArchivo, entradaArchivo, ayudaArchivo, errorArchivo);

    const campoCategoria = crearCampo(
      'Categoría',
      crearSelector('categoria', CATEGORIAS.map((c) => ({ valor: c.valor, texto: c.texto }))),
    );
    const entradaFecha = crearEntrada('date', 'tomadaEl');
    const hoyDia = hoy();
    entradaFecha.value = hoyDia;
    entradaFecha.max = hoyDia;
    const campoFecha = crearCampo('Fecha de la toma', entradaFecha);
    const fila = document.createElement('div');
    fila.className = 'img-form-fila';
    fila.append(campoCategoria.contenedor, campoFecha.contenedor);
    const entradaPieza = crearEntrada('text', 'pieza');
    entradaPieza.inputMode = 'numeric';
    entradaPieza.placeholder = 'Ej.: 36';
    const campoPieza = crearCampo('Pieza (opcional)', entradaPieza);
    const ayudaPieza = document.createElement('p');
    ayudaPieza.className = 'ayuda';
    ayudaPieza.id = `${idCarga}-pieza-ayuda`;
    ayudaPieza.textContent = 'Notación FDI, permanente o temporal. Vacío si la imagen no es de una pieza.';
    campoPieza.contenedor.append(ayudaPieza);
    entradaPieza.setAttribute('aria-describedby', `${entradaPieza.id}-error ${ayudaPieza.id}`);
    const descripcion = crearAreaConContador(`${idCarga}-descripcion`, 'Descripción (opcional)', 'Ej.: control de la endodoncia', LIMITE_DESCRIPCION);

    let archivoElegido: File | null = null;
    let enviando = false;
    entradaArchivo.addEventListener('change', () => {
      archivoElegido = entradaArchivo.files?.[0] ?? null;
      errorArchivo.textContent = '';
      entradaArchivo.removeAttribute('aria-invalid');
      zonaArchivo.classList.remove('con-error');
      elegido.hidden = archivoElegido === null;
      if (archivoElegido !== null) {
        elegido.replaceChildren(
          crearIcono(tipoDeArchivo(archivoElegido) === 'PDF' ? 'documento' : 'imagen'),
          `${archivoElegido.name} · ${formatearPeso(archivoElegido.size)}`,
        );
      }
      etiquetaArchivo.textContent = archivoElegido === null ? 'Elegir archivo' : 'Cambiar archivo';
    });

    const abierto: DialogoAbierto = abrirDialogo({
      titulo: 'Cargar imagen',
      resumen: `${opciones.nombrePaciente}. La imagen queda en su historia clínica y no se puede borrar: si te equivocás, la podés anular.`,
      contenido: [zonaArchivo, fila, campoPieza.contenedor, descripcion.contenedor],
      textoDescartar: 'Cancelar',
      textoPrincipal: 'Cargar',
      alCerrar: () => {
        if (carga?.abierto === abierto) {
          carga = null;
        }
        if (abridor.isConnected) {
          abridor.focus();
        }
      },
    });
    abierto.formulario.classList.add('img-form');
    /** Hay un archivo elegido o texto en la pieza o la descripción; la categoría y la fecha no cuentan. */
    const hayDatos = (): boolean =>
      archivoElegido !== null || entradaPieza.value.trim() !== '' || descripcion.area.value.trim() !== '';
    carga = { abierto, hayDatos };

    /** Con datos sin cargar, "Cancelar" y Escape preguntan antes de cerrar (design D6). */
    function pedirDescarte(): void {
      pregunta?.cerrar();
      const confirmar = abrirDialogo({
        titulo: '¿Descartar la imagen?',
        resumen: MENSAJE_DESCARTAR_IMAGEN,
        textoDescartar: 'Seguir editando',
        textoPrincipal: 'Descartar',
        peligro: true,
        alCerrar: () => {
          if (pregunta === confirmar) {
            pregunta = null;
          }
          if (abierto.dialogo.open) {
            abierto.descartar.focus();
          }
        },
      });
      pregunta = confirmar;
      confirmar.formulario.addEventListener('submit', (evento) => {
        evento.preventDefault();
        confirmar.cerrar();
        abierto.cerrar();
      });
    }
    // En la fase de captura, antes de que el molde del diálogo cierre con "Cancelar".
    abierto.formulario.addEventListener(
      'click',
      (evento) => {
        if (evento.target instanceof Node && abierto.descartar.contains(evento.target) && hayDatos()) {
          evento.stopPropagation();
          pedirDescarte();
        }
      },
      true,
    );
    abierto.dialogo.addEventListener('cancel', (evento) => {
      if (hayDatos()) {
        evento.preventDefault();
        pedirDescarte();
      }
    });

    function validar(): { archivo: File; pieza: number | null } | null {
      const errores: [HTMLElement, string | null][] = [
        [entradaArchivo, validarArchivo(archivoElegido)],
        [entradaFecha, validarFechaDeToma(entradaFecha.value, hoy())],
        [entradaPieza, validarPieza(entradaPieza.value)],
        [descripcion.area, validarDescripcion(descripcion.area.value)],
      ];
      errorArchivo.textContent = errores[0]?.[1] ?? '';
      zonaArchivo.classList.toggle('con-error', errores[0]?.[1] !== null);
      campoFecha.mostrarError(errores[1]?.[1] ?? '');
      campoPieza.mostrarError(errores[2]?.[1] ?? '');
      descripcion.mostrarError(errores[3]?.[1] ?? '');
      if (errores[0]?.[1] === null) {
        entradaArchivo.removeAttribute('aria-invalid');
      } else {
        entradaArchivo.setAttribute('aria-invalid', 'true');
      }
      const primero = errores.find(([, mensaje]) => mensaje !== null);
      if (primero !== undefined || archivoElegido === null) {
        abierto.general.textContent = MENSAJE_REVISAR_DATOS;
        primero?.[0].focus();
        return null;
      }
      abierto.general.textContent = '';
      const texto = entradaPieza.value.trim();
      return { archivo: archivoElegido, pieza: texto === '' ? null : Number(texto) };
    }

    abierto.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      if (enviando) {
        return;
      }
      const validos = validar();
      if (validos === null) {
        return;
      }
      void (async () => {
        enviando = true;
        abierto.principal.disabled = true;
        abierto.principal.textContent = 'Cargando…';
        try {
          await opciones.cargar({
            archivo: validos.archivo,
            categoria: campoCategoria.control.value as Categoria,
            tomadaEl: entradaFecha.value,
            pieza: validos.pieza,
            descripcion: descripcion.area.value,
          });
          if (carga?.abierto !== abierto || !opciones.vigente()) {
            return;
          }
          abierto.cerrar();
          avisar(MENSAJE_IMAGEN_CARGADA, '');
          void recargar();
        } catch (causa) {
          if (carga?.abierto === abierto && opciones.vigente()) {
            abierto.general.textContent = mensajeDeCarga(causa);
          }
        } finally {
          enviando = false;
          abierto.principal.disabled = false;
          abierto.principal.textContent = 'Cargar';
        }
      })();
    });
  }

  /** Diálogo de anulación (design D7): el motivo es obligatorio y la imagen no se borra. */
  function abrirAnulacion(imagen: ImagenClinica, abridor: HTMLElement): void {
    contador += 1;
    const motivo = crearAreaConContador(`${id}-motivo-${contador}`, 'Motivo', 'Ej.: se cargó en el paciente equivocado', LIMITE_MOTIVO);
    let enviando = false;
    const abierto: DialogoAbierto = abrirDialogo({
      titulo: `¿Anular ${conArticulo(imagen)}?`,
      resumen: `${nombreAccesible(imagen)}. La imagen no se borra: queda en la historia clínica como anulada, con tu nombre y el motivo.`,
      contenido: [motivo.contenedor],
      textoDescartar: 'Volver',
      textoPrincipal: 'Anular',
      peligro: true,
      alCerrar: () => {
        if (abridor.isConnected) {
          abridor.focus();
        }
      },
    });
    abierto.formulario.classList.add('img-form');
    anulacion = abierto;
    // Foco en el motivo: es lo único que hay que escribir.
    motivo.area.focus();

    abierto.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      if (enviando) {
        return;
      }
      const mensaje = validarMotivo(motivo.area.value);
      motivo.mostrarError(mensaje ?? '');
      if (mensaje !== null) {
        motivo.area.focus();
        return;
      }
      void (async () => {
        enviando = true;
        abierto.principal.disabled = true;
        abierto.general.textContent = '';
        try {
          await opciones.anular(imagen.id, motivo.area.value);
          if (anulacion !== abierto || !opciones.vigente()) {
            return;
          }
          abierto.cerrar();
          avisar(MENSAJE_IMAGEN_ANULADA, '');
          void recargar();
        } catch (causa) {
          if (anulacion !== abierto || !opciones.vigente()) {
            return;
          }
          if (causa instanceof ErrorNegocio && causa.codigo === 'IMAGEN_YA_ANULADA') {
            // Otra persona la anuló antes: se avisa en la sección y se vuelve a pedir el listado.
            abierto.cerrar();
            avisar('', opciones.mensajeDeError(causa));
            void recargar();
          } else {
            abierto.general.textContent = opciones.mensajeDeError(causa);
          }
        } finally {
          enviando = false;
          abierto.principal.disabled = false;
        }
      })();
    });
  }

  function crearFila(imagen: ImagenClinica): HTMLLIElement {
    const anulada = imagen.anulacion !== null;
    const fila = document.createElement('li');
    fila.className = anulada ? 'img-fila img-fila-anulada' : 'img-fila';
    const esPdf = imagen.tipoContenido === 'application/pdf';
    const tipo = document.createElement('span');
    tipo.className = esPdf ? 'img-tipo img-tipo-pdf' : 'img-tipo';
    tipo.append(crearIcono(esPdf ? 'documento' : 'imagen'));

    const datos = document.createElement('div');
    datos.className = 'img-datos';
    const encabezadoFila = document.createElement('p');
    encabezadoFila.className = 'img-titulo';
    const categoria = document.createElement('span');
    categoria.textContent = nombreDeCategoria(imagen.categoria);
    encabezadoFila.append(categoria);
    if (imagen.pieza !== null) {
      encabezadoFila.append(crearEtiqueta(`Pieza ${imagen.pieza}`));
    }
    if (esPdf) {
      encabezadoFila.append(crearEtiqueta('PDF'));
    }
    if (anulada) {
      encabezadoFila.append(crearEtiqueta('Anulada', 'etiqueta-anulada'));
    }
    const meta = document.createElement('p');
    meta.className = 'img-meta';
    const formato = etiquetaDeFormato(imagen.tipoContenido);
    meta.textContent = [
      `${esMasculino(imagen) ? 'Tomado' : 'Tomada'} el ${fechaDeToma(imagen.tomadaEl)}`,
      imagen.profesional === '' ? '' : `Cargó ${imagen.profesional}`,
      [formato, formatearPeso(imagen.tamanoBytes)].filter((t) => t !== '').join(', '),
    ]
      .filter((t) => t !== '')
      .join(' · ');
    datos.append(encabezadoFila, meta);
    if (imagen.descripcion !== '') {
      const descripcion = document.createElement('p');
      descripcion.className = 'img-descripcion';
      descripcion.textContent = imagen.descripcion;
      datos.append(descripcion);
    }
    if (imagen.anulacion !== null) {
      const linea = document.createElement('p');
      linea.className = 'img-anulacion';
      const cuando = imagen.anulacion.anuladaEn === '' ? '' : ` el ${fechaYHora(imagen.anulacion.anuladaEn)}`;
      const quien = imagen.anulacion.profesional === '' ? '' : ` por ${imagen.anulacion.profesional}`;
      linea.textContent = `Anulada${quien}${cuando}: ${imagen.anulacion.motivo}`;
      datos.append(linea);
    }

    const botones = document.createElement('div');
    botones.className = 'img-acciones';
    if (puedeVerse(imagen.tipoContenido)) {
      const ver = crearBoton('Ver');
      ver.className = 'btn';
      ver.setAttribute('aria-label', nombreDeBoton('Ver', imagen, anulada));
      conIcono(ver, 'ver');
      ver.addEventListener('click', () => void abrirVisor(imagen, ver));
      botones.append(ver);
    }
    const descargar = crearBoton('Descargar');
    descargar.className = 'btn';
    descargar.setAttribute('aria-label', nombreDeBoton('Descargar', imagen, anulada));
    conIcono(descargar, 'descarga');
    descargar.addEventListener('click', () => void descargarImagen(imagen));
    botones.append(descargar);
    if (opciones.editable && !anulada) {
      const anular = crearBoton('Anular');
      anular.className = 'btn btn-peligro';
      anular.setAttribute('aria-label', nombreDeBoton('Anular', imagen, anulada));
      anular.addEventListener('click', () => abrirAnulacion(imagen, anular));
      botones.append(anular);
    }
    fila.append(tipo, datos, botones);
    return fila;
  }

  function mensajeSinResultados(): string {
    if (pieza !== undefined) {
      return `No hay imágenes de la pieza ${pieza}.`;
    }
    return soloAnuladas && !incluirAnuladas ? MENSAJE_SOLO_ANULADAS : MENSAJE_SIN_IMAGENES;
  }

  /**
   * Sin imágenes vigentes, pregunta una sola vez si hay anuladas: si las hay, los filtros quedan a la vista para
   * poder mostrarlas. Si la pregunta falla, la sección sigue como sin imágenes.
   */
  async function hayAnuladas(este: number): Promise<readonly ImagenClinica[] | null> {
    try {
      const anuladas = await opciones.listar({ incluirAnuladas: true });
      return este === pedido && opciones.vigente() ? anuladas : null;
    } catch {
      return este === pedido && opciones.vigente() ? [] : null;
    }
  }

  function dibujar(lista: readonly ImagenClinica[]): void {
    mostrarFiltros(conImagenes || pieza !== undefined || incluirAnuladas);
    dibujarOpcionesDePieza();
    if (lista.length === 0) {
      resumen.hidden = true;
      resultados.replaceChildren(
        crearEstadoVacio({
          texto: mensajeSinResultados(),
          chico: true,
        }),
      );
      return;
    }
    resumen.hidden = false;
    resumen.textContent = resumenDeVigentes(lista);
    const ul = document.createElement('ul');
    ul.className = 'img-lista';
    ul.setAttribute('aria-label', `Imágenes y estudios de ${opciones.nombrePaciente}`);
    ul.append(...lista.map(crearFila));
    resultados.replaceChildren(ul);
  }

  async function recargar(): Promise<void> {
    pedido += 1;
    const este = pedido;
    resultados.replaceChildren(crearCargando('Cargando imágenes…'));
    try {
      const lista = await opciones.listar(pieza === undefined ? { incluirAnuladas } : { pieza, incluirAnuladas });
      if (este !== pedido || !opciones.vigente()) {
        return;
      }
      if (pieza === undefined) {
        let conocidas = lista;
        if (lista.length === 0 && !incluirAnuladas) {
          const anuladas = await hayAnuladas(este);
          if (anuladas === null) {
            return;
          }
          conocidas = anuladas;
          soloAnuladas = anuladas.length > 0;
        } else if (lista.some((i) => i.anulacion === null)) {
          soloAnuladas = false;
        }
        piezasConocidas = [...new Set(conocidas.flatMap((i) => (i.pieza === null ? [] : [i.pieza])))];
        conImagenes = conocidas.length > 0;
      }
      dibujar(lista);
    } catch (causa) {
      if (este === pedido && opciones.vigente()) {
        mostrarFalla(causa);
      }
    }
  }

  selector.addEventListener('change', () => {
    pieza = selector.value === '' ? undefined : Number(selector.value);
    avisar('', '');
    void recargar();
  });
  casilla.addEventListener('change', () => {
    incluirAnuladas = casilla.checked;
    avisar('', '');
    void recargar();
  });

  function sinGuardar(): boolean {
    return carga?.hayDatos() ?? false;
  }

  /** Salir de `#/pacientes` equivale a salir de la ficha: se cierra lo abierto y se libera lo que sigue en memoria. */
  function alCambiarLaDireccion(): void {
    if (!/^#\/pacientes(?:[/?]|$)/.test(window.location.hash)) {
      destruir();
    }
  }

  function destruir(): void {
    window.removeEventListener('hashchange', alCambiarLaDireccion);
    pedido += 1;
    pregunta?.cerrar();
    carga?.abierto.cerrar();
    anulacion?.cerrar();
    cerrarVisor();
    for (const direccion of [...direcciones]) {
      liberar(direccion);
    }
  }

  window.addEventListener('hashchange', alCambiarLaDireccion);
  void recargar();
  return { contenedor: seccion, sinGuardar, destruir };
}
