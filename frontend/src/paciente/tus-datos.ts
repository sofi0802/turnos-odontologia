import type { ClienteApi } from '../shared/api/cliente.ts';
import { crearAviso, crearBoton } from '../shared/ui/campos.ts';
import { crearCargando } from '../shared/ui/estado-vacio.ts';

/**
 * "Tus datos" en "Mi cuenta" del paciente (C-17): solo lectura, sobre `GET /api/v1/paciente/perfil`, que identifica
 * al paciente por la sesión. Un error acá no oculta las demás secciones.
 */

export interface PerfilPaciente {
  readonly nombre: string;
  readonly apellido: string;
  readonly dni: string;
  readonly email: string;
  readonly telefono: string | null;
}

export interface OpcionesSeccionPaciente {
  readonly api: ClienteApi;
  /** Texto del error; `''` si ya se llevó al ingreso por la sesión vencida. */
  mensajeDeError(causa: unknown): string;
  vigente(): boolean;
}

export const MENSAJE_FALLA_PERFIL = 'No pudimos cargar tus datos.';

function texto(cuerpo: object, clave: string): string {
  const valor = Reflect.get(cuerpo, clave);
  return typeof valor === 'string' ? valor : '';
}

export async function obtenerPerfil(api: ClienteApi): Promise<PerfilPaciente> {
  const cuerpo = (await api.obtener<unknown>('/paciente/perfil')) ?? {};
  const datos = typeof cuerpo === 'object' && cuerpo !== null ? cuerpo : {};
  const telefono = texto(datos, 'telefono').trim();
  return {
    nombre: texto(datos, 'nombre'),
    apellido: texto(datos, 'apellido'),
    dni: texto(datos, 'dni'),
    email: texto(datos, 'email'),
    telefono: telefono === '' ? null : telefono,
  };
}

let contador = 0;

function dato(etiqueta: string, valor: string, vacio = false): HTMLElement {
  const fila = document.createElement('div');
  const dt = document.createElement('dt');
  dt.textContent = etiqueta;
  const dd = document.createElement('dd');
  dd.textContent = valor;
  if (vacio) {
    dd.className = 'sin-dato';
  }
  fila.append(dt, dd);
  return fila;
}

export function crearTusDatos(opciones: OpcionesSeccionPaciente): HTMLElement {
  contador += 1;
  const seccion = document.createElement('section');
  seccion.className = 'panel tus-datos';
  const titulo = document.createElement('h2');
  titulo.id = `tus-datos-${contador}`;
  titulo.textContent = 'Tus datos';
  seccion.setAttribute('aria-labelledby', titulo.id);
  const cuerpo = document.createElement('div');
  cuerpo.className = 'pila';
  seccion.append(titulo, cuerpo);

  let carga = 0;

  function cargar(): void {
    carga += 1;
    const esta = carga;
    seccion.setAttribute('aria-busy', 'true');
    cuerpo.replaceChildren(crearCargando('Cargando tus datos…'));
    obtenerPerfil(opciones.api).then(
      (perfil) => {
        if (esta !== carga || !opciones.vigente()) {
          return;
        }
        seccion.removeAttribute('aria-busy');
        const lista = document.createElement('dl');
        lista.className = 'cuenta-datos';
        lista.append(
          dato('Nombre', `${perfil.nombre} ${perfil.apellido}`.trim()),
          dato('DNI', perfil.dni),
          dato('Email', perfil.email),
          dato('Teléfono', perfil.telefono ?? 'Sin teléfono', perfil.telefono === null),
        );
        const ayuda = document.createElement('p');
        ayuda.className = 'cuenta-ayuda';
        ayuda.textContent = 'Si algún dato está mal, avisale a la recepción del consultorio.';
        cuerpo.replaceChildren(lista, ayuda);
      },
      (causa: unknown) => {
        if (esta !== carga || !opciones.vigente()) {
          return;
        }
        seccion.removeAttribute('aria-busy');
        if (opciones.mensajeDeError(causa) === '') {
          cuerpo.replaceChildren();
          return;
        }
        const error = crearAviso('alert');
        error.textContent = MENSAJE_FALLA_PERFIL;
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
