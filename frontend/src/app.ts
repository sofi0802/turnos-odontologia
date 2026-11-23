import { borrarReservaPendiente } from './paciente/reserva.ts';
import { paginaAgenda } from './pages/agenda.ts';
import { paginaConfiguracion } from './pages/configuracion.ts';
import { paginaConfirmar } from './pages/confirmar.ts';
import { paginaIngreso } from './pages/ingreso.ts';
import { paginaMiCuenta } from './pages/mi-cuenta.ts';
import { paginaMisTurnos } from './pages/mis-turnos.ts';
import { crearTitulo, type ContextoPagina, type Pagina } from './pages/pagina.ts';
import { paginaReserva } from './pages/reserva.ts';
import { api as apiPorDefecto, type ClienteApi } from './shared/api/cliente.ts';
import {
  cerrarSesion,
  consultarSesion,
  esPaciente,
  esPersonal,
  SIN_SESION,
  type Sesion,
} from './shared/sesion/sesion.ts';
import { mensajeDeError } from './shared/ui/mensajes.ts';
import { crearLayout } from './shared/ui/layout.ts';
import { crearRouter, type Router } from './shared/ui/router.ts';

export const PAGINAS: readonly Pagina[] = [
  paginaAgenda,
  paginaConfiguracion,
  paginaReserva,
  paginaMisTurnos,
  paginaIngreso,
  paginaMiCuenta,
  paginaConfirmar,
];

export const MENSAJE_SOLO_PERSONAL = 'Esta sección es solo para el personal del consultorio.';
export const MENSAJE_SOLO_PACIENTES = 'Esta sección es para pacientes.';

/** Arma la aplicación dentro de `raiz` y muestra la página de la dirección actual. */
export function iniciarApp(raiz: HTMLElement, api: ClienteApi = apiPorDefecto): Router {
  let navegacion = 0;
  let avisoPendiente: string | null = null;
  let sesionMostrada: Sesion | null = null;

  function mostrarSesion(sesion: Sesion | null): void {
    sesionMostrada = sesion;
    layout.mostrarSesion(sesion);
  }

  const layout = crearLayout(raiz, PAGINAS, {
    alSalir() {
      const eraPaciente = sesionMostrada !== null && esPaciente(sesionMostrada.rol);
      if (eraPaciente) {
        borrarReservaPendiente();
      }
      void cerrarSesion(api).then(() => {
        mostrarSesion(null);
        irA(eraPaciente ? paginaReserva.ruta : paginaIngreso.ruta);
      });
    },
  });

  function irA(ruta: string): void {
    const destino = `#/${ruta}`;
    if (window.location.hash === destino) {
      router.actualizar();
    } else {
      window.location.hash = destino;
    }
  }

  function contexto(sesion: Sesion, sesionLista: Promise<Sesion>): ContextoPagina {
    const aviso = avisoPendiente;
    avisoPendiente = null;
    return {
      api,
      sesion,
      sesionLista,
      aviso,
      irA,
      irAlIngreso(nuevoAviso) {
        avisoPendiente = nuevoAviso ?? null;
        mostrarSesion(null);
        irA(paginaIngreso.ruta);
      },
      irAMiCuenta(nuevoAviso) {
        avisoPendiente = nuevoAviso ?? null;
        irA(paginaMiCuenta.ruta);
      },
    };
  }

  function dibujar(pagina: Pagina, sesion: Sesion, sesionLista: Promise<Sesion> = Promise.resolve(sesion)): void {
    pagina.render(layout.contenido, contexto(sesion, sesionLista));
    layout.mostrar(pagina);
    document.title = `${pagina.titulo} · Turnos odontología`;
  }

  function mostrarAviso(pagina: Pagina, mensaje: string): void {
    const aviso = document.createElement('p');
    aviso.setAttribute('role', 'alert');
    aviso.className = 'aviso aviso-error';
    aviso.textContent = mensaje;
    layout.contenido.replaceChildren(crearTitulo(pagina.titulo), aviso);
  }

  async function mostrar(pagina: Pagina): Promise<void> {
    navegacion += 1;
    const esta = navegacion;
    const acceso = pagina.acceso ?? 'publica';

    if (acceso === 'publica') {
      // Las páginas públicas se dibujan enseguida; la sesión actualiza el encabezado
      // y la reciben las páginas que la necesitan (`sesionLista`).
      const sesionLista = consultarSesion(api).catch(() => SIN_SESION);
      dibujar(pagina, SIN_SESION, sesionLista);
      const sesion = await sesionLista;
      if (esta === navegacion) {
        mostrarSesion(sesion.autenticado ? sesion : null);
      }
      return;
    }

    layout.mostrar(pagina);
    layout.contenido.replaceChildren();
    layout.contenido.setAttribute('aria-busy', 'true');
    let sesion: Sesion;
    try {
      sesion = await consultarSesion(api);
    } catch (error) {
      if (esta === navegacion) {
        layout.contenido.removeAttribute('aria-busy');
        mostrarAviso(pagina, mensajeDeError(error));
      }
      return;
    }
    if (esta !== navegacion) {
      return;
    }
    layout.contenido.removeAttribute('aria-busy');

    if (!sesion.autenticado) {
      // Sin sesión, cada grupo va a su propio ingreso (design D2 de C-12).
      const ingreso = acceso === 'paciente' ? paginaMiCuenta : paginaIngreso;
      history.replaceState(history.state, '', `#/${ingreso.ruta}`);
      mostrarSesion(null);
      dibujar(ingreso, SIN_SESION);
      return;
    }
    mostrarSesion(sesion);
    if (acceso === 'personal' && !esPersonal(sesion.rol)) {
      mostrarAviso(pagina, MENSAJE_SOLO_PERSONAL);
      return;
    }
    if (acceso === 'paciente' && !esPaciente(sesion.rol)) {
      mostrarAviso(pagina, MENSAJE_SOLO_PACIENTES);
      return;
    }
    dibujar(pagina, sesion);
  }

  const router = crearRouter(PAGINAS, paginaAgenda, (pagina) => {
    void mostrar(pagina);
  });
  router.actualizar();
  return router;
}
