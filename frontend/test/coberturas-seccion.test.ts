import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { crearSeccionCobertura, type SeccionCobertura } from '../src/coberturas/seccion.ts';
import { mensajeDeError } from '../src/shared/ui/mensajes.ts';
import { boton, campo, errorDeCampo, escribir, json, ServidorFalso, texto } from './apoyo.ts';
import { dialogoAbierto, enviar, opcionesDe } from './apoyo-configuracion.ts';
import { AFILIACION_DOS, AFILIACION_UNO, OBRAS_SOCIALES, PARTICULAR, responderCoberturas } from './apoyo-coberturas.ts';

/** C-49, grupo 3: sección "Cobertura" del paciente (spec `frontend-coberturas`), con hoy = 2026-11-24. */

let seccion: SeccionCobertura | null = null;

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-11-24T15:00:00Z'));
});

afterEach(() => {
  seccion?.destruir();
  seccion = null;
  document.querySelectorAll('dialog').forEach((d) => d.remove());
  vi.useRealTimers();
});

const NOMBRES: Readonly<Record<number, string>> = { 1: 'Paciente Uno', 2: 'Paciente Dos' };

/** Monta la sección de un paciente y espera a que termine de cargar. */
async function montar(servidor: ServidorFalso, pacienteId = 1, editable = true): Promise<HTMLElement> {
  seccion = crearSeccionCobertura({
    pacienteId,
    nombrePaciente: NOMBRES[pacienteId] ?? '',
    editable,
    api: servidor.api,
    mensajeDeError,
    vigente: () => true,
  });
  document.body.replaceChildren(seccion.contenedor);
  await vi.waitFor(() => expect(seccion?.contenedor.getAttribute('aria-busy')).toBeNull());
  return seccion.contenedor;
}

function servidorCon(): ServidorFalso {
  const servidor = new ServidorFalso();
  responderCoberturas(servidor);
  return servidor;
}

function tarjeta(raiz: HTMLElement): string {
  return texto(raiz.querySelector('.cob-tarjeta') as HTMLElement);
}

function filas(raiz: HTMLElement): HTMLElement[] {
  return [...raiz.querySelectorAll<HTMLElement>('.cob-afiliacion')];
}

/** Abre "Afiliar" y espera a que el catálogo esté cargado. */
async function abrirAfiliar(raiz: HTMLElement): Promise<HTMLDialogElement> {
  boton(raiz, 'Afiliar').click();
  const dialogo = await dialogoAbierto();
  await vi.waitFor(() => expect(campo<HTMLSelectElement>(dialogo, 'Obra social').disabled).toBe(false));
  return dialogo;
}

describe('Cobertura vigente o particular', () => {
  it('Paciente con cobertura vigente', async () => {
    const raiz = await montar(servidorCon(), 1);

    expect(raiz.querySelector('h2')?.textContent).toBe('Cobertura');
    expect(tarjeta(raiz)).toContain('Obra Social Ejemplo (OSE)');
    expect(tarjeta(raiz)).toContain('Plan Base');
    expect(tarjeta(raiz)).toContain('N.º de afiliado OSE-000001');
    expect(tarjeta(raiz)).toContain('Desde 01/01/2026, sin fecha de fin');
  });

  it('Paciente particular', async () => {
    const raiz = await montar(servidorCon(), 2);

    expect(tarjeta(raiz)).toContain('Se atiende como particular');
  });

  it('pide la cobertura y el historial del paciente', async () => {
    const servidor = servidorCon();
    await montar(servidor, 2);

    expect(servidor.a('GET', '/pacientes/2/cobertura')).toHaveLength(1);
    expect(servidor.a('GET', '/pacientes/2/afiliaciones')).toHaveLength(1);
  });

  it('si falla muestra el error y "Reintentar" en la sección', async () => {
    const servidor = servidorCon();
    let pedidos = 0;
    servidor.responder('GET /pacientes/1/cobertura', () => {
      pedidos += 1;
      return pedidos === 1 ? json(500) : json(200, PARTICULAR);
    });
    const raiz = await montar(servidor, 1);

    expect(raiz.querySelector('[role="alert"]:not(:empty)')?.textContent).toBe(
      'No pudimos completar la operación. Probá de nuevo en unos minutos.',
    );
    // Sin la cobertura cargada no se ofrece "Afiliar".
    expect(boton(raiz, 'Afiliar').hidden).toBe(true);
    boton(raiz, 'Reintentar').click();

    await vi.waitFor(() => expect(tarjeta(raiz)).toContain('Se atiende como particular'));
  });
});

