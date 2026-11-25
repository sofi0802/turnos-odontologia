import { formatearFecha } from '../coberturas/modelo.ts';
import { abrirDialogoDesde } from '../coberturas/ui.ts';
import type { ClienteApi } from '../shared/api/cliente.ts';
import { ErrorNegocio } from '../shared/api/errores.ts';
import { crearAviso, crearBoton } from '../shared/ui/campos.ts';
import type { DialogoAbierto } from '../shared/ui/dialogo.ts';
import { crearCargando, crearEstadoVacio } from '../shared/ui/estado-vacio.ts';
import { aceptarPresupuesto, listarPresupuestos, rechazarPresupuesto, type Presupuesto } from './api.ts';
import { abrirArmado } from './armado.ts';
import { estadoPresupuesto, formatearPesos } from './modelo.ts';
import { crearTarjetaPresupuesto } from './vista.ts';

/**
 * Sección "Presupuestos" de un paciente (C-50, design D5): la lista con su detalle y, para el odontólogo, "Nuevo
 * presupuesto", "Aceptar" y "Rechazar". La usan la ficha (`odontologo` o `lectura`) y "Coberturas" (`lectura` o
 * `recepcion`). Carga aparte, con su error y "Reintentar"; nada sale de la pantalla (RN-PR-07).
 */

export type ModoPresupuestos = 'odontologo' | 'lectura' | 'recepcion';

export const MENSAJE_SOLO_LECTURA_PRESUPUESTOS = 'Solo lectura: los presupuestos los arma el odontólogo.';
export const AVISO_VENCIDO = 'Venció: ya no se puede aceptar. Para renovarlo, emití uno nuevo.';

export interface OpcionesSeccionPresupuestos {
  readonly pacienteId: number;
  readonly nombrePaciente: string;
  readonly modo: ModoPresupuestos;
  readonly api: ClienteApi;
  /** Texto del error; `''` si ya se llevó al ingreso por la sesión vencida. */
  mensajeDeError(causa: unknown): string;
  vigente(): boolean;
}

export interface SeccionPresupuestos {
  readonly contenedor: HTMLElement;
  destruir(): void;
}

let contador = 0;

/** Códigos con los que el diálogo de confirmación se cierra, se recarga la lista y se avisa (design D7). */
const CODIGOS_DE_RECARGA: ReadonlySet<string> = new Set(['PRESUPUESTO_VENCIDO', 'PRESUPUESTO_CERRADO']);

