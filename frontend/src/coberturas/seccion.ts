import type { ClienteApi } from '../shared/api/cliente.ts';
import { ErrorNegocio } from '../shared/api/errores.ts';
import { hoy } from '../shared/fecha/zona.ts';
import { crearAviso, crearBoton, crearCampo, crearEntrada, crearSelector, cambiarOpciones, type Campo, type Opcion } from '../shared/ui/campos.ts';
import type { DialogoAbierto } from '../shared/ui/dialogo.ts';
import { crearCargando } from '../shared/ui/estado-vacio.ts';
import { crearIcono } from '../shared/ui/iconos.ts';
import {
  afiliar,
  cerrarAfiliacion,
  corregirAfiliacion,
  listarAfiliaciones,
  listarObrasSociales,
  obtenerCobertura,
  type Afiliacion,
  type Cobertura,
  type ObraSocial,
} from './api.ts';
import {
  estadoAfiliacion,
  formatearFecha,
  MENSAJE_FIN_ANTERIOR,
  MENSAJE_NUMERO_INVALIDO,
  normalizarNumero,
  textoVigencia,
  validarDesde,
  validarFin,
  validarNumero,
  vigenteDesdePorDefecto,
  type EstadoAfiliacion,
} from './modelo.ts';
import { abrirDialogoDesde, agregarAyuda, filaDeCampos, marcarErrores, mostrarRechazo, type DestinoDeCampo } from './ui.ts';

/**
 * Sección "Cobertura" de un paciente (C-49, design D7): la cobertura de hoy y el historial de afiliaciones, con
 * "Afiliar", "Corregir" y "Cerrar afiliación" para quien puede editarla. La usan la ficha (`#/pacientes`) y la página
 * "Coberturas". Carga aparte, con su error y "Reintentar"; nada sale de la pantalla (RN-OS-07).
 */

export const MENSAJE_SOLO_LECTURA_COBERTURA = 'Solo lectura: la cobertura la cargan la recepción y la administración.';
export const MENSAJE_AFILIACION_GUARDADA = 'Afiliación guardada.';
export const MENSAJE_AFILIACION_CORREGIDA = 'Afiliación corregida.';
export const MENSAJE_AFILIACION_CERRADA = 'Afiliación cerrada.';

export interface OpcionesSeccionCobertura {
  readonly pacienteId: number;
  readonly nombrePaciente: string;
  /** `ADMIN` y `RECEPCIONISTA`: afiliar, corregir y cerrar. `ODONTOLOGO`: solo lectura. */
  readonly editable: boolean;
  readonly api: ClienteApi;
  /** Texto del error; `''` si ya se llevó al ingreso por la sesión vencida (como las demás secciones de la ficha). */
  mensajeDeError(causa: unknown): string;
  /** `false` si la página ya cambió: las respuestas que llegan tarde no dibujan nada. */
  vigente(): boolean;
}

export interface SeccionCobertura {
  readonly contenedor: HTMLElement;
  destruir(): void;
}

let contador = 0;

const CLASE_ETIQUETA: Readonly<Record<EstadoAfiliacion, string>> = {
  Vigente: 'etiqueta etiqueta-vigente',
  Próxima: 'etiqueta etiqueta-proxima',
  Finalizada: 'etiqueta etiqueta-finalizada',
};

function parrafo(clase: string, ...contenido: (string | Node)[]): HTMLParagraphElement {
  const p = document.createElement('p');
  p.className = clase;
  p.append(...contenido);
  return p;
}

function numero(valor: string): HTMLSpanElement {
  const span = document.createElement('span');
  span.className = 'cob-numero';
  span.textContent = valor;
  return span;
}

function etiqueta(estado: EstadoAfiliacion): HTMLSpanElement {
  const span = document.createElement('span');
  span.className = CLASE_ETIQUETA[estado];
  span.textContent = estado;
  return span;
}

/** "Obra Social Ejemplo (OSE)", o solo el nombre si no tiene sigla. */
export function nombreConSigla(obra: { readonly nombre: string; readonly sigla: string | null }): string {
  return obra.sigla === null ? obra.nombre : `${obra.nombre} (${obra.sigla})`;
}

