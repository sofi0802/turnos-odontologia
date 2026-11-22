import { paginaAgenda } from './pages/agenda.ts';
import { paginaConfiguracion } from './pages/configuracion.ts';
import { paginaIngreso } from './pages/ingreso.ts';
import { crearTitulo, type ContextoPagina, type Pagina } from './pages/pagina.ts';
import { paginaReserva } from './pages/reserva.ts';
import { api as apiPorDefecto, type ClienteApi } from './shared/api/cliente.ts';
import { cerrarSesion, consultarSesion, esPersonal, SIN_SESION, type Sesion } from './shared/sesion/sesion.ts';
import { mensajeDeError } from './shared/ui/mensajes.ts';
import { crearLayout } from './shared/ui/layout.ts';
import { crearRouter, type Router } from './shared/ui/router.ts';

export const PAGINAS: readonly Pagina[] = [paginaAgenda, paginaReserva, paginaConfiguracion, paginaIngreso];

export const MENSAJE_SOLO_PERSONAL = 'Esta sección es solo para el personal del consultorio.';

/** Arma la aplicación dentro de `raiz` y muestra la página de la dirección actual. */
export function iniciarApp(raiz: HTMLElement, api: ClienteApi = apiPorDefecto): Router {
  let navegacion = 0;
  let avisoPendiente: string | null = null;

  const layout = crearLayout(
    raiz,
    PAGINAS.filter((p) => p.enMenu !== false),
    {
      alSalir() {
        void cerrarSesion(api).then(() => {
          layout.mostrarSesion(null);
          irA(paginaIngreso.ruta);
        });
      },
    },
  );

  function irA(ruta: string): void {
    const destino = `#/${ruta}`;
    if (window.location.hash === destino) {
      router.actualizar();
    } else {
      window.location.hash = destino;
    }
  }

  function contexto(sesion: Sesion): ContextoPagina {
    const aviso = avisoPendiente;
    avisoPendiente = null;
    return {
      api,
      sesion,
      aviso,
      irA,
      irAlIngreso(nuevoAviso) {
        avisoPendiente = nuevoAviso ?? null;
        layout.mostrarSesion(null);
        irA(paginaIngreso.ruta);
      },
    };
  }

  function dibujar(pagina: Pagina, sesion: Sesion): void {
    pagina.render(layout.contenido, contexto(sesion));
    layout.mostrar(pagina);
    document.title = `${pagina.titulo} · Turnos odontología`;
  }

  async function mostrar(pagina: Pagina): Promise<void> {
    navegacion += 1;
    const esta = navegacion;

    if (pagina.privada !== true) {
      // Las páginas públicas se dibujan enseguida; la sesión solo actualiza el encabezado.
      dibujar(pagina, SIN_SESION);
      const sesion = await consultarSesion(api).catch(() => SIN_SESION);
      if (esta === navegacion) {
        layout.mostrarSesion(sesion.autenticado ? sesion : null);
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
        const aviso = document.createElement('p');
        aviso.setAttribute('role', 'alert');
        aviso.textContent = mensajeDeError(error);
        layout.contenido.replaceChildren(crearTitulo(pagina.titulo), aviso);
      }
      return;
    }
    if (esta !== navegacion) {
      return;
    }
    layout.contenido.removeAttribute('aria-busy');

    if (!sesion.autenticado) {
      history.replaceState(history.state, '', `#/${paginaIngreso.ruta}`);
      layout.mostrarSesion(null);
      dibujar(paginaIngreso, SIN_SESION);
      return;
    }
    layout.mostrarSesion(sesion);
    if (!esPersonal(sesion.rol)) {
      const aviso = document.createElement('p');
      aviso.setAttribute('role', 'alert');
      aviso.textContent = MENSAJE_SOLO_PERSONAL;
      layout.contenido.replaceChildren(crearTitulo(pagina.titulo), aviso);
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
