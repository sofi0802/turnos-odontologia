import type { GrupoMenu, Pagina } from '../../pages/pagina.ts';
import { esPersonal, type Sesion } from '../sesion/sesion.ts';

export interface Layout {
  readonly contenido: HTMLElement;
  /** Marca el enlace de la página activa y cierra el menú. */
  mostrar(pagina: Pagina): void;
  /**
   * Muestra el grupo del menú que corresponde a la sesión y, en el encabezado,
   * el usuario y "Salir", o el enlace "Ingresar" si no hay sesión (design D3 de C-12).
   */
  mostrarSesion(sesion: Sesion | null): void;
}

export interface OpcionesLayout {
  alSalir(): void;
}

const ID_MENU = 'menu-principal';

/** Ruta de la cuenta del paciente, a la que lleva "Ingresar" sin sesión. */
export const RUTA_MI_CUENTA = 'mi-cuenta';

/** Ruta de la portada, a la que lleva la marca del encabezado. */
export const RUTA_INICIO = 'inicio';

const TEXTO_INGRESAR = 'Ingresar';

/** Grupo del menú que ve una sesión: el personal ve el suyo; sin sesión o con paciente, el del paciente. */
export function grupoDeSesion(sesion: Sesion | null): GrupoMenu {
  return sesion !== null && esPersonal(sesion.rol) ? 'personal' : 'paciente';
}

/** Arma el encabezado con el menú y el área de contenido dentro de `raiz`. */
export function crearLayout(raiz: HTMLElement, paginas: readonly Pagina[], opciones: OpcionesLayout): Layout {
  const encabezado = document.createElement('header');
  encabezado.className = 'encabezado';

  // La marca lleva a la portada desde cualquier página (C-37).
  const marca = document.createElement('a');
  marca.className = 'marca';
  marca.href = `#/${RUTA_INICIO}`;
  marca.textContent = 'Turnos odontología';

  const boton = document.createElement('button');
  boton.type = 'button';
  boton.className = 'menu-boton';
  boton.textContent = 'Menú';
  boton.setAttribute('aria-controls', ID_MENU);
  boton.setAttribute('aria-expanded', 'false');

  const menu = document.createElement('nav');
  menu.id = ID_MENU;
  menu.className = 'menu';
  menu.setAttribute('aria-label', 'Principal');

  const lista = document.createElement('ul');
  const items = paginas
    .filter((pagina) => pagina.menu !== undefined)
    .map((pagina) => {
      const enlace = document.createElement('a');
      enlace.href = `#/${pagina.ruta}`;
      enlace.textContent = pagina.titulo;
      enlace.dataset['ruta'] = pagina.ruta;
      const item = document.createElement('li');
      item.dataset['grupo'] = pagina.menu;
      item.append(enlace);
      lista.append(item);
      // Las páginas que exigen sesión de paciente (Mis turnos) no se ofrecen al visitante.
      return { item, enlace, conSesion: pagina.acceso === 'paciente' };
    });
  menu.append(lista);

  const usuario = document.createElement('div');
  usuario.className = 'usuario';
  const nombre = document.createElement('span');
  nombre.className = 'usuario-nombre';
  const salir = document.createElement('button');
  salir.type = 'button';
  salir.className = 'boton-secundario';
  salir.textContent = 'Salir';
  salir.addEventListener('click', () => opciones.alSalir());
  const ingresar = document.createElement('a');
  ingresar.className = 'btn btn-fantasma';
  ingresar.href = `#/${RUTA_MI_CUENTA}`;
  ingresar.textContent = TEXTO_INGRESAR;
  usuario.append(nombre, salir, ingresar);

  function abrirMenu(abierto: boolean): void {
    menu.classList.toggle('abierto', abierto);
    boton.setAttribute('aria-expanded', String(abierto));
  }

  boton.addEventListener('click', () => abrirMenu(!menu.classList.contains('abierto')));

  encabezado.append(marca, boton, menu, usuario);

  const contenido = document.createElement('main');
  contenido.className = 'contenido';

  raiz.replaceChildren(encabezado, contenido);

  let textoIngreso: string | undefined;
  let conSesion = false;

  /** Con un texto propio para ingresar (la portada) y sin sesión, el encabezado no lleva menú ni su botón. */
  function ajustarEncabezado(): void {
    const portada = textoIngreso !== undefined;
    ingresar.textContent = textoIngreso ?? TEXTO_INGRESAR;
    ingresar.classList.toggle('btn-acceso', portada);
    ingresar.classList.toggle('btn-fantasma', !portada);
    encabezado.classList.toggle('encabezado-portada', portada && !conSesion);
  }

  function mostrarSesion(sesion: Sesion | null): void {
    conSesion = sesion !== null;
    ajustarEncabezado();
    const grupo = grupoDeSesion(sesion);
    for (const { item, conSesion } of items) {
      item.hidden = item.dataset['grupo'] !== grupo || (conSesion && sesion === null);
    }
    nombre.hidden = sesion === null;
    salir.hidden = sesion === null;
    ingresar.hidden = sesion !== null;
    nombre.textContent = sesion?.username ?? '';
  }

  mostrarSesion(null);

  return {
    contenido,
    mostrar(pagina) {
      textoIngreso = pagina.textoIngreso;
      ajustarEncabezado();
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
