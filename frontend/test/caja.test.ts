import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { formatearPesos, leerImporte } from '../src/caja/dinero.ts';
import { textoDeModulo } from './estilos.ts';
import {
  abrirApp,
  boton,
  campo,
  elegirPaciente,
  errorDeCampo,
  escribir,
  json,
  ServidorFalso,
  texto,
  type AppDePrueba,
} from './apoyo.ts';

// Pantalla Caja (spec frontend-caja, C-22). Hoy es el lunes 2026-12-07; la sesión es de `recepcion` salvo que se diga.

const SESION_ADMIN = { autenticado: true, username: 'admin', rol: 'ADMIN' };

let servidor: ServidorFalso;
let app: AppDePrueba | undefined;
let cajaAbierta: Record<string, unknown> | null;
let pendientes: Record<string, unknown>;
let historial: unknown[];

function raiz(): HTMLElement {
  if (app === undefined) {
    throw new Error('La aplicación no está abierta');
  }
  return app.raiz;
}

function dialogo(): HTMLDialogElement {
  const abierto = document.querySelector<HTMLDialogElement>('dialog[open]');
  if (abierto === null) {
    throw new Error('No hay un formulario abierto');
  }
  return abierto;
}

function seccion(clase: string): HTMLElement {
  const encontrada = raiz().querySelector<HTMLElement>(`.${clase}`);
  if (encontrada === null) {
    throw new Error(`No está la sección ${clase}`);
  }
  return encontrada;
}

function botones(elemento: ParentNode): string[] {
  return [...elemento.querySelectorAll('button')].map((b) => b.textContent?.trim() ?? '');
}

function aviso(rol: 'status' | 'alert'): string {
  return raiz().querySelector(`main > [role="${rol}"]`)?.textContent ?? '';
}

function caja(cambios: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    id: 3,
    fecha: '2026-12-07',
    montoInicial: 5000,
    abiertaPor: 'recepcion',
    abiertaEn: '2026-12-07T11:05:00Z',
    cerradaPor: null,
    cerradaEn: null,
    totales: { EFECTIVO: 9000, DEBITO: 15000, CREDITO: 0, TRANSFERENCIA: 0 },
    efectivoEsperado: 14000,
    efectivoContado: null,
    diferencia: null,
    motivoDiferencia: null,
    cobros: [
      cobro({ id: 21, importe: 4000, medio: 'EFECTIVO' }),
      cobro({ id: 22, importe: 2000, medio: 'EFECTIVO', anulado: true, anuladoPor: 'admin', motivoAnulacion: 'Error' }),
    ],
    ...cambios,
  };
}

function cobro(cambios: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    id: 21,
    cargoId: 7,
    paciente: 'Uno, Paciente',
    registradoEn: '2026-12-07T13:35:00Z',
    medio: 'EFECTIVO',
    importe: 4000,
    registradoPor: 'recepcion',
    anulado: false,
    anuladoPor: null,
    anuladoEn: null,
    motivoAnulacion: null,
    ...cambios,
  };
}

function pendiente(cambios: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    id: 7,
    pacienteId: 1,
    paciente: 'Uno, Paciente',
    fecha: '2026-12-07',
    inicio: '2026-12-07T13:00:00Z',
    prestacion: 'Consulta o control',
    copago: 9000,
    pagado: 4000,
    saldo: 5000,
    ...cambios,
  };
}

async function abrirCaja(): Promise<void> {
  app = abrirApp(servidor, '#/caja');
  await vi.waitFor(() => expect(servidor.a('GET', '/cajas/abierta').length).toBeGreaterThan(0));
  await vi.waitFor(() => expect(raiz().querySelector('.caja-grilla[aria-busy]')).toBeNull());
  await vi.waitFor(() => expect(servidor.a('GET', '/cargos/pendientes').length).toBeGreaterThan(0));
}

function conAdmin(): void {
  servidor.responder('GET /public/sesion', () => json(200, SESION_ADMIN));
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-12-07T15:00:00Z'));
  servidor = new ServidorFalso();
  cajaAbierta = caja();
  pendientes = { cargos: [pendiente()], sinPrecio: [] };
  historial = [];
  servidor.responder('GET /cajas/abierta', () => (cajaAbierta === null ? json(204) : json(200, cajaAbierta)));
  servidor.responder('GET /cargos/pendientes', () => json(200, pendientes));
  servidor.responder('GET /cajas', () => json(200, historial));
  document.cookie = 'XSRF-TOKEN=token-ficticio-123; path=/';
});

