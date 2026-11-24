/* Mockups de C-44: arma el marco de la aplicación (encabezado, barra lateral y pie, como el layout de C-39 y C-41)
   y dibuja el odontograma en SVG con la geometría que propone design D4. Datos 100 % ficticios. */
(function () {
  const NS = 'http://www.w3.org/2000/svg';
  const LOGO = '../../../../design-system/assets/logo.svg?v=5';

  const ICONOS = {
    menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
    cerrar: '<path d="M6 6l12 12M18 6L6 18"/>',
    agenda: '<rect x="3" y="4" width="18" height="17" rx="3"/><path d="M8 2v4M16 2v4M3 10h18"/><path d="M8 14h.01M12 14h.01M16 14h.01"/>',
    pacientes: '<path d="M7.5 3C5 3 3.5 5 3.5 7.5c0 3 1.5 4.5 2 7 .6 3 1 6.5 2.5 6.5s2-3 2.5-5c.3-1.2.8-1.5 1.5-1.5s1.2.3 1.5 1.5c.5 2 1 5 2.5 5s1.9-3.5 2.5-6.5c.5-2.5 2-4 2-7C20.5 5 19 3 16.5 3c-2 0-3 1-4.5 1s-2.5-1-4.5-1z"/>',
    configuracion: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>',
    llave: '<circle cx="8" cy="15" r="4"/><path d="M10.8 12.2L20 3M16 7l3 3M14 9l2 2"/>',
    reloj: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  };

  function icono(nombre) {
    return `<svg class="icono" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">${ICONOS[nombre]}</svg>`;
  }
  window.iconoMockup = icono;

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

  /* ---- Odontograma ---- */
  const T = 40; // lado de una pieza
  const SEP = 6;
  const MEDIA = 14;
  const NOMBRE_CARA = { O: 'oclusal', M: 'mesial', D: 'distal', V: 'vestibular', L: 'lingual', P: 'palatina' };
  const ESTADOS = {
    SANO: ['Sano', 'S'], CARIES: ['Caries', 'C'], OBTURADO: ['Obturado', 'O'], A_TRATAR: ['A tratar', 'T'],
    AUSENTE: ['Ausente', 'A'], CORONA: ['Corona', 'Co'], ENDODONCIA: ['Endodoncia', 'E'],
  };
  window.ESTADOS_ODONTO = ESTADOS;
  const FILAS = {
    permanente: [[18, 17, 16, 15, 14, 13, 12, 11, 21, 22, 23, 24, 25, 26, 27, 28],
      [48, 47, 46, 45, 44, 43, 42, 41, 31, 32, 33, 34, 35, 36, 37, 38]],
    temporal: [[55, 54, 53, 52, 51, 61, 62, 63, 64, 65], [85, 84, 83, 82, 81, 71, 72, 73, 74, 75]],
  };

  function el(nombre, atributos) {
    const e = document.createElementNS(NS, nombre);
    for (const [k, v] of Object.entries(atributos)) e.setAttribute(k, String(v));
    return e;
  }

  function puntos(lista) {
    return lista.map((p) => p.join(',')).join(' ');
  }

  /** Caras de una pieza en (x, y): arriba, abajo, izquierda, derecha y centro, con la letra de cada una. */
  function caras(pieza, x, y) {
    const cuadrante = Math.floor(pieza / 10);
    const superior = [1, 2, 5, 6].includes(cuadrante);
    const mitadIzquierda = [1, 4, 5, 8].includes(cuadrante); // derecha del paciente, a la izquierda de quien mira
    const a = 12;
    const b = 28;
    return [
      [superior ? 'V' : 'L', [[x, y], [x + T, y], [x + b, y + a], [x + a, y + a]]],
      [superior ? 'P' : 'V', [[x + a, y + b], [x + b, y + b], [x + T, y + T], [x, y + T]]],
      [mitadIzquierda ? 'D' : 'M', [[x, y], [x + a, y + a], [x + a, y + b], [x, y + T]]],
      [mitadIzquierda ? 'M' : 'D', [[x + T, y], [x + T, y + T], [x + b, y + b], [x + b, y + a]]],
      ['O', [[x + a, y + a], [x + b, y + a], [x + b, y + b], [x + a, y + b]]],
    ];
  }

  /** Un SVG con dos filas (arriba y abajo). Con `conMedia`, las dos mitades de la boca y la línea media. */
  function dibujarSvg(arriba, abajo, etiqueta, conMedia, datos, foco) {
    const { vigente, pendientes, elegida } = datos;
    const porFila = arriba.length;
    const extra = conMedia ? MEDIA : 0;
    const ancho = porFila * (T + SEP) - SEP + extra;
    const alto = 140;
    const svg = el('svg', { viewBox: `-4 0 ${ancho + 8} ${alto}`, role: 'group', 'aria-label': etiqueta });
    svg.style.maxWidth = `${Math.round((ancho + 8) * 1.4)}px`; // piezas de 56 px como máximo
    if (conMedia) {
      const mediaX = (porFila / 2) * (T + SEP) - SEP + MEDIA / 2;
      svg.append(el('line', { class: 'linea-media', x1: mediaX, y1: 14, x2: mediaX, y2: alto - 14 }));
    }
    [[arriba, 20, 13], [abajo, 80, alto - 4]].forEach(([fila, y, yNumero]) => {
      fila.forEach((pieza, i) => {
        const x = i * (T + SEP) + (conMedia && i >= porFila / 2 ? MEDIA : 0);
        const estado = vigente[pieza] || {};
        const pend = pendientes[pieza] || {};
        const grupo = el('g', { role: 'group', 'aria-label': `Pieza ${pieza}` });
        const numero = el('text', { class: 'numero' + (elegida.startsWith(pieza + ':') ? ' elegida' : ''), x: x + T / 2, y: yNumero });
        numero.textContent = pieza;
        grupo.append(numero);
        const completa = pend.completa || estado.completa;
        caras(pieza, x, y).forEach(([cara, pts]) => {
          const valor = pend[cara] || estado[cara] || completa;
          const clases = ['cara'];
          if (pend[cara] || pend.completa) clases.push('pendiente');
          if (elegida === `${pieza}:${cara}` || elegida === `${pieza}:completa`) clases.push('elegida');
          const nombreEstado = valor ? ESTADOS[valor][0] : 'sin estado';
          const poligono = el('polygon', {
            class: clases.join(' '), points: puntos(pts), role: 'button', tabindex: foco.primera ? 0 : -1,
            'aria-label': `Pieza ${pieza}, cara ${NOMBRE_CARA[cara]}: ${nombreEstado}`,
          });
          foco.primera = false;
          if (valor) poligono.setAttribute('data-estado', valor);
          grupo.append(poligono);
          if (cara === 'O' && valor && !['AUSENTE', 'CORONA', 'ENDODONCIA'].includes(valor)) {
            const letra = el('text', { class: 'letra', x: x + T / 2, y: y + T / 2 });
            letra.textContent = ESTADOS[valor][1];
            grupo.append(letra);
          }
        });
        if (completa === 'AUSENTE') {
          grupo.append(el('line', { class: 'marca-ausente', x1: x + 3, y1: y + 3, x2: x + T - 3, y2: y + T - 3 }));
          grupo.append(el('line', { class: 'marca-ausente', x1: x + T - 3, y1: y + 3, x2: x + 3, y2: y + T - 3 }));
        } else if (completa === 'CORONA') {
          grupo.append(el('rect', { class: 'marca-corona', x: x - 2, y: y - 2, width: T + 4, height: T + 4, rx: 6 }));
        } else if (completa === 'ENDODONCIA') {
          grupo.append(el('line', { class: 'marca-endodoncia', x1: x + T / 2, y1: y - 3, x2: x + T / 2, y2: y + T + 3 }));
        }
        svg.append(grupo);
      });
    });
    return svg;
  }

  /** Desde 48 rem, la boca entera en un SVG; en pantallas angostas, dos SVG: lado derecho y lado izquierdo del paciente (D4). */
  function dibujarOdontograma(contenedor) {
    const denticion = contenedor.dataset.denticion || 'permanente';
    const datos = {
      vigente: JSON.parse(contenedor.dataset.vigente || '{}'),
      pendientes: JSON.parse(contenedor.dataset.pendientes || '{}'),
      elegida: contenedor.dataset.elegida || '',
    };
    const [arriba, abajo] = FILAS[denticion];
    const nombre = denticion === 'permanente' ? 'permanentes' : 'temporales';
    const foco = { primera: true };
    const dibujo = document.createElement('div');
    dibujo.className = 'odontograma-dibujo';
    if (window.matchMedia('(min-width: 48rem)').matches) {
      dibujo.append(dibujarSvg(arriba, abajo, `Odontograma, piezas ${nombre}`, true, datos, foco));
    } else {
      const mitad = arriba.length / 2;
      [['Lado derecho del paciente', 0, mitad], ['Lado izquierdo del paciente', mitad, arriba.length]].forEach(([titulo, desde, hasta]) => {
        const rotulo = document.createElement('p');
        rotulo.className = 'odontograma-lado';
        rotulo.textContent = titulo;
        dibujo.append(rotulo, dibujarSvg(arriba.slice(desde, hasta), abajo.slice(desde, hasta), `${titulo}, piezas ${nombre}`, false, datos, foco));
      });
    }
    contenedor.prepend(dibujo);
  }

  function leyenda(lista) {
    lista.innerHTML = Object.entries(ESTADOS).map(([clave, [nombre, letra]]) =>
      `<li><span class="muestra" data-estado="${clave}" aria-hidden="true">${letra}</span>${nombre}</li>`).join('');
  }

  marco();
  document.querySelectorAll('.odontograma').forEach(dibujarOdontograma);
  document.querySelectorAll('.leyenda').forEach(leyenda);
  document.querySelectorAll('[data-icono]').forEach((e) => { e.insertAdjacentHTML('afterbegin', icono(e.dataset.icono)); });
})();
