/* Mockups de C-15: arma el marco de la aplicación (encabezado, barra lateral y pie, como el layout de C-39 y C-41)
   según el rol de la página, y dibuja los íconos de [data-icono]. Basado en el mockup.js de C-49. Suma el ícono
   "espera" (Lista de espera, después de Agenda) y el rol VISITANTE (sin sesión). Datos 100 % ficticios. */
(function () {
  const LOGO = '../../../../design-system/assets/logo.svg?v=5';

  const ICONOS = {
    menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
    cerrar: '<path d="M6 6l12 12M18 6L6 18"/>',
    agenda: '<rect x="3" y="4" width="18" height="17" rx="3"/><path d="M8 2v4M16 2v4M3 10h18"/><path d="M8 14h.01M12 14h.01M16 14h.01"/>',
    reservar: '<rect x="3" y="4" width="18" height="17" rx="3"/><path d="M8 2v4M16 2v4M3 10h18"/><path d="M12 13v5M9.5 15.5h5"/>',
    turnos: '<path d="M9 6h11M9 12h11M9 18h11"/><path d="M4 6h.01M4 12h.01M4 18h.01"/>',
    pacientes: '<path d="M7.5 3C5 3 3.5 5 3.5 7.5c0 3 1.5 4.5 2 7 .6 3 1 6.5 2.5 6.5s2-3 2.5-5c.3-1.2.8-1.5 1.5-1.5s1.2.3 1.5 1.5c.5 2 1 5 2.5 5s1.9-3.5 2.5-6.5c.5-2.5 2-4 2-7C20.5 5 19 3 16.5 3c-2 0-3 1-4.5 1s-2.5-1-4.5-1z"/>',
    reportes: '<path d="M4 20V10M10 20V4M16 20v-7M21 20H3"/>',
    configuracion: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>',
    llave: '<circle cx="8" cy="15" r="4"/><path d="M10.8 12.2L20 3M16 7l3 3M14 9l2 2"/>',
    volver: '<path d="M15 18l-6-6 6-6"/>',
    // C-49 (Juan Sebastián): credencial de Coberturas, para mostrar el orden final del menú.
    credencial: '<rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="9" cy="11" r="2"/><path d="M6 16c.5-1.4 1.6-2 3-2s2.5.6 3 2M14 10h4M14 14h3"/>',
    // C-15: lista de espera (reloj de arena).
    espera: '<path d="M6 3h12M6 21h12M7 3c0 5 10 7 10 9s-10 4-10 9M17 3c0 5-10 7-10 9s10 4 10 9"/>',
    cuenta: '<circle cx="12" cy="8" r="4"/><path d="M4 21c1-4 4.5-6 8-6s7 2 8 6"/>',
  };

  function icono(nombre) {
    return `<svg class="icono" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">${ICONOS[nombre]}</svg>`;
  }

  const MENUS = {
    ADMIN: [['agenda', 'Agenda'], ['lista-espera', 'Lista de espera', 'espera'], ['pacientes', 'Pacientes'], ['coberturas', 'Coberturas', 'credencial'], ['reportes', 'Reportes'], ['configuracion', 'Configuración']],
    RECEPCIONISTA: [['agenda', 'Agenda'], ['lista-espera', 'Lista de espera', 'espera'], ['coberturas', 'Coberturas', 'credencial'], ['reportes', 'Reportes'], ['configuracion', 'Configuración']],
    ODONTOLOGO: [['agenda', 'Agenda'], ['pacientes', 'Pacientes'], ['configuracion', 'Configuración']],
    VISITANTE: [['reserva', 'Reservar turno', 'reservar']],
    PACIENTE: [['reserva', 'Reservar turno', 'reservar'], ['mis-turnos', 'Mis turnos', 'turnos']],
  };

  function marco() {
    const pagina = document.getElementById('pagina');
    if (!pagina) return;
    const { usuario, rol, activa } = pagina.dataset;
    const personal = rol !== 'PACIENTE' && rol !== 'VISITANTE';
    const grupo = personal ? 'personal' : 'paciente';
    const enlaces = MENUS[rol].map(([ruta, texto, ico]) =>
      `<li data-grupo="${grupo}"><a href="#/${ruta}" data-ruta="${ruta}"${ruta === activa ? ' aria-current="page"' : ''}>${icono(ico || ruta)}${texto}</a></li>`).join('');
    const cuenta = personal
      ? `<nav class="lateral-cuenta" aria-label="Tu cuenta"><ul><li><a href="#/cambiar-contrasena">${icono('llave')}Mi contraseña</a></li></ul></nav>`
      : '';
    const app = document.createElement('div');
    app.className = 'app';
    app.innerHTML =
      `<header class="cabecera"><button type="button" class="btn-sobre-azul menu-boton" aria-controls="menu-principal" aria-expanded="false">${icono('menu')}Menú</button>` +
      `<a class="marca" href="#/inicio"><img src="${LOGO}" alt="Turnos odontología" class="logo"></a>` +
      (rol === 'VISITANTE' ? '<div class="acciones-cabecera"><a class="enlace-sobre-azul" href="#/mi-cuenta">Ingresar</a></div></header>' : `<div class="acciones-cabecera"><span class="avatar" aria-hidden="true">${usuario.charAt(0).toUpperCase()}</span><span class="usuario-nombre">${usuario}</span><button type="button" class="btn-sobre-azul">Salir</button></div></header>`) +
      `<div class="cuerpo con-lateral fondo-suave"><aside id="menu-principal" class="lateral"><div class="cajon-cabecera"><span>Menú</span><button type="button" class="cerrar" aria-label="Cerrar el menú">${icono('cerrar')}</button></div>` +
      `<nav aria-label="Principal"><ul>${enlaces}</ul></nav>${cuenta}</aside>` +
      `<div class="velo" aria-hidden="true"></div><main class="contenido"></main></div>` +
      `<footer class="pie"><span data-brand-name>Turnos odontología</span></footer>`;
    pagina.replaceWith(app);
    app.querySelector('main').append(...pagina.childNodes);
  }

  marco();
  document.querySelectorAll('[data-icono]').forEach((e) => { e.insertAdjacentHTML('afterbegin', icono(e.dataset.icono)); });
})();
