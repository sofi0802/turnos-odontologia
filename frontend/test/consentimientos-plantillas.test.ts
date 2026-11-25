import { afterEach, describe, expect, it, vi } from 'vitest';
import { MENSAJE_ERROR_GENERICO } from '../src/shared/api/errores.ts';
import { boton, campo, errorDeCampo, json, type ServidorFalso } from './apoyo.ts';
import {
  abrirConfiguracion,
  elegirPestana,
  enviar,
  pestanas,
  SESION_RECEPCION_CONFIG,
  servidorCon,
} from './apoyo-configuracion.ts';
import {
  dialogosAbiertos,
  escribirTexto,
  limpiarDialogos,
  plantillaVista,
  PLANTILLAS,
  presionarEscape,
  SESION_ODONTOLOGA,
  servidorAdmin,
  TEXTO_V3,
  TITULO_BLANQUEAMIENTO,
  TITULO_EXTRACCION,
  ultimoDialogo,
  version,
  visible,
} from './apoyo-consentimientos.ts';

/** C-48, grupo 3: Configuración → "Consentimientos" (spec `frontend-plantillas-consentimiento`). Datos ficticios. */

afterEach(() => {
  limpiarDialogos();
});

/** Abre la pestaña "Consentimientos" y espera a que se dibuje la lista. */
async function abrirPestana(servidor: ServidorFalso = servidorAdmin()): Promise<{ panel: HTMLElement; servidor: ServidorFalso; raiz: HTMLElement }> {
  const { raiz } = await abrirConfiguracion(servidor);
  const panel = elegirPestana(raiz, 'Consentimientos');
  await vi.waitFor(() => expect(panel.querySelector('[aria-busy="true"]')).toBeNull());
  return { panel, servidor, raiz };
}

function filas(panel: HTMLElement): HTMLElement[] {
  return [...panel.querySelectorAll<HTMLElement>('.cons-plantillas > li')];
}

/** Responde el detalle de la plantilla 4 (versión 3) para abrir el editor. */
function responderDetalle(servidor: ServidorFalso): void {
  servidor.responder('GET /consentimientos/plantillas/{id}', () => json(200, plantillaVista(4, true, 3)));
}

async function abrirEditor(servidor: ServidorFalso, panel: HTMLElement): Promise<HTMLDialogElement> {
  responderDetalle(servidor);
  boton(panel, `Editar ${TITULO_EXTRACCION}`).click();
  return vi.waitFor(() => {
    const dialogo = dialogosAbiertos()[0];
    expect(dialogo).toBeDefined();
    return dialogo as HTMLDialogElement;
  });
}

describe('Pestaña "Consentimientos" solo para el administrador', () => {
  it('El administrador abre la pestaña', async () => {
    const servidor = servidorAdmin();
    const { raiz } = await abrirConfiguracion(servidor);
    expect(servidor.a('GET', '/consentimientos/plantillas')).toHaveLength(0);

    const panel = elegirPestana(raiz, 'Consentimientos');

    await vi.waitFor(() => expect(servidor.a('GET', '/consentimientos/plantillas')).toHaveLength(1));
    expect(servidor.ultimo('GET', '/consentimientos/plantillas').query.get('incluirInactivas')).toBe('true');
    await vi.waitFor(() => expect(filas(panel)).toHaveLength(2));
    expect(pestanas(raiz).find((p) => p.textContent === 'Consentimientos')?.getAttribute('aria-selected')).toBe('true');
  });

  it('El odontólogo no ve la pestaña', async () => {
    const servidor = servidorCon(SESION_ODONTOLOGA);
    const { raiz } = await abrirConfiguracion(servidor);

    expect(pestanas(raiz).map((p) => p.textContent)).not.toContain('Consentimientos');
    expect(servidor.pedidos.filter((p) => p.ruta.startsWith('/consentimientos/plantillas'))).toHaveLength(0);
  });

  it('La recepción no ve la pestaña', async () => {
    const servidor = servidorCon(SESION_RECEPCION_CONFIG);
    const { raiz } = await abrirConfiguracion(servidor);

    expect(pestanas(raiz).map((p) => p.textContent)).not.toContain('Consentimientos');
    expect(servidor.pedidos.filter((p) => p.ruta.startsWith('/consentimientos/plantillas'))).toHaveLength(0);
  });

  it('va después de "Obras sociales", última de la tira', async () => {
    const { raiz } = await abrirConfiguracion(servidorAdmin());

    const nombres = pestanas(raiz).map((p) => p.textContent);
    expect(nombres.at(-1)).toBe('Consentimientos');
    expect(nombres.at(-2)).toBe('Obras sociales');
  });
});

