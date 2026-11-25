import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { crearSeccionPresupuestos, type SeccionPresupuestos } from '../src/presupuestos/seccion.ts';
import { mensajeDeError } from '../src/shared/ui/mensajes.ts';
import { boton, campo, errorDeCampo, escribir, json, ServidorFalso, texto } from './apoyo.ts';
import { dialogoAbierto, enviar, opcionesDe } from './apoyo-configuracion.ts';
import { ARANCELES, NOMENCLADOR_C19, PRESUPUESTO_41, responderPresupuestos } from './apoyo-presupuestos.ts';

/** C-50, grupo 3: el armado "Nuevo presupuesto" (spec `frontend-presupuestos`), con hoy = 2026-11-24. */

let seccion: SeccionPresupuestos | null = null;

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

function servidorCon(): ServidorFalso {
  const servidor = new ServidorFalso();
  responderPresupuestos(servidor);
  return servidor;
}

async function montar(servidor: ServidorFalso, pacienteId = 1): Promise<HTMLElement> {
  seccion = crearSeccionPresupuestos({
    pacienteId,
    nombrePaciente: pacienteId === 1 ? 'Paciente Uno' : 'Paciente Dos',
    modo: 'odontologo',
    api: servidor.api,
    mensajeDeError,
    vigente: () => true,
  });
  document.body.replaceChildren(seccion.contenedor);
  await vi.waitFor(() => expect(seccion?.contenedor.getAttribute('aria-busy')).toBeNull());
  return seccion.contenedor;
}

/** Abre "Nuevo presupuesto" y espera a que se pueda emitir. */
async function abrirArmado(servidor: ServidorFalso = servidorCon(), pacienteId = 1): Promise<{ dialogo: HTMLDialogElement; raiz: HTMLElement; servidor: ServidorFalso }> {
  const raiz = await montar(servidor, pacienteId);
  boton(raiz, 'Nuevo presupuesto').click();
  const dialogo = await dialogoAbierto();
  await vi.waitFor(() => expect(boton(dialogo, 'Emitir presupuesto').disabled).toBe(false));
  return { dialogo, raiz, servidor };
}

function items(dialogo: HTMLElement): HTMLElement[] {
  return [...dialogo.querySelectorAll<HTMLElement>('.pre-item')];
}

function control<T extends HTMLElement = HTMLInputElement>(dialogo: HTMLElement, nombre: string, numero: number): T {
  return campo<T>(dialogo, `${nombre} del ítem ${numero}`);
}

function elegirPrestacion(dialogo: HTMLElement, numero: number, prestacionId: number): void {
  escribir(control<HTMLSelectElement>(dialogo, 'Prestación', numero), String(prestacionId));
}

function totales(dialogo: HTMLElement): string {
  return texto(dialogo.querySelector('.pre-pie') as HTMLElement);
}

