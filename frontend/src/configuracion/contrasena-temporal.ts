import { abrirDialogo } from '../shared/ui/dialogo.ts';
import { crearIcono } from '../shared/ui/iconos.ts';

/**
 * Muestra la contraseña temporal de una cuenta recién creada o restablecida (spec "Contraseña temporal mostrada
 * una sola vez", C-41). Al cerrar el diálogo, la contraseña se borra de la página; nunca se guarda en el navegador.
 */
export function mostrarContrasenaTemporal(username: string, contrasena: string, restablecida: boolean): void {
  const datos = document.createElement('dl');
  datos.className = 'detalle-lista';
  const dt = document.createElement('dt');
  dt.textContent = 'Usuario';
  const dd = document.createElement('dd');
  dd.className = 'dato-literal';
  dd.textContent = username;
  datos.append(dt, dd);

  const caja = document.createElement('div');
  caja.className = 'contrasena-caja';
  const codigo = document.createElement('code');
  codigo.setAttribute('aria-label', 'Contraseña temporal');
  codigo.textContent = contrasena;
  const copiar = document.createElement('button');
  copiar.type = 'button';
  copiar.className = 'btn btn-chico';
  copiar.append(crearIcono('copiar'), 'Copiar');
  const copiado = document.createElement('span');
  copiado.className = 'ayuda';
  copiado.setAttribute('role', 'status');
  caja.append(codigo, copiar, copiado);

  const aviso = document.createElement('p');
  aviso.className = 'aviso aviso-alerta';
  aviso.setAttribute('role', 'note');
  const fuerte = document.createElement('strong');
  fuerte.textContent = 'Esta contraseña no se vuelve a mostrar.';
  aviso.append(fuerte, ' Pasásela a la persona por un medio seguro.');

  const explicacion = document.createElement('p');
  explicacion.className = 'ayuda';
  explicacion.textContent =
    (restablecida ? 'Las sesiones abiertas de esta cuenta se cerraron. ' : '') +
    'La persona va a tener que elegir una contraseña nueva la primera vez que ingrese.';

  const { formulario, descartar, cerrar } = abrirDialogo({
    titulo: restablecida ? 'Contraseña restablecida' : 'Cuenta creada',
    contenido: [datos, caja, aviso, explicacion],
    textoDescartar: 'Cerrar',
    textoPrincipal: 'Listo',
    alCerrar() {
      // La contraseña no queda en la página después de cerrar.
      codigo.textContent = '';
    },
  });
  // Un solo botón: "Listo" cierra el diálogo.
  descartar.hidden = true;
  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    cerrar();
  });

  copiar.addEventListener('click', () => {
    const portapapeles = typeof navigator === 'undefined' ? undefined : navigator.clipboard;
    if (portapapeles !== undefined && typeof portapapeles.writeText === 'function') {
      portapapeles.writeText(contrasena).then(
        () => {
          copiado.textContent = 'Copiada.';
        },
        () => seleccionar(codigo, copiado),
      );
    } else {
      seleccionar(codigo, copiado);
    }
  });
}

/** Sin portapapeles (por ejemplo, fuera de un contexto seguro), deja el texto seleccionado para copiarlo a mano. */
function seleccionar(codigo: HTMLElement, copiado: HTMLElement): void {
  const seleccion = window.getSelection();
  if (seleccion !== null) {
    const rango = document.createRange();
    rango.selectNodeContents(codigo);
    seleccion.removeAllRanges();
    seleccion.addRange(rango);
  }
  copiado.textContent = 'Seleccionada: copiala con Ctrl+C.';
}