describe('Lista de plantillas', () => {
  it('Filas de la lista', async () => {
    const { panel } = await abrirPestana();
    const [primera, segunda] = filas(panel) as [HTMLElement, HTMLElement];

    expect(visible(primera)).toContain(TITULO_EXTRACCION);
    expect(visible(primera)).toContain('Versión 3');
    expect(visible(primera)).toContain('Actualizada el 24/11/2026');
    expect(visible(primera)).toContain('Activa');
    expect(visible(primera)).not.toContain('Inactiva');
    expect(visible(segunda)).toContain(TITULO_BLANQUEAMIENTO);
    expect(visible(segunda)).toContain('Versión 1');
    expect(visible(segunda)).toContain('Inactiva');
  });

  it('conserva el orden de la API', async () => {
    const servidor = servidorAdmin();
    servidor.responder('GET /consentimientos/plantillas', () => json(200, [...PLANTILLAS].reverse()));
    const { panel } = await abrirPestana(servidor);

    expect(filas(panel).map((f) => f.querySelector('.item-nombre')?.textContent)).toEqual([TITULO_BLANQUEAMIENTO, TITULO_EXTRACCION]);
  });

  it('Sin plantillas', async () => {
    const servidor = servidorAdmin();
    servidor.responder('GET /consentimientos/plantillas', () => json(200, []));
    const { panel } = await abrirPestana(servidor);

    expect(visible(panel)).toContain('Todavía no hay plantillas de consentimiento.');
    expect(boton(panel, 'Nueva plantilla')).toBeDefined();
    expect(filas(panel)).toHaveLength(0);
  });

  it('Falla al cargar la lista', async () => {
    const servidor = servidorAdmin();
    servidor.responder('GET /consentimientos/plantillas', () => json(500));
    const { panel, raiz } = await abrirPestana(servidor);

    expect(visible(panel)).toContain(MENSAJE_ERROR_GENERICO);
    expect(boton(panel, 'Reintentar')).toBeDefined();
    const sillones = elegirPestana(raiz, 'Sillones');
    await vi.waitFor(() => expect(visible(sillones)).toContain('Sillón 1'));

    servidor.responder('GET /consentimientos/plantillas', () => json(200, PLANTILLAS));
    boton(panel, 'Reintentar').click();
    await vi.waitFor(() => expect(filas(panel)).toHaveLength(2));
  });
});

describe('Texto de la plantilla como texto plano', () => {
  it('Un texto con marcas de HTML', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('GET /consentimientos/plantillas/{id}/versiones', () =>
      json(200, [version(1, { texto: 'Primera línea\n<b>Segunda</b>' })]),
    );

    boton(panel, `Versiones de ${TITULO_EXTRACCION}`).click();
    const dialogo = await ultimoDialogo();
    dialogo.querySelector<HTMLElement>('details summary')?.click();
    const detalle = dialogo.querySelector('details');
    detalle?.setAttribute('open', '');

    const texto = dialogo.querySelector<HTMLElement>('.cons-texto');
    expect(texto?.textContent).toBe('Primera línea\n<b>Segunda</b>');
    expect(dialogo.querySelector('.cons-texto b')).toBeNull();
    expect(texto?.children).toHaveLength(0);
  });

  it('un título con marcas de HTML se ve literal en la lista', async () => {
    const servidor = servidorAdmin();
    servidor.responder('GET /consentimientos/plantillas', () =>
      json(200, [{ ...PLANTILLAS[0], titulo: '<img src=x onerror=alert(1)> Plantilla' }]),
    );
    const { panel } = await abrirPestana(servidor);

    expect(panel.querySelector('.cons-plantillas img')).toBeNull();
    expect(visible(filas(panel)[0] as HTMLElement)).toContain('<img src=x onerror=alert(1)> Plantilla');
  });
});

