import { botonChico, cargarEn, crearCabecera, guardando, type ContextoSeccion } from '../configuracion/ui.ts';
import { fechaNumerica } from '../ficha/formato.ts';
import { ErrorNegocio } from '../shared/api/errores.ts';
import { crearAviso, crearBoton } from '../shared/ui/campos.ts';
import { crearEstadoVacio } from '../shared/ui/estado-vacio.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { formatearHora } from '../shared/fecha/zona.ts';
import {
  cambiarEstadoPlantilla,
  crearPlantilla,
  editarPlantilla,
  listarPlantillas,
  listarVersiones,
  obtenerPlantilla,
} from './api.ts';
import {
  LIMITE_TEXTO,
  LIMITE_TITULO,
  validarTexto,
  validarTitulo,
  type PlantillaResumen,
  type PlantillaVista,
  type VersionPlantilla,
} from './modelo.ts';
import {
  abrirDialogoDeLectura,
  abrirDialogoDesde,
  crearCampoConContador,
  crearTextoPlano,
  pedirDescarteAlCancelar,
  type CampoConContador,
} from './ui.ts';

/**
 * Pestaña "Consentimientos" de Configuración (C-48, design D2 y D4): las plantillas de consentimiento con su editor de
 * texto plano, las versiones y activar o desactivar. Solo la monta el administrador; nada se borra.
 */

export const AYUDA_PLANTILLAS =
  'Las plantillas no se borran. Cada edición crea una versión nueva y lo que ya se firmó no cambia. Una plantilla inactiva no se puede usar para firmar.';
export const AYUDA_TEXTO_PLANO = 'Texto plano: se respetan los saltos de línea; no se aplica formato ni HTML.';
export const MENSAJE_DESCARTAR_PLANTILLA = 'Hay cambios sin guardar en la plantilla. ¿Descartarlos?';
export const MENSAJE_CONFLICTO_DE_VERSION =
  'Otra persona guardó una versión nueva de esta plantilla mientras la editabas. Copiá tus cambios, cerrá y volvé a abrirla.';

function span(clase: string, texto: string): HTMLSpanElement {
  const elemento = document.createElement('span');
  if (clase !== '') {
    elemento.className = clase;
  }
  elemento.textContent = texto;
  return elemento;
}

/** "Activa" o "Inactiva", escrito y con la forma del punto: no depende solo del color. */
function estadoDeLaPlantilla(activa: boolean): HTMLSpanElement {
  return span(activa ? 'estado-activo' : 'estado-inactivo', activa ? 'Activa' : 'Inactiva');
}

function fechaYHoraDeVersion(instante: string): string {
  return `${fechaNumerica(instante)} ${formatearHora(instante)}`;
}

function anunciarTexto(cantidad: number, limite: number, excedido: boolean): string {
  const cuenta = (n: number): string => String(n).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return excedido
    ? `El texto supera el límite de ${cuenta(limite)} caracteres.`
    : `Llevás ${cuenta(cantidad)} de ${cuenta(limite)} caracteres.`;
}

function anunciarTitulo(cantidad: number, limite: number, excedido: boolean): string {
  return excedido ? `El título supera el límite de ${limite} caracteres.` : `Llevás ${cantidad} de ${limite} caracteres.`;
}