describe('Historial de afiliaciones', () => {
  it('Historial con una afiliación finalizada', async () => {
    const raiz = await montar(servidorCon(), 2);
    const [fila] = filas(raiz);

    expect(raiz.querySelector('h3')?.textContent).toBe('Historial de afiliaciones');
    expect(texto(fila as HTMLElement)).toContain('Plan Oro');
    expect(texto(fila as HTMLElement)).toContain('Prepaga Prueba');
    expect(texto(fila as HTMLElement)).toContain('PP-77');
    expect(texto(fila as HTMLElement)).toContain('Del 01/01/2026 al 31/10/2026');
    expect(fila?.querySelector('.etiqueta')?.textContent).toBe('Finalizada');
  });

  it('Afiliación próxima', async () => {
    const servidor = servidorCon();
    servidor.responder('GET /pacientes/1/afiliaciones', () =>
      json(200, [
        { ...AFILIACION_DOS, id: 40, numeroAfiliado: 'PP-78', vigenteDesde: '2026-12-01', vigenteHasta: null, vigente: false },
        { ...AFILIACION_UNO, vigenteHasta: '2026-11-30' },
      ]),
    );
    const raiz = await montar(servidor, 1);
    const [oro, base] = filas(raiz);

    expect(texto(oro as HTMLElement)).toContain('Plan Oro');
    expect(oro?.querySelector('.etiqueta')?.textContent).toBe('Próxima');
    expect(texto(base as HTMLElement)).toContain('Plan Base');
    expect(base?.querySelector('.etiqueta')?.textContent).toBe('Vigente');
  });

  it('Sin afiliaciones', async () => {
    const servidor = servidorCon();
    servidor.responder('GET /pacientes/2/afiliaciones', () => json(200, []));
    const raiz = await montar(servidor, 2);

    expect(texto(raiz)).toContain('Se atiende como particular');
    expect(texto(raiz)).toContain('Sin afiliaciones registradas.');
  });
});