export function crearSeccionPresupuestos(opciones: OpcionesSeccionPresupuestos): SeccionPresupuestos {
  contador += 1;
  const odontologo = opciones.modo === 'odontologo';
  const seccion = document.createElement('section');
  seccion.className = 'panel ficha-seccion pre-seccion';
  const titulo = document.createElement('h2');
  titulo.id = `presupuestos-titulo-${contador}`;
  titulo.textContent = 'Presupuestos';
  seccion.setAttribute('aria-labelledby', titulo.id);
  const cabecera = document.createElement('div');
  cabecera.className = 'ficha-seccion-cabecera';
  cabecera.append(titulo);
  const nuevo = crearBoton('Nuevo presupuesto');
  nuevo.className = 'btn btn-principal';
  nuevo.hidden = true;
  if (odontologo) {
    cabecera.append(nuevo);
  }
  const exito = crearAviso('status');
  const aviso = crearAviso('alert');
  const cuerpo = document.createElement('div');
  cuerpo.className = 'pre-seccion';
  seccion.append(cabecera, exito, aviso);
  if (!odontologo) {
    const ayuda = document.createElement('p');
    ayuda.className = 'aviso aviso-info';
    ayuda.textContent = MENSAJE_SOLO_LECTURA_PRESUPUESTOS;
    seccion.append(ayuda);
  }
  seccion.append(cuerpo);

  let destruida = false;
  let carga = 0;
  let abrirDetalle: number | null = null;
  let dialogoActual: DialogoAbierto | null = null;
  const vigente = (): boolean => !destruida && opciones.vigente();
  const mensaje = (causa: unknown): string | null => {
    const texto = opciones.mensajeDeError(causa);
    return texto === '' ? null : texto;
  };

  nuevo.addEventListener('click', () => {
    exito.textContent = '';
    aviso.textContent = '';
    dialogoActual = abrirArmado({
      origen: nuevo,
      pacienteId: opciones.pacienteId,
      nombrePaciente: opciones.nombrePaciente,
      api: opciones.api,
      mensajeDeError: opciones.mensajeDeError,
      vigente,
      alEmitir: (emitido) => {
        dialogoActual = null;
        exito.textContent = 'Presupuesto emitido.';
        abrirDetalle = emitido?.id ?? null;
        cargar();
      },
    });
  });

  function cargar(): void {
    carga += 1;
    const esta = carga;
    nuevo.hidden = true;
    seccion.setAttribute('aria-busy', 'true');
    cuerpo.replaceChildren(crearCargando('Cargando los presupuestos…'));
    listarPresupuestos(opciones.api, opciones.pacienteId).then(
      (lista) => {
        if (esta !== carga || !vigente()) {
          return;
        }
        seccion.removeAttribute('aria-busy');
        dibujar(lista);
        nuevo.hidden = false;
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

  function dibujar(lista: readonly Presupuesto[]): void {
    if (lista.length === 0) {
      cuerpo.replaceChildren(crearEstadoVacio({ texto: 'Este paciente no tiene presupuestos.', chico: true }));
      return;
    }
    const ul = document.createElement('ul');
    ul.className = 'pre-lista';
    ul.setAttribute('aria-label', `Presupuestos de ${opciones.nombrePaciente}`);
    ul.append(
      ...lista.map((presupuesto) =>
        crearTarjetaPresupuesto(presupuesto, {
          mostrarPieza: opciones.modo !== 'recepcion',
          acciones: odontologo ? accionesDe(presupuesto) : [],
          ...(odontologo ? { avisoVencido: AVISO_VENCIDO } : {}),
          detalleAbierto: presupuesto.id === abrirDetalle,
        }),
      ),
    );
    abrirDetalle = null;
    cuerpo.replaceChildren(ul);
  }

  function accionesDe(presupuesto: Presupuesto): HTMLButtonElement[] {
    const estado = estadoPresupuesto(presupuesto);
    if (estado === 'Aceptado' || estado === 'Rechazado') {
      return [];
    }
    const rechazar = crearBoton('Rechazar');
    rechazar.className = 'btn btn-chico btn-peligro';
    rechazar.addEventListener('click', () => confirmar(rechazar, presupuesto, 'rechazar'));
    if (estado === 'Vencido') {
      return [rechazar];
    }
    const aceptar = crearBoton('Aceptar');
    aceptar.className = 'btn btn-chico btn-principal';
    aceptar.addEventListener('click', () => confirmar(aceptar, presupuesto, 'aceptar'));
    return [rechazar, aceptar];
  }

  function confirmar(origen: HTMLButtonElement, presupuesto: Presupuesto, accion: 'aceptar' | 'rechazar'): void {
    exito.textContent = '';
    aviso.textContent = '';
    const aceptando = accion === 'aceptar';
    const vencido = presupuesto.vencido ? ' (vencido)' : '';
    const dialogo = abrirDialogoDesde(origen, {
      titulo: aceptando ? '¿Aceptar el presupuesto?' : '¿Rechazar el presupuesto?',
      resumen: `${opciones.nombrePaciente} · Presupuesto del ${formatearFecha(presupuesto.fecha)}${vencido} · Copago ${formatearPesos(presupuesto.totales.copago)}. ${aceptando ? 'No se puede deshacer.' : 'Queda en el historial; no se puede deshacer.'}`,
      textoDescartar: 'Cancelar',
      textoPrincipal: aceptando ? 'Aceptar presupuesto' : 'Rechazar presupuesto',
      ...(aceptando ? {} : { peligro: true }),
    });
    dialogoActual = dialogo;
    dialogo.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      if (dialogo.enviar.disabled) {
        return;
      }
      dialogo.enviar.disabled = true;
      dialogo.general.textContent = '';
      const pedido = aceptando ? aceptarPresupuesto : rechazarPresupuesto;
      pedido(opciones.api, opciones.pacienteId, presupuesto.id).then(
        () => {
          dialogo.enviar.disabled = false;
          dialogo.cerrar();
          if (vigente()) {
            exito.textContent = aceptando ? 'Presupuesto aceptado.' : 'Presupuesto rechazado.';
            cargar();
          }
        },
        (causa: unknown) => {
          dialogo.enviar.disabled = false;
          const texto = mensaje(causa);
          if (texto === null) {
            dialogo.cerrar();
            return;
          }
          if (causa instanceof ErrorNegocio && CODIGOS_DE_RECARGA.has(causa.codigo)) {
            dialogo.cerrar();
            if (vigente()) {
              aviso.textContent = texto;
              cargar();
            }
            return;
          }
          dialogo.general.textContent = texto;
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
