import { formatearPesos } from '../caja/dinero.ts';
import type { ClienteApi } from '../shared/api/cliente.ts';
import { formatearHora } from '../shared/fecha/zona.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { pedirLinkSena, type SenaPaciente } from './reserva.ts';

/** La seña del paciente en la reserva y en "Mis turnos" (C-23, spec frontend-senas). */

export const MENSAJE_VUELTA_DE_PAGO = 'Estamos confirmando tu pago. Puede tardar unos minutos.';

/** El estado de la seña dicho con texto, no solo con color. */
export function textoSena(sena: SenaPaciente): string {
  switch (sena.estado) {
    case 'PENDIENTE':
      return `Seña pendiente: ${formatearPesos(sena.importe)}. Vence a las ${formatearHora(sena.venceEn)}.`;
    case 'PAGADA':
    case 'IMPUTADA':
      return 'Seña pagada';
    case 'VENCIDA':
      return 'Seña vencida: el turno se canceló';
    case 'DEVUELTA':
      return 'Seña devuelta';
    case 'RETENIDA':
      return 'Seña retenida por el consultorio';
  }
}

const CLASE_POR_ESTADO: Readonly<Record<SenaPaciente['estado'], string>> = {
  PENDIENTE: 'sena-pendiente',
  PAGADA: 'sena-pagada',
  IMPUTADA: 'sena-pagada',
  VENCIDA: 'sena-vencida',
  DEVUELTA: 'sena-devuelta',
  RETENIDA: 'sena-retenida',
};

/** La línea del estado de la seña en una tarjeta de "Mis turnos". */
export function crearLineaSena(sena: SenaPaciente): HTMLParagraphElement {
  const linea = document.createElement('p');
  linea.className = `sena-linea ${CLASE_POR_ESTADO[sena.estado]}`;
  linea.textContent = textoSena(sena);
  return linea;
}

/**
 * "Pagar seña": un enlace a Mercado Pago si ya hay link; si no, un botón que pide uno nuevo y lo abre en la misma
 * pestaña. Un error se informa con `alError`.
 */
export function crearPagarSena(
  sena: SenaPaciente,
  numeroReserva: string,
  api: ClienteApi,
  alError: (mensaje: string) => void,
): HTMLElement {
  if (sena.linkPago !== null) {
    const enlace = document.createElement('a');
    enlace.className = 'btn btn-principal';
    enlace.href = sena.linkPago;
    enlace.textContent = 'Pagar seña';
    return enlace;
  }
  const boton = document.createElement('button');
  boton.type = 'button';
  boton.className = 'btn btn-principal';
  boton.textContent = 'Pagar seña';
  boton.addEventListener('click', () => {
    boton.disabled = true;
    pedirLinkSena(api, numeroReserva).then(
      (link) => {
        boton.disabled = false;
        window.open(link, '_self');
      },
      (causa: unknown) => {
        boton.disabled = false;
        alError(mensajeDeError(causa));
      },
    );
  });
  return boton;
}

/** El bloque de la seña pendiente al terminar la reserva; `null` si no hay seña pendiente. */
export function crearBloqueReserva(
  sena: SenaPaciente | null,
  numeroReserva: string,
  api: ClienteApi,
  alError: (mensaje: string) => void,
): HTMLElement | null {
  if (sena === null || sena.estado !== 'PENDIENTE') {
    return null;
  }
  const bloque = document.createElement('div');
  bloque.className = 'sena-reserva';
  const importe = document.createElement('p');
  importe.className = 'sena-importe';
  importe.textContent = `Seña: ${formatearPesos(sena.importe)}. Pagala antes de las ${formatearHora(sena.venceEn)} para no perder el turno.`;
  const ayuda = document.createElement('p');
  ayuda.className = 'ayuda';
  ayuda.textContent = 'Se paga con Mercado Pago. Si no la pagás a tiempo, el turno se cancela solo y el horario queda libre.';
  bloque.append(importe, ayuda, crearPagarSena(sena, numeroReserva, api, alError));
  return bloque;
}