describe('Afiliar a un paciente', () => {
  it('Afiliar a un paciente particular', async () => {
    const servidor = servidorCon();
    servidor.responder('POST /pacientes/{id}/afiliaciones', () => json(201, {}));
    const raiz = await montar(servidor, 2);

    const dialogo = await abrirAfiliar(raiz);
    escribir(campo<HTMLSelectElement>(dialogo, 'Obra social'), '1');
    escribir(campo<HTMLSelectElement>(dialogo, 'Plan'), '11');
    escribir(campo(dialogo, 'N.º de afiliado'), 'OSE-000002');
    expect(campo(dialogo, 'Vigente desde').value).toBe('2026-11-24');
    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('POST', '/pacientes/2/afiliaciones')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/pacientes/2/afiliaciones').cuerpo).toEqual({
      planId: 11,
      numeroAfiliado: 'OSE-000002',
      vigenteDesde: '2026-11-24',
      vigenteHasta: null,
    });
    await vi.waitFor(() => expect(servidor.a('GET', '/pacientes/2/cobertura')).toHaveLength(2));
    expect(servidor.a('GET', '/pacientes/2/afiliaciones')).toHaveLength(2);
    await vi.waitFor(() => expect(raiz.querySelector('[role="status"]:not(.cargando)')?.textContent).toBe('Afiliación guardada.'));
    expect(dialogo.open).toBe(false);
  });

  it('Solo planes activos', async () => {
    const raiz = await montar(servidorCon(), 2);

    const dialogo = await abrirAfiliar(raiz);
    escribir(campo<HTMLSelectElement>(dialogo, 'Obra social'), '1');

    const planes = opcionesDe(campo<HTMLSelectElement>(dialogo, 'Plan'));
    expect(planes).toContain('Plan Base');
    expect(planes.some((p) => p.includes('Plan Integral'))).toBe(false);
  });

  it('solo obras sociales activas', async () => {
    const servidor = servidorCon();
    servidor.responder('GET /obras-sociales', () => json(200, [OBRAS_SOCIALES[0], { ...OBRAS_SOCIALES[1], activa: false }]));
    const raiz = await montar(servidor, 2);

    const dialogo = await abrirAfiliar(raiz);

    const obras = opcionesDe(campo<HTMLSelectElement>(dialogo, 'Obra social'));
    expect(obras).toContain('Obra Social Ejemplo (OSE)');
    expect(obras.some((o) => o.includes('Prepaga Prueba'))).toBe(false);
  });

  it('Plan deshabilitado sin obra social', async () => {
    const raiz = await montar(servidorCon(), 2);

    const dialogo = await abrirAfiliar(raiz);

    expect(campo<HTMLSelectElement>(dialogo, 'Plan').disabled).toBe(true);
  });

  it('el inicio por defecto es el día siguiente al último cierre si es posterior a hoy', async () => {
    const servidor = servidorCon();
    servidor.responder('GET /pacientes/1/afiliaciones', () => json(200, [{ ...AFILIACION_UNO, vigenteHasta: '2026-11-30' }]));
    const raiz = await montar(servidor, 1);

    const dialogo = await abrirAfiliar(raiz);

    expect(campo(dialogo, 'Vigente desde').value).toBe('2026-12-01');
  });

  it('pide el catálogo una sola vez por sección', async () => {
    const servidor = servidorCon();
    const raiz = await montar(servidor, 2);

    const dialogo = await abrirAfiliar(raiz);
    boton(dialogo, 'Cancelar').click();
    await abrirAfiliar(raiz);

    expect(servidor.a('GET', '/obras-sociales')).toHaveLength(1);
  });
});