/**
 * Tarjeta de la cobertura de hoy, compartida con "Mi cobertura" del paciente. `rotulo` y `textoParticular` cambian
 * según quién la mira.
 */
export function crearTarjetaCobertura(
  cobertura: Cobertura,
  textos: { readonly rotulo: string | null; readonly particular: string; readonly detalleParticular: string },
): HTMLDivElement {
  const tarjeta = document.createElement('div');
  const icono = document.createElement('span');
  icono.className = 'cob-tarjeta-icono';
  icono.append(crearIcono('credencial'));
  const datos = document.createElement('div');
  datos.className = 'cob-tarjeta-datos';
  if (textos.rotulo !== null) {
    datos.append(parrafo('cob-tarjeta-rotulo', textos.rotulo));
  }
  const afiliacion = cobertura.afiliacion;
  if (afiliacion === null) {
    tarjeta.className = 'cob-tarjeta cob-tarjeta-particular';
    datos.append(parrafo('cob-tarjeta-titulo', textos.particular), parrafo('cob-vigencia', textos.detalleParticular));
    tarjeta.append(icono, datos);
    return tarjeta;
  }
  tarjeta.className = 'cob-tarjeta';
  datos.append(
    parrafo('cob-tarjeta-titulo', nombreConSigla(afiliacion.obraSocial)),
    parrafo('cob-tarjeta-plan', `${afiliacion.plan.nombre} · N.º de afiliado `, numero(afiliacion.numeroAfiliado)),
    parrafo('cob-vigencia', textoVigencia(afiliacion.vigenteDesde, afiliacion.vigenteHasta)),
  );
  tarjeta.append(icono, datos);
  return tarjeta;
}

function campoFecha(etiqueta: string, nombre: string, valor: string): Campo<HTMLInputElement> {
  const entrada = crearEntrada('date', nombre);
  entrada.classList.add('control');
  entrada.value = valor;
  return crearCampo(etiqueta, entrada);
}

