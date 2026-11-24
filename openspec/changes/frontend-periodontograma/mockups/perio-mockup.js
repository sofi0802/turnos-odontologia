// Mockups de C-46: dibuja el mapa de piezas, la leyenda y las tablas de sitios desde atributos data-*.
// Solo para las vistas previas; en el apply la sección la arma frontend/src/ficha/periodontograma.ts.
(function () {
  const ARRIBA = [[18, 17, 16, 15, 14, 13, 12, 11], [21, 22, 23, 24, 25, 26, 27, 28]];
  const ABAJO = [[48, 47, 46, 45, 44, 43, 42, 41], [31, 32, 33, 34, 35, 36, 37, 38]];
  const NOMBRE = { SANO: 'Sano', LEVE: 'Leve', MODERADO: 'Moderado', SEVERO: 'Severo', AUSENTE: 'Ausente', SIN_CARGAR: 'Sin cargar', CARGANDO: 'En carga' };
  const NIVEL = { SANO: 0, LEVE: 1, MODERADO: 2, SEVERO: 3 };

  function marca(estado) {
    if (estado === 'AUSENTE') return '<span class="perio-cruz" aria-hidden="true">×</span>';
    if (!(estado in NIVEL)) return '<span class="perio-nivel" aria-hidden="true"></span>';
    const barras = [0, 1, 2].map((i) => `<span class="${i < NIVEL[estado] ? 'lleno' : ''}"></span>`).join('');
    return `<span class="perio-nivel" aria-hidden="true">${barras}</span>`;
  }

  function boton(numero, estado, elegida, error) {
    const nombre = NOMBRE[estado];
    return `<button type="button" class="perio-pieza" data-estado="${estado}" aria-pressed="${elegida}"` +
      `${error ? ' data-error' : ''} aria-label="Pieza ${numero}: ${nombre}${error ? ', con errores' : ''}">` +
      `<span class="perio-num">${numero}</span>${marca(estado)}<span class="perio-estado">${nombre}</span></button>`;
  }

  function dibujarMapa(mapa) {
    const piezas = JSON.parse(mapa.dataset.piezas || '{}');
    const elegida = Number(mapa.dataset.elegida || 0);
    const errores = JSON.parse(mapa.dataset.errores || '[]');
    const arcada = (titulo, cuadrantes) => `<div class="perio-arcada" role="group" aria-label="${titulo}">` +
      `<p class="perio-arcada-titulo" aria-hidden="true">${titulo}</p>` +
      cuadrantes.map((c) => `<div class="perio-cuadrante">${c.map((n) =>
        boton(n, piezas[n] || 'SIN_CARGAR', n === elegida, errores.includes(n))).join('')}</div>`).join('') +
      '</div>';
    mapa.innerHTML = arcada('Maxilar superior', ARRIBA) + arcada('Maxilar inferior', ABAJO);
  }

  function dibujarLeyenda(lista) {
    lista.innerHTML = ['SANO', 'LEVE', 'MODERADO', 'SEVERO', 'AUSENTE', 'SIN_CARGAR'].map((e) =>
      `<li><span class="perio-pieza" data-estado="${e}" aria-hidden="true">${marca(e)}</span>${NOMBRE[e]}</li>`).join('');
  }

  const SITIOS = [['MV', 'Mesiovestibular'], ['V', 'Vestibular'], ['DV', 'Distovestibular'], ['ML', 'Mesio'], ['L', ''], ['DL', 'Disto']];

  function nombreSitio(codigo, superior) {
    const lado = superior ? 'palatino' : 'lingual';
    if (codigo === 'ML') return `Mesio${lado}`;
    if (codigo === 'L') return superior ? 'Palatino' : 'Lingual';
    if (codigo === 'DL') return `Disto${lado}`;
    return SITIOS.find(([c]) => c === codigo)[1];
  }

  // data-sitios: [[profundidad, recesion, sangrado, placa], ...] en el orden MV, V, DV, ML, L, DL; null = vacío.
  function dibujarTabla(tabla) {
    const pieza = Number(tabla.dataset.pieza);
    const superior = [1, 2].includes(Math.floor(pieza / 10));
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
      const casilla = (campo, valor, rotulo) => `<input type="checkbox"${valor ? ' checked' : ''} aria-label="Pieza ${pieza}, ${nombre.toLowerCase()}: ${rotulo}" />`;
      if (editable) {
        return `<tr><th scope="row">${nombre}<span class="perio-cara">${codigo}</span></th>` +
          `<td data-rotulo="Profundidad (mm)">${numero('pr', pr, 0, 15, 'profundidad de sondaje en mm')}</td>` +
          `<td data-rotulo="Recesión (mm)">${numero('re', re, -5, 15, 'recesión en mm', ayuda)}</td>` +
          `<td data-rotulo="Pérdida de inserción">${perdida}</td>` +
          `<td data-rotulo="Sangrado">${casilla('sa', sa, 'sangrado')}</td>` +
          `<td data-rotulo="Placa">${casilla('pl', pl, 'placa')}</td></tr>`;
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
  document.querySelectorAll('.perio-tabla').forEach(dibujarTabla);
})();
