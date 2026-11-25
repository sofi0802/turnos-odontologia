import type { ClienteApi } from '../shared/api/cliente.ts';
import { crearAviso, crearBoton } from '../shared/ui/campos.ts';
import { abrirDialogo } from '../shared/ui/dialogo.ts';
import { crearIcono } from '../shared/ui/iconos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import {
  obtenerConsentimiento,
  registrarConsentimiento,
  type EstadoConsentimiento,
  type MedioConsentimiento,
} from './api.ts';

/**
 * Línea de consentimiento debajo del paciente elegido con el buscador (C-17, design D5): "Acepta mensajes" o "No
 * acepta mensajes", con "Registrar consentimiento" o "Revocar". Solo para `ADMIN` y `RECEPCIONISTA` (`visible`). El
 * diálogo del medio se abre encima del formulario, que no se cierra ni pierde lo cargado. El buscador de C-36 no
 * cambia: el consentimiento se pide aparte.
 */

export interface OpcionesLineaConsentimiento {
  readonly api: ClienteApi;
  /** `false` para el odontólogo: la línea no se muestra ni pide nada. */
  readonly visible: boolean;
  /** Si el error es de sesión vencida, lleva al ingreso y devuelve `true`. */
  sesionVencida(causa: unknown): boolean;
  /** Avisa que la recepción registró o revocó el consentimiento del paciente elegido. */
  alCambiar?(estado: EstadoConsentimiento): void;
}

export interface LineaConsentimiento {
  readonly contenedor: HTMLElement;
  /** Muestra el consentimiento del paciente elegido; `null` lo oculta. */
  mostrarPaciente(pacienteId: number | null): void;
  /** Lleva el foco a la acción de registrar (ante `SIN_CONSENTIMIENTO`). */
  destacarRegistro(): void;
}

export const MENSAJE_SIN_MEDIO = 'Elegí cómo lo pidió el paciente.';

const MEDIOS: ReadonlyArray<readonly [MedioConsentimiento, string, 'telefono' | 'persona']> = [
  ['TELEFONO', 'Por teléfono', 'telefono'],
  ['PRESENCIAL', 'En persona', 'persona'],
];

let contador = 0;

export function crearLineaConsentimiento(opciones: OpcionesLineaConsentimiento): LineaConsentimiento {
  const contenedor = document.createElement('div');
  contenedor.className = 'consentimiento-linea';
  contenedor.hidden = true;

  let pacienteActual: number | null = null;
  let consulta = 0;
  let accion: HTMLButtonElement | null = null;
  let destacar = false;

  function dibujar(estado: EstadoConsentimiento): void {
    const linea = document.createElement('p');
    linea.className = `mensajes-estado ${estado.otorgado ? 'si' : 'no'}`;
    linea.append(
      crearIcono(estado.otorgado ? 'mensaje' : 'mensaje-no'),
      estado.otorgado ? 'Acepta mensajes' : 'No acepta mensajes',
    );
    accion = crearBoton(estado.otorgado ? 'Revocar' : 'Registrar consentimiento');
    accion.className = destacar && !estado.otorgado ? 'btn btn-chico btn-principal' : 'btn btn-chico';
    const origen = accion;
    accion.addEventListener('click', () => abrirMedio(!estado.otorgado, origen));
    contenedor.replaceChildren(linea, accion);
  }

  function mostrarPaciente(pacienteId: number | null): void {
    consulta += 1;
    pacienteActual = pacienteId;
    destacar = false;
    accion = null;
    contenedor.replaceChildren();
    contenedor.hidden = !opciones.visible || pacienteId === null;
    if (contenedor.hidden || pacienteId === null) {
      return;
    }
    const esta = consulta;
    contenedor.setAttribute('aria-busy', 'true');
    obtenerConsentimiento(opciones.api, pacienteId).then(
      (estado) => {
        if (esta !== consulta) {
          return;
        }
        contenedor.removeAttribute('aria-busy');
        dibujar(estado);
      },
      (causa: unknown) => {
        if (esta !== consulta) {
          return;
        }
        contenedor.removeAttribute('aria-busy');
        if (opciones.sesionVencida(causa)) {
          return;
        }
        const error = crearAviso('alert');
        error.textContent = 'No pudimos ver si el paciente acepta mensajes.';
        contenedor.replaceChildren(error);
      },
    );
  }

  function abrirMedio(otorgar: boolean, origen: HTMLButtonElement): void {
    const pacienteId = pacienteActual;
    if (pacienteId === null) {
      return;
    }
    contador += 1;
    const nombre = `medio-consentimiento-${contador}`;
    const grupo = document.createElement('fieldset');
    grupo.className = 'medio-opciones';
    const leyenda = document.createElement('legend');
    leyenda.textContent = '¿Cómo lo pidió?';
    grupo.append(leyenda);
    const radios: HTMLInputElement[] = [];
    for (const [valor, texto, icono] of MEDIOS) {
      const etiqueta = document.createElement('label');
      etiqueta.className = 'medio-opcion';
      const radio = document.createElement('input');
      radio.type = 'radio';
      radio.name = nombre;
      radio.value = valor;
      radios.push(radio);
      etiqueta.append(radio, crearIcono(icono), texto);
      grupo.append(etiqueta);
    }
    const errorMedio = document.createElement('p');
    errorMedio.className = 'campo-error';
    errorMedio.id = `${nombre}-error`;
    grupo.setAttribute('aria-describedby', errorMedio.id);

    const { formulario, general, principal, cerrar } = abrirDialogo({
      titulo: otorgar ? 'Registrar consentimiento' : 'Revocar consentimiento',
      resumen: otorgar
        ? 'El paciente va a recibir recordatorios, confirmaciones y ofertas de la lista de espera, sin datos de ' +
          'salud. Registralo solo si el paciente lo pidió.'
        : 'El paciente deja de recibir recordatorios, confirmaciones y ofertas de la lista de espera. Registralo solo ' +
          'si el paciente lo pidió.',
      contenido: [grupo, errorMedio],
      textoDescartar: 'Volver',
      textoPrincipal: otorgar ? 'Registrar' : 'Revocar',
      peligro: !otorgar,
      alCerrar: () => {
        if (origen.isConnected) {
          origen.focus();
        } else {
          accion?.focus();
        }
      },
    });
    formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      const medio = MEDIOS[radios.findIndex((r) => r.checked)]?.[0];
      if (medio === undefined) {
        errorMedio.textContent = MENSAJE_SIN_MEDIO;
        radios[0]?.focus();
        return;
      }
      errorMedio.textContent = '';
      general.textContent = '';
      principal.disabled = true;
      registrarConsentimiento(opciones.api, pacienteId, otorgar, medio).then(
        (estado) => {
          if (pacienteActual === pacienteId) {
            destacar = false;
            dibujar(estado);
            opciones.alCambiar?.(estado);
          }
          cerrar();
        },
        (causa: unknown) => {
          principal.disabled = false;
          if (opciones.sesionVencida(causa)) {
            cerrar();
            return;
          }
          general.textContent = mensajeDeError(causa);
        },
      );
    });
  }

  function destacarRegistro(): void {
    destacar = true;
    if (accion !== null && accion.textContent === 'Registrar consentimiento') {
      accion.classList.add('btn-principal');
      accion.focus();
    }
  }

  return { contenedor, mostrarPaciente, destacarRegistro };
}
