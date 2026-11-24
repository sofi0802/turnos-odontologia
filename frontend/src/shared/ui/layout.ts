import type { GrupoMenu, Pagina } from '../../pages/pagina.ts';
import { esPersonal, type Sesion } from '../sesion/sesion.ts';
import { crearIcono } from './iconos.ts';
import { crearLogo, NOMBRE_APLICACION } from './marca.ts';

export interface Layout {
  readonly contenido: HTMLElement;
  /** Marca el enlace de la página activa y cierra el cajón del menú. */
  mostrar(pagina: Pagina): void;
  /**
   * Con una sesión muestra la barra lateral con las opciones del rol y, en el encabezado, el usuario y "Salir";
   * sin sesión no hay barra lateral y el encabezado ofrece "Reservar turno" e "Ingresar" (C-39, D4).
   */
  mostrarSesion(sesion: Sesion | null): void;
}

export interface OpcionesLayout {
  alSalir(): void;
}

const ID_MENU = 'menu-principal';

/** Ruta de la cuenta del paciente, a la que lleva "Ingresar" sin sesión. */
export const RUTA_MI_CUENTA = 'mi-cuenta';

/** Ruta de la portada, a la que lleva el logo del encabezado. */
export const RUTA_INICIO = 'inicio';

/** Ruta de la reserva, que el encabezado ofrece a quien no tiene sesión. */
const RUTA_RESERVA = 'reserva';

/** Grupo del menú que ve una sesión: el personal ve el suyo; sin sesión o con paciente, el del paciente. */
export function grupoDeSesion(sesion: Sesion | null): GrupoMenu {
  return sesion !== null && esPersonal(sesion.rol) ? 'personal' : 'paciente';
}

function iniciales(usuario: string): string {
  return usuario
    .split(/[\s@._-]+/)
    .filter((parte) => parte !== '')
    .slice(0, 2)
    .map((parte) => parte.charAt(0).toUpperCase())
    .join('');
}

