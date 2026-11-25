import type { ClienteApi } from '../shared/api/cliente.ts';
import { crearAviso, crearBoton } from '../shared/ui/campos.ts';
import { crearCargando } from '../shared/ui/estado-vacio.ts';
import { obtenerMiCobertura } from './api.ts';
import { crearTarjetaCobertura } from './seccion.ts';

/**
 * "Mi cobertura" del paciente en "Mi cuenta" (C-49, design D3): solo lectura, sobre `GET /api/v1/paciente/cobertura`,
 * que identifica al paciente por la sesión. Nada sale de la pantalla (RN-OS-07).
 */

export interface OpcionesMiCobertura {
  readonly api: ClienteApi;
  /** Texto del error; `''` si ya se llevó al ingreso por la sesión vencida. */
  mensajeDeError(causa: unknown): string;
  vigente(): boolean;
}

let contador = 0;

export function crearMiCobertura(opciones: OpcionesMiCobertura): HTMLElement {
  contador += 1;
  const seccion = document.createElement('section');
  seccion.className = 'panel mi-cobertura';
  const titulo = document.createElement('h2');
  titulo.id = `mi-cobertura-${contador}`;
  titulo.textContent = 'Mi cobertura';
  seccion.setAttribute('aria-labelledby', titulo.id);
  const cuerpo = document.createElement('div');
  cuerpo.className = 'cob-seccion';
  seccion.append(titulo, cuerpo);

  let carga = 0;

  function cargar(): void {
    carga += 1;
    const esta = carga;
    seccion.setAttribute('aria-busy', 'true');
    cuerpo.replaceChildren(crearCargando('Cargando tu cobertura…'));
    obtenerMiCobertura(opciones.api).then(
      (cobertura) => {
        if (esta !== carga || !opciones.vigente()) {
          return;
        }
        seccion.removeAttribute('aria-busy');
        const ayuda = document.createElement('p');
        ayuda.className = 'ayuda';
        ayuda.textContent =
          cobertura.afiliacion === null
            ? 'Si tenés obra social o prepaga, avisá en la recepción del consultorio.'
            : 'Si cambiaste de obra social o de plan, avisá en la recepción del consultorio.';
        cuerpo.replaceChildren(
          crearTarjetaCobertura(cobertura, {
            rotulo: null,
            particular: 'Te atendés como particular.',
            detalleParticular: 'No tenés una obra social o prepaga cargada para hoy.',
          }),
          ayuda,
        );
      },
      (causa: unknown) => {
        if (esta !== carga || !opciones.vigente()) {
          return;
        }
        seccion.removeAttribute('aria-busy');
        const texto = opciones.mensajeDeError(causa);
        if (texto === '') {
          cuerpo.replaceChildren();
          return;
        }
        const error = crearAviso('alert');
        error.textContent = texto;
        const reintentar = crearBoton('Reintentar');
        reintentar.className = 'btn';
        reintentar.addEventListener('click', cargar);
        const caja = document.createElement('div');
        caja.append(reintentar);
        cuerpo.replaceChildren(error, caja);
      },
    );
  }

  cargar();
  return seccion;
}