describe('Armar un presupuesto', () => {
  it('Abrir el armado', async () => {
    const { dialogo, servidor } = await abrirArmado();

    expect(dialogo.querySelector('h2')?.textContent).toBe('Nuevo presupuesto');
    expect(items(dialogo)).toHaveLength(1);
    expect(control(dialogo, 'Cantidad', 1).value).toBe('1');
    expect(opcionesDe(control<HTMLSelectElement>(dialogo, 'Prestación', 1)).slice(1)).toEqual([
      'Consulta o control',
      'Limpieza (profilaxis)',
      'Tratamiento de conducto',
      'Extracción simple',
    ]);
    for (const ruta of ['/aranceles', '/nomenclador', '/pacientes/1/cobertura', '/obras-sociales/1/planes/11/coberturas']) {
      expect(servidor.a('GET', ruta)).toHaveLength(1);
    }
    expect(servidor.a('GET', '/pacientes/1/presupuestos/sugerencias')).toHaveLength(0);
  });

  it('Prestación sin precio', async () => {
    const servidor = servidorCon();
    servidor.responder('GET /aranceles', () => json(200, ARANCELES.map((a) => (a.prestacion.id === 3 ? { ...a, precio: null } : a))));
    const { dialogo } = await abrirArmado(servidor);

    expect(opcionesDe(control<HTMLSelectElement>(dialogo, 'Prestación', 1))).not.toContain('Tratamiento de conducto');
    expect(texto(dialogo)).toContain('Las prestaciones sin precio no se pueden presupuestar.');
  });

  it('Agregar y quitar ítems', async () => {
    const { dialogo } = await abrirArmado();

    boton(dialogo, 'Agregar ítem').click();
    expect(items(dialogo)).toHaveLength(2);
    boton(dialogo, 'Quitar el ítem 1').click();

    expect(items(dialogo)).toHaveLength(1);
    expect(document.activeElement).toBe(control(dialogo, 'Prestación', 1));
  });

  it('no deja quitar el único ítem ni pasar de 50', async () => {
    const { dialogo } = await abrirArmado();

    expect(boton(dialogo, 'Quitar el ítem 1').disabled).toBe(true);
    for (let i = 1; i < 50; i += 1) {
      boton(dialogo, 'Agregar ítem').click();
    }
    expect(items(dialogo)).toHaveLength(50);
    expect(boton(dialogo, 'Agregar ítem').disabled).toBe(true);
  });

  it('el borrador se pierde al cerrar', async () => {
    const { dialogo, raiz } = await abrirArmado();
    elegirPrestacion(dialogo, 1, 2);

    boton(dialogo, 'Cancelar').click();
    boton(raiz, 'Nuevo presupuesto').click();
    const nuevo = await dialogoAbierto();

    expect(control<HTMLSelectElement>(nuevo, 'Prestación', 1).value).toBe('');
  });
});

describe('Código de cada ítem', () => {
  const VARIOS = [...NOMENCLADOR_C19, { id: 104, codigo: 'N-0102', descripcion: 'Consulta de urgencia', prestacion: { id: 1, nombre: 'Consulta o control' }, activo: true }];

  it('Prestación con varios códigos', async () => {
    const servidor = servidorCon();
    servidor.responder('GET /nomenclador', () => json(200, VARIOS));
    const { dialogo } = await abrirArmado(servidor);

    elegirPrestacion(dialogo, 1, 1);

    const codigo = control<HTMLSelectElement>(dialogo, 'Código', 1);
    expect(codigo.value).toBe('');
    expect(opcionesDe(codigo).slice(1)).toEqual(['N-0101 · Consulta', 'N-0102 · Consulta de urgencia']);
  });

  it('con un solo código no hay selector y se muestra', async () => {
    const { dialogo } = await abrirArmado();

    elegirPrestacion(dialogo, 1, 2);

    expect(() => control(dialogo, 'Código', 1)).toThrow();
    expect(texto(items(dialogo)[0] as HTMLElement)).toContain('N-0201');
  });

  it('sin códigos dice que no tiene cobertura', async () => {
    const { dialogo } = await abrirArmado();

    elegirPrestacion(dialogo, 1, 3);

    expect(texto(items(dialogo)[0] as HTMLElement)).toContain('Sin código: no tiene cobertura');
  });
});

describe('Piezas sugeridas del odontograma', () => {
  it('Sumar las sugeridas', async () => {
    const { dialogo, servidor } = await abrirArmado();

    boton(dialogo, 'Agregar piezas sugeridas').click();

    await vi.waitFor(() => expect(items(dialogo)).toHaveLength(2));
    expect(servidor.a('GET', '/pacientes/1/presupuestos/sugerencias')).toHaveLength(1);
    expect(control(dialogo, 'Pieza', 1).value).toBe('16');
    expect(control<HTMLSelectElement>(dialogo, 'Cara', 1).value).toBe('O');
    expect(texto(items(dialogo)[0] as HTMLElement)).toContain('Caries');
    expect(control(dialogo, 'Pieza', 2).value).toBe('36');
    expect(control<HTMLSelectElement>(dialogo, 'Cara', 2).value).toBe('');
    expect(texto(items(dialogo)[1] as HTMLElement)).toContain('A tratar');
    expect(control<HTMLSelectElement>(dialogo, 'Prestación', 1).value).toBe('');
    expect(control<HTMLSelectElement>(dialogo, 'Prestación', 2).value).toBe('');
  });

  it('Sin sugerencias', async () => {
    const { dialogo } = await abrirArmado(servidorCon(), 2);

    boton(dialogo, 'Agregar piezas sugeridas').click();

    await vi.waitFor(() =>
      expect(dialogo.querySelector('[role="status"]:not(.cargando)')?.textContent).toBe('No hay caries ni piezas a tratar en el odontograma.'),
    );
    expect(items(dialogo)).toHaveLength(1);
  });
});

