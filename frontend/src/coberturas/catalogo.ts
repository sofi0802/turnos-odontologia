import { abrirCoberturaPlan, MENSAJE_COBERTURA_GUARDADA } from '../aranceles/cobertura-plan.ts';
import { cargarPrestaciones } from '../configuracion/api.ts';
import { botonChico, cargarEn, crearCabecera, crearEstado, crearFila, crearLista, guardando, type ContextoSeccion } from '../configuracion/ui.ts';
import { crearAviso, crearBoton, crearCampo, crearEntrada, crearSelector, cambiarOpciones, type Campo } from '../shared/ui/campos.ts';
import { crearEstadoVacio } from '../shared/ui/estado-vacio.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import {
  cambiarEstadoCodigo,
  cambiarEstadoObraSocial,
  cambiarEstadoPlan,
  guardarCodigo,
  guardarObraSocial,
  guardarPlan,
  listarNomenclador,
  listarObrasSociales,
  type CodigoNomenclador,
  type ObraSocial,
  type PlanCatalogo,
  type TipoCobertura,
} from './api.ts';
import {
  validarCodigo,
  validarDescripcion,
  validarNombreObraSocial,
  validarNombrePlan,
  validarSigla,
} from './modelo.ts';
import { abrirDialogoDesde, filaDeCampos, marcarErrores, mostrarRechazo, agregarAyuda } from './ui.ts';

/**
 * Pestaña "Obras sociales" de Configuración (C-49, design D5): obras sociales y prepagas con sus planes anidados, y el
 * nomenclador, en dos bloques que cargan aparte. Solo la monta el administrador; nada se borra, se desactiva.
 */

export const AYUDA_OBRAS = 'No se borran: desactivá las que ya no trabajan con el consultorio. Las afiliaciones que ya existen no cambian.';
export const AYUDA_NOMENCLADOR = 'Códigos de práctica con los que se informa a las obras sociales. Se cargan a mano.';
export const AYUDA_OBRA_INACTIVA = 'Inactiva: no se puede afiliar a sus planes. Las afiliaciones que ya existen no cambian.';

const TIPOS: readonly { valor: TipoCobertura; texto: string }[] = [
  { valor: 'OBRA_SOCIAL', texto: 'Obra social' },
  { valor: 'PREPAGA', texto: 'Prepaga' },
];

function textoTipo(tipo: TipoCobertura): string {
  return tipo === 'PREPAGA' ? 'Prepaga' : 'Obra social';
}

function campoTexto(etiqueta: string, nombre: string, maximo: number, valor = ''): Campo<HTMLInputElement> {
  const entrada = crearEntrada('text', nombre);
  entrada.classList.add('control');
  entrada.maxLength = maximo;
  entrada.autocomplete = 'off';
  entrada.value = valor;
  return crearCampo(etiqueta, entrada);
}

function span(clase: string, texto: string): HTMLSpanElement {
  const elemento = document.createElement('span');
  if (clase !== '') {
    elemento.className = clase;
  }
  elemento.textContent = texto;
  return elemento;
}

/** "Activa" o "Inactiva" de una obra social, escrito y con la forma del punto (como `crearEstado`). */
function estadoFemenino(activa: boolean): HTMLSpanElement {
  return span(activa ? 'estado-activo' : 'estado-inactivo', activa ? 'Activa' : 'Inactiva');
}

interface Bloque {
  readonly elemento: HTMLDivElement;
  /** C-50: aviso de éxito ("Cobertura guardada."). */
  readonly exito: HTMLParagraphElement;
  readonly aviso: HTMLParagraphElement;
  readonly contenido: HTMLDivElement;
}

function crearBloque(titulo: string, ayuda: string, accion: HTMLButtonElement): Bloque {
  const elemento = document.createElement('div');
  elemento.className = 'cob-bloque';
  const texto = document.createElement('p');
  texto.className = 'ayuda';
  texto.textContent = ayuda;
  const exito = crearAviso('status');
  const aviso = crearAviso('alert');
  const contenido = document.createElement('div');
  elemento.append(crearCabecera(titulo, accion), texto, exito, aviso, contenido);
  return { elemento, exito, aviso, contenido };
}

