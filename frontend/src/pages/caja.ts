import { crearBuscadorPacientes } from '../agenda/buscador-pacientes.ts';
import { cajaAbierta, consultarCuenta, consultarHistorial, consultarPendientes, type Caja, type Pendientes } from '../caja/api.ts';
import { leerImporte } from '../caja/dinero.ts';
import { abrirAnulacion, abrirCierre, abrirCobro, type ControlCaja } from '../caja/formularios.ts';
import { dibujarCobros, dibujarCuenta, dibujarEstado, dibujarHistorial, dibujarPendientes } from '../caja/vista.ts';
import { ErrorSesion } from '../shared/api/errores.ts';
import { hoy, sumarDias } from '../shared/fecha/zona.ts';
import { crearAviso, crearBoton, crearCampo, crearEntrada } from '../shared/ui/campos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { MENSAJE_SESION_TERMINADA } from './agenda.ts';
import { crearTitulo, type ContextoPagina, type Pagina } from './pagina.ts';
import { MENSAJE_SOLO_ADMINISTRACION_Y_RECEPCION } from './reportes.ts';

export const MENSAJE_MONTO_INICIAL = 'Escribí el monto inicial, aunque sea 0.';
const DIAS_HISTORIAL = 30;

function seccion(clase: string): HTMLElement {
  const elemento = document.createElement('section');
  elemento.className = `panel ${clase}`;
  return elemento;
}

/** Pantalla Caja (US-207, C-22): la recepción abre, cobra y cierra; el admin consulta y anula. */
function dibujar(contenedor: HTMLElement, contexto: ContextoPagina): void {
  const esRecepcion = contexto.sesion.rol === 'RECEPCIONISTA';
  const esAdmin = contexto.sesion.rol === 'ADMIN';
  const exito = crearAviso('status');
  const error = crearAviso('alert');
  const grilla = document.createElement('div');
  grilla.className = 'caja-grilla';
  const estado = seccion('caja-estado');
  const pendientesSeccion = seccion('caja-pendientes');
  const cobrosSeccion = seccion('caja-cobros');
  const cuentaSeccion = seccion('caja-cuenta');
  const historialSeccion = seccion('caja-historial');
  grilla.append(estado, pendientesSeccion, cobrosSeccion, cuentaSeccion, historialSeccion);
  contenedor.replaceChildren(crearTitulo('Caja'), exito, error, grilla);

  let caja: Caja | null = null;
  let consulta = 0;

  function sesionVencida(causa: unknown): boolean {
    if (causa instanceof ErrorSesion) {
      contexto.irAMiCuenta(MENSAJE_SESION_TERMINADA);
      return true;
    }
    return false;
  }

  const control: ControlCaja = {
    api: contexto.api,
    mostrarExito(mensaje) {
      error.textContent = '';
      exito.textContent = mensaje;
    },
    recargar: () => void cargar(),
    sesionVencida,
  };

  // Apertura de la caja (solo la recepción): monto inicial en efectivo.
  const entradaMonto = crearEntrada('text', 'montoInicial');
  entradaMonto.inputMode = 'decimal';
  entradaMonto.autocomplete = 'off';
  const monto = crearCampo('Monto inicial en efectivo', entradaMonto);
  const apertura = document.createElement('form');
  apertura.className = 'caja-apertura';
  apertura.noValidate = true;
  const abrir = crearBoton('Abrir caja', 'submit');
  abrir.className = 'btn btn-principal';
  apertura.append(monto.contenedor, abrir);
  apertura.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const leido = leerImporte(entradaMonto.value);
    monto.mostrarError(leido === null ? MENSAJE_MONTO_INICIAL : '');
    if (leido === null) {
      return;
    }
    abrir.disabled = true;
    contexto.api
      .crear<unknown>('/cajas', { montoInicial: leido })
      .then(() => {
        entradaMonto.value = '';
        control.mostrarExito('Caja abierta.');
      })
      .catch((causa: unknown) => {
        if (!sesionVencida(causa)) {
          exito.textContent = '';
          error.textContent = mensajeDeError(causa);
        }
      })
      .finally(() => {
        abrir.disabled = false;
        void cargar();
      });
  });

  // Cuenta corriente con el buscador de pacientes de C-36.
  const detalleCuenta = document.createElement('div');
  detalleCuenta.className = 'caja-cuenta-detalle';
  detalleCuenta.setAttribute('aria-live', 'polite');
  const buscador = crearBuscadorPacientes(contexto.api, {
    sesionVencida,
    alElegir(paciente) {
      detalleCuenta.replaceChildren();
      consultarCuenta(contexto.api, paciente.id)
        .then((cuenta) => dibujarCuenta(detalleCuenta, cuenta))
        .catch((causa: unknown) => {
          if (!sesionVencida(causa)) {
            const aviso = crearAviso('alert');
            aviso.textContent = mensajeDeError(causa);
            detalleCuenta.replaceChildren(aviso);
          }
        });
    },
  });
  const tituloCuenta = document.createElement('h2');
  tituloCuenta.textContent = 'Cuenta corriente';
  cuentaSeccion.append(tituloCuenta, buscador.contenedor, detalleCuenta);

  async function cargar(): Promise<void> {
    consulta += 1;
    const esta = consulta;
    grilla.setAttribute('aria-busy', 'true');
    const fechaHoy = hoy();
    try {
      const [abierta, pendientes, cajas] = await Promise.all([
        cajaAbierta(contexto.api),
        consultarPendientes(contexto.api),
        consultarHistorial(contexto.api, sumarDias(fechaHoy, -(DIAS_HISTORIAL - 1)), fechaHoy),
      ]);
      if (esta !== consulta) {
        return;
      }
      caja = abierta;
      dibujarEstado(estado, caja, apertura, {
        puedeOperar: esRecepcion,
        abrirCaja: () => undefined,
        cerrarCaja: (actual) => abrirCierre(control, actual),
      });
      dibujarSecciones(pendientes);
      dibujarCobros(cobrosSeccion, caja, esAdmin, (cobro) => abrirAnulacion(control, cobro));
      dibujarHistorial(historialSeccion, cajas);
    } catch (causa) {
      if (esta !== consulta || sesionVencida(causa)) {
        return;
      }
      exito.textContent = '';
      error.textContent = mensajeDeError(causa);
    } finally {
      if (esta === consulta) {
        grilla.removeAttribute('aria-busy');
      }
    }
  }

  function dibujarSecciones(pendientes: Pendientes): void {
    dibujarPendientes(pendientesSeccion, pendientes, esRecepcion && caja !== null, (indice) => {
      const pendiente = pendientes.cargos[indice];
      if (pendiente !== undefined) {
        abrirCobro(control, pendiente);
      }
    });
  }

  void cargar();
}

/** Caja y cobros: solo administración y recepción (RN-CA-01, delta de frontend-navegacion). */
export const paginaCaja: Pagina = {
  ruta: 'caja',
  fondoSuave: true,
  titulo: 'Caja',
  acceso: 'personal',
  menu: 'personal',
  icono: 'caja',
  roles: ['ADMIN', 'RECEPCIONISTA'],
  avisoSinRol: MENSAJE_SOLO_ADMINISTRACION_Y_RECEPCION,
  render: dibujar,
};