describe('Estimado antes de emitir', () => {
  it('Estimado de Paciente Uno', async () => {
    const { dialogo } = await abrirArmado();
    boton(dialogo, 'Agregar ítem').click();

    elegirPrestacion(dialogo, 1, 1);
    elegirPrestacion(dialogo, 2, 2);

    const [consulta, limpieza] = items(dialogo) as [HTMLElement, HTMLElement];
    expect(texto(consulta)).toContain('Cobertura $ 15.000,00');
    expect(texto(consulta)).toContain('Copago $ 0,00');
    expect(texto(limpieza)).toContain('Cobertura $ 12.500,00');
    expect(texto(limpieza)).toContain('Copago $ 12.500,00');
    for (const dato of ['$ 40.000,00', '$ 27.500,00', '$ 12.500,00', 'Estimado: el importe final lo calcula el sistema al emitir.']) {
      expect(totales(dialogo)).toContain(dato);
    }
    expect(texto(dialogo)).toContain('Obra Social Ejemplo · Plan Base');
  });

  it('Estimado de un paciente particular', async () => {
    const { dialogo } = await abrirArmado(servidorCon(), 2);

    elegirPrestacion(dialogo, 1, 1);

    expect(texto(items(dialogo)[0] as HTMLElement)).toContain('Cobertura $ 0,00');
    expect(texto(items(dialogo)[0] as HTMLElement)).toContain('Copago $ 15.000,00');
    expect(texto(dialogo.querySelector('.pre-cobertura-usada') as HTMLElement)).toContain('Particular');
  });

  it('un ítem incompleto no suma al estimado', async () => {
    const { dialogo } = await abrirArmado();

    expect(totales(dialogo)).toContain('$ 0,00');
    expect(texto(items(dialogo)[0] as HTMLElement)).not.toContain('Copago');
  });
});

describe('Cálculo del estimado', () => {
  it('Redondeo half up', async () => {
    const servidor = servidorCon();
    servidor.responder('GET /aranceles', () => json(200, ARANCELES.map((a) => (a.prestacion.id === 2 ? { ...a, precio: 25000.15 } : a))));
    const { dialogo } = await abrirArmado(servidor);

    elegirPrestacion(dialogo, 1, 2);

    expect(texto(items(dialogo)[0] as HTMLElement)).toContain('Cobertura $ 12.500,08');
    expect(texto(items(dialogo)[0] as HTMLElement)).toContain('Copago $ 12.500,07');
  });

  it('Cantidad mayor que uno', async () => {
    const { dialogo } = await abrirArmado();

    elegirPrestacion(dialogo, 1, 4);
    escribir(control(dialogo, 'Cantidad', 1), '2');

    const item = texto(items(dialogo)[0] as HTMLElement);
    expect(item).toContain('Importe $ 60.000,00');
    expect(item).toContain('Cobertura $ 0,00');
    expect(item).toContain('Copago $ 60.000,00');
    expect(item).toContain('el plan no lo cubre (0 %)');
  });
});

