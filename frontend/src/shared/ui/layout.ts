import type { Pagina } from '../../pages/pagina.ts';
import type { Sesion } from '../sesion/sesion.ts';

export interface Layout {
  readonly contenido: HTMLElement;
  /** Marca el enlace de la página activa y cierra el menú. */
  mostrar(pagina: Pagina): void;
  /** Muestra el usuario y el botón "Salir", o los oculta si no hay sesión. */
  mostrarSesion(sesion: Sesion | null): void;
}

export interface OpcionesLayout {
  alSalir(): void;
}

const ID_MENU = 'menu-principal';

/** Arma el encabezado con el menú y el área de contenido dentro de `raiz`. */
export function crearLayout(raiz: HTMLElement, paginas: readonly Pagina[], opciones: OpcionesLayout): Layout {
  const encabezado = document.createElement('header');
  encabezado.className = 'encabezado';

  const marca = document.createElement('span');
  marca.className = 'marca';
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
  const enlaces = paginas.map((pagina) => {
    const enlace = document.createElement('a');
    enlace.href = `#/${pagina.ruta}`;
    enlace.textContent = pagina.titulo;
    enlace.dataset['ruta'] = pagina.ruta;
    const item = document.createElement('li');
    item.append(enlace);
    lista.append(item);
    return enlace;
  });
  menu.append(lista);

  const usuario = document.createElement('div');
  usuario.className = 'usuario';
  usuario.hidden = true;
  const nombre = document.createElement('span');
  nombre.className = 'usuario-nombre';
  const salir = document.createElement('button');
  salir.type = 'button';
  salir.className = 'boton-secundario';
  salir.textContent = 'Salir';
  salir.addEventListener('click', () => opciones.alSalir());
  usuario.append(nombre, salir);

  function abrirMenu(abierto: boolean): void {
    menu.classList.toggle('abierto', abierto);
    boton.setAttribute('aria-expanded', String(abierto));
  }

  boton.addEventListener('click', () => abrirMenu(!menu.classList.contains('abierto')));

  encabezado.append(marca, boton, menu, usuario);

  const contenido = document.createElement('main');
  contenido.className = 'contenido';

  raiz.replaceChildren(encabezado, contenido);

  return {
    contenido,
    mostrar(pagina) {
      for (const enlace of enlaces) {
        if (enlace.dataset['ruta'] === pagina.ruta) {
          enlace.setAttribute('aria-current', 'page');
        } else {
          enlace.removeAttribute('aria-current');
        }
      }
      abrirMenu(false);
    },
    mostrarSesion(sesion) {
      usuario.hidden = sesion === null;
      nombre.textContent = sesion?.username ?? '';
    },
  };
}