describe('Validar la afiliación antes de enviarla', () => {
  async function dialogoConPlanBase(servidor: ServidorFalso): Promise<HTMLDialogElement> {
    const raiz = await montar(servidor, 2);
    const dialogo = await abrirAfiliar(raiz);
    escribir(campo<HTMLSelectElement>(dialogo, 'Obra social'), '1');
    escribir(campo<HTMLSelectElement>(dialogo, 'Plan'), '11');
    return dialogo;
  }

  it('Número con caracteres no permitidos', async () => {
    const servidor = servidorCon();
    const dialogo = await dialogoConPlanBase(servidor);

    escribir(campo(dialogo, 'N.º de afiliado'), 'OSE 0001#');
    enviar(dialogo);

    expect(errorDeCampo(dialogo, 'N.º de afiliado')).toBe('Usá de 1 a 30 letras, números, guiones, barras o puntos.');
    expect(campo(dialogo, 'N.º de afiliado').getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(campo(dialogo, 'N.º de afiliado'));
    expect(servidor.a('POST', '/pacientes/2/afiliaciones')).toHaveLength(0);
  });

  it('Espacios en los extremos', async () => {
    const servidor = servidorCon();
    servidor.responder('POST /pacientes/{id}/afiliaciones', () => json(201, {}));
    const dialogo = await dialogoConPlanBase(servidor);

    escribir(campo(dialogo, 'Vigente desde'), '2026-01-01');
    escribir(campo(dialogo, 'N.º de afiliado'), '  OSE-000001  ');
    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('POST', '/pacientes/2/afiliaciones')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/pacientes/2/afiliaciones').cuerpo).toMatchObject({ numeroAfiliado: 'OSE-000001' });
  });

  it('Fin anterior al inicio', async () => {
    const servidor = servidorCon();
    const dialogo = await dialogoConPlanBase(servidor);

    escribir(campo(dialogo, 'N.º de afiliado'), 'OSE-000002');
    escribir(campo(dialogo, 'Vigente desde'), '2026-06-01');
    escribir(campo(dialogo, 'Vigente hasta (opcional)'), '2026-05-31');
    enviar(dialogo);

    expect(errorDeCampo(dialogo, 'Vigente hasta (opcional)')).toBe('Tiene que ser igual o posterior a la fecha de inicio.');
    expect(servidor.a('POST', '/pacientes/2/afiliaciones')).toHaveLength(0);
  });

  it('sin plan no se envía y el foco va al primer campo con error', async () => {
    const servidor = servidorCon();
    const raiz = await montar(servidor, 2);
    const dialogo = await abrirAfiliar(raiz);

    escribir(campo(dialogo, 'N.º de afiliado'), 'OSE-000002');
    enviar(dialogo);

    expect(errorDeCampo(dialogo, 'Obra social')).toBe('Elegí la obra social.');
    expect(errorDeCampo(dialogo, 'Plan')).toBe('Elegí el plan.');
    expect(document.activeElement).toBe(campo(dialogo, 'Obra social'));
    expect(servidor.a('POST', '/pacientes/2/afiliaciones')).toHaveLength(0);
  });
});

describe('Errores de la API al afiliar o corregir', () => {
  async function afiliarYRechazar(pacienteId: number, plan: { obra: string; plan: string }, numero: string, respuesta: Response): Promise<{ dialogo: HTMLDialogElement; raiz: HTMLElement }> {
    const servidor = servidorCon();
    // "Plan Integral" se ofrece activo para poder elegirlo y que la API lo rechace.
    servidor.responder('GET /obras-sociales', () =>
      json(200, [{ ...OBRAS_SOCIALES[0], planes: OBRAS_SOCIALES[0]?.planes.map((p) => ({ ...p, activo: true })) }, OBRAS_SOCIALES[1]]),
    );
    servidor.responder('POST /pacientes/{id}/afiliaciones', () => respuesta);
    const raiz = await montar(servidor, pacienteId);
    const dialogo = await abrirAfiliar(raiz);
    escribir(campo<HTMLSelectElement>(dialogo, 'Obra social'), plan.obra);
    escribir(campo<HTMLSelectElement>(dialogo, 'Plan'), plan.plan);
    escribir(campo(dialogo, 'N.º de afiliado'), numero);
    escribir(campo(dialogo, 'Vigente desde'), '2026-11-01');
    enviar(dialogo);
    await vi.waitFor(() => expect(servidor.a('POST', `/pacientes/${pacienteId}/afiliaciones`)).toHaveLength(1));
    return { dialogo, raiz };
  }

  it('Vigencia superpuesta', async () => {
    const { dialogo } = await afiliarYRechazar(1, { obra: '2', plan: '21' }, 'PP-77', json(409, { codigo: 'VIGENCIA_SUPERPUESTA' }));

    await vi.waitFor(() =>
      expect(dialogo.querySelector('[role="alert"]')?.textContent).toBe(
        'El paciente ya tiene una cobertura en esas fechas. Cerrá la vigente o cambiá las fechas.',
      ),
    );
    expect(dialogo.open).toBe(true);
    expect(campo(dialogo, 'N.º de afiliado').value).toBe('PP-77');
  });

  it('Número de afiliado repetido', async () => {
    const { dialogo, raiz } = await afiliarYRechazar(2, { obra: '1', plan: '12' }, 'ose-000001', json(409, { codigo: 'AFILIADO_DUPLICADO' }));

    await vi.waitFor(() =>
      expect(errorDeCampo(dialogo, 'N.º de afiliado')).toBe('Ese número ya lo tiene otro paciente de la misma obra social.'),
    );
    expect(dialogo.querySelector('[role="alert"]')?.textContent).toBe('');
    expect(document.body.textContent).not.toContain('ose-000001');
    expect(raiz.textContent).not.toContain('ose-000001');
  });

  it('Plan inactivo', async () => {
    const { dialogo } = await afiliarYRechazar(2, { obra: '1', plan: '11' }, 'OSE-000002', json(409, { codigo: 'PLAN_INACTIVO' }));

    await vi.waitFor(() => expect(dialogo.querySelector('[role="alert"]')?.textContent).toBe('El plan está inactivo. Elegí otro.'));
  });

  it('DATOS_INVALIDOS del número va junto al número, sin repetirlo', async () => {
    const { dialogo } = await afiliarYRechazar(
      2,
      { obra: '1', plan: '11' },
      'OSE-000002',
      json(422, { codigo: 'DATOS_INVALIDOS', detail: 'Datos inválidos', errores: [{ campo: 'numeroAfiliado', mensaje: 'valor inválido' }] }),
    );

    await vi.waitFor(() =>
      expect(errorDeCampo(dialogo, 'N.º de afiliado')).toBe('Usá de 1 a 30 letras, números, guiones, barras o puntos.'),
    );
    expect(document.body.textContent).not.toContain('OSE-000002');
  });
});

describe('Corregir una afiliación', () => {
  it('Corregir el número', async () => {
    const servidor = servidorCon();
    servidor.responder('PUT /pacientes/{id}/afiliaciones/{id}', () => json(200, {}));
    const raiz = await montar(servidor, 1);

    boton(raiz, 'Corregir la afiliación a Plan Base').click();
    const dialogo = await dialogoAbierto();
    await vi.waitFor(() => expect(campo<HTMLSelectElement>(dialogo, 'Plan').value).toBe('11'));
    expect(campo(dialogo, 'Vigente desde').value).toBe('2026-01-01');
    expect(campo(dialogo, 'N.º de afiliado').value).toBe('OSE-000001');
    escribir(campo(dialogo, 'N.º de afiliado'), 'OSE-000010');
    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('PUT', '/pacientes/1/afiliaciones/31')).toHaveLength(1));
    expect(servidor.ultimo('PUT', '/pacientes/1/afiliaciones/31').cuerpo).toEqual({
      planId: 11,
      numeroAfiliado: 'OSE-000010',
      vigenteDesde: '2026-01-01',
    });
    await vi.waitFor(() => expect(servidor.a('GET', '/pacientes/1/afiliaciones')).toHaveLength(2));
  });

  it('Conservar un plan desactivado', async () => {
    const servidor = servidorCon();
    servidor.responder('GET /obras-sociales', () =>
      json(200, [{ ...OBRAS_SOCIALES[0], planes: OBRAS_SOCIALES[0]?.planes.map((p) => ({ ...p, activo: false })) }, OBRAS_SOCIALES[1]]),
    );
    const raiz = await montar(servidor, 1);

    boton(raiz, 'Corregir la afiliación a Plan Base').click();
    const dialogo = await dialogoAbierto();
    const plan = campo<HTMLSelectElement>(dialogo, 'Plan');

    await vi.waitFor(() => expect(plan.value).toBe('11'));
    expect(plan.selectedOptions[0]?.textContent).toBe('Plan Base (inactivo)');
    // Los otros planes inactivos no se ofrecen.
    expect(opcionesDe(plan).some((p) => p.includes('Plan Integral'))).toBe(false);
  });
});