export function montarConsentimientos(panel: HTMLElement, contexto: ContextoSeccion): void {
  const nueva = crearBoton('Nueva plantilla');
  const ayuda = document.createElement('p');
  ayuda.className = 'ayuda';
  ayuda.textContent = AYUDA_PLANTILLAS;
  const exito = crearAviso('status');
  const error = crearAviso('alert');
  const contenido = document.createElement('div');
  panel.append(crearCabecera('Plantillas de consentimiento', nueva), ayuda, exito, error, contenido);

  let plantillas: PlantillaResumen[] = [];
  const recargar = cargarEn(contenido, contexto, () => listarPlantillas(contexto.api, true), (lista, contenedor) => {
    plantillas = lista;
    dibujarLista(contenedor);
  });

  nueva.addEventListener('click', () => dialogoDePlantilla(nueva, null));

  function avisar(mensaje: string): void {
    error.textContent = '';
    exito.textContent = mensaje;
  }

  function fallar(causa: unknown): void {
    if (contexto.sesionVencida(causa)) {
      return;
    }
    exito.textContent = '';
    error.textContent = mensajeDeError(causa);
  }

  function dibujarLista(contenedor: HTMLElement): void {
    if (plantillas.length === 0) {
      contenedor.replaceChildren(crearEstadoVacio({ texto: 'Todavía no hay plantillas de consentimiento.', chico: true }));
      return;
    }
    const lista = document.createElement('ul');
    lista.className = 'lista-config cons-plantillas';
    lista.setAttribute('aria-label', 'Plantillas de consentimiento');
    lista.append(...plantillas.map(dibujarFila));
    contenedor.replaceChildren(lista);
  }

  function dibujarFila(plantilla: PlantillaResumen): HTMLLIElement {
    const fila = document.createElement('li');
    if (!plantilla.activa) {
      fila.className = 'item-inactivo';
    }
    const nombre = document.createElement('div');
    nombre.className = 'item-nombre';
    nombre.textContent = plantilla.titulo;
    const datos = document.createElement('div');
    datos.className = 'item-datos';
    datos.append(span('', `Versión ${plantilla.versionActual}`));
    if (plantilla.actualizadaEn !== '') {
      datos.append(span('', `Actualizada el ${fechaNumerica(plantilla.actualizadaEn)}`));
    }
    const resumen = document.createElement('div');
    resumen.className = 'cons-plantilla-datos';
    resumen.append(nombre, datos, estadoDeLaPlantilla(plantilla.activa));

    const editar = botonChico('Editar');
    editar.setAttribute('aria-label', `Editar ${plantilla.titulo}`);
    editar.addEventListener('click', () => void abrirEdicion(editar, plantilla));
    const versiones = botonChico('Versiones');
    versiones.setAttribute('aria-label', `Versiones de ${plantilla.titulo}`);
    versiones.addEventListener('click', () => void abrirVersiones(versiones, plantilla));
    const estado = plantilla.activa ? botonChico('Desactivar', 'peligro') : botonChico('Activar');
    estado.setAttribute('aria-label', `${plantilla.activa ? 'Desactivar' : 'Activar'} ${plantilla.titulo}`);
    estado.addEventListener('click', () => void cambiarEstado(estado, plantilla));

    const acciones = document.createElement('div');
    acciones.className = 'item-acciones';
    acciones.append(editar, versiones, estado);
    fila.append(resumen, acciones);
    return fila;
  }

  /** Activar o desactivar, sin confirmación: es reversible y no borra nada. */
  async function cambiarEstado(boton: HTMLButtonElement, plantilla: PlantillaResumen): Promise<void> {
    error.textContent = '';
    exito.textContent = '';
    const activar = !plantilla.activa;
    try {
      const ok = await guardando(boton, () => cambiarEstadoPlantilla(contexto.api, plantilla.id, activar));
      if (!ok) {
        return;
      }
    } catch (causa) {
      fallar(causa);
      return;
    }
    plantillas = plantillas.map((p) => (p.id === plantilla.id ? { ...p, activa: activar } : p));
    dibujarLista(contenido);
    avisar(activar ? 'Plantilla activada.' : 'Plantilla desactivada.');
    const nuevoBoton = [...contenido.querySelectorAll<HTMLButtonElement>('button')].find(
      (b) => b.getAttribute('aria-label') === `${activar ? 'Desactivar' : 'Activar'} ${plantilla.titulo}`,
    );
    nuevoBoton?.focus();
  }

  async function abrirEdicion(origen: HTMLButtonElement, plantilla: PlantillaResumen): Promise<void> {
    error.textContent = '';
    exito.textContent = '';
    const cargada: { vista: PlantillaVista | null } = { vista: null };
    try {
      const ok = await guardando(origen, async () => {
        cargada.vista = await obtenerPlantilla(contexto.api, plantilla.id);
      });
      if (!ok) {
        return;
      }
    } catch (causa) {
      fallar(causa);
      return;
    }
    if (cargada.vista !== null) {
      dialogoDePlantilla(origen, cargada.vista);
    }
  }

  async function abrirVersiones(origen: HTMLButtonElement, plantilla: PlantillaResumen): Promise<void> {
    error.textContent = '';
    exito.textContent = '';
    const cargadas: { lista: VersionPlantilla[] } = { lista: [] };
    try {
      const ok = await guardando(origen, async () => {
        cargadas.lista = await listarVersiones(contexto.api, plantilla.id);
      });
      if (!ok) {
        return;
      }
    } catch (causa) {
      fallar(causa);
      return;
    }
    const ul = document.createElement('ul');
    ul.className = 'cons-versiones';
    ul.setAttribute('aria-label', 'Versiones');
    for (const version of cargadas.lista) {
      const item = document.createElement('li');
      const detalle = document.createElement('details');
      detalle.className = 'cons-version';
      const encabezado = document.createElement('summary');
      encabezado.append(`Versión ${version.numero} `, span('cons-version-meta', `· ${fechaYHoraDeVersion(version.creadaEn)} · ${version.creadaPor}`));
      detalle.append(encabezado, crearTextoPlano(version.texto));
      item.append(detalle);
      ul.append(item);
    }
    abrirDialogoDeLectura({
      titulo: `Versiones de «${plantilla.titulo}»`,
      resumen: 'Las versiones no se modifican. Cada consentimiento firmado guarda la copia del texto que se firmó.',
      contenido: [ul],
      clase: 'cons-editor',
      origen,
    });
  }

  /** Alta (`vista` es `null`) o edición de una plantilla: el título y el texto con su contador. */
  function dialogoDePlantilla(origen: HTMLButtonElement, vista: PlantillaVista | null): void {
    error.textContent = '';
    exito.textContent = '';
    const tituloInicial = vista?.version.titulo ?? '';
    const textoInicial = vista?.version.texto ?? '';
    const titulo: CampoConContador = crearCampoConContador({
      etiqueta: 'Título',
      limite: LIMITE_TITULO,
      valor: tituloInicial,
      anunciar: anunciarTitulo,
    });
    const texto: CampoConContador = crearCampoConContador({
      etiqueta: 'Texto del consentimiento',
      limite: LIMITE_TEXTO,
      valor: textoInicial,
      multilinea: true,
      placeholder: 'Escribí el texto que el paciente va a leer y aceptar.',
      ayuda: AYUDA_TEXTO_PLANO,
      anunciar: anunciarTexto,
    });
    const abierto = abrirDialogoDesde(
      origen,
      {
        titulo: vista === null ? 'Nueva plantilla' : `Editar «${vista.version.titulo}»`,
        ...(vista === null
          ? {}
          : { resumen: `Se guarda como versión ${vista.version.numero + 1}. Lo que ya se firmó con versiones anteriores no cambia.` }),
        contenido: [titulo.contenedor, texto.contenedor],
        textoDescartar: 'Cancelar',
        textoPrincipal: 'Guardar',
      },
      'cons-editor',
    );
    // Primero el campo del título: `abrirDialogo` llevó el foco al primer campo.
    pedirDescarteAlCancelar({
      abierto,
      hayCambios: () => titulo.control.value !== tituloInicial || texto.control.value !== textoInicial,
      titulo: '¿Descartar los cambios?',
      mensaje: MENSAJE_DESCARTAR_PLANTILLA,
    });

    let conflicto = false;
    abierto.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      if (conflicto) {
        return;
      }
      abierto.general.textContent = '';
      const errorTitulo = validarTitulo(titulo.control.value);
      const errorTexto = validarTexto(texto.control.value);
      titulo.mostrarError(errorTitulo);
      texto.mostrarError(errorTexto);
      if (errorTitulo !== '' || errorTexto !== '') {
        (errorTitulo !== '' ? titulo : texto).control.focus();
        return;
      }
      const datos = { titulo: titulo.control.value, texto: texto.control.value };
      let mensaje = '';
      void guardando(abierto.enviar, async () => {
        if (vista === null) {
          await crearPlantilla(contexto.api, datos);
          mensaje = 'Plantilla creada.';
          return;
        }
        const resultado = await editarPlantilla(contexto.api, vista.id, { ...datos, versionBase: vista.version.numero });
        if (resultado === null) {
          mensaje = 'Plantilla guardada.';
        } else if (resultado.sinCambios) {
          mensaje = `No hubo cambios: la plantilla sigue en la versión ${resultado.numero}.`;
        } else {
          mensaje = `Plantilla guardada como versión ${resultado.numero}.`;
        }
      }).then(
        (ok) => {
          if (ok) {
            abierto.cerrar();
            avisar(mensaje);
            recargar();
          }
        },
        (causa: unknown) => {
          if (contexto.sesionVencida(causa)) {
            return;
          }
          if (causa instanceof ErrorNegocio && causa.codigo === 'PLANTILLA_DUPLICADA') {
            titulo.mostrarError(mensajeDeError(causa));
            titulo.control.focus();
            return;
          }
          if (causa instanceof ErrorNegocio && causa.codigo === 'VERSION_DESACTUALIZADA') {
            // Se conserva lo escrito y no se envía nada más: solo se vuelve a pedir la lista (specs).
            conflicto = true;
            abierto.enviar.disabled = true;
            abierto.general.textContent = MENSAJE_CONFLICTO_DE_VERSION;
            recargar();
            return;
          }
          abierto.general.textContent = mensajeDeError(causa);
        },
      );
    });
  }
}
