import { borrarReservaPendiente } from './paciente/reserva.ts';
import { paginaAgenda } from './pages/agenda.ts';
import { paginaCambiarContrasena } from './pages/cambiar-contrasena.ts';
import { paginaConfiguracion } from './pages/configuracion.ts';
import { paginaConfirmar } from './pages/confirmar.ts';
import { paginaInicio } from './pages/inicio.ts';
import { paginaListaEspera } from './pages/lista-espera.ts';
import { paginaMiCuenta } from './pages/mi-cuenta.ts';
import { paginaMisTurnos } from './pages/mis-turnos.ts';
import { paginaOferta } from './pages/oferta.ts';
import { paginaPacientes } from './pages/pacientes.ts';
import { paginaReportes } from './pages/reportes.ts';
import { paginaCaja } from './pages/caja.ts';
import { crearTitulo, type ContextoPagina, type Pagina } from './pages/pagina.ts';
import { paginaReserva } from './pages/reserva.ts';
import { api as apiPorDefecto, type ClienteApi } from './shared/api/cliente.ts';
import { ErrorPermiso } from './shared/api/errores.ts';
import {
  cerrarSesion,
  consultarSesion,
  debeCambiarContrasena,
  esPaciente,
  esPersonal,
  SIN_SESION,
  type Sesion,
} from './shared/sesion/sesion.ts';
import { mensajeDeError } from './shared/ui/mensajes.ts';
import { crearLayout } from './shared/ui/layout.ts';
import { NOMBRE_APLICACION } from './shared/ui/marca.ts';
import { crearRouter, type Router } from './shared/ui/router.ts';

export const PAGINAS: readonly Pagina[] = [
  paginaInicio,
  paginaAgenda,
  paginaListaEspera,
  paginaPacientes,
  paginaReportes,
  paginaCaja,
  paginaConfiguracion,
  paginaCambiarContrasena,
  paginaReserva,
  paginaMisTurnos,
  paginaMiCuenta,
  paginaConfirmar,
  paginaOferta,
];

export const MENSAJE_SOLO_PERSONAL = 'Esta sección es solo para el personal del consultorio.';
export const MENSAJE_SOLO_PACIENTES = 'Esta sección es para pacientes.';
/** Aviso de una página con `roles` para quien no tiene uno de esos roles (C-44: Pacientes). */
export const MENSAJE_SOLO_ODONTOLOGOS_Y_ADMIN = 'Esta sección es solo para odontólogos y administradores.';

/** Direcciones viejas que siguen sirviendo: `#/ingreso` era el ingreso del personal (C-09). */
const REDIRECCIONES: Readonly<Record<string, string>> = { ingreso: paginaMiCuenta.ruta };

/** Código del 403 que el servidor responde mientras la contraseña temporal no se cambió (C-41). */
const CAMBIO_PENDIENTE = 'CAMBIO_DE_CONTRASENA_PENDIENTE';

/**
 * Envuelve la API: si un pedido responde 403 CAMBIO_DE_CONTRASENA_PENDIENTE, avisa con `alPendiente` (la app lleva al
 * cambio de contraseña) y deja pasar el error a la pantalla que hizo el pedido (spec frontend-cuentas-personal).
 */
function conCambioPendiente(base: ClienteApi, alPendiente: () => void): ClienteApi {
  async function vigilar<T>(pedido: Promise<T>): Promise<T> {
    try {
      return await pedido;
    } catch (causa: unknown) {
      if (causa instanceof ErrorPermiso && causa.codigo === CAMBIO_PENDIENTE) {
        alPendiente();
      }
      throw causa;
    }
  }
  return {
    solicitar: <T>(metodo: Parameters<ClienteApi['solicitar']>[0], ruta: string, cuerpo?: unknown) =>
      vigilar(base.solicitar<T>(metodo, ruta, cuerpo)),
    obtener: <T>(ruta: string) => vigilar(base.obtener<T>(ruta)),
    crear: <T>(ruta: string, cuerpo?: unknown) => vigilar(base.crear<T>(ruta, cuerpo)),
    actualizar: <T>(ruta: string, cuerpo?: unknown) => vigilar(base.actualizar<T>(ruta, cuerpo)),
    borrar: <T>(ruta: string) => vigilar(base.borrar<T>(ruta)),
    enviarFormulario: <T>(ruta: string, datos: FormData) => vigilar(base.enviarFormulario<T>(ruta, datos)),
    obtenerArchivo: (ruta: string) => vigilar(base.obtenerArchivo(ruta)),
  };
}

/** Arma la aplicación dentro de `raiz` y muestra la página de la dirección actual. */
export function iniciarApp(raiz: HTMLElement, apiBase: ClienteApi = apiPorDefecto): Router {
  const api = conCambioPendiente(apiBase, () => irA(paginaCambiarContrasena.ruta));
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
        irA(paginaInicio.ruta);
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
      irA(ruta, nuevoAviso) {
        avisoPendiente = nuevoAviso ?? null;
        irA(ruta);
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
    document.title = pagina === paginaInicio ? NOMBRE_APLICACION : `${pagina.titulo} · ${NOMBRE_APLICACION}`;
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
      // Sin sesión, pacientes y personal ingresan en Mi cuenta (design D3 de ingreso-unico-mi-cuenta).
      history.replaceState(history.state, '', `#/${paginaMiCuenta.ruta}`);
      mostrarSesion(null);
      dibujar(paginaMiCuenta, SIN_SESION);
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
    if (pagina.roles !== undefined && (sesion.rol === null || !pagina.roles.includes(sesion.rol))) {
      // No se llama a `render`, así la página no pide ningún dato (C-44, design D2).
      mostrarAviso(pagina, pagina.avisoSinRol ?? MENSAJE_SOLO_ODONTOLOGOS_Y_ADMIN);
      return;
    }
    if (debeCambiarContrasena(sesion) && pagina !== paginaCambiarContrasena) {
      // Con la contraseña temporal pendiente, toda página privada lleva al cambio, sin pedir sus datos (C-41).
      history.replaceState(history.state, '', `#/${paginaCambiarContrasena.ruta}`);
      dibujar(paginaCambiarContrasena, sesion);
      return;
    }
    dibujar(pagina, sesion);
  }

  const router = crearRouter(
    PAGINAS,
    paginaInicio,
    (pagina) => {
      void mostrar(pagina);
    },
    REDIRECCIONES,
  );
  router.actualizar();
  return router;
}