describe('Validar el presupuesto antes de emitirlo', () => {
  it('Ítem sin prestación', async () => {
    const { dialogo, servidor } = await abrirArmado();

    enviar(dialogo);

    expect(errorDeCampo(dialogo, 'Prestación del ítem 1')).toBe('Elegí la prestación.');
    expect(document.activeElement).toBe(control(dialogo, 'Prestación', 1));
    expect(servidor.a('POST', '/pacientes/1/presupuestos')).toHaveLength(0);
  });

  it('Falta el código', async () => {
    const servidor = servidorCon();
    servidor.responder('GET /nomenclador', () =>
      json(200, [...NOMENCLADOR_C19, { id: 104, codigo: 'N-0102', descripcion: 'Urgencia', prestacion: { id: 1, nombre: 'Consulta o control' }, activo: true }]),
    );
    const { dialogo } = await abrirArmado(servidor);
    elegirPrestacion(dialogo, 1, 1);

    enviar(dialogo);

    expect(errorDeCampo(dialogo, 'Código del ítem 1')).toBe('Elegí el código del nomenclador.');
    expect(servidor.a('POST', '/pacientes/1/presupuestos')).toHaveLength(0);
  });

  it('Pieza fuera de la notación FDI', async () => {
    const { dialogo, servidor } = await abrirArmado();
    elegirPrestacion(dialogo, 1, 4);

    escribir(control(dialogo, 'Pieza', 1), '19');
    enviar(dialogo);

    expect(errorDeCampo(dialogo, 'Pieza del ítem 1')).toBe('La pieza no existe en la notación FDI.');
    expect(servidor.a('POST', '/pacientes/1/presupuestos')).toHaveLength(0);
  });

  it('Cara palatina en una pieza inferior', async () => {
    const { dialogo } = await abrirArmado();
    elegirPrestacion(dialogo, 1, 1);
    escribir(control(dialogo, 'Pieza', 1), '36');

    escribir(control<HTMLSelectElement>(dialogo, 'Cara', 1), 'P');
    enviar(dialogo);

    expect(errorDeCampo(dialogo, 'Cara del ítem 1')).toBe('Esa cara no corresponde a la pieza.');
  });

  it('Cara sin pieza', async () => {
    const { dialogo } = await abrirArmado();
    elegirPrestacion(dialogo, 1, 1);

    escribir(control<HTMLSelectElement>(dialogo, 'Cara', 1), 'O');
    enviar(dialogo);

    expect(errorDeCampo(dialogo, 'Cara del ítem 1')).toBe('Indicá la pieza para elegir una cara.');
  });

  it('cantidad fuera de rango', async () => {
    const { dialogo } = await abrirArmado();
    elegirPrestacion(dialogo, 1, 1);

    escribir(control(dialogo, 'Cantidad', 1), '0');
    enviar(dialogo);

    expect(errorDeCampo(dialogo, 'Cantidad del ítem 1')).toBe('Usá un número entero de 1 a 99.');
  });
});

describe('Emitir un presupuesto', () => {
  it('Emitir el presupuesto de Paciente Uno', async () => {
    const servidor = servidorCon();
    servidor.responder('POST /pacientes/{id}/presupuestos', () => json(201, PRESUPUESTO_41));
    const { dialogo, raiz } = await abrirArmado(servidor);
    boton(dialogo, 'Agregar ítem').click();
    elegirPrestacion(dialogo, 1, 1);
    elegirPrestacion(dialogo, 2, 2);

    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('POST', '/pacientes/1/presupuestos')).toHaveLength(1));
    const cuerpo = servidor.ultimo('POST', '/pacientes/1/presupuestos').cuerpo;
    expect(cuerpo).toEqual({
      items: [
        { prestacionId: 1, nomencladorId: 101, pieza: null, cara: null, cantidad: 1 },
        { prestacionId: 2, nomencladorId: 102, pieza: null, cara: null, cantidad: 1 },
      ],
    });
    expect(JSON.stringify(cuerpo)).not.toMatch(/profesional|precio|porcentaje/);
    await vi.waitFor(() => expect(dialogo.open).toBe(false));
    await vi.waitFor(() => expect(servidor.a('GET', '/pacientes/1/presupuestos')).toHaveLength(2));
    await vi.waitFor(() => expect(raiz.querySelector('[role="status"]:not(.cargando)')?.textContent).toBe('Presupuesto emitido.'));
  });

  it('Ítem de una pieza sugerida', async () => {
    const servidor = servidorCon();
    servidor.responder('POST /pacientes/{id}/presupuestos', () => json(201, PRESUPUESTO_41));
    const { dialogo } = await abrirArmado(servidor);
    boton(dialogo, 'Agregar piezas sugeridas').click();
    await vi.waitFor(() => expect(items(dialogo)).toHaveLength(2));
    elegirPrestacion(dialogo, 1, 1);
    elegirPrestacion(dialogo, 2, 4);

    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('POST', '/pacientes/1/presupuestos')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/pacientes/1/presupuestos').cuerpo).toEqual({
      items: [
        { prestacionId: 1, nomencladorId: 101, pieza: 16, cara: 'O', cantidad: 1 },
        { prestacionId: 4, nomencladorId: 103, pieza: 36, cara: null, cantidad: 1 },
      ],
    });
  });

  it('Los valores finales son los de la API', async () => {
    const servidor = servidorCon();
    const emitido = {
      ...PRESUPUESTO_41,
      id: 42,
      items: [{ ...PRESUPUESTO_41.items[1], importe: 26000.0, precioUnitario: 26000.0, cobertura: 13000.0, copago: 13000.0 }],
      totales: { precio: 26000.0, cobertura: 13000.0, copago: 13000.0 },
    };
    servidor.responder('POST /pacientes/{id}/presupuestos', () => {
      servidor.responder('GET /pacientes/1/presupuestos', () => json(200, [emitido]));
      return json(201, emitido);
    });
    const { dialogo, raiz } = await abrirArmado(servidor);
    elegirPrestacion(dialogo, 1, 2);
    expect(texto(items(dialogo)[0] as HTMLElement)).toContain('Copago $ 12.500,00');

    enviar(dialogo);

    // El detalle del emitido se abre con los valores de la respuesta.
    await vi.waitFor(() => expect(raiz.querySelector('.pre-detalle')).not.toBeNull());
    expect(texto(raiz.querySelector('.pre-detalle') as HTMLElement)).toContain('$ 13.000,00');
  });
});

