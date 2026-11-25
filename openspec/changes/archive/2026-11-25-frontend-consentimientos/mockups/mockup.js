// Mockups de C-48: arma el marco de la aplicación (encabezado, barra lateral y pie, como el mockup.js de C-47, sin el
// odontograma) y dibuja los íconos marcados con data-icono. Suma los íconos firma, verificado, alterado y candado. Datos 100 % ficticios.
(function () {
  const LOGO = '../../../../design-system/assets/logo.svg?v=5';

  const ICONOS = {
    menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
    cerrar: '<path d="M6 6l12 12M18 6L6 18"/>',
    agenda: '<rect x="3" y="4" width="18" height="17" rx="3"/><path d="M8 2v4M16 2v4M3 10h18"/><path d="M8 14h.01M12 14h.01M16 14h.01"/>',
    pacientes: '<path d="M7.5 3C5 3 3.5 5 3.5 7.5c0 3 1.5 4.5 2 7 .6 3 1 6.5 2.5 6.5s2-3 2.5-5c.3-1.2.8-1.5 1.5-1.5s1.2.3 1.5 1.5c.5 2 1 5 2.5 5s1.9-3.5 2.5-6.5c.5-2.5 2-4 2-7C20.5 5 19 3 16.5 3c-2 0-3 1-4.5 1s-2.5-1-4.5-1z"/>',
    configuracion: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>',
    llave: '<circle cx="8" cy="15" r="4"/><path d="M10.8 12.2L20 3M16 7l3 3M14 9l2 2"/>',
    reloj: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    documento: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5M9 13h6M9 17h6"/>',
    ver: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
    firma: '<path d="M16.5 3.5l4 4L9 19l-5 1 1-5z"/><path d="M14 6l4 4M3 22h18"/>',
    verificado: '<path d="M12 3l7 3v5c0 4.5-3 8.5-7 10-4-1.5-7-5.5-7-10V6z"/><path d="M8.5 12l2.5 2.5 4.5-5"/>',
    alterado: '<path d="M12 3l9.5 17h-19z"/><path d="M12 10v4M12 17h.01"/>',
    candado: '<rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/><path d="M12 15v2"/>',
  };

  function icono(nombre) {
    return `<svg class="icono" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">${ICONOS[nombre]}</svg>`;
  }

  /* ---- Marco de la aplicación ---- */
  function marco() {
    const pagina = document.getElementById('pagina');
    if (!pagina) return;
    const usuario = pagina.dataset.usuario;
    const rol = pagina.dataset.rol;
    const activa = pagina.dataset.activa;
    const opciones = [['agenda', 'Agenda'], ['pacientes', 'Pacientes'], ['configuracion', 'Configuración']]
      .filter(([ruta]) => ruta !== 'pacientes' || rol === 'ADMIN' || rol === 'ODONTOLOGO');
    const enlaces = opciones.map(([ruta, texto]) =>
      `<li data-grupo="personal"><a href="#/${ruta}" data-ruta="${ruta}"${ruta === activa ? ' aria-current="page"' : ''}>${icono(ruta)}${texto}</a></li>`).join('');
    const app = document.createElement('div');
    app.className = 'app';
    app.innerHTML =
      `<header class="cabecera"><button type="button" class="btn-sobre-azul menu-boton" aria-controls="menu-principal" aria-expanded="false">${icono('menu')}Menú</button>` +
      `<a class="marca" href="#/inicio"><img src="${LOGO}" alt="Turnos odontología" class="logo"></a>` +
      `<div class="acciones-cabecera"><span class="avatar" aria-hidden="true">${usuario.charAt(0).toUpperCase()}</span><span class="usuario-nombre">${usuario}</span><button type="button" class="btn-sobre-azul">Salir</button></div></header>` +
      `<div class="cuerpo con-lateral fondo-suave"><aside id="menu-principal" class="lateral"><div class="cajon-cabecera"><span>Menú</span><button type="button" class="cerrar" aria-label="Cerrar el menú">${icono('cerrar')}</button></div>` +
      `<nav aria-label="Principal"><ul>${enlaces}</ul></nav>` +
      `<nav class="lateral-cuenta" aria-label="Tu cuenta"><ul><li><a href="#/cambiar-contrasena">${icono('llave')}Mi contraseña</a></li></ul></nav></aside>` +
      `<div class="velo" aria-hidden="true"></div><main class="contenido"></main></div>` +
      `<footer class="pie"><span data-brand-name>Turnos odontología</span></footer>`;
    pagina.replaceWith(app);
    app.querySelector('main').append(...pagina.childNodes);
  }

  /* ---- Contadores "N/20.000" con separador de miles de es-AR (design D3) ---- */
  function contadores() {
    const formato = new Intl.NumberFormat('es-AR');
    document.querySelectorAll('[data-contador]').forEach((campo) => {
      const salida = document.getElementById(campo.dataset.contador);
      const limite = Number(campo.dataset.limite);
      const largo = Number(campo.dataset.largo || campo.value.length);
      salida.textContent = `${formato.format(largo)}/${formato.format(limite)}`;
      salida.classList.toggle('excedido', largo > limite);
    });
  }

  /* ---- Texto plano: se asigna con textContent, nunca como HTML ---- */
  function textos() {
    document.querySelectorAll('template[data-texto]').forEach((plantilla) => {
      const destino = document.getElementById(plantilla.dataset.texto);
      destino.textContent = plantilla.content.textContent.replace(/^\n/, '').replace(/\n\s*$/, '');
    });
  }

  /* La pestaña elegida queda a la vista aunque la tira de pestañas tenga desplazamiento (diez pestañas). */
  function pestanaVisible() {
    const elegida = document.querySelector('.pestanas-config [aria-selected="true"]');
    if (elegida) {
      const tira = elegida.parentElement;
      tira.scrollLeft = tira.scrollWidth;
    }
  }

  marco();
  pestanaVisible();
  textos();
  contadores();
  document.querySelectorAll('[data-icono]').forEach((e) => { e.insertAdjacentHTML('afterbegin', icono(e.dataset.icono)); });
})();
