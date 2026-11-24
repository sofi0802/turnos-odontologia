import { esPaciente, esPersonal, type Sesion } from '../shared/sesion/sesion.ts';
import {
  crearIconoConfirmacion,
  crearIconoHorario,
  crearIconoPrestacion,
  crearIlustracionPortada,
} from './inicio-ilustraciones.ts';
import { NOMBRE_APLICACION } from '../shared/ui/marca.ts';
import type { ContextoPagina, Pagina } from './pagina.ts';

const EYEBROW = 'Consultorio odontológico';
const TITULAR = 'Tu turno odontológico, sin llamadas ni esperas';

const TEXTOS = {
  visitante: 'Reservá tu consulta online en pocos pasos. Mirá los horarios libres sin registrarte y confirmá cuando estés listo.',
  paciente: 'Reservá un turno nuevo o revisá los que ya tenés.',
  personal: 'Entrá a la agenda del consultorio para ver y organizar los turnos.',
} as const;

const PASOS: readonly {
  readonly titulo: string;
  readonly detalle: string;
  readonly icono: () => SVGElement;
}[] = [
  {
    titulo: 'Elegí la prestación',
    detalle: 'Consulta, limpieza, extracción y más. Te mostramos solo los profesionales que la hacen.',
    icono: crearIconoPrestacion,
  },
  {
    titulo: 'Elegí día y horario',
    detalle: 'Mirá los horarios libres antes de decidir, sin registrarte.',
    icono: crearIconoHorario,
  },
  {
    titulo: 'Confirmá tu turno',
    detalle: 'Ingresá o creá tu cuenta en un minuto y guardá tu número de reserva.',
    icono: crearIconoConfirmacion,
  },
];

interface Boton {
  readonly texto: string;
  readonly ruta: string;
  readonly principal: boolean;
}

/** Botones de la portada según quién la mira (design D2). */
function botonesPara(sesion: Sesion | null): { readonly lead: string; readonly botones: readonly Boton[] } {
  if (sesion !== null && esPersonal(sesion.rol)) {
    return { lead: TEXTOS.personal, botones: [{ texto: 'Ir a la agenda', ruta: 'agenda', principal: true }] };
  }
  if (sesion !== null && esPaciente(sesion.rol)) {
    return {
      lead: TEXTOS.paciente,
      botones: [
        { texto: 'Reservar turno', ruta: 'reserva', principal: true },
        { texto: 'Mis turnos', ruta: 'mis-turnos', principal: false },
      ],
    };
  }
  return {
    lead: TEXTOS.visitante,
    botones: [
      { texto: 'Reservar turno', ruta: 'reserva', principal: true },
      { texto: 'Ingresar a mi cuenta', ruta: 'mi-cuenta', principal: false },
    ],
  };
}

function crearEnlace({ texto, ruta, principal }: Boton): HTMLAnchorElement {
  const enlace = document.createElement('a');
  enlace.className = principal ? 'btn btn-principal' : 'btn';
  enlace.href = `#/${ruta}`;
  enlace.textContent = texto;
  return enlace;
}

function crearPasos(): HTMLElement {
  const seccion = document.createElement('section');
  seccion.className = 'seccion-pasos';
  seccion.setAttribute('aria-labelledby', 'como-funciona');
  const titulo = document.createElement('h2');
  titulo.id = 'como-funciona';
  titulo.textContent = 'Cómo funciona';
  const lista = document.createElement('ol');
  lista.className = 'tarjetas-pasos';
  PASOS.forEach((paso, indice) => {
    const icono = document.createElement('span');
    icono.className = 'paso-icono';
    icono.append(paso.icono());
    const numero = document.createElement('span');
    numero.textContent = `${indice + 1}.`;
    const encabezado = document.createElement('h3');
    encabezado.append(numero, paso.titulo);
    const detalle = document.createElement('p');
    detalle.textContent = paso.detalle;
    const tarjeta = document.createElement('li');
    tarjeta.className = 'tarjeta-paso';
    tarjeta.append(icono, encabezado, detalle);
    lista.append(tarjeta);
  });
  seccion.append(titulo, lista);
  return seccion;
}

function dibujar(contenedor: HTMLElement, contexto: ContextoPagina): void {
  const eyebrow = document.createElement('p');
  eyebrow.className = 'eyebrow';
  eyebrow.textContent = EYEBROW;
  const titular = document.createElement('h1');
  titular.id = 'titulo-inicio';
  titular.tabIndex = -1;
  titular.textContent = TITULAR;
  const lead = document.createElement('p');
  lead.className = 'hero-lead';
  const acciones = document.createElement('div');
  acciones.className = 'hero-acciones';

  /** La portada se dibuja enseguida como visitante y se ajusta cuando llega la sesión. */
  function mostrarPara(sesion: Sesion | null): void {
    const { lead: texto, botones } = botonesPara(sesion);
    lead.textContent = texto;
    acciones.replaceChildren(...botones.map(crearEnlace));
  }
  mostrarPara(null);

  const ilustracion = document.createElement('div');
  ilustracion.className = 'hero-ilustracion';
  ilustracion.append(crearIlustracionPortada());

  const texto = document.createElement('div');
  texto.append(eyebrow, titular, lead, acciones);
  const hero = document.createElement('section');
  hero.className = 'hero';
  hero.setAttribute('aria-labelledby', 'titulo-inicio');
  hero.append(texto, ilustracion);

  const portada = document.createElement('div');
  portada.className = 'inicio';
  portada.append(hero, crearPasos());
  contenedor.replaceChildren(portada);

  void contexto.sesionLista.then((sesion) => {
    if (contenedor.contains(portada) && sesion.autenticado) {
      mostrarPara(sesion);
    }
  });
}

/** Portada (`#/inicio`): página inicial, pública y fuera del menú. */
export const paginaInicio: Pagina = {
  ruta: 'inicio',
  titulo: NOMBRE_APLICACION,
  render: dibujar,
};
