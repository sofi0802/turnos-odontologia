import type { ClienteApi } from '../shared/api/cliente.ts';
import { crearAviso, crearBoton } from '../shared/ui/campos.ts';
import { crearCargando, crearEstadoVacio } from '../shared/ui/estado-vacio.ts';
import { listarMisPresupuestos } from './api.ts';
import { crearTarjetaPresupuesto } from './vista.ts';

/**
 * "Mis presupuestos" del paciente en "Mi cuenta" (C-50, PA-27): solo lectura, sobre `GET /api/v1/paciente/presupuestos`,
 * que identifica al paciente por la sesión. Muestra la pieza y la cara de sus propios ítems. Nada sale de la pantalla.
 */

export interface OpcionesMisPresupuestos {
  readonly api: ClienteApi;
  /** Texto del error; `''` si ya se llevó al ingreso por la sesión vencida. */
  mensajeDeError(causa: unknown): string;
  vigente(): boolean;
}

let contador = 0;

export function crearMisPresupuestos(opciones: OpcionesMisPresupuestos): HTMLElement {
  contador += 1;
  const seccion = document.createElement('section');
  seccion.className = 'panel mis-presupuestos';
  const titulo = document.createElement('h2');
  titulo.id = `mis-presupuestos-${contador}`;
  titulo.textContent = 'Mis presupuestos';
  seccion.setAttribute('aria-labelledby', titulo.id);
  const cuerpo = document.createElement('div');
  cuerpo.className = 'pre-seccion';
  seccion.append(titulo, cuerpo);

  let carga = 0;

  function cargar(): void {
    carga += 1;
    const esta = carga;
    seccion.setAttribute('aria-busy', 'true');
    cuerpo.replaceChildren(crearCargando('Cargando tus presupuestos…'));
    listarMisPresupuestos(opciones.api).then(
      (lista) => {
        if (esta !== carga || !opciones.vigente()) {
          return;
        }
        seccion.removeAttribute('aria-busy');
        if (lista.length === 0) {
          cuerpo.replaceChildren(crearEstadoVacio({ texto: 'Todavía no tenés presupuestos.', chico: true }));
          return;
        }
        const ul = document.createElement('ul');
        ul.className = 'pre-lista';
        ul.setAttribute('aria-label', 'Mis presupuestos');
        ul.append(...lista.map((p) => crearTarjetaPresupuesto(p, { mostrarPieza: true })));
        const ayuda = document.createElement('p');
        ayuda.className = 'ayuda';
        ayuda.textContent = 'Para aceptar un presupuesto o pedir uno nuevo, hablalo con tu odontólogo en el consultorio.';
        cuerpo.replaceChildren(ul, ayuda);
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
