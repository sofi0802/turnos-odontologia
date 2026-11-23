import { consultarDisponibilidad, type Horario } from '../paciente/disponibilidad.ts';
import { crearGrillaHorarios } from '../paciente/horarios.ts';
import type { ClienteApi } from '../shared/api/cliente.ts';
import type { Fecha } from '../shared/fecha/zona.ts';

/** Lo que el diálogo "Reprogramar turno" muestra en el lugar de los horarios. */
export type EstadoHorarios =
  | { readonly fase: 'sin-consulta' }
  | { readonly fase: 'cargando' }
  | { readonly fase: 'listo'; readonly horarios: readonly Horario[] }
  | { readonly fase: 'error' };

export interface BuscadorHorarios {
  /** Pide los horarios del profesional ese día y descarta el horario elegido. Sin fecha no consulta. */
  buscar(fecha: Fecha, profesionalId: number): void;
  /** Guarda el horario elegido; no avisa, porque la grilla ya muestra el botón elegido. */
  elegir(horario: Horario): void;
  elegido(): Horario | null;
  estado(): EstadoHorarios;
}

/**
 * Estado de los horarios de la reprogramación (design D4), separado de la vista. Pide la disponibilidad
 * con `consultarDisponibilidad` y avisa con `alCambiar` cada vez que cambia el estado de la consulta.
 * Una respuesta de una consulta anterior a la última se descarta, sea un éxito o un error.
 */
export function crearBuscadorHorarios(api: ClienteApi, prestacionId: number, alCambiar: () => void): BuscadorHorarios {
  let estado: EstadoHorarios = { fase: 'sin-consulta' };
  let elegido: Horario | null = null;
  let consultaActual = 0;

  function cambiar(nuevo: EstadoHorarios): void {
    estado = nuevo;
    alCambiar();
  }

  return {
    buscar(fecha, profesionalId) {
      consultaActual += 1;
      const consulta = consultaActual;
      elegido = null;
      if (fecha === '') {
        cambiar({ fase: 'sin-consulta' });
        return;
      }
      cambiar({ fase: 'cargando' });
      consultarDisponibilidad(api, { prestacionId, fecha, profesionalId })
        .then((horarios) => {
          if (consulta === consultaActual) {
            cambiar({ fase: 'listo', horarios });
          }
        })
        .catch(() => {
          if (consulta === consultaActual) {
            cambiar({ fase: 'error' });
          }
        });
    },
    elegir(horario) {
      elegido = horario;
    },
    elegido: () => elegido,
    estado: () => estado,
  };
}

/** El lugar de los horarios en el diálogo: un `<fieldset>` con la grilla o el estado de la consulta. */
export interface ZonaHorarios {
  readonly contenedor: HTMLFieldSetElement;
  mostrar(estado: EstadoHorarios, elegido: Horario | null, alElegir: (horario: Horario) => void): void;
  /** Mensaje de campo bajo la grilla, por ejemplo "Elegí un horario."; vacío lo borra. */
  mostrarError(mensaje: string): void;
}

export const TEXTO_PISAN_EL_TURNO =
  'Los horarios que se superponen con este turno no aparecen. Para esos, cancelá el turno y creá uno nuevo.';

function parrafo(clase: string, rol: 'status' | 'alert' | null, texto: string): HTMLParagraphElement {
  const elemento = document.createElement('p');
  elemento.className = clase;
  if (rol !== null) {
    elemento.setAttribute('role', rol);
  }
  elemento.textContent = texto;
  return elemento;
}

/** Textos de la zona de horarios; por defecto, los de la reprogramación (C-33). */
export interface TextosZonaHorarios {
  /** Ayuda fija bajo la grilla; `null` no la muestra. */
  readonly ayuda: string | null;
  /** Lo que se ve antes de la primera consulta; vacío no muestra nada. */
  readonly sinConsulta: string;
  readonly sinHorarios: string;
  readonly error: string;
}

const TEXTOS_REPROGRAMACION: TextosZonaHorarios = {
  ayuda: TEXTO_PISAN_EL_TURNO,
  sinConsulta: '',
  sinHorarios: 'No hay horarios libres ese día.',
  error: 'No pudimos cargar los horarios. Cambiá la fecha para volver a intentar.',
};

/** Arma la zona de horarios de un diálogo (mockups de C-33; C-40 la reusa con sus textos). */
export function crearZonaHorarios(textos: Partial<TextosZonaHorarios> = {}): ZonaHorarios {
  const { ayuda, sinConsulta, sinHorarios, error: textoError } = { ...TEXTOS_REPROGRAMACION, ...textos };
  const contenedor = document.createElement('fieldset');
  contenedor.className = 'campo';
  const leyenda = document.createElement('legend');
  leyenda.textContent = 'Horario';
  const cuerpo = document.createElement('div');
  const error = document.createElement('p');
  error.className = 'campo-error';
  error.setAttribute('role', 'alert');
  contenedor.append(leyenda, cuerpo, error);
  if (ayuda !== null) {
    contenedor.append(parrafo('ayuda', null, ayuda));
  }

  return {
    contenedor,
    mostrar(estado, elegido, alElegir) {
      switch (estado.fase) {
        case 'sin-consulta':
          cuerpo.replaceChildren(...(sinConsulta === '' ? [] : [parrafo('ayuda', null, sinConsulta)]));
          break;
        case 'cargando':
          cuerpo.replaceChildren(parrafo('buscando', 'status', 'Buscando horarios…'));
          break;
        case 'error':
          cuerpo.replaceChildren(parrafo('aviso aviso-error', 'alert', textoError));
          break;
        case 'listo':
          cuerpo.replaceChildren(
            estado.horarios.length === 0
              ? parrafo('sin-horarios', 'status', sinHorarios)
              : crearGrillaHorarios(estado.horarios, elegido?.inicio ?? null, alElegir),
          );
          break;
      }
    },
    mostrarError(mensaje) {
      error.textContent = mensaje;
    },
  };
}