describe('Activar y desactivar una plantilla', () => {
  it('Desactivar una plantilla', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('PUT /consentimientos/plantillas/{id}/estado', () => json(200, plantillaVista(4, false, 3)));

    boton(panel, `Desactivar ${TITULO_EXTRACCION}`).click();

    await vi.waitFor(() => expect(servidor.a('PUT', '/consentimientos/plantillas/4/estado')).toHaveLength(1));
    expect(servidor.ultimo('PUT', '/consentimientos/plantillas/4/estado').cuerpo).toEqual({ activa: false });
    await vi.waitFor(() => expect(visible(panel)).toContain('Plantilla desactivada.'));
    const primera = filas(panel)[0] as HTMLElement;
    expect(visible(primera)).toContain('Inactiva');
    expect(boton(primera, `Activar ${TITULO_EXTRACCION}`)).toBeDefined();
    expect(panel.querySelector('[role="status"]')?.textContent).toBe('Plantilla desactivada.');
  });

  it('Activar una plantilla inactiva', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('PUT /consentimientos/plantillas/{id}/estado', () => json(200, plantillaVista(5, true, 1)));

    boton(panel, `Activar ${TITULO_BLANQUEAMIENTO}`).click();

    await vi.waitFor(() => expect(visible(panel)).toContain('Plantilla activada.'));
    expect(servidor.ultimo('PUT', '/consentimientos/plantillas/5/estado').cuerpo).toEqual({ activa: true });
    expect(visible(filas(panel)[1] as HTMLElement)).not.toContain('Inactiva');
    expect(boton(filas(panel)[1] as HTMLElement, `Desactivar ${TITULO_BLANQUEAMIENTO}`)).toBeDefined();
  });

  it('si el cambio falla, avisa y la fila no cambia', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('PUT /consentimientos/plantillas/{id}/estado', () => json(500));

    boton(panel, `Desactivar ${TITULO_EXTRACCION}`).click();

    await vi.waitFor(() => expect(visible(panel)).toContain(MENSAJE_ERROR_GENERICO));
    expect(visible(filas(panel)[0] as HTMLElement)).toContain('Activa');
    expect(visible(panel)).not.toContain('Plantilla desactivada.');
  });

  it('Sin opción de borrar', async () => {
    const { panel } = await abrirPestana();

    for (const fila of filas(panel)) {
      const textos = [...fila.querySelectorAll('button')].map((b) => `${b.textContent} ${b.getAttribute('aria-label')}`).join(' ');
      expect(textos).not.toMatch(/Borrar|Eliminar/);
    }
    expect(panel.textContent).not.toMatch(/Borrar|Eliminar/);
  });
});

describe('Crear una plantilla', () => {
  it('Crear una plantilla', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('POST /consentimientos/plantillas', () => json(201, plantillaVista(9, true, 1)));
    const texto = 'Primer párrafo del implante.\n\nSegundo párrafo.\n\nTercer párrafo.';

    boton(panel, 'Nueva plantilla').click();
    const dialogo = await ultimoDialogo();
    expect((campo<HTMLInputElement>(dialogo, 'Título')).value).toBe('');
    expect((campo<HTMLTextAreaElement>(dialogo, 'Texto del consentimiento')).value).toBe('');
    escribirTexto(campo(dialogo, 'Título'), 'Consentimiento para implante');
    escribirTexto(campo<HTMLTextAreaElement>(dialogo, 'Texto del consentimiento'), texto);
    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('POST', '/consentimientos/plantillas')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/consentimientos/plantillas').cuerpo).toEqual({ titulo: 'Consentimiento para implante', texto });
    await vi.waitFor(() => expect(dialogosAbiertos()).toHaveLength(0));
    expect(visible(panel)).toContain('Plantilla creada.');
    await vi.waitFor(() => expect(servidor.a('GET', '/consentimientos/plantillas')).toHaveLength(2));
  });

  it('Título repetido', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('POST /consentimientos/plantillas', () => json(409, { codigo: 'PLANTILLA_DUPLICADA', detail: 'duplicado' }));

    boton(panel, 'Nueva plantilla').click();
    const dialogo = await ultimoDialogo();
    escribirTexto(campo(dialogo, 'Título'), TITULO_EXTRACCION);
    escribirTexto(campo<HTMLTextAreaElement>(dialogo, 'Texto del consentimiento'), 'Texto ficticio');
    enviar(dialogo);

    await vi.waitFor(() => expect(errorDeCampo(dialogo, 'Título')).toBe('Ya existe una plantilla con ese título.'));
    expect(dialogo.open).toBe(true);
    expect(campo<HTMLInputElement>(dialogo, 'Título').value).toBe(TITULO_EXTRACCION);
    expect(campo<HTMLTextAreaElement>(dialogo, 'Texto del consentimiento').value).toBe('Texto ficticio');
  });
});