/** Arma el encabezado, la barra lateral, el contenido y el pie dentro de `raiz`. */
export function crearLayout(raiz: HTMLElement, paginas: readonly Pagina[], opciones: OpcionesLayout): Layout {
  // --- Encabezado ---
  const encabezado = document.createElement('header');
  encabezado.className = 'cabecera';

  const marca = document.createElement('a');
  marca.className = 'marca';
  marca.href = `#/${RUTA_INICIO}`;
  marca.append(crearLogo());

  const boton = document.createElement('button');
  boton.type = 'button';
  boton.className = 'btn-sobre-azul menu-boton';
  boton.setAttribute('aria-controls', ID_MENU);
  boton.setAttribute('aria-expanded', 'false');
  boton.append(crearIcono('menu'), 'Menú');

  const acciones = document.createElement('div');
  acciones.className = 'acciones-cabecera';
  const avatar = document.createElement('span');
  avatar.className = 'avatar';
  avatar.setAttribute('aria-hidden', 'true');
  const nombre = document.createElement('span');
  nombre.className = 'usuario-nombre';
  const salir = document.createElement('button');
  salir.type = 'button';
  salir.className = 'btn-sobre-azul';
  salir.textContent = 'Salir';
  salir.addEventListener('click', () => opciones.alSalir());
  const reservar = document.createElement('a');
  reservar.className = 'enlace-sobre-azul';
  reservar.href = `#/${RUTA_RESERVA}`;
  reservar.textContent = 'Reservar turno';
  const ingresar = document.createElement('a');
  ingresar.className = 'btn-sobre-azul';
  ingresar.href = `#/${RUTA_MI_CUENTA}`;
  ingresar.textContent = 'Ingresar';
  acciones.append(avatar, nombre, salir, reservar, ingresar);

  encabezado.append(boton, marca, acciones);

  // --- Barra lateral: cajón en el celular, fija desde 768 px ---
  const lateral = document.createElement('aside');
  lateral.id = ID_MENU;
  lateral.className = 'lateral';

  const cajonCabecera = document.createElement('div');
  cajonCabecera.className = 'cajon-cabecera';
  const cajonTitulo = document.createElement('span');
  cajonTitulo.textContent = 'Menú';
  const cerrarCajon = document.createElement('button');
  cerrarCajon.type = 'button';
  cerrarCajon.className = 'cerrar';
  cerrarCajon.setAttribute('aria-label', 'Cerrar el menú');
  cerrarCajon.append(crearIcono('cerrar'));
  cajonCabecera.append(cajonTitulo, cerrarCajon);

  const menu = document.createElement('nav');
  menu.setAttribute('aria-label', 'Principal');
  const lista = document.createElement('ul');
  const items = paginas
    .filter((pagina) => pagina.menu !== undefined)
    .map((pagina) => {
      const enlace = document.createElement('a');
      enlace.href = `#/${pagina.ruta}`;
      if (pagina.icono !== undefined) {
        enlace.append(crearIcono(pagina.icono));
      }
      enlace.append(pagina.titulo);
      enlace.dataset['ruta'] = pagina.ruta;
      const item = document.createElement('li');
      item.dataset['grupo'] = pagina.menu;
      item.append(enlace);
      lista.append(item);
      // Las páginas que exigen sesión de paciente (Mis turnos) no se ofrecen al visitante.
      return { item, enlace, conSesion: pagina.acceso === 'paciente' };
    });
  menu.append(lista);
  lateral.append(cajonCabecera, menu);

  const velo = document.createElement('div');
  velo.className = 'velo';
  velo.setAttribute('aria-hidden', 'true');

  const contenido = document.createElement('main');
  contenido.className = 'contenido';

  const cuerpo = document.createElement('div');
  cuerpo.className = 'cuerpo';
  cuerpo.append(lateral, velo, contenido);

  // --- Pie ---
  const pie = document.createElement('footer');
  pie.className = 'pie';
  pie.textContent = NOMBRE_APLICACION;

  const aplicacion = document.createElement('div');
  aplicacion.className = 'app';
  aplicacion.append(encabezado, cuerpo, pie);
  raiz.replaceChildren(aplicacion);

  function abrirMenu(abierto: boolean, devolverFoco = false): void {
    const estabaAbierto = lateral.classList.contains('abierto');
    lateral.classList.toggle('abierto', abierto);
    aplicacion.classList.toggle('cajon-abierto', abierto);
    boton.setAttribute('aria-expanded', String(abierto));
    if (abierto && !estabaAbierto) {
      const primero = lateral.querySelector<HTMLElement>('li:not([hidden]) a');
      (primero ?? cerrarCajon).focus();
    } else if (!abierto && estabaAbierto && devolverFoco) {
      boton.focus();
    }
  }

  boton.addEventListener('click', () => abrirMenu(!lateral.classList.contains('abierto')));
  cerrarCajon.addEventListener('click', () => abrirMenu(false, true));
  velo.addEventListener('click', () => abrirMenu(false, true));
  document.addEventListener('keydown', (evento) => {
    if (evento.key === 'Escape' && lateral.classList.contains('abierto')) {
      abrirMenu(false, true);
    }
  });

  function mostrarSesion(sesion: Sesion | null): void {
    const grupo = grupoDeSesion(sesion);
    const conSesion = sesion !== null;
    for (const { item, conSesion: soloConSesion } of items) {
      item.hidden = item.dataset['grupo'] !== grupo || (soloConSesion && !conSesion);
    }
    lateral.hidden = !conSesion;
    boton.hidden = !conSesion;
    avatar.hidden = !conSesion;
    nombre.hidden = !conSesion;
    salir.hidden = !conSesion;
    reservar.hidden = conSesion;
    ingresar.hidden = conSesion;
    nombre.textContent = sesion?.username ?? '';
    avatar.textContent = sesion?.username == null ? '' : iniciales(sesion.username);
    cuerpo.classList.toggle('con-lateral', conSesion);
    if (!conSesion) {
      abrirMenu(false);
    }
  }

  mostrarSesion(null);

  return {
    contenido,
    mostrar(pagina) {
      cuerpo.classList.toggle('fondo-suave', pagina.fondoSuave === true);
      for (const { enlace } of items) {
        if (enlace.dataset['ruta'] === pagina.ruta) {
          enlace.setAttribute('aria-current', 'page');
        } else {
          enlace.removeAttribute('aria-current');
        }
      }
      abrirMenu(false);
    },
    mostrarSesion,
  };
}