afterEach(() => {
  for (const abierto of document.querySelectorAll('dialog')) {
    abierto.remove();
  }
  app?.router.detener();
  app = undefined;
  vi.useRealTimers();
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
});

describe('Importes en pesos', () => {
  it('formatea en pesos de Argentina, también negativos', () => {
    expect(formatearPesos(15000).replace(/\s/g, ' ')).toBe('$ 15.000,00');
    expect(formatearPesos(-500).replace(/\s/g, ' ')).toBe('-$ 500,00');
  });

  it.each([
    ['4000', '4000.00'],
    ['4000,5', '4000.50'],
    ['4.000,50', '4000.50'],
    [' 9000 ', '9000.00'],
    ['0', '0.00'],
  ])('lee "%s" como %s', (escrito, esperado) => {
    expect(leerImporte(escrito)).toBe(esperado);
  });

  it.each(['', 'abc', '10,005', '-5', '1.2.3,4'])('no lee "%s"', (escrito) => {
    expect(leerImporte(escrito)).toBeNull();
  });
});

describe('Estado de la caja', () => {
  it('Sin caja abierta para la recepción', async () => {
    cajaAbierta = null;
    await abrirCaja();

    expect(texto(seccion('caja-estado'))).toContain('No hay una caja abierta.');
    expect(botones(seccion('caja-estado'))).toContain('Abrir caja');
  });

  it('Sin caja abierta para el admin', async () => {
    conAdmin();
    cajaAbierta = null;
    await abrirCaja();

    expect(texto(seccion('caja-estado'))).toContain('No hay una caja abierta.');
    expect(botones(seccion('caja-estado'))).not.toContain('Abrir caja');
  });

  it('Caja abierta', async () => {
    await abrirCaja();

    const estado = texto(seccion('caja-estado'));
    expect(estado).toContain('Abierta por recepcion');
    expect(estado).toContain('Monto inicial: $ 5.000,00');
    expect(estado).toContain('Efectivo: $ 9.000,00');
    expect(estado).toContain('Débito: $ 15.000,00');
    expect(estado).toContain('Efectivo esperado: $ 14.000,00');
  });
});

describe('Grilla de la caja en el celular', () => {
  it('la columna única no crece con la tabla de la cuenta corriente (la tabla se desplaza en su panel)', () => {
    const css = textoDeModulo('22-caja.css');
    const antesDelEscritorio = css.slice(0, css.indexOf('@media'));
    const reglaBase = /\.caja-grilla\s*\{([^}]*)\}/.exec(antesDelEscritorio)?.[1] ?? '';
    const reglaPanel = /\.caja-grilla \.panel\s*\{([^}]*)\}/.exec(antesDelEscritorio)?.[1] ?? '';

    expect(reglaBase).toMatch(/grid-template-columns:\s*minmax\(0,\s*1fr\)/);
    expect(reglaPanel).toMatch(/grid-template-columns:\s*minmax\(0,\s*1fr\)/);
  });
});

describe('Ayudas de los importes (mockup aprobado)', () => {
  function ayudaDe(control: HTMLElement): string {
    const ids = (control.getAttribute('aria-describedby') ?? '').split(' ');
    return ids
      .map((id) => document.getElementById(id))
      .filter((nodo): nodo is HTMLElement => nodo !== null && nodo.classList.contains('ayuda'))
      .map((nodo) => nodo.textContent ?? '')
      .join(' ');
  }

  it('el monto inicial explica que es el efectivo con el que arranca el día', async () => {
    cajaAbierta = null;
    await abrirCaja();

    expect(ayudaDe(campo(raiz(), 'Monto inicial en efectivo'))).toBe('El efectivo con el que arranca el día.');
  });

  it('el importe del cobro avisa que se puede cobrar una parte', async () => {
    pendientes = { cargos: [pendiente({ pagado: 0, saldo: 9000 })], sinPrecio: [] };
    await abrirCaja();

    boton(seccion('caja-pendientes'), 'Cobrar').click();

    expect(ayudaDe(campo(dialogo(), 'Importe'))).toBe('Podés cobrar una parte; lo que falta queda como deuda.');
  });
});

