import { aceptarOferta, rechazarOferta, verOferta, type Oferta } from '../lista-espera/api.ts';
import { diaYHora } from '../lista-espera/formato.ts';
import { hayTokenCsrf } from '../shared/api/cliente.ts';
import { ErrorGenerico, ErrorNegocio, ErrorNoEncontrado } from '../shared/api/errores.ts';
import { aIsoConZona, fechaLarga } from '../shared/fecha/zona.ts';
import { crearBoton } from '../shared/ui/campos.ts';
import { tokenDeOferta } from '../shared/ui/entrada.ts';
import { crearCargando } from '../shared/ui/estado-vacio.ts';
import { crearIcono } from '../shared/ui/iconos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { MENSAJE_LINK_INVALIDO } from './confirmar.ts';
import { crearTitulo, type ContextoPagina, type Pagina } from './pagina.ts';

export const MENSAJE_OFERTA_VENCIDA = 'Esta oferta venció. Seguís en la lista de espera.';
export const MENSAJE_OFERTA_RECHAZADA = 'Listo, se lo ofrecemos a otra persona. Seguís en la lista de espera.';
export const MENSAJE_OFERTA_YA_RESPONDIDA = 'Ya respondiste esta oferta.';
export const MENSAJE_HORARIO_OCUPADO = 'Ese horario ya se ocupó. Seguís en la lista de espera.';

/** Estado de la oferta que ya no se puede responder → su mensaje y su estilo. */
const ESTADOS_CERRADOS: Readonly<Record<string, readonly [string, string]>> = {
  VENCIDA: [MENSAJE_OFERTA_VENCIDA, 'aviso aviso-alerta'],
  NO_DISPONIBLE: [MENSAJE_HORARIO_OCUPADO, 'aviso aviso-alerta'],
  ACEPTADA: [MENSAJE_OFERTA_YA_RESPONDIDA, 'aviso aviso-info'],
  RECHAZADA: [MENSAJE_OFERTA_YA_RESPONDIDA, 'aviso aviso-info'],
};

/** Rechazos de aceptar o rechazar → su estilo; el texto sale de los mensajes por código. */
const CLASE_POR_CODIGO: Readonly<Record<string, string>> = {
  OFERTA_NO_DISPONIBLE: 'aviso aviso-alerta',
  OFERTA_VENCIDA: 'aviso aviso-alerta',
  OFERTA_YA_RESPONDIDA: 'aviso aviso-info',
  OFERTA_INEXISTENTE: 'aviso aviso-error',
};

function fechaLargaDe(fecha: string, hora: string): string {
  return fechaLarga(aIsoConZona(fecha, hora));
}

function aviso(texto: string, clase: string, rol: 'alert' | 'status'): HTMLParagraphElement {
  const parrafo = document.createElement('p');
  parrafo.className = clase;
  parrafo.setAttribute('role', rol);
  parrafo.textContent = texto;
  return parrafo;
}