describe('Editor de texto plano con contador', () => {
  it('El contador sigue al texto', async () => {
    const { panel } = await abrirPestana();
    boton(panel, 'Nueva plantilla').click();
    const dialogo = await ultimoDialogo();
    const contador = (id: string): string => document.getElementById(`${id}-contador`)?.textContent ?? '';
    const area = campo<HTMLTextAreaElement>(dialogo, 'Texto del consentimiento');
    const titulo = campo(dialogo, 'Título');

    expect(contador(area.id)).toBe('0/20.000');
    escribirTexto(area, 'a'.repeat(1234));
    escribirTexto(titulo, 'Implante');

    expect(contador(area.id)).toBe('1.234/20.000');
    expect(contador(titulo.id)).toBe('8/120');
    expect(area.getAttribute('aria-describedby')).toContain(`${area.id}-contador`);
    expect(titulo.getAttribute('aria-describedby')).toContain(`${titulo.id}-contador`);
    expect(area.getAttribute('maxlength')).toBeNull();
    expect(titulo.getAttribute('maxlength')).toBeNull();
    expect(visible(dialogo)).toContain('Texto plano');
  });

  it('Texto que supera el límite', async () => {
    const { panel, servidor } = await abrirPestana();
    boton(panel, 'Nueva plantilla').click();
    const dialogo = await ultimoDialogo();
    const area = campo<HTMLTextAreaElement>(dialogo, 'Texto del consentimiento');
    const largo = 'x'.repeat(20_001);
    escribirTexto(campo(dialogo, 'Título'), 'Implante');
    escribirTexto(area, largo);

    expect(document.getElementById(`${area.id}-contador`)?.classList.contains('excedido')).toBe(true);
    enviar(dialogo);

    expect(servidor.a('POST', '/consentimientos/plantillas')).toHaveLength(0);
    expect(area.value).toBe(largo);
    expect(errorDeCampo(dialogo, 'Texto del consentimiento')).toBe('El texto supera los 20.000 caracteres.');
  });

  it('un texto de exactamente 20.000 caracteres se envía entero', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('POST /consentimientos/plantillas', () => json(201, plantillaVista(9, true, 1)));
    boton(panel, 'Nueva plantilla').click();
    const dialogo = await ultimoDialogo();
    escribirTexto(campo(dialogo, 'Título'), 'Implante');
    escribirTexto(campo<HTMLTextAreaElement>(dialogo, 'Texto del consentimiento'), 'x'.repeat(20_000));

    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('POST', '/consentimientos/plantillas')).toHaveLength(1));
    expect(((servidor.ultimo('POST', '/consentimientos/plantillas').cuerpo as { texto: string }).texto).length).toBe(20_000);
  });

  it('un título de 121 caracteres no se envía', async () => {
    const { panel, servidor } = await abrirPestana();
    boton(panel, 'Nueva plantilla').click();
    const dialogo = await ultimoDialogo();
    escribirTexto(campo(dialogo, 'Título'), 't'.repeat(121));
    escribirTexto(campo<HTMLTextAreaElement>(dialogo, 'Texto del consentimiento'), 'Texto');

    enviar(dialogo);

    expect(servidor.a('POST', '/consentimientos/plantillas')).toHaveLength(0);
    expect(errorDeCampo(dialogo, 'Título')).toBe('El título supera los 120 caracteres.');
  });

  it('el contador avisa una sola vez al pasar el 90 % y al pasar el límite, no en cada tecla', async () => {
    const { panel } = await abrirPestana();
    boton(panel, 'Nueva plantilla').click();
    const dialogo = await ultimoDialogo();
    const area = campo<HTMLTextAreaElement>(dialogo, 'Texto del consentimiento');
    const aviso = area.closest('.campo-suave')?.querySelector('[role="status"]');
    expect(aviso?.textContent).toBe('');

    escribirTexto(area, 'x'.repeat(17_999));
    expect(aviso?.textContent).toBe('');
    escribirTexto(area, 'x'.repeat(18_000));
    const alPasarElNoventa = aviso?.textContent ?? '';
    expect(alPasarElNoventa).toContain('18.000');
    escribirTexto(area, 'x'.repeat(18_001));
    expect(aviso?.textContent).toBe(alPasarElNoventa);
    escribirTexto(area, 'x'.repeat(20_001));
    expect(aviso?.textContent).toContain('supera');
    escribirTexto(area, 'x'.repeat(100));
    expect(aviso?.textContent).toBe('');
  });

  it('Título o texto vacío', async () => {
    const { panel, servidor } = await abrirPestana();
    boton(panel, 'Nueva plantilla').click();
    const dialogo = await ultimoDialogo();

    enviar(dialogo);
    expect(errorDeCampo(dialogo, 'Título')).toBe('Escribí el título.');
    expect(errorDeCampo(dialogo, 'Texto del consentimiento')).toBe('Escribí el texto del consentimiento.');

    escribirTexto(campo(dialogo, 'Título'), '   ');
    escribirTexto(campo<HTMLTextAreaElement>(dialogo, 'Texto del consentimiento'), ' \n  ');
    enviar(dialogo);

    expect(servidor.a('POST', '/consentimientos/plantillas')).toHaveLength(0);
    expect(errorDeCampo(dialogo, 'Título')).toBe('Escribí el título.');
    expect(errorDeCampo(dialogo, 'Texto del consentimiento')).toBe('Escribí el texto del consentimiento.');
    expect(document.activeElement).toBe(campo(dialogo, 'Título'));
  });
});

