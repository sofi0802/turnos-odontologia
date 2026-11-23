import { hayTokenCsrf } from '../shared/api/cliente.ts';
import { ErrorGenerico, ErrorNegocio, ErrorNoEncontrado } from '../shared/api/errores.ts';
import { aIsoConZona, fechaLarga } from '../shared/fecha/zona.ts';
import { tokenDeConfirmacion } from '../shared/ui/entrada.ts';
import { crearAviso, crearBoton } from '../shared/ui/campos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { crearTitulo, type ContextoPagina, type Pagina } from './pagina.ts';

export const MENSAJE_LINK_INVALIDO = 'El link no es válido. Revisá que esté completo.';

/** Mensajes de los rechazos de la confirmación (spec "Confirmación rechazada"). */
const MENSAJES_CONFIRMACION: Readonly<Record<string, string>> = {
  TOKEN_USADO: 'Este turno ya estaba confirmado.',
  TOKEN_INEXISTENTE: MENSAJE_LINK_INVALIDO,
  TURNO_NO_CONFIRMABLE: 'Este turno ya no se puede confirmar. Si tenés dudas, contactá al consultorio.',
};

function texto(cuerpo: unknown, clave: string): string {
  if (typeof cuerpo !== 'object' || cuerpo === null) {
    return '';
  }
  const valor: unknown = Reflect.get(cuerpo, clave);
  return typeof valor === 'string' ? valor : '';
}

/** Arma "Listo, confirmaste tu turno del … a las … con …." con la respuesta del servidor. */
export function mensajeConfirmado(cuerpo: unknown): string {
  const fecha = texto(cuerpo, 'fecha');
  const hora = texto(cuerpo, 'hora');
  const profesional = texto(cuerpo, 'profesional');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(fecha) || !/^\d{2}:\d{2}$/.test(hora)) {
    return 'Listo, confirmaste tu turno.';
  }
  const con = profesional === '' ? '' : ` con ${profesional}`;
  return `Listo, confirmaste tu turno del ${fechaLarga(aIsoConZona(fecha, hora))} a las ${hora}${con}.`;
}

function dibujar(contenedor: HTMLElement, contexto: ContextoPagina): void {
  const panel = document.createElement('section');
  panel.className = 'panel';
  panel.setAttribute('aria-label', 'Confirmar asistencia');
  const pagina = document.createElement('div');
  pagina.className = 'pagina-paciente';
  pagina.append(crearTitulo('Confirmar asistencia'), panel);
  contenedor.replaceChildren(pagina);

  const token = tokenDeConfirmacion();
  const error = crearAviso('alert');
  if (token === null || token === '') {
    error.textContent = MENSAJE_LINK_INVALIDO;
    panel.replaceChildren(error);
    return;
  }

  const explicacion = document.createElement('p');
  explicacion.textContent = 'Tocá el botón para avisarle al consultorio que vas a ir a tu turno.';
  const confirmar = crearBoton('Confirmar mi asistencia');
  confirmar.className = 'btn btn-principal';
  const acciones = document.createElement('div');
  acciones.className = 'acciones';
  acciones.append(confirmar);
  panel.replaceChildren(explicacion, error, acciones);

  let enviando = false;
  // El pedido sale solo al tocar el botón: un revisor de links que abra la dirección no gasta el token.
  confirmar.addEventListener('click', () => {
    if (enviando) {
      return;
    }
    enviando = true;
    confirmar.disabled = true;
    error.textContent = '';
    void (async () => {
      try {
        if (!hayTokenCsrf()) {
          await contexto.api.obtener<unknown>('/public/sesion');
        }
        const cuerpo = await contexto.api.crear<unknown>(`/public/confirmaciones/${encodeURIComponent(token)}`);
        const comprobante = document.createElement('div');
        comprobante.className = 'comprobante';
        comprobante.setAttribute('role', 'status');
        comprobante.textContent = mensajeConfirmado(cuerpo);
        panel.replaceChildren(comprobante);
      } catch (rechazo) {
        const codigo = rechazo instanceof ErrorNegocio || rechazo instanceof ErrorNoEncontrado ? rechazo.codigo : null;
        const mensaje = codigo === null ? undefined : MENSAJES_CONFIRMACION[codigo];
        if (mensaje !== undefined) {
          error.textContent = mensaje;
          panel.replaceChildren(error);
          return;
        }
        error.textContent = mensajeDeError(rechazo);
        if (rechazo instanceof ErrorGenerico) {
          // Una falla de red o del servidor se puede reintentar.
          confirmar.disabled = false;
          enviando = false;
        } else {
          panel.replaceChildren(error);
        }
      }
    })();
  });
}

/** Confirmación de asistencia desde el link del recordatorio (`#/confirmar`), pública y fuera del menú. */
export const paginaConfirmar: Pagina = {
  ruta: 'confirmar',
  titulo: 'Confirmar asistencia',
  render: dibujar,
};