describe('Errores de la API al emitir', () => {
  async function emitirConRespuesta(respuesta: Response, cantidadItems = 1): Promise<HTMLDialogElement> {
    const servidor = servidorCon();
    servidor.responder('POST /pacientes/{id}/presupuestos', () => respuesta);
    const { dialogo } = await abrirArmado(servidor);
    for (let i = 1; i < cantidadItems; i += 1) {
      boton(dialogo, 'Agregar ítem').click();
    }
    for (let i = 1; i <= cantidadItems; i += 1) {
      elegirPrestacion(dialogo, i, 2);
    }
    enviar(dialogo);
    await vi.waitFor(() => expect(servidor.a('POST', '/pacientes/1/presupuestos')).toHaveLength(1));
    return dialogo;
  }

  it('Prestación sin arancel al emitir', async () => {
    const dialogo = await emitirConRespuesta(json(409, { codigo: 'ARANCEL_INEXISTENTE' }));

    await vi.waitFor(() =>
      expect(dialogo.querySelector('[role="alert"]')?.textContent).toBe('Una de las prestaciones ya no tiene precio. Revisá los aranceles.'),
    );
    expect(dialogo.open).toBe(true);
    expect(control<HTMLSelectElement>(dialogo, 'Prestación', 1).value).toBe('2');
  });

  it('Código requerido', async () => {
    const dialogo = await emitirConRespuesta(json(422, { codigo: 'CODIGO_NOMENCLADOR_REQUERIDO' }));

    await vi.waitFor(() =>
      expect(dialogo.querySelector('[role="alert"]')?.textContent).toBe('Una de las prestaciones tiene varios códigos: elegí uno.'),
    );
  });

  it('Campo de un ítem', async () => {
    const dialogo = await emitirConRespuesta(
      json(422, { codigo: 'DATOS_INVALIDOS', detail: 'Datos inválidos', errores: [{ campo: 'items[1].pieza', mensaje: 'valor inválido' }] }),
      2,
    );

    await vi.waitFor(() => expect(control(dialogo, 'Pieza', 2).getAttribute('aria-invalid')).toBe('true'));
    expect(errorDeCampo(dialogo, 'Pieza del ítem 2')).toBe('La pieza no existe en la notación FDI.');
  });
});

describe('Accesibilidad del armado', () => {
  it('Etiquetas de los ítems', async () => {
    const { dialogo } = await abrirArmado();
    boton(dialogo, 'Agregar ítem').click();

    for (const nombre of ['Prestación', 'Pieza', 'Cara', 'Cantidad']) {
      const elemento = control(dialogo, nombre, 2);
      expect(elemento.labels?.[0]?.textContent).toBe(`${nombre} del ítem 2`);
    }
  });

  it('Foco al cerrar el armado', async () => {
    const { dialogo, raiz } = await abrirArmado();
    const nuevo = boton(raiz, 'Nuevo presupuesto');

    boton(dialogo, 'Cancelar').click();

    expect(document.activeElement).toBe(nuevo);
  });
});
