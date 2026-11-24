import { crearBoton } from '../shared/ui/campos.ts';
import { crearIcono } from '../shared/ui/iconos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { cargarProfesionales, nombreCompleto, type Profesional } from './api.ts';
import { mostrarContrasenaTemporal } from './contrasena-temporal.ts';
import {
  activarCuenta,
  cargarCuentas,
  desactivarCuenta,
  NOMBRE_DEL_ROL,
  restablecerContrasena,
  type CuentaPersonal,
} from './cuentas-api.ts';
import { abrirAltaUsuario } from './usuario-alta.ts';
import {
  abrirDialogo,
  botonChico,
  cargarEn,
  crearCabecera,
  crearEstado,
  crearLista,
  guardando,
  type ContextoSeccion,
} from './ui.ts';

export const NOTA_CUENTA_PROPIA = 'Tu contraseña la cambiás en "Mi contraseña", en el menú.';

interface DatosUsuarios {
  cuentas: CuentaPersonal[];
  readonly profesionales: readonly Profesional[];
}

/**
 * Pestaña "Usuarios" de Configuración, solo para `ADMIN` (spec frontend-cuentas-personal, C-41): las cuentas del
 * personal con su rol, su profesional y su estado, el alta y las acciones de cada cuenta.
 */
export function montarUsuarios(panel: HTMLElement, contexto: ContextoSeccion): void {
  const avisoExito = document.createElement('p');
  avisoExito.className = 'aviso aviso-ok';
  avisoExito.setAttribute('role', 'status');
  avisoExito.hidden = true;
  const agregar = crearBoton('Agregar usuario');
  agregar.disabled = true;
  const cuerpo = document.createElement('div');
  panel.append(crearCabecera('Usuarios', agregar), avisoExito, cuerpo);

  let datos: DatosUsuarios | null = null;

  agregar.addEventListener('click', () => {
    if (datos === null) {
      return;
    }
    const actuales = datos;
    abrirAltaUsuario(contexto, { profesionales: actuales.profesionales, cuentas: actuales.cuentas }, (cuenta) => {
      actuales.cuentas = [...actuales.cuentas, cuenta];
      dibujarLista(actuales);
    });
  });

  function dibujarLista(actuales: DatosUsuarios): void {
    const lista = crearLista();
    const ordenadas = [...actuales.cuentas].sort((a, b) => a.username.localeCompare(b.username));
    for (const cuenta of ordenadas) {
      lista.append(filaDeCuenta(cuenta, actuales));
    }
    cuerpo.replaceChildren(lista);
  }

  function reemplazar(actuales: DatosUsuarios, cuenta: CuentaPersonal): void {
    actuales.cuentas = actuales.cuentas.map((c) => (c.id === cuenta.id ? cuenta : c));
    dibujarLista(actuales);
  }

  function filaDeCuenta(cuenta: CuentaPersonal, actuales: DatosUsuarios): HTMLLIElement {
    const fila = document.createElement('li');
    if (!cuenta.activo) {
      fila.className = 'item-inactivo';
    }
    const datosFila = document.createElement('div');
    const nombre = document.createElement('div');
    nombre.className = 'item-nombre';
    nombre.textContent = cuenta.username;
    const linea = document.createElement('div');
    linea.className = 'item-datos';
    const rol = document.createElement('span');
    rol.textContent = NOMBRE_DEL_ROL[cuenta.rol];
    linea.append(rol);
    const profesional = actuales.profesionales.find((p) => p.id === cuenta.profesionalId);
    if (profesional !== undefined) {
      const quien = document.createElement('span');
      quien.textContent = nombreCompleto(profesional);
      linea.append(quien);
    }
    const marcas = document.createElement('div');
    marcas.className = 'cuenta-marcas';
    marcas.append(crearEstado(cuenta.activo));
    if (cuenta.debeCambiarContrasena) {
      const temporal = document.createElement('span');
      temporal.className = 'marca-temporal';
      temporal.append(crearIcono('reloj'), 'Contraseña temporal pendiente');
      marcas.append(temporal);
    }
    const propia = cuenta.username === contexto.sesion.username;
    if (propia) {
      const marcaPropia = document.createElement('span');
      marcaPropia.className = 'marca-propia';
      marcaPropia.textContent = 'Tu cuenta';
      marcas.append(marcaPropia);
    }
    datosFila.append(nombre, linea, marcas);
    fila.append(datosFila);

    const acciones = document.createElement('div');
    acciones.className = 'item-acciones';
    if (propia) {
      const nota = document.createElement('span');
      nota.className = 'nota-fila';
      nota.textContent = NOTA_CUENTA_PROPIA;
      acciones.append(nota);
    } else if (cuenta.activo) {
      const restablecer = botonChico('Restablecer contraseña');
      restablecer.addEventListener('click', () => confirmarRestablecer(cuenta, actuales));
      const desactivar = botonChico('Desactivar', 'peligro');
      desactivar.addEventListener('click', () => confirmarDesactivar(cuenta, actuales));
      acciones.append(restablecer, desactivar);
    } else {
      const activar = botonChico('Activar');
      activar.addEventListener('click', () => {
        void guardando(activar, async () => {
          reemplazar(actuales, await activarCuenta(contexto.api, cuenta.id));
          mostrarExito(`La cuenta ${cuenta.username} está activa de nuevo.`);
        }).catch((causa: unknown) => {
          if (!contexto.sesionVencida(causa)) {
            mostrarExito('');
            mostrarErrorEnFila(fila, mensajeDeError(causa));
          }
        });
      });
      acciones.append(activar);
    }
    fila.append(acciones);
    return fila;
  }

  function confirmarDesactivar(cuenta: CuentaPersonal, actuales: DatosUsuarios): void {
    confirmarAccion(
      `¿Desactivar la cuenta ${cuenta.username}?`,
      'No va a poder ingresar y se cierran sus sesiones abiertas. Podés volver a activarla cuando quieras.',
      'Desactivar',
      true,
      async () => {
        reemplazar(actuales, await desactivarCuenta(contexto.api, cuenta.id));
        mostrarExito(`La cuenta ${cuenta.username} quedó desactivada.`);
      },
    );
  }

  function confirmarRestablecer(cuenta: CuentaPersonal, actuales: DatosUsuarios): void {
    confirmarAccion(
      `¿Restablecer la contraseña de ${cuenta.username}?`,
      'Su contraseña actual deja de servir y se cierran sus sesiones abiertas. Vas a ver una contraseña temporal nueva.',
      'Restablecer',
      false,
      async () => {
        const restablecida = await restablecerContrasena(contexto.api, cuenta.id);
        reemplazar(actuales, restablecida.cuenta);
        return () => mostrarContrasenaTemporal(restablecida.cuenta.username, restablecida.contrasenaTemporal, true);
      },
    );
  }

  /** Confirmación con el molde de diálogos; si la acción devuelve una función, se llama después de cerrar. */
  function confirmarAccion(
    titulo: string,
    resumen: string,
    textoConfirmar: string,
    peligro: boolean,
    accion: () => Promise<(() => void) | void>,
  ): void {
    const dialogo = abrirDialogo(titulo, [], textoConfirmar, { resumen, peligro });
    dialogo.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      let despues: (() => void) | void = undefined;
      void guardando(dialogo.enviar, async () => {
        despues = await accion();
      }).then(
        (ok) => {
          if (ok) {
            dialogo.cerrar();
            if (typeof despues === 'function') {
              despues();
            }
          }
        },
        (causa: unknown) => {
          if (!contexto.sesionVencida(causa)) {
            dialogo.general.textContent = mensajeDeError(causa);
          }
        },
      );
    });
  }

  function mostrarExito(mensaje: string): void {
    avisoExito.textContent = mensaje;
    avisoExito.hidden = mensaje === '';
  }

  function mostrarErrorEnFila(fila: HTMLElement, mensaje: string): void {
    const aviso = document.createElement('p');
    aviso.className = 'aviso aviso-error';
    aviso.setAttribute('role', 'alert');
    aviso.textContent = mensaje;
    fila.querySelector('.aviso-error')?.remove();
    fila.append(aviso);
  }

  cargarEn(
    cuerpo,
    contexto,
    async (): Promise<DatosUsuarios> => {
      const [cuentas, profesionales] = await Promise.all([cargarCuentas(contexto.api), cargarProfesionales(contexto.api)]);
      return { cuentas, profesionales };
    },
    (cargados) => {
      datos = cargados;
      agregar.disabled = false;
      dibujarLista(cargados);
    },
  );
}