export function crearSeccionCobertura(opciones: OpcionesSeccionCobertura): SeccionCobertura {
  contador += 1;
  const idTitulo = `cobertura-titulo-${contador}`;
  const seccion = document.createElement('section');
  seccion.className = 'panel ficha-seccion cob-seccion';
  seccion.setAttribute('aria-labelledby', idTitulo);

  const titulo = document.createElement('h2');
  titulo.id = idTitulo;
  titulo.textContent = 'Cobertura';
  const cabecera = document.createElement('div');
  cabecera.className = 'ficha-seccion-cabecera';
  cabecera.append(titulo);
  const afiliarBoton = crearBoton('Afiliar');
  afiliarBoton.className = 'btn btn-principal';
  afiliarBoton.hidden = true;
  if (opciones.editable) {
    cabecera.append(afiliarBoton);
  }
  const exito = crearAviso('status');
  const aviso = crearAviso('alert');
  const cuerpo = document.createElement('div');
  cuerpo.className = 'cob-seccion';
  seccion.append(cabecera, exito, aviso);
  if (!opciones.editable) {
    seccion.append(parrafo('aviso aviso-info', MENSAJE_SOLO_LECTURA_COBERTURA));
  }
  seccion.append(cuerpo);

  let destruida = false;
  let carga = 0;
  let afiliaciones: Afiliacion[] = [];
  let catalogo: Promise<ObraSocial[]> | null = null;
  let dialogoActual: DialogoAbierto | null = null;

  const vigente = (): boolean => !destruida && opciones.vigente();
  /** El mensaje del error, o `null` si es de sesión vencida (ya se llevó al ingreso). */
  const mensaje = (causa: unknown): string | null => {
    const texto = opciones.mensajeDeError(causa);
    return texto === '' ? null : texto;
  };
  const sesionVencida = (causa: unknown): boolean => mensaje(causa) === null;

  afiliarBoton.addEventListener('click', () => dialogoAfiliar());

  function cargar(): void {
    carga += 1;
    const esta = carga;
    afiliarBoton.hidden = true;
    seccion.setAttribute('aria-busy', 'true');
    cuerpo.replaceChildren(crearCargando('Cargando la cobertura…'));
    // Sin `AbortController` en el cliente: un contador descarta las respuestas viejas (design D7).
    Promise.all([obtenerCobertura(opciones.api, opciones.pacienteId), listarAfiliaciones(opciones.api, opciones.pacienteId)]).then(
      ([cobertura, lista]) => {
        if (esta !== carga || !vigente()) {
          return;
        }
        seccion.removeAttribute('aria-busy');
        afiliaciones = lista;
        dibujar(cobertura);
      },
      (causa: unknown) => {
        if (esta !== carga || !vigente()) {
          return;
        }
        seccion.removeAttribute('aria-busy');
        const texto = mensaje(causa);
        if (texto === null) {
          cuerpo.replaceChildren();
          return;
        }
        const error = crearAviso('alert');
        error.textContent = texto;
        const reintentar = crearBoton('Reintentar');
        reintentar.className = 'btn';
        reintentar.addEventListener('click', () => {
          aviso.textContent = '';
          cargar();
        });
        const caja = document.createElement('div');
        caja.append(reintentar);
        cuerpo.replaceChildren(error, caja);
      },
    );
  }

  function dibujar(cobertura: Cobertura): void {
    const tarjeta = crearTarjetaCobertura(cobertura, {
      rotulo: 'Cobertura de hoy',
      particular: 'Se atiende como particular',
      detalleParticular: 'No tiene una afiliación vigente hoy. Dar un turno no exige cobertura.',
    });
    if (cobertura.afiliacion !== null) {
      tarjeta.append(etiqueta('Vigente'));
    }
    const subtitulo = document.createElement('h3');
    subtitulo.className = 'cob-historial-titulo';
    subtitulo.textContent = 'Historial de afiliaciones';
    cuerpo.replaceChildren(tarjeta, subtitulo, dibujarHistorial());
    afiliarBoton.hidden = false;
  }

  function dibujarHistorial(): HTMLElement {
    if (afiliaciones.length === 0) {
      return parrafo('ayuda', 'Sin afiliaciones registradas.');
    }
    const hoyEnElConsultorio = hoy();
    const lista = document.createElement('ul');
    lista.className = 'cob-historial';
    lista.setAttribute('aria-label', `Historial de afiliaciones de ${opciones.nombrePaciente}`);
    for (const afiliacion of afiliaciones) {
      const estado = estadoAfiliacion(afiliacion, hoyEnElConsultorio);
      const item = document.createElement('li');
      item.className = estado === 'Finalizada' ? 'cob-afiliacion cob-afiliacion-finalizada' : 'cob-afiliacion';
      const nombrePlan = document.createElement('span');
      nombrePlan.textContent = afiliacion.plan.nombre;
      const datos = document.createElement('div');
      datos.append(
        parrafo('cob-afiliacion-titulo', nombrePlan, etiqueta(estado)),
        parrafo(
          'cob-afiliacion-meta',
          `${afiliacion.obraSocial.nombre} · N.º `,
          numero(afiliacion.numeroAfiliado),
          ` · ${textoVigencia(afiliacion.vigenteDesde, afiliacion.vigenteHasta)}`,
        ),
      );
      item.append(datos);
      if (opciones.editable) {
        const acciones = document.createElement('div');
        acciones.className = 'cob-afiliacion-acciones';
        const corregir = crearBoton('Corregir');
        corregir.className = 'btn btn-chico';
        corregir.setAttribute('aria-label', `Corregir la afiliación a ${afiliacion.plan.nombre}`);
        corregir.addEventListener('click', () => dialogoCorregir(corregir, afiliacion));
        acciones.append(corregir);
        if (afiliacion.vigenteHasta === null) {
          const cerrar = crearBoton('Cerrar afiliación');
          cerrar.className = 'btn btn-chico';
          cerrar.setAttribute('aria-label', `Cerrar la afiliación a ${afiliacion.plan.nombre}`);
          cerrar.addEventListener('click', () => dialogoCerrar(cerrar, afiliacion));
          acciones.append(cerrar);
        }
        item.append(acciones);
      }
      lista.append(item);
    }
    return lista;
  }

  /** El catálogo se pide una vez por sección, al abrir el primer diálogo (design D7). */
  function obtenerCatalogo(): Promise<ObraSocial[]> {
    catalogo ??= listarObrasSociales(opciones.api).catch((causa: unknown) => {
      catalogo = null;
      throw causa;
    });
    return catalogo;
  }

  function terminar(dialogo: DialogoAbierto, texto: string): void {
    dialogo.cerrar();
    aviso.textContent = '';
    exito.textContent = texto;
    cargar();
  }

  /** Obra social y plan, con solo los activos más (al corregir) el plan actual, marcado "(inactivo)". */
  function selectoresDePlan(actual: Afiliacion | null): {
    obra: Campo<HTMLSelectElement>;
    plan: Campo<HTMLSelectElement>;
    planElegido(): number | null;
    /** Pide el catálogo; hasta tenerlo, el diálogo no se puede enviar. */
    cargarEn(dialogo: DialogoAbierto): void;
  } {
    const obra = crearCampo('Obra social', crearSelector('obraSocialId', [{ valor: '', texto: 'Cargando…' }]));
    const plan = crearCampo('Plan', crearSelector('planId', [{ valor: '', texto: 'Elegí primero la obra social' }]));
    obra.control.classList.add('control');
    plan.control.classList.add('control');
    obra.control.disabled = true;
    plan.control.disabled = true;
    let obras: ObraSocial[] = [];

    function opcionesDePlanes(): void {
      const elegida = obras.find((o) => String(o.id) === obra.control.value);
      if (elegida === undefined) {
        cambiarOpciones(plan.control, [{ valor: '', texto: 'Elegí primero la obra social' }]);
        plan.control.value = '';
        plan.control.disabled = true;
        return;
      }
      const planes: Opcion[] = elegida.planes
        .filter((p) => p.activo || p.id === actual?.plan.id)
        .map((p) => ({ valor: String(p.id), texto: p.activo ? p.nombre : `${p.nombre} (inactivo)` }));
      cambiarOpciones(plan.control, [{ valor: '', texto: 'Elegí un plan' }, ...planes]);
      plan.control.disabled = false;
    }
    obra.control.addEventListener('change', opcionesDePlanes);

    function cargarEn(dialogo: DialogoAbierto): void {
    dialogo.enviar.disabled = true;
    obtenerCatalogo().then(
      (lista) => {
        const deLaActual = actual === null ? undefined : lista.find((o) => o.planes.some((p) => p.id === actual.plan.id));
        obras = lista.filter((o) => o.activa || o === deLaActual);
        cambiarOpciones(obra.control, [
          { valor: '', texto: 'Elegí una obra social' },
          ...obras.map((o) => ({ valor: String(o.id), texto: o.activa ? nombreConSigla(o) : `${nombreConSigla(o)} (inactiva)` })),
        ]);
        obra.control.value = deLaActual === undefined ? '' : String(deLaActual.id);
        obra.control.disabled = false;
        opcionesDePlanes();
        if (actual !== null) {
          plan.control.value = String(actual.plan.id);
        }
        dialogo.enviar.disabled = false;
        // El selector estaba deshabilitado al abrir el diálogo: el foco va a él cuando ya se puede usar.
        if (!dialogo.dialogo.contains(document.activeElement) || document.activeElement === dialogo.enviar) {
          obra.control.focus();
        }
      },
      (causa: unknown) => {
        const texto = mensaje(causa);
        if (texto !== null) {
          dialogo.general.textContent = texto;
        }
      },
    );
    }

    return {
      obra,
      plan,
      planElegido: () => (plan.control.value === '' ? null : Number(plan.control.value)),
      cargarEn,
    };
  }

  function campoNumero(valor: string): Campo<HTMLInputElement> {
    const entrada = crearEntrada('text', 'numeroAfiliado');
    entrada.classList.add('control', 'cob-numero');
    entrada.maxLength = 30;
    entrada.autocomplete = 'off';
    entrada.value = valor;
    const campo = crearCampo('N.º de afiliado', entrada);
    agregarAyuda(campo, 'Hasta 30 letras, números, guiones, barras o puntos.');
    return campo;
  }

  function enviarConRechazo(
    dialogo: DialogoAbierto,
    pedido: () => Promise<void>,
    textoExito: string,
    campos: Readonly<Record<string, DestinoDeCampo>>,
  ): void {
    dialogo.general.textContent = '';
    if (dialogo.enviar.disabled) {
      return;
    }
    dialogo.enviar.disabled = true;
    pedido().then(
      () => {
        dialogo.enviar.disabled = false;
        if (vigente()) {
          terminar(dialogo, textoExito);
        }
      },
      (causa: unknown) => {
        dialogo.enviar.disabled = false;
        mostrarRechazo(causa, {
          general: dialogo.general,
          campos,
          codigosDeCampo: { AFILIADO_DUPLICADO: 'numeroAfiliado' },
          sesionVencida,
        });
      },
    );
  }

  function abrir(origen: HTMLElement, datos: Parameters<typeof abrirDialogoDesde>[1]): DialogoAbierto {
    exito.textContent = '';
    aviso.textContent = '';
    const dialogo = abrirDialogoDesde(origen, {
      ...datos,
      alCerrar: () => {
        if (dialogoActual === dialogo) {
          dialogoActual = null;
        }
      },
    });
    dialogoActual = dialogo;
    return dialogo;
  }

  function dialogoAfiliar(): void {
    const desdePropuesto = vigenteDesdePorDefecto(afiliaciones, hoy());
    const numeroAfiliado = campoNumero('');
    const desde = campoFecha('Vigente desde', 'vigenteDesde', desdePropuesto);
    const hasta = campoFecha('Vigente hasta (opcional)', 'vigenteHasta', '');
    const { obra, plan, planElegido, cargarEn } = selectoresDePlan(null);
    const dialogo = abrir(afiliarBoton, {
      titulo: 'Afiliar',
      resumen: `${opciones.nombrePaciente}. Una sola cobertura por fecha: para cambiarla, cerrá antes la vigente.`,
      contenido: [filaDeCampos(obra, plan), numeroAfiliado.contenedor, filaDeCampos(desde, hasta)],
      textoDescartar: 'Cancelar',
      textoPrincipal: 'Afiliar',
    });
    cargarEn(dialogo);

    dialogo.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      const planId = planElegido();
      const valido = marcarErrores([
        [obra, obra.control.value === '' ? 'Elegí la obra social.' : ''],
        [plan, planId === null ? 'Elegí el plan.' : ''],
        [numeroAfiliado, validarNumero(numeroAfiliado.control.value)],
        [desde, validarDesde(desde.control.value)],
        [hasta, validarFin(desde.control.value, hasta.control.value)],
      ]);
      if (!valido || planId === null) {
        return;
      }
      const datos = {
        planId,
        numeroAfiliado: normalizarNumero(numeroAfiliado.control.value),
        vigenteDesde: desde.control.value,
        vigenteHasta: hasta.control.value === '' ? null : hasta.control.value,
      };
      enviarConRechazo(dialogo, () => afiliar(opciones.api, opciones.pacienteId, datos), MENSAJE_AFILIACION_GUARDADA, {
        planId: { campo: plan, mensaje: 'Elegí otro plan.' },
        numeroAfiliado: { campo: numeroAfiliado, mensaje: MENSAJE_NUMERO_INVALIDO },
        vigenteDesde: { campo: desde, mensaje: 'Revisá la fecha de inicio.' },
        vigenteHasta: { campo: hasta, mensaje: MENSAJE_FIN_ANTERIOR },
      });
    });
  }

  function dialogoCorregir(origen: HTMLButtonElement, afiliacion: Afiliacion): void {
    const numeroAfiliado = campoNumero(afiliacion.numeroAfiliado);
    const desde = campoFecha('Vigente desde', 'vigenteDesde', afiliacion.vigenteDesde);
    const { obra, plan, planElegido, cargarEn } = selectoresDePlan(afiliacion);
    const dialogo = abrir(origen, {
      titulo: 'Corregir afiliación',
      resumen: `${opciones.nombrePaciente} · ${afiliacion.plan.nombre} desde ${formatearFecha(afiliacion.vigenteDesde)}. Para cambiar la fecha de fin, cerrá la afiliación.`,
      contenido: [filaDeCampos(obra, plan), numeroAfiliado.contenedor, desde.contenedor],
      textoDescartar: 'Cancelar',
      textoPrincipal: 'Guardar',
    });
    cargarEn(dialogo);

    dialogo.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      const planId = planElegido();
      const valido = marcarErrores([
        [obra, obra.control.value === '' ? 'Elegí la obra social.' : ''],
        [plan, planId === null ? 'Elegí el plan.' : ''],
        [numeroAfiliado, validarNumero(numeroAfiliado.control.value)],
        [desde, validarDesde(desde.control.value)],
      ]);
      if (!valido || planId === null) {
        return;
      }
      const datos = { planId, numeroAfiliado: normalizarNumero(numeroAfiliado.control.value), vigenteDesde: desde.control.value };
      enviarConRechazo(
        dialogo,
        () => corregirAfiliacion(opciones.api, opciones.pacienteId, afiliacion.id, datos),
        MENSAJE_AFILIACION_CORREGIDA,
        {
          planId: { campo: plan, mensaje: 'Elegí otro plan.' },
          numeroAfiliado: { campo: numeroAfiliado, mensaje: MENSAJE_NUMERO_INVALIDO },
          vigenteDesde: { campo: desde, mensaje: 'Revisá la fecha de inicio.' },
        },
      );
    });
  }

  function dialogoCerrar(origen: HTMLButtonElement, afiliacion: Afiliacion): void {
    const hoyEnElConsultorio = hoy();
    const hasta = campoFecha(
      'Vigente hasta',
      'vigenteHasta',
      hoyEnElConsultorio < afiliacion.vigenteDesde ? afiliacion.vigenteDesde : hoyEnElConsultorio,
    );
    hasta.control.min = afiliacion.vigenteDesde;
    agregarAyuda(hasta, 'Último día con esta cobertura. Para cambiar de cobertura, después afiliá desde el día siguiente.');
    const dialogo = abrir(origen, {
      titulo: 'Cerrar afiliación',
      resumen: `${opciones.nombrePaciente} · ${afiliacion.obraSocial.nombre}, ${afiliacion.plan.nombre}, desde ${formatearFecha(afiliacion.vigenteDesde)}. Queda en el historial; no se borra.`,
      contenido: [hasta.contenedor],
      textoDescartar: 'Cancelar',
      textoPrincipal: 'Cerrar afiliación',
    });

    dialogo.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      const valor = hasta.control.value;
      const valido = marcarErrores([[hasta, valor === '' ? 'Completá la fecha de fin.' : validarFin(afiliacion.vigenteDesde, valor)]]);
      if (!valido) {
        return;
      }
      dialogo.general.textContent = '';
      if (dialogo.enviar.disabled) {
        return;
      }
      dialogo.enviar.disabled = true;
      cerrarAfiliacion(opciones.api, opciones.pacienteId, afiliacion.id, valor).then(
        () => {
          dialogo.enviar.disabled = false;
          if (vigente()) {
            terminar(dialogo, MENSAJE_AFILIACION_CERRADA);
          }
        },
        (causa: unknown) => {
          dialogo.enviar.disabled = false;
          if (causa instanceof ErrorNegocio && causa.codigo === 'AFILIACION_CERRADA') {
            // Otra persona ya la cerró: se cierra el diálogo, se recarga y se avisa.
            dialogo.cerrar();
            exito.textContent = '';
            aviso.textContent = mensaje(causa) ?? '';
            if (vigente()) {
              cargar();
            }
            return;
          }
          mostrarRechazo(causa, {
            general: dialogo.general,
            campos: { vigenteHasta: { campo: hasta, mensaje: MENSAJE_FIN_ANTERIOR } },
            sesionVencida,
          });
        },
      );
    });
  }

  cargar();

  return {
    contenedor: seccion,
    destruir() {
      destruida = true;
      dialogoActual?.cerrar();
      dialogoActual = null;
    },
  };
}
