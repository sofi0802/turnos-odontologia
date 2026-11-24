import { ErrorNegocio, ErrorValidacion } from '../shared/api/errores.ts';
import type { ClienteApi } from '../shared/api/cliente.ts';
import { crearCampo, crearEntrada, crearSelector, type Campo } from '../shared/ui/campos.ts';
import { abrirDialogo } from '../shared/ui/dialogo.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { leerSaldoDelCobro, MEDIOS, type Caja, type Cobro, type Pendiente } from './api.ts';
import { comoNumero, formatearPesos, importeParaCampo, leerImporte } from './dinero.ts';
import { fechaYHora, NOMBRE_MEDIO } from './texto.ts';

/** Formularios de la caja (design D10 de C-22): cobrar, cerrar y anular. */

export interface ControlCaja {
  readonly api: ClienteApi;
  mostrarExito(mensaje: string): void;
  recargar(): void;
  /** Si el error es de sesión vencida, lleva a Mi cuenta y devuelve `true`. */
  sesionVencida(causa: unknown): boolean;
}

export const MENSAJE_IMPORTE_OBLIGATORIO = 'Escribí un importe mayor que cero.';
export const MENSAJE_CONTADO_OBLIGATORIO = 'Escribí el efectivo contado.';
export const MENSAJE_MOTIVO_DIFERENCIA = 'Escribí el motivo de la diferencia.';
export const MENSAJE_MOTIVO_ANULACION = 'Escribí el motivo de la anulación.';

function campoImporte(etiqueta: string, nombre: string, valor = ''): Campo<HTMLInputElement> {
  const entrada = crearEntrada('text', nombre);
  entrada.inputMode = 'decimal';
  entrada.autocomplete = 'off';
  entrada.value = valor;
  return crearCampo(etiqueta, entrada);
}

function campoTexto(etiqueta: string, nombre: string): Campo<HTMLInputElement> {
  const entrada = crearEntrada('text', nombre);
  entrada.maxLength = 255;
  entrada.autocomplete = 'off';
  return crearCampo(etiqueta, entrada);
}

/**
 * Manda el pedido de un formulario. Con éxito cierra y avisa; un `422` marca los campos; un `409` muestra su mensaje
 * en el formulario y vuelve a pedir la caja (spec "Mensajes de los rechazos de la caja").
 */
async function enviar(
  control: ControlCaja,
  dialogo: { general: HTMLElement; principal: HTMLButtonElement; cerrar(): void },
  porCampo: Readonly<Record<string, Campo<HTMLInputElement | HTMLSelectElement>>>,
  pedido: () => Promise<unknown>,
  exito: (cuerpo: unknown) => string,
): Promise<void> {
  dialogo.general.textContent = '';
  dialogo.principal.disabled = true;
  try {
    const cuerpo = await pedido();
    dialogo.cerrar();
    control.mostrarExito(exito(cuerpo));
    control.recargar();
  } catch (causa) {
    if (control.sesionVencida(causa)) {
      dialogo.cerrar();
      return;
    }
    if (causa instanceof ErrorValidacion && causa.errores.length > 0) {
      const sueltos: string[] = [];
      for (const { campo, mensaje } of causa.errores) {
        const destino = porCampo[campo];
        if (destino === undefined) {
          sueltos.push(mensaje);
        } else {
          destino.mostrarError(mensaje);
        }
      }
      dialogo.general.textContent = sueltos.join(' ');
      return;
    }
    dialogo.general.textContent = mensajeDeError(causa);
    if (causa instanceof ErrorNegocio) {
      control.recargar();
    }
  } finally {
    dialogo.principal.disabled = false;
  }
}

