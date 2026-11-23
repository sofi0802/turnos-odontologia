import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import estilos from '../src/styles.css?raw';
import { abrirApp, boton, campo, escribir, json, ServidorFalso, turno, type AppDePrueba } from './apoyo.ts';

// jsdom no aplica media queries ni calcula el diseño: estos tests comprueban la
// estructura y las reglas de estilos.css; lo visual se verifica con capturas reales
// a 390 × 844 y 1440 × 900 (tarea 6.2).

let app: AppDePrueba | undefined;

/** Texto de las reglas que están dentro de `@media (min-width: 768px)`. */
function reglasDeEscritorio(): string {
  const inicio = estilos.indexOf('@media (min-width: 768px)');
  return inicio === -1 ? '' : estilos.slice(inicio);
}

/** Reglas mobile first: todo lo anterior al primer media query. */
function reglasDeCelular(): string {
  const inicio = estilos.indexOf('@media');
  return inicio === -1 ? estilos : estilos.slice(0, inicio);
}

function regla(css: string, selector: string): string {
  const escapado = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`(^|\\n\\s*|,\\s*)${escapado}\\s*(,[^{]*)?\\{([^}]*)\\}`).exec(css)?.[3] ?? '';
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-12-01T11:00:00Z'));
});

afterEach(() => {
  app?.router.detener();
  for (const abierto of document.querySelectorAll('dialog')) {
    abierto.remove();
  }
  app = undefined;
  vi.useRealTimers();
});