export function montarObrasSociales(panel: HTMLElement, contexto: ContextoSeccion): void {
  const agregarObra = crearBoton('Agregar obra social');
  const agregarCodigo = crearBoton('Agregar código');
  const obras = crearBloque('Obras sociales y prepagas', AYUDA_OBRAS, agregarObra);
  const nomenclador = crearBloque('Nomenclador', AYUDA_NOMENCLADOR, agregarCodigo);
  panel.append(obras.elemento, nomenclador.elemento);

  const recargarObras = cargarEn(obras.contenido, contexto, () => listarObrasSociales(contexto.api), dibujarObras);
  const recargarNomenclador = cargarEn(nomenclador.contenido, contexto, () => listarNomenclador(contexto.api), dibujarNomenclador);

  agregarObra.addEventListener('click', () => dialogoObra(agregarObra, null));
  agregarCodigo.addEventListener('click', () => dialogoCodigo(agregarCodigo, null));

  /** "Activar" o "Desactivar" sin confirmación (design D6); al terminar bien vuelve a pedir la lista. */
  function botonEstado(activo: boolean, nombre: string, aviso: HTMLElement, cambiar: (activar: boolean) => Promise<void>, recargar: () => void): HTMLButtonElement {
    const boton = activo ? botonChico('Desactivar', 'peligro') : botonChico('Activar');
    boton.setAttribute('aria-label', `${activo ? 'Desactivar' : 'Activar'} ${nombre}`);
    boton.addEventListener('click', () => {
      aviso.textContent = '';
      void guardando(boton, () => cambiar(!activo)).then(
        (ok) => {
          if (ok) {
            recargar();
          }
        },
        (causa: unknown) => {
          if (!contexto.sesionVencida(causa)) {
            aviso.textContent = mensajeDeError(causa);
          }
        },
      );
    });
    return boton;
  }

  function botonEditar(nombre: string, abrir: (boton: HTMLButtonElement) => void): HTMLButtonElement {
    const boton = botonChico('Editar');
    boton.setAttribute('aria-label', `Editar ${nombre}`);
    boton.addEventListener('click', () => abrir(boton));
    return boton;
  }

  function acciones(...botones: HTMLButtonElement[]): HTMLDivElement {
    const caja = document.createElement('div');
    caja.className = 'item-acciones';
    caja.append(...botones);
    return caja;
  }

  // --- Obras sociales y planes ---

  function dibujarObras(lista: ObraSocial[], contenedor: HTMLElement): void {
    if (lista.length === 0) {
      contenedor.append(crearEstadoVacio({ texto: 'Todavía no hay obras sociales ni prepagas.', chico: true }));
      return;
    }
    const ul = document.createElement('ul');
    ul.className = 'cob-obras';
    ul.append(...lista.map(dibujarObra));
    contenedor.append(ul);
  }

  function dibujarObra(obra: ObraSocial): HTMLLIElement {
    const item = document.createElement('li');
    item.className = obra.activa ? 'cob-obra' : 'cob-obra cob-obra-inactiva';

    const nombre = document.createElement('h3');
    nombre.className = 'cob-obra-nombre';
    nombre.textContent = obra.nombre;
    const datos = document.createElement('p');
    datos.className = 'cob-datos';
    if (obra.sigla !== null) {
      datos.append(span('cob-sigla', obra.sigla));
    }
    datos.append(span('cob-tipo', textoTipo(obra.tipo)));
    if (obra.sigla === null) {
      datos.append(span('', 'Sin sigla'));
    }
    datos.append(estadoFemenino(obra.activa));
    const quien = document.createElement('div');
    quien.append(nombre, datos);
    const cabecera = document.createElement('div');
    cabecera.className = 'cob-obra-cabecera';
    cabecera.append(
      quien,
      acciones(
        botonEditar(obra.nombre, (boton) => dialogoObra(boton, obra)),
        botonEstado(obra.activa, obra.nombre, obras.aviso, (activar) => cambiarEstadoObraSocial(contexto.api, obra.id, activar), recargarObras),
      ),
    );
    item.append(cabecera);
    if (!obra.activa) {
      const ayuda = document.createElement('p');
      ayuda.className = 'ayuda';
      ayuda.textContent = AYUDA_OBRA_INACTIVA;
      item.append(ayuda);
    }

    if (obra.planes.length === 0) {
      const vacio = document.createElement('p');
      vacio.className = 'cob-planes-vacio';
      vacio.textContent = 'Sin planes todavía.';
      item.append(vacio);
    } else {
      const planes = document.createElement('ul');
      planes.className = 'cob-planes';
      planes.setAttribute('aria-label', `Planes de ${obra.nombre}`);
      planes.append(...obra.planes.map((plan) => dibujarPlan(obra, plan)));
      item.append(planes);
    }

    const agregarPlan = botonChico('Agregar plan');
    agregarPlan.setAttribute('aria-label', `Agregar plan a ${obra.nombre}`);
    agregarPlan.addEventListener('click', () => dialogoPlan(agregarPlan, obra, null));
    const pie = document.createElement('div');
    pie.append(agregarPlan);
    item.append(pie);
    return item;
  }

  function dibujarPlan(obra: ObraSocial, plan: PlanCatalogo): HTMLLIElement {
    const item = document.createElement('li');
    item.className = plan.activo ? 'cob-plan' : 'cob-plan cob-plan-inactivo';
    const nombre = document.createElement('p');
    nombre.className = 'cob-plan-nombre';
    nombre.textContent = plan.nombre;
    const datos = document.createElement('div');
    datos.append(nombre, crearEstado(plan.activo));
    item.append(
      datos,
      acciones(
        botonCobertura(obra, plan),
        botonEditar(plan.nombre, (boton) => dialogoPlan(boton, obra, plan)),
        botonEstado(plan.activo, plan.nombre, obras.aviso, (activar) => cambiarEstadoPlan(contexto.api, obra.id, plan.id, activar), recargarObras),
      ),
    );
    return item;
  }

  /** C-50 (design D2): el porcentaje que cubre el plan por código del nomenclador. */
  function botonCobertura(obra: ObraSocial, plan: PlanCatalogo): HTMLButtonElement {
    const boton = botonChico('Cobertura');
    boton.setAttribute('aria-label', `Cobertura de ${plan.nombre}`);
    boton.addEventListener('click', () => {
      obras.exito.textContent = '';
      obras.aviso.textContent = '';
      abrirCoberturaPlan({
        origen: boton,
        obraSocial: { id: obra.id, nombre: obra.nombre },
        plan: { id: plan.id, nombre: plan.nombre },
        contexto,
        alGuardar: () => {
          obras.exito.textContent = MENSAJE_COBERTURA_GUARDADA;
        },
      });
    });
    return boton;
  }

  function dialogoObra(origen: HTMLButtonElement, obra: ObraSocial | null): void {
    obras.aviso.textContent = '';
    const nombre = campoTexto('Nombre', 'nombre', 120, obra?.nombre ?? '');
    const sigla = campoTexto('Sigla (opcional)', 'sigla', 20, obra?.sigla ?? '');
    sigla.control.placeholder = 'Ej.: OSE';
    const tipo = crearCampo('Tipo', crearSelector('tipo', TIPOS));
    tipo.control.classList.add('control');
    tipo.control.value = obra?.tipo ?? 'OBRA_SOCIAL';
    const dialogo = abrirDialogoDesde(origen, {
      titulo: obra === null ? 'Agregar obra social' : 'Editar obra social',
      ...(obra === null ? {} : { resumen: obra.nombre }),
      contenido: [nombre.contenedor, filaDeCampos(sigla, tipo)],
      textoDescartar: 'Cancelar',
      textoPrincipal: 'Guardar',
    });
    dialogo.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      dialogo.general.textContent = '';
      const valido = marcarErrores([
        [nombre, validarNombreObraSocial(nombre.control.value)],
        [sigla, validarSigla(sigla.control.value)],
        [tipo, ''],
      ]);
      if (!valido) {
        return;
      }
      const datos = { nombre: nombre.control.value, sigla: sigla.control.value, tipo: tipo.control.value === 'PREPAGA' ? 'PREPAGA' : 'OBRA_SOCIAL' } as const;
      void guardando(dialogo.enviar, () => guardarObraSocial(contexto.api, obra?.id ?? null, datos)).then(
        (ok) => {
          if (ok) {
            dialogo.cerrar();
            recargarObras();
          }
        },
        (causa: unknown) =>
          mostrarRechazo(causa, {
            general: dialogo.general,
            campos: {
              nombre: { campo: nombre, mensaje: 'Revisá el nombre.' },
              sigla: { campo: sigla, mensaje: 'Revisá la sigla.' },
              tipo: { campo: tipo, mensaje: 'Elegí el tipo.' },
            },
            sesionVencida: (c) => contexto.sesionVencida(c),
          }),
      );
    });
  }

  function dialogoPlan(origen: HTMLButtonElement, obra: ObraSocial, plan: PlanCatalogo | null): void {
    obras.aviso.textContent = '';
    const nombre = campoTexto('Nombre', 'nombre', 80, plan?.nombre ?? '');
    const dialogo = abrirDialogoDesde(origen, {
      titulo: plan === null ? 'Agregar plan' : 'Editar plan',
      resumen: plan === null ? `Plan de ${obra.nombre}.` : `${plan.nombre}, de ${obra.nombre}.`,
      contenido: [nombre.contenedor],
      textoDescartar: 'Cancelar',
      textoPrincipal: 'Guardar',
    });
    dialogo.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      dialogo.general.textContent = '';
      if (!marcarErrores([[nombre, validarNombrePlan(nombre.control.value)]])) {
        return;
      }
      void guardando(dialogo.enviar, () => guardarPlan(contexto.api, obra.id, plan?.id ?? null, nombre.control.value)).then(
        (ok) => {
          if (ok) {
            dialogo.cerrar();
            recargarObras();
          }
        },
        (causa: unknown) =>
          mostrarRechazo(causa, {
            general: dialogo.general,
            campos: { nombre: { campo: nombre, mensaje: 'Revisá el nombre.' } },
            sesionVencida: (c) => contexto.sesionVencida(c),
          }),
      );
    });
  }

  // --- Nomenclador ---

  function dibujarNomenclador(codigos: CodigoNomenclador[], contenedor: HTMLElement): void {
    if (codigos.length === 0) {
      contenedor.append(crearEstadoVacio({ texto: 'Todavía no hay códigos en el nomenclador.', chico: true }));
      return;
    }
    const lista = crearLista();
    lista.append(
      ...codigos.map((codigo) => {
        const fila = crearFila(
          `${codigo.codigo} · ${codigo.descripcion}`,
          [codigo.prestacion === null ? 'Sin prestación' : `Prestación: ${codigo.prestacion.nombre}`],
          {
            activo: codigo.activo,
            acciones: [
              botonEditar(codigo.codigo, (boton) => dialogoCodigo(boton, codigo)),
              botonEstado(codigo.activo, codigo.codigo, nomenclador.aviso, (activar) => cambiarEstadoCodigo(contexto.api, codigo.id, activar), recargarNomenclador),
            ],
          },
        );
        // El código en monoespaciada, como en el mockup.
        fila.querySelector('.item-nombre')?.replaceChildren(span('cob-codigo', codigo.codigo), ` · ${codigo.descripcion}`);
        return fila;
      }),
    );
    contenedor.append(lista);
  }

  function dialogoCodigo(origen: HTMLButtonElement, codigo: CodigoNomenclador | null): void {
    nomenclador.aviso.textContent = '';
    const campoCodigo = campoTexto('Código', 'codigo', 20, codigo?.codigo ?? '');
    campoCodigo.control.classList.add('cob-codigo');
    const prestacion = crearCampo('Prestación', crearSelector('prestacionId', [{ valor: '', texto: 'Sin prestación' }]));
    prestacion.control.classList.add('control');
    prestacion.control.disabled = true;
    const descripcion = campoTexto('Descripción', 'descripcion', 200, codigo?.descripcion ?? '');
    agregarAyuda(descripcion, 'Hasta 200 caracteres.');
    const dialogo = abrirDialogoDesde(origen, {
      titulo: codigo === null ? 'Agregar código' : 'Editar código',
      ...(codigo === null ? {} : { resumen: `${codigo.codigo} · ${codigo.descripcion}` }),
      contenido: [filaDeCampos(campoCodigo, prestacion), descripcion.contenedor],
      textoDescartar: 'Cancelar',
      textoPrincipal: 'Guardar',
    });
    // Las prestaciones se piden recién al abrir el diálogo (design D5); hasta tenerlas no se puede guardar.
    dialogo.enviar.disabled = true;
    cargarPrestaciones(contexto.api).then(
      (prestaciones) => {
        const actual = codigo?.prestacion?.id ?? null;
        cambiarOpciones(prestacion.control, [
          { valor: '', texto: 'Sin prestación' },
          ...prestaciones
            .filter((p) => p.activo || p.id === actual)
            .map((p) => ({ valor: String(p.id), texto: p.activo ? p.nombre : `${p.nombre} (inactiva)` })),
        ]);
        prestacion.control.value = actual === null ? '' : String(actual);
        prestacion.control.disabled = false;
        dialogo.enviar.disabled = false;
      },
      (causa: unknown) => {
        if (!contexto.sesionVencida(causa)) {
          dialogo.general.textContent = mensajeDeError(causa);
        }
      },
    );
    dialogo.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      dialogo.general.textContent = '';
      const valido = marcarErrores([
        [campoCodigo, validarCodigo(campoCodigo.control.value)],
        [prestacion, ''],
        [descripcion, validarDescripcion(descripcion.control.value)],
      ]);
      if (!valido) {
        return;
      }
      const elegida = prestacion.control.value;
      const datos = {
        codigo: campoCodigo.control.value,
        descripcion: descripcion.control.value,
        prestacionId: elegida === '' ? null : Number(elegida),
      };
      void guardando(dialogo.enviar, () => guardarCodigo(contexto.api, codigo?.id ?? null, datos)).then(
        (ok) => {
          if (ok) {
            dialogo.cerrar();
            recargarNomenclador();
          }
        },
        (causa: unknown) =>
          mostrarRechazo(causa, {
            general: dialogo.general,
            campos: {
              codigo: { campo: campoCodigo, mensaje: 'Revisá el código.' },
              descripcion: { campo: descripcion, mensaje: 'Revisá la descripción.' },
              prestacionId: { campo: prestacion, mensaje: 'Elegí otra prestación.' },
            },
            sesionVencida: (c) => contexto.sesionVencida(c),
          }),
      );
    });
  }
}