/** "Cobrar" un pendiente: importe (por defecto, el saldo) y medio de pago. */
export function abrirCobro(control: ControlCaja, pendiente: Pendiente): void {
  const importe = campoImporte('Importe', 'importe', importeParaCampo(pendiente.saldo));
  const medio = crearCampo('Medio de pago', crearSelector('medio', MEDIOS.map((m) => ({ valor: m.valor, texto: m.texto }))));
  const dialogo = abrirDialogo({
    titulo: 'Cobrar',
    resumen: `${pendiente.paciente} · ${pendiente.prestacion} · ${fechaYHora(pendiente.inicio)}. Saldo: ${formatearPesos(pendiente.saldo)}.`,
    contenido: [importe.contenedor, medio.contenedor],
    textoDescartar: 'Cerrar',
    textoPrincipal: 'Registrar cobro',
  });
  dialogo.formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const leido = leerImporte(importe.control.value);
    if (leido === null || comoNumero(leido) <= 0) {
      importe.mostrarError(MENSAJE_IMPORTE_OBLIGATORIO);
      return;
    }
    if (comoNumero(leido) > pendiente.saldo) {
      importe.mostrarError(`El importe no puede superar el saldo (${formatearPesos(pendiente.saldo)}).`);
      return;
    }
    importe.mostrarError('');
    void enviar(
      control,
      dialogo,
      { importe, medio },
      () => control.api.crear<unknown>(`/cargos/${pendiente.id}/cobros`, { importe: leido, medio: medio.control.value }),
      (cuerpo) => `Cobro registrado. Saldo: ${formatearPesos(leerSaldoDelCobro(cuerpo))}.`,
    );
  });
}

/** "Cerrar caja": efectivo contado, diferencia mientras se escribe y motivo obligatorio si hay diferencia. */
export function abrirCierre(control: ControlCaja, caja: Caja): void {
  const contado = campoImporte('Efectivo contado', 'efectivoContado');
  const diferencia = document.createElement('p');
  diferencia.className = 'caja-diferencia-viva';
  diferencia.setAttribute('aria-live', 'polite');
  const motivo = campoTexto('Motivo de la diferencia', 'motivoDiferencia');
  const dialogo = abrirDialogo({
    titulo: 'Cerrar caja',
    resumen: `Efectivo esperado: ${formatearPesos(caja.efectivoEsperado)} (monto inicial más cobros en efectivo).`,
    contenido: [contado.contenedor, diferencia, motivo.contenedor],
    textoDescartar: 'Cerrar',
    textoPrincipal: 'Cerrar caja',
  });

  function diferenciaActual(): number | null {
    const leido = leerImporte(contado.control.value);
    return leido === null ? null : Math.round((comoNumero(leido) - caja.efectivoEsperado) * 100) / 100;
  }

  contado.control.addEventListener('input', () => {
    const valor = diferenciaActual();
    diferencia.textContent = valor === null ? '' : `Diferencia: ${formatearPesos(valor)}`;
    diferencia.classList.toggle('con-diferencia', valor !== null && valor !== 0);
  });

  dialogo.formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const leido = leerImporte(contado.control.value);
    contado.mostrarError(leido === null ? MENSAJE_CONTADO_OBLIGATORIO : '');
    const valor = diferenciaActual();
    const textoMotivo = motivo.control.value.trim();
    const faltaMotivo = valor !== null && valor !== 0 && textoMotivo === '';
    motivo.mostrarError(faltaMotivo ? MENSAJE_MOTIVO_DIFERENCIA : '');
    if (leido === null || faltaMotivo) {
      return;
    }
    const cuerpo: Record<string, string> = { efectivoContado: leido };
    if (textoMotivo !== '') {
      cuerpo['motivoDiferencia'] = textoMotivo;
    }
    void enviar(
      control,
      dialogo,
      { efectivoContado: contado, motivoDiferencia: motivo },
      () => control.api.crear<unknown>(`/cajas/${caja.id}/cierre`, cuerpo),
      () => `Caja cerrada. Diferencia: ${formatearPesos(valor ?? 0)}.`,
    );
  });
}

/** "Anular" un cobro (solo el admin): motivo obligatorio. */
export function abrirAnulacion(control: ControlCaja, cobro: Cobro): void {
  const motivo = campoTexto('Motivo', 'motivo');
  const dialogo = abrirDialogo({
    titulo: 'Anular cobro',
    resumen: `${cobro.paciente} · ${NOMBRE_MEDIO[cobro.medio]} · ${formatearPesos(cobro.importe)}. El cobro no se borra: queda anulado.`,
    contenido: [motivo.contenedor],
    textoDescartar: 'Cerrar',
    textoPrincipal: 'Anular cobro',
    peligro: true,
  });
  dialogo.formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const texto = motivo.control.value.trim();
    motivo.mostrarError(texto === '' ? MENSAJE_MOTIVO_ANULACION : '');
    if (texto === '') {
      return;
    }
    void enviar(
      control,
      dialogo,
      { motivo },
      () => control.api.crear<unknown>(`/cobros/${cobro.id}/anulacion`, { motivo: texto }),
      () => 'Cobro anulado.',
    );
  });
}