describe('Abrir la caja desde la pantalla', () => {
  it('Abrir con monto inicial', async () => {
    cajaAbierta = null;
    servidor.responder('POST /cajas', () => {
      cajaAbierta = caja({ cobros: [], totales: { EFECTIVO: 0, DEBITO: 0, CREDITO: 0, TRANSFERENCIA: 0 } });
      return json(201, cajaAbierta);
    });
    await abrirCaja();

    escribir(campo(raiz(), 'Monto inicial en efectivo'), '5000');
    boton(seccion('caja-estado'), 'Abrir caja').click();

    await vi.waitFor(() => expect(servidor.a('POST', '/cajas')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/cajas').cuerpo).toEqual({ montoInicial: '5000.00' });
    await vi.waitFor(() => expect(texto(seccion('caja-estado'))).toContain('Monto inicial: $ 5.000,00'));
  });

  it('Monto inicial inválido', async () => {
    cajaAbierta = null;
    await abrirCaja();

    boton(seccion('caja-estado'), 'Abrir caja').click();

    expect(errorDeCampo(raiz(), 'Monto inicial en efectivo')).toBe('Escribí el monto inicial, aunque sea 0.');
    expect(servidor.a('POST', '/cajas')).toHaveLength(0);
  });
});

describe('Pendientes de cobro en la pantalla', () => {
  it('Pendientes con caja abierta', async () => {
    await abrirCaja();

    const fila = seccion('caja-pendientes').querySelector<HTMLElement>('.caja-fila');
    expect(texto(fila ?? raiz())).toContain('Uno, Paciente');
    expect(texto(fila ?? raiz())).toContain('Consulta o control');
    expect(texto(fila ?? raiz())).toContain('Saldo: $ 5.000,00');
    expect(botones(fila ?? raiz())).toContain('Cobrar');
  });

  it('Sin caja no se cobra', async () => {
    cajaAbierta = null;
    await abrirCaja();

    expect(texto(seccion('caja-pendientes'))).toContain('Uno, Paciente');
    expect(botones(seccion('caja-pendientes'))).not.toContain('Cobrar');
  });

  it('Turno sin precio', async () => {
    pendientes = {
      cargos: [],
      sinPrecio: [
        {
          turnoId: 30,
          pacienteId: 2,
          paciente: 'Dos, Paciente',
          fecha: '2026-12-07',
          inicio: '2026-12-07T12:00:00Z',
          prestacion: 'Limpieza (profilaxis)',
        },
      ],
    };
    await abrirCaja();

    const pendientesTexto = texto(seccion('caja-pendientes'));
    expect(pendientesTexto).toContain('Sin precio');
    expect(pendientesTexto).toContain('Dos, Paciente');
    expect(pendientesTexto).toContain('Sin precio: el administrador tiene que cargar el arancel.');
    expect(botones(seccion('caja-pendientes'))).not.toContain('Cobrar');
  });

  it('Sin pendientes se avisa', async () => {
    pendientes = { cargos: [], sinPrecio: [] };
    await abrirCaja();

    expect(texto(seccion('caja-pendientes'))).toContain('No hay cobros pendientes.');
  });
});

describe('Cobrar desde la pantalla', () => {
  it('Cobro parcial desde la pantalla', async () => {
    pendientes = { cargos: [pendiente({ pagado: 0, saldo: 9000 })], sinPrecio: [] };
    servidor.responder('POST /cargos/{id}/cobros', () => json(201, { cobro: cobro(), saldo: 5000 }));
    await abrirCaja();
    const pedidosAntes = servidor.a('GET', '/cargos/pendientes').length;

    boton(seccion('caja-pendientes'), 'Cobrar').click();
    escribir(campo(dialogo(), 'Importe'), '4000');
    escribir(campo<HTMLSelectElement>(dialogo(), 'Medio de pago'), 'DEBITO');
    boton(dialogo(), 'Registrar cobro').click();

    await vi.waitFor(() => expect(servidor.a('POST', '/cargos/7/cobros')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/cargos/7/cobros').cuerpo).toEqual({ importe: '4000.00', medio: 'DEBITO' });
    await vi.waitFor(() => expect(texto(raiz())).toContain('Cobro registrado. Saldo: $ 5.000,00.'));
    expect(document.querySelector('dialog[open]')).toBeNull();
    await vi.waitFor(() => expect(servidor.a('GET', '/cargos/pendientes').length).toBeGreaterThan(pedidosAntes));
  });

  it('Importe mayor que el saldo en la pantalla', async () => {
    pendientes = { cargos: [pendiente({ pagado: 0, saldo: 9000 })], sinPrecio: [] };
    await abrirCaja();

    boton(seccion('caja-pendientes'), 'Cobrar').click();
    expect(campo(dialogo(), 'Importe').value).toBe('9000,00');
    escribir(campo(dialogo(), 'Importe'), '9500');
    boton(dialogo(), 'Registrar cobro').click();

    expect(errorDeCampo(dialogo(), 'Importe').replace(/\s/g, ' ')).toBe(
      'El importe no puede superar el saldo ($ 9.000,00).',
    );
    expect(servidor.a('POST', '/cargos/7/cobros')).toHaveLength(0);
  });
});

describe('Cobros de la caja y anulación', () => {
  it('El admin anula desde la pantalla', async () => {
    conAdmin();
    servidor.responder('POST /cobros/{id}/anulacion', () => json(200, cobro({ anulado: true })));
    await abrirCaja();

    const filas = [...seccion('caja-cobros').querySelectorAll<HTMLElement>('.caja-cobro')];
    expect(botones(filas[1] ?? raiz())).not.toContain('Anular');
    boton(filas[0] ?? raiz(), 'Anular').click();
    escribir(campo(dialogo(), 'Motivo'), 'Cargado al paciente equivocado');
    boton(dialogo(), 'Anular cobro').click();

    await vi.waitFor(() => expect(servidor.a('POST', '/cobros/21/anulacion')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/cobros/21/anulacion').cuerpo).toEqual({
      motivo: 'Cargado al paciente equivocado',
    });
  });

  it('La recepción no ve "Anular"', async () => {
    await abrirCaja();

    expect(botones(seccion('caja-cobros'))).not.toContain('Anular');
    expect(texto(seccion('caja-cobros'))).toContain('Anulado');
  });
});

describe('Cerrar la caja desde la pantalla', () => {
  it('Cierre cuadrado', async () => {
    servidor.responder('POST /cajas/{id}/cierre', () => {
      cajaAbierta = null;
      return json(200, caja({ cerradaPor: 'recepcion', cerradaEn: '2026-12-07T22:00:00Z', efectivoContado: 14000, diferencia: 0 }));
    });
    await abrirCaja();

    boton(seccion('caja-estado'), 'Cerrar caja').click();
    expect(texto(dialogo())).toContain('Efectivo esperado: $ 14.000,00');
    escribir(campo(dialogo(), 'Efectivo contado'), '14000');
    boton(dialogo(), 'Cerrar caja').click();

    await vi.waitFor(() => expect(servidor.a('POST', '/cajas/3/cierre')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/cajas/3/cierre').cuerpo).toEqual({ efectivoContado: '14000.00' });
    await vi.waitFor(() => expect(texto(raiz())).toContain('Caja cerrada. Diferencia: $ 0,00.'));
    await vi.waitFor(() => expect(texto(seccion('caja-estado'))).toContain('No hay una caja abierta.'));
  });

  it('Cierre con diferencia sin motivo', async () => {
    await abrirCaja();

    boton(seccion('caja-estado'), 'Cerrar caja').click();
    escribir(campo(dialogo(), 'Efectivo contado'), '13500');

    expect(texto(dialogo())).toContain('Diferencia: -$ 500,00');
    boton(dialogo(), 'Cerrar caja').click();
    expect(errorDeCampo(dialogo(), 'Motivo de la diferencia')).toBe('Escribí el motivo de la diferencia.');
    expect(servidor.a('POST', '/cajas/3/cierre')).toHaveLength(0);
  });

  it('El admin no ve "Cerrar caja"', async () => {
    conAdmin();
    await abrirCaja();

    expect(botones(seccion('caja-estado'))).not.toContain('Cerrar caja');
    expect(botones(seccion('caja-pendientes'))).not.toContain('Cobrar');
  });
});

describe('Cuenta corriente de un paciente', () => {
  it('Ver la cuenta corriente', async () => {
    servidor.responder('GET /pacientes/{id}/cuenta-corriente', () =>
      json(200, {
        pacienteId: 1,
        paciente: 'Uno, Paciente',
        cargos: [
          {
            id: 7,
            fecha: '2026-12-07',
            inicio: '2026-12-07T13:00:00Z',
            prestacion: 'Consulta o control',
            precio: 15000,
            porcentajeCobertura: 40,
            cobertura: 6000,
            copago: 9000,
            pagado: 4000,
            saldo: 5000,
            cobros: [cobro()],
          },
        ],
        saldoTotal: 5000,
      }),
    );
    await abrirCaja();

    await elegirPaciente(seccion('caja-cuenta'), 'uno', 'Uno, Paciente · DNI 99000001');

    await vi.waitFor(() => expect(servidor.a('GET', '/pacientes/1/cuenta-corriente')).toHaveLength(1));
    await vi.waitFor(() => expect(texto(seccion('caja-cuenta'))).toContain('Saldo total: $ 5.000,00'));
    const cuenta = texto(seccion('caja-cuenta'));
    expect(cuenta).toContain('Pagado: $ 4.000,00');
    expect(cuenta).toContain('Saldo: $ 5.000,00');
  });

  it('la tabla de la cuenta corriente muestra el turno como día y mes, como el mockup', async () => {
    servidor.responder('GET /pacientes/{id}/cuenta-corriente', () =>
      json(200, {
        pacienteId: 1,
        paciente: 'Uno, Paciente',
        cargos: [
          {
            id: 7,
            fecha: '2026-12-07',
            inicio: '2026-12-07T13:00:00Z',
            prestacion: 'Consulta o control',
            precio: 15000,
            porcentajeCobertura: 40,
            cobertura: 6000,
            copago: 9000,
            pagado: 4000,
            saldo: 5000,
            cobros: [],
          },
        ],
        saldoTotal: 5000,
      }),
    );
    await abrirCaja();

    await elegirPaciente(seccion('caja-cuenta'), 'uno', 'Uno, Paciente · DNI 99000001');

    await vi.waitFor(() => expect(seccion('caja-cuenta').querySelector('tbody td')).not.toBeNull());
    expect(seccion('caja-cuenta').querySelector('tbody td')?.textContent).toBe('07/12');
  });

  it('Un paciente sin cargos', async () => {
    servidor.responder('GET /pacientes/{id}/cuenta-corriente', () =>
      json(200, { pacienteId: 2, paciente: 'Dos, Paciente', cargos: [], saldoTotal: 0 }),
    );
    await abrirCaja();

    await elegirPaciente(seccion('caja-cuenta'), 'dos', 'Dos, Paciente · DNI 99000002');

    await vi.waitFor(() => expect(texto(seccion('caja-cuenta'))).toContain('El paciente no tiene cargos.'));
  });
});

describe('Historial de cajas en la pantalla', () => {
  it('Caja descuadrada en el historial', async () => {
    historial = [
      caja({
        id: 2,
        fecha: '2026-12-04',
        cerradaPor: 'recepcion',
        cerradaEn: '2026-12-04T22:00:00Z',
        efectivoEsperado: 22000,
        efectivoContado: 21500,
        diferencia: -500,
        motivoDiferencia: 'Faltante sin explicar',
        cobros: [],
      }),
    ];
    await abrirCaja();

    await vi.waitFor(() =>
      expect(texto(seccion('caja-historial'))).toContain('Diferencia: -$ 500,00 (Faltante sin explicar)'),
    );
    expect(servidor.ultimo('GET', '/cajas').query.toString()).toBe('desde=2026-11-08&hasta=2026-12-07');
  });
});

describe('Mensajes de los rechazos de la caja', () => {
  it('Otro puesto cobró antes', async () => {
    servidor.responder('POST /cargos/{id}/cobros', () =>
      json(409, { status: 409, codigo: 'IMPORTE_SUPERA_SALDO', detail: 'El importe supera el saldo del cargo.' }),
    );
    await abrirCaja();
    const pedidosAntes = servidor.a('GET', '/cargos/pendientes').length;

    boton(seccion('caja-pendientes'), 'Cobrar').click();
    boton(dialogo(), 'Registrar cobro').click();

    await vi.waitFor(() => expect(texto(dialogo())).toContain('El importe supera el saldo del cargo.'));
    await vi.waitFor(() => expect(servidor.a('GET', '/cargos/pendientes').length).toBeGreaterThan(pedidosAntes));
  });

  it('Un error al abrir la caja va al aviso general', async () => {
    cajaAbierta = null;
    servidor.responder('POST /cajas', () =>
      json(409, { status: 409, codigo: 'CAJA_YA_ABIERTA', detail: 'Ya hay una caja abierta.' }),
    );
    await abrirCaja();

    escribir(campo(raiz(), 'Monto inicial en efectivo'), '100');
    boton(seccion('caja-estado'), 'Abrir caja').click();

    await vi.waitFor(() =>
      expect(aviso('alert')).toBe('Ya hay una caja abierta. Cerrala antes de abrir otra.'),
    );
  });
});