describe('Editar una plantilla crea una versión nueva', () => {
  it('Guardar una versión nueva', async () => {
    const { panel, servidor } = await abrirPestana();
    const dialogo = await abrirEditor(servidor, panel);
    servidor.responder('POST /consentimientos/plantillas/{id}/versiones', () => json(201, plantillaVista(4, true, 4)));

    expect(campo<HTMLInputElement>(dialogo, 'Título').value).toBe(TITULO_EXTRACCION);
    expect(campo<HTMLTextAreaElement>(dialogo, 'Texto del consentimiento').value).toBe(TEXTO_V3);
    expect(visible(dialogo)).toContain('Se guarda como versión 4.');
    expect(visible(dialogo)).toContain('Lo que ya se firmó con versiones anteriores no cambia.');
    escribirTexto(campo<HTMLTextAreaElement>(dialogo, 'Texto del consentimiento'), `${TEXTO_V3}\n\nUn párrafo más.`);
    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('POST', '/consentimientos/plantillas/4/versiones')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/consentimientos/plantillas/4/versiones').cuerpo).toEqual({
      versionBase: 3,
      titulo: TITULO_EXTRACCION,
      texto: `${TEXTO_V3}\n\nUn párrafo más.`,
    });
    await vi.waitFor(() => expect(dialogosAbiertos()).toHaveLength(0));
    expect(visible(panel)).toContain('Plantilla guardada como versión 4.');
    await vi.waitFor(() => expect(servidor.a('GET', '/consentimientos/plantillas')).toHaveLength(2));
  });

  it('Guardar sin cambios', async () => {
    const { panel, servidor } = await abrirPestana();
    const dialogo = await abrirEditor(servidor, panel);
    servidor.responder('POST /consentimientos/plantillas/{id}/versiones', () => json(200, plantillaVista(4, true, 3)));

    enviar(dialogo);

    await vi.waitFor(() => expect(dialogosAbiertos()).toHaveLength(0));
    expect(visible(panel)).toContain('No hubo cambios: la plantilla sigue en la versión 3.');
    expect(servidor.ultimo('POST', '/consentimientos/plantillas/4/versiones').cuerpo).toMatchObject({ versionBase: 3 });
  });

  it('si no se puede abrir la plantilla, avisa y no abre el editor', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('GET /consentimientos/plantillas/{id}', () => json(500));

    boton(panel, `Editar ${TITULO_EXTRACCION}`).click();

    await vi.waitFor(() => expect(visible(panel)).toContain(MENSAJE_ERROR_GENERICO));
    expect(dialogosAbiertos()).toHaveLength(0);
  });
});

