// Mockups de C-46: dibuja el mapa de dientes, la leyenda, las vistas grandes y las tablas de sitios desde
// atributos data-*. Solo para las vistas previas; en el apply la sección la arma frontend/src/ficha/periodontograma.ts.
// Los dientes son dibujos propios y simplificados por tipo (incisivo, canino, premolar, molar).
(function () {
  const ARRIBA = [[18, 17, 16, 15, 14, 13, 12, 11], [21, 22, 23, 24, 25, 26, 27, 28]];
  const ABAJO = [[48, 47, 46, 45, 44, 43, 42, 41], [31, 32, 33, 34, 35, 36, 37, 38]];
  const NOMBRE = { SANO: 'Sano', LEVE: 'Leve', MODERADO: 'Moderado', SEVERO: 'Severo', AUSENTE: 'Ausente', SIN_CARGAR: 'Sin cargar', CARGANDO: 'En carga' };
  // Sitios de ejemplo según el estado, cuando el mockup no trae los valores de la pieza: [profundidad, recesión].
  const EJEMPLO = { SANO: [2, -2], LEVE: [3, -1], MODERADO: [4, 0], SEVERO: [5, 2], CARGANDO: [2, -2] };

  // Geometría en un lienzo de 40 × 100 con la raíz arriba (diente superior). El límite amelocementario está en y = 55.
  const LAC = 55;
  const MM = 3.2;
  const FORMAS = {
    incisivo: {
      corona: 'M9 55 L31 55 L33 78 Q33 93 20 95 Q7 93 7 78 Z',
      raices: ['M10 55 Q11 18 20 8 Q29 18 30 55 Z'],
    },
    canino: {
      corona: 'M8 55 L32 55 Q35 72 26 88 L20 97 L14 88 Q5 72 8 55 Z',
      raices: ['M10 55 Q11 10 20 1 Q29 10 30 55 Z'],
    },
    premolar: {
      corona: 'M7 55 L33 55 Q37 72 31 89 Q20 96 9 89 Q3 72 7 55 Z',
      raices: ['M11 55 Q12 16 20 9 Q28 16 29 55 Z'],
    },
    premolarDoble: {
      corona: 'M7 55 L33 55 Q37 72 31 89 Q20 96 9 89 Q3 72 7 55 Z',
      raices: ['M9 55 Q9 20 14 12 Q19 22 19 55 Z', 'M21 55 Q21 22 26 12 Q31 20 31 55 Z'],
    },
    molarSuperior: {
      corona: 'M3 55 L37 55 Q40 74 35 90 L28 94 L20 90 L12 94 L5 90 Q0 74 3 55 Z',
      raices: ['M5 55 Q4 22 10 15 Q15 24 15 55 Z', 'M16 55 Q16 14 21 8 Q25 16 24 55 Z', 'M26 55 Q26 24 31 15 Q37 22 35 55 Z'],
    },
    molarInferior: {
      corona: 'M3 55 L37 55 Q40 74 35 90 L28 94 L20 90 L12 94 L5 90 Q0 74 3 55 Z',
      raices: ['M5 55 Q4 20 11 12 Q17 22 17 55 Z', 'M23 55 Q23 22 29 12 Q36 20 35 55 Z'],
    },
  };

  function forma(numero) {
    const cuadrante = Math.floor(numero / 10);
    const posicion = numero % 10;
    const superior = cuadrante === 1 || cuadrante === 2;
    if (posicion <= 2) return FORMAS.incisivo;
    if (posicion === 3) return FORMAS.canino;
    if (posicion <= 5) return posicion === 4 && superior ? FORMAS.premolarDoble : FORMAS.premolar;
    return superior ? FORMAS.molarSuperior : FORMAS.molarInferior;
  }

  function esSuperior(numero) {
    return [1, 2].includes(Math.floor(numero / 10));
  }

  // x de los tres sitios de una cara (mesial, medio, distal). En los cuadrantes 1 y 4 (lado derecho del paciente,
  // dibujado a la izquierda) el mesial queda a la derecha del dibujo.
  function xSitios(numero) {
    const cuadrante = Math.floor(numero / 10);
    return cuadrante === 1 || cuadrante === 4 ? [32, 20, 8] : [8, 20, 32];
  }

  function limitar(y) {
    return Math.max(1, Math.min(99, y));
  }

  /** Líneas de margen (roja) y fondo de bolsa (azul) para tres sitios [[profundidad, recesión], …]. */
  function lineas(numero, tres) {
    const xs = xSitios(numero);
    const margen = tres.map(([, re], i) => `${xs[i]},${limitar(LAC - re * MM).toFixed(1)}`).join(' ');
    const fondo = tres.map(([pr, re], i) => `${xs[i]},${limitar(LAC - (re + pr) * MM).toFixed(1)}`).join(' ');
    return `<polyline class="perio-linea-fondo" points="${fondo}" />` +
      `<polyline class="perio-linea-margen" points="${margen}" />`;
  }

  /** Un diente en SVG, con la raíz hacia afuera de la boca: arriba en los superiores y abajo en los inferiores. */
  function diente(numero, estado, tres, opciones = {}) {
    const { ancho = 40, alto = 100, grilla = false } = opciones;
    const f = forma(numero);
    const ausente = estado === 'AUSENTE';
    const conLineas = tres && !ausente && estado !== 'SIN_CARGAR';
    let cuerpo = f.raices.map((d) => `<path class="perio-raiz" d="${d}" />`).join('') +
      `<path class="perio-corona" d="${f.corona}" />` +
      `<line class="perio-lac" x1="2" y1="${LAC}" x2="38" y2="${LAC}" />`;
    if (grilla) {
      let rayas = '';
      for (let mm = -5; mm <= 15; mm += 1) {
        const y = LAC - mm * MM;
        if (y > 0 && y < 100) rayas += `<line class="perio-grilla${mm % 5 === 0 ? ' fuerte' : ''}" x1="0" y1="${y.toFixed(1)}" x2="40" y2="${y.toFixed(1)}" />`;
      }
      cuerpo = rayas + cuerpo;
    }
    if (conLineas) cuerpo += lineas(numero, tres);
    if (ausente) cuerpo += '<path class="perio-cruz-svg" d="M6 20 L34 88 M34 20 L6 88" />';
    const giro = esSuperior(numero) ? '' : ' transform="translate(0 100) scale(1 -1)"';
    return `<svg class="perio-diente" viewBox="0 0 40 100" width="${ancho}" height="${alto}" aria-hidden="true" focusable="false">` +
      `<g${giro}>${cuerpo}</g></svg>`;
  }

  function vestibulares(sitios) {
    return sitios.slice(0, 3).map(([pr, re]) => [pr, re]);
  }

  function linguales(sitios) {
    return sitios.slice(3, 6).map(([pr, re]) => [pr, re]);
  }

  function sitiosDe(numero, estado, conocidos) {
    if (conocidos[numero]) return conocidos[numero];
    const base = EJEMPLO[estado];
    if (!base) return null;
    // Pequeñas variaciones por sitio para que las líneas no sean planas.
    const v = [0, 1, 0, 0, -1, 1];
    return v.map((d, i) => [Math.max(0, base[0] + (i % 2 ? d : 0)), base[1] + (i === 2 ? d : 0), false, false]);
  }

  function boton(numero, estado, elegida, error, sitios) {
    const nombre = NOMBRE[estado];
    const tres = sitios ? vestibulares(sitios) : null;
    const etiqueta = `<span class="perio-num">${numero}</span><span class="perio-estado">${nombre}</span>`;
    const figura = diente(numero, estado, tres, { ancho: 34, alto: 85 });
    const contenido = esSuperior(numero) ? figura + etiqueta : etiqueta + figura;
    return `<button type="button" class="perio-pieza" data-estado="${estado}" aria-pressed="${elegida}"` +
      `${error ? ' data-error' : ''} aria-label="Pieza ${numero}: ${nombre}${error ? ', con errores' : ''}">${contenido}</button>`;
  }

  function dibujarMapa(mapa) {
    const piezas = JSON.parse(mapa.dataset.piezas || '{}');
    const conocidos = JSON.parse(mapa.dataset.sitios || '{}');
    const elegida = Number(mapa.dataset.elegida || 0);
    const errores = JSON.parse(mapa.dataset.errores || '[]');
    const arcada = (titulo, cuadrantes) => `<div class="perio-arcada" role="group" aria-label="${titulo}">` +
      `<p class="perio-arcada-titulo" aria-hidden="true">${titulo} · vista vestibular</p>` +
      cuadrantes.map((c) => `<div class="perio-cuadrante">${c.map((n) => {
        const estado = piezas[n] || 'SIN_CARGAR';
        return boton(n, estado, n === elegida, errores.includes(n), sitiosDe(n, estado, conocidos));
      }).join('')}</div>`).join('') +
      '</div>';
    mapa.innerHTML = arcada('Maxilar superior', ARRIBA) + arcada('Maxilar inferior', ABAJO);
  }

  function dibujarLeyenda(lista) {
    const estados = ['SANO', 'LEVE', 'MODERADO', 'SEVERO', 'AUSENTE', 'SIN_CARGAR'].map((e) =>
      `<li><span class="perio-muestra" data-estado="${e}" aria-hidden="true">${diente(11, e, null, { ancho: 14, alto: 35 })}</span>${NOMBRE[e]}</li>`).join('');
    const trazos = '<li><span class="perio-trazo margen" aria-hidden="true"></span>Margen de la encía</li>' +
      '<li><span class="perio-trazo fondo" aria-hidden="true"></span>Fondo de la bolsa</li>' +
      '<li><span class="perio-trazo lac" aria-hidden="true"></span>Límite amelocementario</li>';
    lista.innerHTML = estados + trazos;
  }

  // data-sitios: [[profundidad, recesion, sangrado, placa], ...] en el orden MV, V, DV, ML, L, DL.
  function dibujarVistas(contenedor) {
    const numero = Number(contenedor.dataset.pieza);
    const estado = contenedor.dataset.estado;
    const sitios = JSON.parse(contenedor.dataset.sitios);
    const lingual = esSuperior(numero) ? 'Palatino' : 'Lingual';
    const completo = (s) => s.every(([pr, re]) => pr !== null && re !== null);
    const vista = (titulo, tres) => `<figure class="perio-vista"><figcaption>${titulo}</figcaption>` +
      diente(numero, estado, completo(tres) ? tres : null, { ancho: 96, alto: 240, grilla: true }) + '</figure>';
    contenedor.innerHTML = vista('Vestibular', vestibulares(sitios)) + vista(lingual, linguales(sitios));
  }

  const SITIOS = [['MV', 'Mesiovestibular'], ['V', 'Vestibular'], ['DV', 'Distovestibular'], ['ML', ''], ['L', ''], ['DL', '']];

  function nombreSitio(codigo, superior) {
    const lado = superior ? 'palatino' : 'lingual';
    if (codigo === 'ML') return `Mesio${lado}`;
    if (codigo === 'L') return superior ? 'Palatino' : 'Lingual';
    if (codigo === 'DL') return `Disto${lado}`;
    return SITIOS.find(([c]) => c === codigo)[1];
  }

  function dibujarTabla(tabla) {
    const pieza = Number(tabla.dataset.pieza);
    const superior = esSuperior(pieza);
    const sitios = JSON.parse(tabla.dataset.sitios);
    const editable = tabla.hasAttribute('data-editable');
    const errores = JSON.parse(tabla.dataset.errores || '{}');
    const ayuda = tabla.dataset.ayuda || '';
    const filas = SITIOS.map(([codigo], i) => {
      const [pr, re, sa, pl] = sitios[i] || [null, null, false, false];
      const nombre = nombreSitio(codigo, superior);
      const perdida = pr === null || re === null ? '—' : `${pr + re} mm`;
      const err = errores[codigo] || {};
      const numero = (campo, valor, min, max, rotulo, describir) => {
        const id = `p${pieza}-${codigo}-${campo}`;
        const invalido = err[campo] ? ` aria-invalid="true" aria-describedby="${id}-e${describir ? ' ' + describir : ''}"` : (describir ? ` aria-describedby="${describir}"` : '');
        return `<input id="${id}" type="number" inputmode="numeric" step="1" min="${min}" max="${max}" value="${valor === null ? '' : valor}"` +
          ` aria-label="Pieza ${pieza}, ${nombre.toLowerCase()}: ${rotulo}"${invalido} />` +
          (err[campo] ? `<span class="campo-error" id="${id}-e">${err[campo]}</span>` : '');
      };
      const casilla = (valor, rotulo) => `<input type="checkbox"${valor ? ' checked' : ''} aria-label="Pieza ${pieza}, ${nombre.toLowerCase()}: ${rotulo}" />`;
      if (editable) {
        return `<tr><th scope="row">${nombre}<span class="perio-cara">${codigo}</span></th>` +
          `<td data-rotulo="Profundidad (mm)">${numero('pr', pr, 0, 15, 'profundidad de sondaje en mm')}</td>` +
          `<td data-rotulo="Recesión (mm)">${numero('re', re, -5, 15, 'recesión en mm', ayuda)}</td>` +
          `<td data-rotulo="Pérdida de inserción">${perdida}</td>` +
          `<td data-rotulo="Sangrado">${casilla(sa, 'sangrado')}</td>` +
          `<td data-rotulo="Placa">${casilla(pl, 'placa')}</td></tr>`;
      }
      return `<tr><th scope="row">${nombre}<span class="perio-cara">${codigo}</span></th>` +
        `<td data-rotulo="Profundidad">${pr} mm</td><td data-rotulo="Recesión">${re} mm</td>` +
        `<td data-rotulo="Pérdida de inserción">${perdida}</td>` +
        `<td data-rotulo="Sangrado">${sa ? 'Sí' : 'No'}</td><td data-rotulo="Placa">${pl ? 'Sí' : 'No'}</td></tr>`;
    }).join('');
    tabla.innerHTML = '<thead><tr><th scope="col">Sitio</th><th scope="col">Profundidad (mm)</th><th scope="col">Recesión (mm)</th>' +
      '<th scope="col">Pérdida de inserción</th><th scope="col">Sangrado</th><th scope="col">Placa</th></tr></thead>' +
      `<tbody>${filas}</tbody>`;
  }

  document.querySelectorAll('.perio-mapa').forEach(dibujarMapa);
  document.querySelectorAll('.perio-leyenda').forEach(dibujarLeyenda);
  document.querySelectorAll('.perio-vistas').forEach(dibujarVistas);
  document.querySelectorAll('.perio-tabla').forEach(dibujarTabla);
})();