function dibujar(contenedor: HTMLElement, contexto: ContextoPagina): void {
  const panel = document.createElement('section');
  panel.className = 'panel oferta';
  panel.setAttribute('aria-label', 'Turno disponible');
  const pagina = document.createElement('div');
  pagina.className = 'pagina-paciente';
  pagina.append(crearTitulo('Turno disponible'), panel);
  contenedor.replaceChildren(pagina);
  const vigente = (): boolean => contenedor.contains(panel);

  const token = tokenDeOferta();
  if (token === null || token === '') {
    panel.replaceChildren(aviso(MENSAJE_LINK_INVALIDO, 'aviso aviso-error', 'alert'));
    return;
  }

  /** Muestra un rechazo con su mensaje, sin botones. */
  function cerrarCon(causa: unknown): void {
    const codigo = causa instanceof ErrorNegocio || causa instanceof ErrorNoEncontrado ? causa.codigo : null;
    const clase = codigo === null ? undefined : CLASE_POR_CODIGO[codigo];
    panel.replaceChildren(aviso(mensajeDeError(causa), clase ?? 'aviso aviso-error', clase === 'aviso aviso-info' ? 'status' : 'alert'));
  }

  function mostrarPendiente(oferta: Oferta, tokenOferta: string): void {
    const cuando = `${fechaLargaDe(oferta.fecha, oferta.hora)} a las ${oferta.hora}`;
    const con = oferta.profesional === '' ? '' : ` con ${oferta.profesional}`;
    const turno = document.createElement('div');
    turno.className = 'oferta-turno';
    const textos = document.createElement('div');
    const dia = document.createElement('p');
    dia.className = 'oferta-dia';
    dia.textContent = `${fechaLargaDe(oferta.fecha, oferta.hora)} · ${oferta.hora}`;
    textos.append(dia);
    if (oferta.profesional !== '') {
      const profesional = document.createElement('p');
      profesional.className = 'oferta-con';
      profesional.textContent = `con ${oferta.profesional}`;
      textos.append(profesional);
    }
    turno.append(crearIcono('agenda'), textos);
    const explicacion = document.createElement('p');
    explicacion.textContent = `Se liberó un turno el ${cuando}${con}.`;
    const plazo = document.createElement('p');
    plazo.className = 'oferta-plazo';
    plazo.textContent = `Podés aceptarlo hasta el ${diaYHora(oferta.venceEn)}.`;
    const error = aviso('', 'aviso aviso-error', 'alert');
    const rechazar = crearBoton('No me interesa');
    rechazar.className = 'btn';
    const aceptar = crearBoton('Aceptar el turno');
    aceptar.className = 'btn btn-principal';
    const acciones = document.createElement('div');
    acciones.className = 'oferta-acciones';
    acciones.append(rechazar, aceptar);
    panel.replaceChildren(turno, explicacion, plazo, error, acciones);

    let enviando = false;
    /** Cada botón envía una sola vez; una falla de red o del servidor deja reintentar. */
    function responder(accion: () => Promise<void>): void {
      if (enviando) {
        return;
      }
      enviando = true;
      aceptar.disabled = true;
      rechazar.disabled = true;
      error.textContent = '';
      void (async () => {
        try {
          if (!hayTokenCsrf()) {
            await contexto.api.obtener<unknown>('/public/sesion');
          }
          await accion();
        } catch (causa) {
          if (causa instanceof ErrorGenerico) {
            error.textContent = mensajeDeError(causa);
            enviando = false;
            aceptar.disabled = false;
            rechazar.disabled = false;
            return;
          }
          cerrarCon(causa);
        }
      })();
    }

    aceptar.addEventListener('click', () =>
      responder(async () => {
        const turnoReservado = await aceptarOferta(contexto.api, tokenOferta);
        const conProfesional = turnoReservado.profesional === '' ? '' : ` con ${turnoReservado.profesional}`;
        const listo = aviso('', 'aviso aviso-ok', 'status');
        const numero = document.createElement('span');
        numero.className = 'oferta-numero';
        numero.textContent = turnoReservado.numeroReserva;
        listo.append(
          `Listo, tu turno quedó reservado para el ${fechaLargaDe(turnoReservado.fecha, turnoReservado.hora)} a las ${turnoReservado.hora}${conProfesional}. Tu número de reserva es `,
          numero,
          '.',
        );
        const ayuda = document.createElement('p');
        ayuda.className = 'ayuda';
        ayuda.textContent = 'Guardá el número de reserva: lo vas a necesitar para cancelar o cambiar el turno.';
        panel.replaceChildren(listo, ayuda);
      }),
    );
    rechazar.addEventListener('click', () =>
      responder(async () => {
        await rechazarOferta(contexto.api, tokenOferta);
        panel.replaceChildren(aviso(MENSAJE_OFERTA_RECHAZADA, 'aviso aviso-ok', 'status'));
      }),
    );
  }

  panel.replaceChildren(crearCargando('Buscando la oferta…'));
  // Ver la oferta no la responde: los botones son los únicos que envían algo.
  void verOferta(contexto.api, token).then(
    (oferta) => {
      if (!vigente()) {
        return;
      }
      if (oferta.estado === 'PENDIENTE') {
        mostrarPendiente(oferta, token);
        return;
      }
      const [mensaje, clase] = ESTADOS_CERRADOS[oferta.estado] ?? [MENSAJE_OFERTA_YA_RESPONDIDA, 'aviso aviso-info'];
      panel.replaceChildren(aviso(mensaje, clase, clase === 'aviso aviso-info' ? 'status' : 'alert'));
    },
    (causa: unknown) => {
      if (vigente()) {
        cerrarCon(causa);
      }
    },
  );
}

/** C-15: la oferta de la lista de espera desde el link del mensaje (`#/oferta`), pública y fuera del menú. */
export const paginaOferta: Pagina = {
  ruta: 'oferta',
  fondoSuave: true,
  titulo: 'Turno disponible',
  render: dibujar,
};