describe('Conflicto de versión al editar', () => {
  it('Otra persona guardó antes', async () => {
    const { panel, servidor } = await abrirPestana();
    const dialogo = await abrirEditor(servidor, panel);
    servidor.responder('POST /consentimientos/plantillas/{id}/versiones', () =>
      json(409, { codigo: 'VERSION_DESACTUALIZADA', detail: 'cambió' }),
    );
    escribirTexto(campo<HTMLTextAreaElement>(dialogo, 'Texto del consentimiento'), 'Mi texto editado');

    enviar(dialogo);

    await vi.waitFor(() =>
      expect(visible(dialogo)).toContain(
        'Otra persona guardó una versión nueva de esta plantilla mientras la editabas. Copiá tus cambios, cerrá y volvé a abrirla.',
      ),
    );
    expect(dialogo.open).toBe(true);
    expect(campo<HTMLTextAreaElement>(dialogo, 'Texto del consentimiento').value).toBe('Mi texto editado');
    expect(servidor.a('POST', '/consentimientos/plantillas/4/versiones')).toHaveLength(1);
    await vi.waitFor(() => expect(servidor.a('GET', '/consentimientos/plantillas')).toHaveLength(2));
    // Sin enviar nada más: volver a guardar con la misma versión base no manda otro pedido.
    enviar(dialogo);
    expect(servidor.a('POST', '/consentimientos/plantillas/4/versiones')).toHaveLength(1);
  });
});

describe('Descartar una edición sin guardar', () => {
  it('Cancelar con cambios', async () => {
    const { panel, servidor } = await abrirPestana();
    const dialogo = await abrirEditor(servidor, panel);
    escribirTexto(campo<HTMLTextAreaElement>(dialogo, 'Texto del consentimiento'), 'Texto modificado');

    boton(dialogo, 'Cancelar').click();

    const pregunta = await ultimoDialogo();
    expect(pregunta).not.toBe(dialogo);
    expect(visible(pregunta)).toContain('Hay cambios sin guardar en la plantilla. ¿Descartarlos?');
    expect(dialogo.open).toBe(true);
    boton(pregunta, 'Seguir editando').click();
    expect(dialogo.open).toBe(true);
    expect(campo<HTMLTextAreaElement>(dialogo, 'Texto del consentimiento').value).toBe('Texto modificado');

    boton(dialogo, 'Cancelar').click();
    boton(await ultimoDialogo(), 'Descartar').click();
    await vi.waitFor(() => expect(dialogosAbiertos()).toHaveLength(0));
  });

  it('Cancelar sin cambios', async () => {
    const { panel, servidor } = await abrirPestana();
    const dialogo = await abrirEditor(servidor, panel);

    boton(dialogo, 'Cancelar').click();

    expect(dialogosAbiertos()).toHaveLength(0);
  });

  it('Escape con cambios pregunta y sin cambios cierra', async () => {
    const { panel, servidor } = await abrirPestana();
    const dialogo = await abrirEditor(servidor, panel);
    escribirTexto(campo(dialogo, 'Título'), `${TITULO_EXTRACCION} (nuevo)`);

    const conCambios = presionarEscape(dialogo);

    expect(conCambios.defaultPrevented).toBe(true);
    expect(visible(await ultimoDialogo())).toContain('Hay cambios sin guardar en la plantilla. ¿Descartarlos?');
    boton(await ultimoDialogo(), 'Descartar').click();
    await vi.waitFor(() => expect(dialogosAbiertos()).toHaveLength(0));

    const otro = await abrirEditor(servidor, panel);
    expect(presionarEscape(otro).defaultPrevented).toBe(false);
    expect(dialogosAbiertos()).toHaveLength(0);
  });

  it('volver al texto original cuenta como sin cambios', async () => {
    const { panel, servidor } = await abrirPestana();
    const dialogo = await abrirEditor(servidor, panel);
    escribirTexto(campo(dialogo, 'Título'), 'Otro');
    escribirTexto(campo(dialogo, 'Título'), TITULO_EXTRACCION);

    boton(dialogo, 'Cancelar').click();

    expect(dialogosAbiertos()).toHaveLength(0);
  });
});