describe('Agenda usable en el celular', () => {
  it('Agenda en el celular', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /turnos', () => json(200, [turno()]));
    app = abrirApp(servidor, '#/agenda');
    await vi.waitFor(() => expect(app?.raiz.querySelectorAll('.turno')).toHaveLength(1));
    boton(app.raiz, 'Semana').click();
    await vi.waitFor(() => expect(app?.raiz.querySelectorAll('.agenda-dia')).toHaveLength(7));

    // Una sola columna: los días y los turnos se apilan sin grilla hasta 768 px.
    expect(regla(reglasDeCelular(), '.agenda-lista')).not.toMatch(/grid-template-columns/);
    expect(regla(reglasDeCelular(), '.turnos')).not.toMatch(/grid-template-columns/);
    // Filtros y formularios a todo el ancho; controles de al menos 44 px.
    expect(regla(reglasDeCelular(), '.agenda-filtros')).toMatch(/flex-direction:\s*column/);
    expect(regla(reglasDeCelular(), 'button')).toMatch(/min-height:\s*44px/);
    expect(regla(reglasDeCelular(), 'select')).toMatch(/min-height:\s*44px/);
    expect(regla(reglasDeCelular(), '.dialogo')).toMatch(/width:\s*calc\(100% - /);
    // Nada fuerza desplazamiento horizontal: ningún ancho fijo en píxeles mayor a 320.
    const anchosFijos = [...estilos.matchAll(/(?<!-)width:\s*(\d+)px/g)].map((m) => Number(m[1]));
    expect(anchosFijos.filter((ancho) => ancho > 320)).toEqual([]);
    expect(regla(estilos, 'body')).toMatch(/min-width:\s*320px/);
  });

  it('Agenda en la computadora', () => {
    // Desde 768 px, filtros en fila y los días de la semana en columnas que se ajustan al ancho.
    expect(regla(reglasDeEscritorio(), '.agenda-filtros')).toMatch(/flex-direction:\s*row/);
    expect(regla(reglasDeEscritorio(), '.agenda-lista')).toMatch(/grid-template-columns:\s*repeat\(auto-fill, minmax\(/);
    expect(regla(estilos, '.contenido')).toMatch(/max-width:\s*var\(--ancho-maximo\)/);
  });
});

describe('Buscador usable en el celular (C-36)', () => {
  it('Buscador en el celular', async () => {
    const servidor = new ServidorFalso();
    app = abrirApp(servidor, '#/agenda');
    await vi.waitFor(() => expect(servidor.a('GET', '/turnos')).toHaveLength(1));
    await vi.waitFor(() => expect(campo<HTMLSelectElement>(app?.raiz ?? document.body, 'Profesional').options.length).toBeGreaterThan(1));
    boton(app.raiz, 'Nuevo turno').click();
    const dialogo = document.querySelector<HTMLDialogElement>('dialog[open]') ?? document.body;

    escribir(campo(dialogo, 'Paciente'), 'paciente');
    await vi.waitFor(() => expect(dialogo.querySelectorAll('[role="option"]')).toHaveLength(2));

    // jsdom no calcula el diseño: se comprueba la estructura y la regla de estilos.css.
    for (const opcion of dialogo.querySelectorAll('[role="option"]')) {
      expect(opcion.classList.contains('buscador-opcion')).toBe(true);
    }
    const celular = reglasDeCelular();
    const altoOpcion = Number(/min-height:\s*(\d+)px/.exec(regla(celular, '.buscador-opcion'))?.[1]);
    expect(altoOpcion).toBeGreaterThanOrEqual(44);
    expect(regla(celular, '.buscador-lista')).not.toMatch(/position:\s*absolute/);
    expect(regla(celular, '.buscador-lista')).not.toMatch(/(?<!-)width:\s*\d+px/);
    expect(regla(celular, '.buscador-elegido button')).toMatch(/min-height:\s*44px/);
    document.querySelector('dialog')?.remove();
  });

  it('los estilos del buscador usan solo variables del sistema de diseño', () => {
    const reglasDelBuscador = estilos.slice(estilos.indexOf('/* Buscador de pacientes (C-36)'));
    expect(reglasDelBuscador.length).toBeGreaterThan(0);
    expect(reglasDelBuscador).not.toMatch(/#[0-9a-fA-F]{3,8}/);
    expect(reglasDelBuscador).not.toMatch(/rgba?\(/);
    expect(reglasDelBuscador).toMatch(/var\(--ds-/);
  });
});

describe('Páginas del paciente en el celular (C-12)', () => {
  it('Reserva en el celular', async () => {
    const servidor = new ServidorFalso();
    servidor.sinSesion();
    servidor.responder('GET /public/prestaciones', () => json(200, [{ id: 1, nombre: 'Consulta', duracionMinutos: 30 }]));
    servidor.responder('GET /public/profesionales', () => json(200, []));
    servidor.responder('GET /public/disponibilidad', () =>
      json(200, [{ inicio: '2026-12-01T10:00:00-03:00', fin: '2026-12-01T10:30:00-03:00' }]),
    );
    app = abrirApp(servidor, '#/reserva');
    await vi.waitFor(() => expect(app?.raiz.querySelector('input[role="combobox"]')).not.toBeNull());
    app.raiz.querySelector<HTMLInputElement>('input[role="combobox"]')?.focus();
    app.raiz.querySelector<HTMLElement>('[role="option"]')?.click();
    boton(app.raiz, 'Continuar').click();
    await vi.waitFor(() => expect(app?.raiz.querySelectorAll('.chip')).toHaveLength(1));

    // Los horarios son botones .chip de al menos 44 px: todo botón mide 44 px y .chip usa
    // --ds-alto-control, que tokens.css fija en 44px.
    expect(app.raiz.querySelector('.chip')?.tagName).toBe('BUTTON');
    expect(regla(reglasDeCelular(), 'button')).toMatch(/min-height:\s*44px/);
    expect(regla(reglasDeCelular(), '.chip')).toMatch(/min-height:\s*var\(--ds-alto-control\)/);
    expect(regla(reglasDeCelular(), '.btn')).toMatch(/min-height:\s*var\(--ds-alto-control\)/);
    // Controles uno debajo del otro: opciones en una columna y acciones apiladas hasta 768 px.
    expect(regla(reglasDeCelular(), '.opciones')).toMatch(/display:\s*grid/);
    expect(regla(reglasDeCelular(), '.opciones')).not.toMatch(/grid-template-columns/);
    expect(regla(reglasDeCelular(), '.acciones')).toMatch(/flex-direction:\s*column-reverse/);
    expect(regla(reglasDeCelular(), '.pagina-paciente')).toMatch(/max-width:\s*36rem/);
    // Ningún ancho fijo en píxeles mayor a 320 (sin desplazamiento horizontal).
    const anchosFijos = [...estilos.matchAll(/(?<!-)width:\s*(\d+)px/g)].map((m) => Number(m[1]));
    expect(anchosFijos.filter((ancho) => ancho > 320)).toEqual([]);
  });

  it('Mis turnos en el celular', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /public/sesion', () =>
      json(200, { autenticado: true, username: 'paciente.uno@example.com', rol: 'PACIENTE' }),
    );
    servidor.responder('GET /paciente/turnos', () =>
      json(200, [
        {
          numeroReserva: 'R-0002',
          inicio: '2026-12-03T10:00:00-03:00',
          fin: '2026-12-03T10:30:00-03:00',
          estado: 'RESERVADO',
          prestacion: { id: 1, nombre: 'Consulta' },
          profesional: { id: 1, nombre: 'Ana Ejemplo' },
          modificableOnline: true,
        },
      ]),
    );
    app = abrirApp(servidor, '#/mis-turnos');
    await vi.waitFor(() => expect(app?.raiz.querySelectorAll('.turno-paciente')).toHaveLength(1));

    // Tarjetas a lo ancho, una debajo de la otra, con los botones apilados.
    expect(boton(app.raiz, 'Cancelar turno').classList.contains('btn')).toBe(true);
    expect(boton(app.raiz, 'Cambiar horario').classList.contains('btn')).toBe(true);
    expect(regla(reglasDeCelular(), '.lista-turnos')).not.toMatch(/grid-template-columns/);
    expect(regla(reglasDeCelular(), '.turno-paciente .turno-acciones')).toMatch(/flex-direction:\s*column/);
    expect(regla(reglasDeEscritorio(), '.turno-paciente .turno-acciones')).toMatch(/flex-direction:\s*row/);
  });
});

describe('Reprogramar turno en el celular (C-33)', () => {
  it('Horarios en el celular', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /turnos', () => json(200, [turno()]));
    servidor.responder('GET /public/profesionales', () => json(200, [{ id: 1, nombre: 'Ana Ejemplo' }]));
    servidor.responder('GET /public/disponibilidad', () =>
      json(200, [
        { inicio: '2026-12-01T11:00:00-03:00', fin: '2026-12-01T11:30:00-03:00' },
        { inicio: '2026-12-01T11:30:00-03:00', fin: '2026-12-01T12:00:00-03:00' },
      ]),
    );
    app = abrirApp(servidor, '#/agenda');
    await vi.waitFor(() => expect(app?.raiz.querySelectorAll('.turno')).toHaveLength(1));
    boton(app.raiz.querySelector('.turno') ?? app.raiz, 'Reprogramar').click();
    await vi.waitFor(() => expect(document.querySelectorAll('dialog[open] .chip')).toHaveLength(2));

    // Los horarios son los botones .chip de C-12, dentro de una grilla que se ajusta al ancho.
    const grilla = document.querySelector('dialog[open] .chips');
    expect(grilla?.getAttribute('role')).toBe('group');
    expect(document.querySelector('dialog[open] .chip')?.tagName).toBe('BUTTON');
    expect(regla(reglasDeCelular(), 'button')).toMatch(/min-height:\s*44px/);
    expect(regla(reglasDeCelular(), '.chip')).toMatch(/min-height:\s*var\(--ds-alto-control\)/);
    expect(regla(reglasDeCelular(), '.chips')).toMatch(/grid-template-columns:\s*repeat\(auto-fill, minmax\(5\.5rem, 1fr\)\)/);
    // Con 320 px de ancho caben tres columnas de 5,5 rem: la grilla no fuerza desplazamiento horizontal.
    expect(regla(reglasDeCelular(), '.dialogo fieldset')).toMatch(/min-width:\s*0/);
    // El diálogo cabe en la pantalla y se desplaza por dentro, sin ancho fijo.
    expect(regla(reglasDeCelular(), '.dialogo')).toMatch(/max-height:\s*calc\(100dvh - 2 \* var\(--espacio-2\)\)/);
    expect(regla(reglasDeCelular(), '.dialogo')).toMatch(/overflow-y:\s*auto/);
    expect(regla(reglasDeCelular(), '.dialogo')).toMatch(/width:\s*calc\(100% - /);
    // DNI y número de reserva uno debajo del otro en el celular, lado a lado desde 768 px.
    expect(regla(reglasDeCelular(), '.dos-campos')).not.toMatch(/grid-template-columns/);
    expect(regla(reglasDeEscritorio(), '.dos-campos')).toMatch(/grid-template-columns:\s*1fr 1fr/);
    // Estados de la zona de horarios (mockups de C-33).
    expect(regla(reglasDeCelular(), '.sin-horarios')).toMatch(/border:\s*1px dashed/);
    expect(regla(reglasDeCelular(), '.buscando')).toMatch(/color:\s*var\(--ds-texto-suave\)/);
    // Ningún ancho fijo en píxeles mayor a 320.
    const anchosFijos = [...estilos.matchAll(/(?<!-)width:\s*(\d+)px/g)].map((m) => Number(m[1]));
    expect(anchosFijos.filter((ancho) => ancho > 320)).toEqual([]);
  });

  it('Los botones de horario entran en el diálogo en la computadora', () => {
    // Verificación visual de C-33 (1440 × 900): con 6 columnas fijas de 1fr los botones no entraban en los 32 rem del
    // diálogo y aparecía una barra de desplazamiento horizontal. El diálogo con horarios mide 34 rem, como en los
    // mockups aprobados, y la grilla del diálogo se ajusta al ancho en lugar de fijar las columnas.
    expect(regla(reglasDeEscritorio(), '.dialogo:has(.chips)')).toMatch(/max-width:\s*34rem/);
    expect(regla(reglasDeEscritorio(), '.dialogo .chips')).toMatch(/grid-template-columns:\s*repeat\(auto-fill, minmax\(4\.5rem, 1fr\)\)/);
    expect(regla(reglasDeEscritorio(), '.dialogo .chips')).not.toMatch(/repeat\(6/);
  });
});

describe('Configuración usable en el celular (C-34)', () => {
  it('Las pestañas se desplazan dentro de su barra en el celular', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /public/sesion', () => json(200, { autenticado: true, username: 'admin', rol: 'ADMIN' }));
    const desplazar = vi.fn();
    Element.prototype.scrollIntoView = desplazar;
    try {
      app = abrirApp(servidor, '#/configuracion');
      const raiz = app.raiz;
      const barra = await vi.waitFor(() => {
        const encontrada = raiz.querySelector<HTMLElement>('[role="tablist"]');
        expect(encontrada).not.toBeNull();
        return encontrada as HTMLElement;
      });

      expect(barra.classList.contains('pestanas-config')).toBe(true);
      expect(regla(estilos, '.pestanas-config')).toMatch(/overflow-x:\s*auto/);
      expect(regla(estilos, '.pestana')).toMatch(/white-space:\s*nowrap/);
      expect(regla(estilos, '.pestana')).toMatch(/min-height:\s*var\(--ds-alto-control\)/);

      boton(raiz, 'Bloqueos').click();
      expect(desplazar).toHaveBeenLastCalledWith({ block: 'nearest', inline: 'center' });
    } finally {
      Reflect.deleteProperty(Element.prototype, 'scrollIntoView');
    }
  });

  it('Las listas de Configuración se apilan en el celular y van en columnas en la computadora', () => {
    expect(regla(estilos, '.lista-config > li')).toMatch(/display:\s*grid/);
    expect(regla(estilos, '.lista-config > li')).not.toMatch(/grid-template-columns/);
    const escritorio = estilos.slice(estilos.lastIndexOf('@media (min-width: 768px)'));
    expect(regla(escritorio, '.lista-config > li')).toMatch(/grid-template-columns:\s*1fr auto/);
  });

  it('el detalle del profesional separa el botón de su campo y un bloque del otro (D11)', () => {
    expect(regla(estilos, '.grupo-config')).toMatch(/gap:\s*var\(--ds-espacio-1\)/);
    expect(regla(estilos, '.grupo-config + .grupo-config')).toMatch(/margin-top:\s*var\(--ds-espacio-3\)/);
  });

  it('las tarjetas de sillón se acomodan en grilla y la ocupada se distingue por el borde, no solo por el color', () => {
    expect(regla(estilos, '.opciones-sillon')).toMatch(/minmax\(12rem,\s*1fr\)/);
    expect(regla(estilos, '.opcion-ocupada')).toMatch(/border-style:\s*dashed/);
    expect(regla(estilos, '.opcion-ocupada')).toMatch(/cursor:\s*not-allowed/);
  });

  it('los días y las horas de las franjas se apilan en el celular y van en fila en la computadora', () => {
    expect(regla(estilos, '.dias-semana')).toMatch(/grid-template-columns:\s*repeat\(2,\s*1fr\)/);
    expect(regla(estilos, '.hora-5')).toMatch(/display:\s*flex/);
    const escritorio = estilos.slice(estilos.lastIndexOf('@media (min-width: 768px)'));
    expect(regla(escritorio, '.dias-semana')).toMatch(/repeat\(7,\s*auto\)/);
  });

  it('los estilos de Configuración usan solo variables del sistema de diseño', () => {
    const reglas = estilos.slice(estilos.indexOf('/* --- Configuración del consultorio (C-34'));
    expect(reglas.length).toBeGreaterThan(0);
    expect(reglas).not.toMatch(/#[0-9a-fA-F]{3,8}/);
    expect(reglas).not.toMatch(/rgba?\(/);
    expect(reglas).toMatch(/var\(--ds-/);
  });
});

describe('Portada usable en el celular (C-37)', () => {
  /** Estilos de la portada: del comentario de C-37 hasta el final. */
  function estilosDePortada(): string {
    return estilos.slice(estilos.indexOf('/* Portada (C-37'));
  }

  function reglasMobileFirstDePortada(): string {
    const css = estilosDePortada();
    return css.slice(0, css.indexOf('@media'));
  }

  it('La portada en el celular', () => {
    const celular = reglasMobileFirstDePortada();
    const alto = /min-height:\s*var\(--ds-alto-control\)/;
    expect(regla(celular, '.btn-acceso')).toMatch(alto);
    expect(regla(celular, '.hero-acciones .btn')).toMatch(alto);
    expect(regla(celular, '.hero')).toMatch(/grid-template-columns:\s*minmax\(0,\s*1fr\)/);
    expect(regla(celular, '.tarjetas-pasos')).toMatch(/grid-template-columns:\s*minmax\(0,\s*1fr\)/);
    // Sin ancho fijo mayor a 320 px: en el celular nada fuerza un desplazamiento horizontal.
    for (const [, ancho] of estilosDePortada().matchAll(/(?<!min-)(?<!max-)width:\s*(\d+)px/g)) {
      expect(Number(ancho)).toBeLessThanOrEqual(320);
    }
    // En la computadora los tres pasos pasan a columnas.
    const escritorio = estilosDePortada().slice(estilosDePortada().indexOf('@media (min-width: 768px)'));
    expect(escritorio).toMatch(/\.tarjetas-pasos\s*\{[^}]*repeat\(3/);
  });

  it('los estilos de la portada usan solo variables del sistema de diseño', () => {
    const portada = estilosDePortada();
    expect(portada.length).toBeGreaterThan(100);
    expect(portada).not.toMatch(/#[0-9a-fA-F]{3,8}/);
    expect(portada).not.toMatch(/var\(--(?!ds-)/);
  });
});

describe('Nuevo turno guiado en el celular y en la computadora (C-40)', () => {
  const MARCA = '/* Nuevo turno guiado (C-40)';

  function seccion(): string {
    const inicio = estilos.indexOf(MARCA);
    const fin = estilos.indexOf('/* Fin del nuevo turno guiado (C-40) */');
    return inicio === -1 || fin === -1 ? '' : estilos.slice(inicio, fin);
  }

  function celular(): string {
    const css = seccion();
    const media = css.indexOf('@media');
    return media === -1 ? css : css.slice(0, media);
  }

  function escritorio(): string {
    const css = seccion();
    const media = css.indexOf('@media (min-width: 768px)');
    return media === -1 ? '' : css.slice(media);
  }

  it('Calendario en el celular', () => {
    // Siete columnas que se reparten el ancho, sin anchos fijos: entran desde 320 px.
    expect(regla(celular(), '.cal-dias')).toMatch(/grid-template-columns:\s*repeat\(7, minmax\(0, 1fr\)\)/);
    expect(regla(celular(), '.cal-dia')).toMatch(/min-height:\s*var\(--ds-alto-control\)/);
    expect(regla(celular(), '.cal-dia')).toMatch(/min-width:\s*0/);
    // El día elegido no se distingue solo por el color, y los pasados se tachan.
    expect(regla(celular(), ".cal-dia[aria-pressed='true']")).toMatch(/font-weight:/);
    expect(regla(celular(), '.cal-dia:disabled')).toMatch(/text-decoration:\s*line-through/);
    expect(regla(celular(), '.cal-dia.otro-mes')).toMatch(/visibility:\s*hidden/);
    expect(regla(celular(), '.cal-nav')).toMatch(/min-height:\s*var\(--ds-alto-control\)/);
    const anchosFijos = [...seccion().matchAll(/(?<!-)width:\s*(\d+)px/g)].map((m) => Number(m[1]));
    expect(anchosFijos.filter((ancho) => ancho > 320)).toEqual([]);
  });

  it('Nuevo turno en la computadora', () => {
    // El diálogo se ensancha y pone el calendario y los horarios lado a lado (mockups aprobados).
    expect(regla(escritorio(), '.dialogo.dialogo-ancho')).toMatch(/max-width:\s*52rem/);
    expect(regla(escritorio(), '.fecha-y-horario')).toMatch(/grid-template-columns:/);
    expect(regla(escritorio(), '.dos-columnas')).toMatch(/grid-template-columns:\s*1fr 1fr/);
  });

  it('los estilos del nuevo turno usan solo variables del sistema de diseño', () => {
    expect(seccion().length).toBeGreaterThan(0);
    expect(seccion()).not.toMatch(/#[0-9a-fA-F]{3,8}\b/);
    expect(seccion()).not.toMatch(/rgba?\(/);
    expect(seccion()).toMatch(/var\(--ds-/);
  });
});

describe('Selector de prestaciones usable en el celular (C-38)', () => {
  /** Estilos del selector: del comentario de C-38 hasta la regla `.chips`. */
  function estilosDelSelector(): string {
    const inicio = estilos.indexOf('/* Selector de prestaciones con buscador (C-38');
    return estilos.slice(inicio, estilos.indexOf('\n.chips {', inicio));
  }

  it('Selector en el celular', () => {
    const css = estilosDelSelector();
    expect(regla(css, '.selector-opcion')).toMatch(/min-height:\s*48px/);
    expect(regla(css, '.selector-lista')).toMatch(/max-height:\s*16rem/);
    expect(regla(css, '.selector-lista')).toMatch(/overflow-y:\s*auto/);
    expect(regla(css, '.selector-flecha')).toMatch(/width:\s*var\(--ds-alto-control\)/);
    expect(regla(css, '.selector')).toMatch(/max-width:\s*100%/);
    expect(css).not.toMatch(/@media/);
    const anchosFijos = [...css.matchAll(/(?<!-)width:\s*(\d+)px/g)].map((m) => Number(m[1]));
    expect(anchosFijos.filter((ancho) => ancho > 320)).toEqual([]);
  });

  it('los estilos del selector usan solo variables del sistema de diseño', () => {
    const css = estilosDelSelector();
    expect(css.length).toBeGreaterThan(200);
    expect(css).not.toMatch(/#[0-9a-fA-F]{3,8}/);
    expect(css).not.toMatch(/rgba?\(/);
    expect(css).not.toMatch(/var\(--(?!ds-)/);
  });
});