describe('Cerrar una afiliación', () => {
  it('Cerrar al cambiar de cobertura', async () => {
    const servidor = servidorCon();
    servidor.responder('POST /pacientes/{id}/afiliaciones/{id}/cerrar', () => {
      servidor.responder('GET /pacientes/1/afiliaciones', () => json(200, [{ ...AFILIACION_UNO, vigenteHasta: '2026-11-30' }]));
      return json(200, {});
    });
    const raiz = await montar(servidor, 1);

    boton(raiz, 'Cerrar la afiliación a Plan Base').click();
    const dialogo = await dialogoAbierto();
    expect(campo(dialogo, 'Vigente hasta').value).toBe('2026-11-24');
    escribir(campo(dialogo, 'Vigente hasta'), '2026-11-30');
    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('POST', '/pacientes/1/afiliaciones/31/cerrar')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/pacientes/1/afiliaciones/31/cerrar').cuerpo).toEqual({ vigenteHasta: '2026-11-30' });
    await vi.waitFor(() => expect(texto(filas(raiz)[0] as HTMLElement)).toContain('Del 01/01/2026 al 30/11/2026'));
  });

  it('Cierre anterior al inicio', async () => {
    const servidor = servidorCon();
    const raiz = await montar(servidor, 1);

    boton(raiz, 'Cerrar la afiliación a Plan Base').click();
    const dialogo = await dialogoAbierto();
    escribir(campo(dialogo, 'Vigente hasta'), '2025-12-31');
    enviar(dialogo);

    expect(errorDeCampo(dialogo, 'Vigente hasta')).toBe('Tiene que ser igual o posterior a la fecha de inicio.');
    expect(servidor.a('POST', '/pacientes/1/afiliaciones/31/cerrar')).toHaveLength(0);
  });

  it('Otra persona ya la cerró', async () => {
    const servidor = servidorCon();
    servidor.responder('POST /pacientes/{id}/afiliaciones/{id}/cerrar', () => json(409, { codigo: 'AFILIACION_CERRADA' }));
    const raiz = await montar(servidor, 1);

    boton(raiz, 'Cerrar la afiliación a Plan Base').click();
    const dialogo = await dialogoAbierto();
    enviar(dialogo);

    await vi.waitFor(() => expect(dialogo.open).toBe(false));
    await vi.waitFor(() => expect(servidor.a('GET', '/pacientes/1/afiliaciones')).toHaveLength(2));
    expect([...raiz.querySelectorAll('[role="alert"]')].map((a) => a.textContent)).toContain('Esa afiliación ya estaba cerrada.');
  });

  it('Sin cierre para una afiliación cerrada', async () => {
    const raiz = await montar(servidorCon(), 2);

    const [fila] = filas(raiz);
    const textos = [...(fila as HTMLElement).querySelectorAll('button')].map((b) => b.textContent);
    expect(textos).toContain('Corregir');
    expect(textos).not.toContain('Cerrar afiliación');
  });
});