describe('Ver las versiones de una plantilla', () => {
  it('Lista de versiones', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('GET /consentimientos/plantillas/{id}/versiones', () => json(200, [version(3), version(2), version(1)]));

    boton(panel, `Versiones de ${TITULO_EXTRACCION}`).click();

    const dialogo = await ultimoDialogo();
    expect(servidor.a('GET', '/consentimientos/plantillas/4/versiones')).toHaveLength(1);
    const resumenes = [...dialogo.querySelectorAll('summary')].map((s) => visible(s));
    expect(resumenes).toHaveLength(3);
    expect(resumenes[0]).toContain('Versión 3');
    expect(resumenes[1]).toContain('Versión 2');
    expect(resumenes[2]).toContain('Versión 1');
    for (const resumen of resumenes) {
      expect(resumen).toContain('admin');
      expect(resumen).toMatch(/\d{2}\/11\/2026 09:15/);
    }
    expect(resumenes[0]).toContain('23/11/2026 09:15');
  });

  it('Leer el texto de una versión anterior', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('GET /consentimientos/plantillas/{id}/versiones', () => json(200, [version(3), version(2), version(1)]));
    boton(panel, `Versiones de ${TITULO_EXTRACCION}`).click();
    const dialogo = await ultimoDialogo();

    const detalles = [...dialogo.querySelectorAll('details')];
    expect(detalles.every((d) => !d.open)).toBe(true);
    const primera = detalles[2] as HTMLDetailsElement;
    primera.open = true;

    expect(primera.querySelector('.cons-texto')?.textContent).toBe('Texto de la versión 1.');
    expect(dialogo.querySelectorAll('textarea, input')).toHaveLength(0);
    expect(dialogo.querySelectorAll('[contenteditable]')).toHaveLength(0);
  });

  it('solo tiene "Cerrar" y devuelve el foco al botón que lo abrió', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('GET /consentimientos/plantillas/{id}/versiones', () => json(200, [version(3)]));
    const abrir = boton(panel, `Versiones de ${TITULO_EXTRACCION}`);
    abrir.click();
    const dialogo = await ultimoDialogo();

    expect([...dialogo.querySelectorAll('button')].map((b) => b.textContent)).toEqual(['Cerrar']);
    boton(dialogo, 'Cerrar').click();

    expect(dialogosAbiertos()).toHaveLength(0);
    expect(document.activeElement).toBe(abrir);
  });

  it('si fallan las versiones, avisa y no abre el diálogo', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('GET /consentimientos/plantillas/{id}/versiones', () => json(500));

    boton(panel, `Versiones de ${TITULO_EXTRACCION}`).click();

    await vi.waitFor(() => expect(visible(panel)).toContain(MENSAJE_ERROR_GENERICO));
    expect(dialogosAbiertos()).toHaveLength(0);
  });
});

describe('Pestaña de plantillas accesible', () => {
  it('Nombre accesible de los botones', async () => {
    const { panel } = await abrirPestana();
    const primera = filas(panel)[0] as HTMLElement;

    const nombres = [...primera.querySelectorAll('button')].map((b) => b.getAttribute('aria-label'));

    expect(nombres).toEqual([
      `Editar ${TITULO_EXTRACCION}`,
      `Versiones de ${TITULO_EXTRACCION}`,
      `Desactivar ${TITULO_EXTRACCION}`,
    ]);
    const segunda = filas(panel)[1] as HTMLElement;
    expect([...segunda.querySelectorAll('button')].map((b) => b.getAttribute('aria-label'))).toEqual([
      `Editar ${TITULO_BLANQUEAMIENTO}`,
      `Versiones de ${TITULO_BLANQUEAMIENTO}`,
      `Activar ${TITULO_BLANQUEAMIENTO}`,
    ]);
  });

  it('El foco vuelve al cerrar el diálogo', async () => {
    const { panel, servidor } = await abrirPestana();
    const abrir = boton(panel, `Editar ${TITULO_EXTRACCION}`);
    responderDetalle(servidor);
    abrir.click();
    const dialogo = await ultimoDialogo();

    presionarEscape(dialogo);

    expect(dialogosAbiertos()).toHaveLength(0);
    expect(document.activeElement).toBe(abrir);
  });

  it('los avisos se anuncian: el de éxito con status y el de error con alert', async () => {
    const { panel } = await abrirPestana();

    expect(panel.querySelector('p[role="status"].aviso')).not.toBeNull();
    expect(panel.querySelector('p[role="alert"].aviso')).not.toBeNull();
  });

  it('la lista tiene nombre accesible', async () => {
    const { panel } = await abrirPestana();

    expect(panel.querySelector('ul.cons-plantillas')?.getAttribute('aria-label')).toBe('Plantillas de consentimiento');
  });
});
