// Mockups de C-17: arma el marco de la aplicación (encabezado, barra lateral y pie, como en C-39 y C-48) según la
// sesión del mockup (data-rol PACIENTE, RECEPCIONISTA o vacío) y dibuja los íconos marcados con data-icono.
// Datos 100 % ficticios.
(function () {
  const LOGO = '../../../../design-system/assets/logo.svg?v=5';

  const ICONOS = {
    menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
    cerrar: '<path d="M6 6l12 12M18 6L6 18"/>',
    agenda: '<rect x="3" y="4" width="18" height="17" rx="3"/><path d="M8 2v4M16 2v4M3 10h18"/><path d="M8 14h.01M12 14h.01M16 14h.01"/>',
    'lista-espera': '<path d="M9 6h11M9 12h11M9 18h11"/><circle cx="4.5" cy="6" r="1"/><circle cx="4.5" cy="12" r="1"/><circle cx="4.5" cy="18" r="1"/>',
    coberturas: '<path d="M12 3l7 3v5c0 4.5-3 8.5-7 10-4-1.5-7-5.5-7-10V6z"/>',
    reportes: '<path d="M4 20V10M10 20V4M16 20v-7M21 20H3"/>',
    caja: '<rect x="3" y="6" width="18" height="13" rx="2"/><path d="M3 10h18M7 15h3"/>',
    configuracion: '<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1"/>',
    reserva: '<rect x="3" y="4" width="18" height="17" rx="3"/><path d="M8 2v4M16 2v4M3 10h18M12 13v5M9.5 15.5h5"/>',
    'mis-turnos': '<path d="M9 6h11M9 12h11M9 18h11"/><path d="M4 6h.01M4 12h.01M4 18h.01"/>',
    'mi-cuenta': '<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 3.6-6.5 8-6.5s8 2.5 8 6.5"/>',
    llave: '<circle cx="8" cy="15" r="4"/><path d="M10.8 12.2L20 3M16 7l3 3M14 9l2 2"/>',
    mensaje: '<path d="M21 12a8 8 0 0 1-11.6 7.1L4 20.5l1.4-4.6A8 8 0 1 1 21 12z"/>',
    'mensaje-no': '<path d="M21 12a8 8 0 0 1-11.6 7.1L4 20.5l1.4-4.6A8 8 0 1 1 21 12z"/><path d="M4 4l16 16"/>',
    telefono: '<path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z"/>',
    persona: '<circle cx="12" cy="7" r="3.5"/><path d="M5 21v-1a7 7 0 0 1 14 0v1"/>',
  };

  function icono(nombre) {
    return `<svg class="icono" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">${ICONOS[nombre]}</svg>`;
  }

  const MENUS = {
    PACIENTE: [['reserva', 'Reservar turno'], ['mis-turnos', 'Mis turnos'], ['mi-cuenta', 'Mi cuenta']],
    RECEPCIONISTA: [['agenda', 'Agenda'], ['lista-espera', 'Lista de espera'], ['coberturas', 'Coberturas'],
      ['reportes', 'Reportes'], ['caja', 'Caja'], ['configuracion', 'Configuración']],
  };

  function marco() {
    const pagina = document.getElementById('pagina');
    if (!pagina) return;
    const usuario = pagina.dataset.usuario || '';
    const rol = pagina.dataset.rol || '';
    const activa = pagina.dataset.activa;
    const app = document.createElement('div');
    app.className = 'app';
    const pie = '<footer class="pie"><span data-brand-name>Turnos odontología</span></footer>';
    if (rol === '') {
      // Sin sesión (C-39): sin barra lateral, "Reservar turno" e "Ingresar" en el encabezado.
      app.innerHTML =
        `<header class="cabecera"><a class="marca" href="#/inicio"><img src="${LOGO}" alt="Turnos odontología" class="logo"></a>` +
        '<div class="acciones-cabecera"><a class="enlace-cabecera" href="#/reserva">Reservar turno</a>' +
        '<a class="btn-sobre-azul" href="#/mi-cuenta" aria-current="page">Ingresar</a></div></header>' +
        `<div class="cuerpo fondo-suave"><main class="contenido"></main></div>${pie}`;
    } else {
      const enlaces = MENUS[rol].map(([ruta, texto]) =>
        `<li><a href="#/${ruta}" data-ruta="${ruta}"${ruta === activa ? ' aria-current="page"' : ''}>${icono(ruta)}${texto}</a></li>`).join('');
      const cuentaPersonal = rol === 'PACIENTE' ? '' :
        `<nav class="lateral-cuenta" aria-label="Tu cuenta"><ul><li><a href="#/cambiar-contrasena">${icono('llave')}Mi contraseña</a></li></ul></nav>`;
      app.innerHTML =
        `<header class="cabecera"><button type="button" class="btn-sobre-azul menu-boton" aria-controls="menu-principal" aria-expanded="false">${icono('menu')}Menú</button>` +
        `<a class="marca" href="#/inicio"><img src="${LOGO}" alt="Turnos odontología" class="logo"></a>` +
        `<div class="acciones-cabecera"><span class="avatar" aria-hidden="true">${usuario.slice(0, 2).toUpperCase()}</span><span class="usuario-nombre">${usuario}</span><button type="button" class="btn-sobre-azul">Salir</button></div></header>` +
        `<div class="cuerpo con-lateral fondo-suave"><aside id="menu-principal" class="lateral"><div class="cajon-cabecera"><span>Menú</span><button type="button" class="cerrar" aria-label="Cerrar el menú">${icono('cerrar')}</button></div>` +
        `<nav aria-label="Principal"><ul>${enlaces}</ul></nav>${cuentaPersonal}</aside>` +
        `<div class="velo" aria-hidden="true"></div><main class="contenido"></main></div>${pie}`;
    }
    pagina.replaceWith(app);
    app.querySelector('main').append(...pagina.childNodes);
  }

  marco();
  document.querySelectorAll('[data-icono]').forEach((e) => { e.insertAdjacentHTML('afterbegin', icono(e.dataset.icono)); });
})();