describe('Solo lectura para el odontólogo', () => {
  it('El odontólogo ve la cobertura', async () => {
    const raiz = await montar(servidorCon(), 1, false);

    expect(texto(raiz)).toContain('Plan Base');
    expect(texto(raiz)).toContain('OSE-000001');
    expect(texto(raiz)).toContain('Solo lectura: la cobertura la cargan la recepción y la administración.');
    const textos = [...raiz.querySelectorAll('button')].map((b) => b.textContent);
    expect(textos).not.toContain('Afiliar');
    expect(textos).not.toContain('Corregir');
    expect(textos).not.toContain('Cerrar afiliación');
  });
});

describe('Coberturas accesibles', () => {
  it('Foco al cerrar el diálogo', async () => {
    const raiz = await montar(servidorCon(), 2);
    const afiliar = boton(raiz, 'Afiliar');

    const dialogo = await abrirAfiliar(raiz);
    boton(dialogo, 'Cancelar').click();

    expect(dialogo.isConnected).toBe(false);
    expect(document.activeElement).toBe(afiliar);
  });

  it('Etiquetas de los campos', async () => {
    const raiz = await montar(servidorCon(), 2);
    const dialogo = await abrirAfiliar(raiz);

    for (const etiqueta of ['Obra social', 'Plan', 'N.º de afiliado', 'Vigente desde', 'Vigente hasta (opcional)']) {
      const control = campo(dialogo, etiqueta);
      expect(control.labels?.[0]?.textContent).toBe(etiqueta);
    }
  });

  it('la sección tiene su título y los estados escritos', async () => {
    const raiz = await montar(servidorCon(), 1);

    expect(raiz.getAttribute('aria-labelledby')).toBe(raiz.querySelector('h2')?.id);
    expect(raiz.querySelector('.cob-tarjeta .etiqueta')?.textContent).toBe('Vigente');
  });

  it('destruir() descarta las respuestas que llegan después', async () => {
    const servidor = servidorCon();
    let responder: (r: Response) => void = () => undefined;
    servidor.responder('GET /pacientes/1/cobertura', () => new Promise<Response>((r) => (responder = r)));
    seccion = crearSeccionCobertura({ pacienteId: 1, nombrePaciente: 'Paciente Uno', editable: true, api: servidor.api, mensajeDeError, vigente: () => true });
    const contenedor = seccion.contenedor;
    document.body.replaceChildren(contenedor);

    seccion.destruir();
    responder(json(200, { particular: false, afiliacion: AFILIACION_UNO }));
    await new Promise((r) => setTimeout(r, 0));

    expect(contenedor.querySelector('.cob-tarjeta')).toBeNull();
  });
});
