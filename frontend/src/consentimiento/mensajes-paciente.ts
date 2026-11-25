import { crearAviso, crearBoton } from '../shared/ui/campos.ts';
import { abrirDialogo } from '../shared/ui/dialogo.ts';
import { crearCargando } from '../shared/ui/estado-vacio.ts';
import { crearIcono } from '../shared/ui/iconos.ts';
import type { OpcionesSeccionPaciente } from '../paciente/tus-datos.ts';
import {
  cambiarMiConsentimiento,
  fechaDelCambio,
  obtenerMiConsentimiento,
  type EstadoConsentimiento,
} from './api.ts';

/**
 * "Mensajes del consultorio" en "Mi cuenta" (C-17, design D7): el paciente acepta o deja de recibir los mensajes.
 * Explica qué viaja en ellos (fecha, hora, consultorio y links, sin datos de salud). Dejar de recibirlos pide
 * confirmación, porque deja sin recordatorios ni ofertas de la lista de espera.
 */

export const MENSAJE_ACEPTADO = 'Listo, vas a recibir mensajes del consultorio.';
export const MENSAJE_REVOCADO = 'Ya no te vamos a recordar tus turnos ni ofrecer horarios de la lista de espera.';
export const MENSAJE_FALLA_CONSENTIMIENTO = 'No pudimos cargar tus preferencias de mensajes.';

let contador = 0;

function parrafo(texto: string, clase?: string): HTMLParagraphElement {
  const p = document.createElement('p');
  p.textContent = texto;
  if (clase !== undefined) {
    p.className = clase;
  }
  return p;
}

function estadoVisible(estado: EstadoConsentimiento): HTMLElement[] {
  const linea = document.createElement('p');
  linea.className = `mensajes-estado ${estado.otorgado ? 'si' : 'no'}`;
  linea.append(
    crearIcono(estado.otorgado ? 'mensaje' : 'mensaje-no'),
    estado.otorgado ? 'Aceptás recibir mensajes' : 'No recibís mensajes',
  );
  const partes: HTMLElement[] = [linea];
  if (estado.actualizadoEn !== null) {
    partes.push(parrafo(`Desde el ${fechaDelCambio(estado.actualizadoEn)}`, 'mensajes-fecha'));
  }
  return partes;
}

function explicacion(estado: EstadoConsentimiento): HTMLElement[] {
  const privacidad =
    'Los mensajes solo traen la fecha, la hora, el consultorio y links para confirmar, cancelar o cambiar el turno, ' +
    'sin datos de salud.';
  if (estado.otorgado) {
    return [parrafo(`Te escribimos por WhatsApp o, si no tenés, por email. ${privacidad}`, 'cuenta-ayuda')];
  }
  const lista = document.createElement('ul');
  lista.className = 'mensajes-que';
  for (const item of [
    'recordarte tus turnos un día antes,',
    'confirmar las reservas, cancelaciones y cambios que hagas,',
    'ofrecerte un horario de la lista de espera.',
  ]) {
    const li = document.createElement('li');
    li.textContent = item;
    lista.append(li);
  }
  return [
    parrafo('Si los aceptás, te escribimos por WhatsApp o, si no tenés, por email para:'),
    lista,
    parrafo(`${privacidad} Podés dejar de recibirlos cuando quieras.`, 'cuenta-ayuda'),
  ];
}

export function crearMensajesDelConsultorio(opciones: OpcionesSeccionPaciente): HTMLElement {
  contador += 1;
  const seccion = document.createElement('section');
  seccion.className = 'panel mensajes-consultorio';
  const titulo = document.createElement('h2');
  titulo.id = `mensajes-consultorio-${contador}`;
  titulo.textContent = 'Mensajes del consultorio';
  seccion.setAttribute('aria-labelledby', titulo.id);
  const anuncio = crearAviso('status');
  const error = crearAviso('alert');
  const cuerpo = document.createElement('div');
  cuerpo.className = 'pila';
  seccion.append(titulo, anuncio, error, cuerpo);

  function dibujar(estado: EstadoConsentimiento): void {
    const accion = crearBoton(estado.otorgado ? 'Dejar de recibir mensajes' : 'Aceptar mensajes');
    accion.className = estado.otorgado ? 'btn' : 'btn btn-principal';
    accion.addEventListener('click', () => {
      if (estado.otorgado) {
        confirmarRevocar(accion);
      } else {
        cambiar(true, accion);
      }
    });
    const acciones = document.createElement('div');
    acciones.className = 'acciones';
    acciones.append(accion);
    cuerpo.replaceChildren(...estadoVisible(estado), ...explicacion(estado), acciones);
  }

  function cambiar(otorgado: boolean, boton: HTMLButtonElement): void {
    boton.disabled = true;
    error.textContent = '';
    anuncio.textContent = '';
    cambiarMiConsentimiento(opciones.api, otorgado).then(
      (estado) => {
        if (!opciones.vigente()) {
          return;
        }
        dibujar(estado);
        anuncio.className = otorgado ? 'aviso aviso-ok' : 'aviso aviso-info';
        anuncio.textContent = otorgado ? MENSAJE_ACEPTADO : MENSAJE_REVOCADO;
      },
      (causa: unknown) => {
        if (!opciones.vigente()) {
          return;
        }
        boton.disabled = false;
        error.textContent = opciones.mensajeDeError(causa);
      },
    );
  }

  function confirmarRevocar(origen: HTMLButtonElement): void {
    const { formulario, cerrar } = abrirDialogo({
      titulo: '¿Dejar de recibir mensajes?',
      resumen:
        'No te vamos a recordar tus turnos ni ofrecer horarios de la lista de espera. Podés volver a aceptarlos ' +
        'cuando quieras.',
      textoDescartar: 'Volver',
      textoPrincipal: 'Dejar de recibir',
      peligro: true,
      alCerrar: () => {
        if (origen.isConnected) {
          origen.focus();
        }
      },
    });
    formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      cerrar();
      cambiar(false, origen);
    });
  }

  function cargar(): void {
    seccion.setAttribute('aria-busy', 'true');
    cuerpo.replaceChildren(crearCargando('Cargando tus preferencias…'));
    obtenerMiConsentimiento(opciones.api).then(
      (estado) => {
        if (!opciones.vigente()) {
          return;
        }
        seccion.removeAttribute('aria-busy');
        dibujar(estado);
      },
      (causa: unknown) => {
        if (!opciones.vigente()) {
          return;
        }
        seccion.removeAttribute('aria-busy');
        if (opciones.mensajeDeError(causa) === '') {
          cuerpo.replaceChildren();
          return;
        }
        const reintentar = crearBoton('Reintentar');
        reintentar.className = 'btn';
        reintentar.addEventListener('click', cargar);
        cuerpo.replaceChildren(parrafo(MENSAJE_FALLA_CONSENTIMIENTO), reintentar);
      },
    );
  }

  cargar();
  return seccion;
}
