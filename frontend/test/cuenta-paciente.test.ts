import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { abrirApp, boton, campo, errorDeCampo, escribir, json, ServidorFalso, texto, type AppDePrueba } from './apoyo.ts';

// Datos ficticios: "Paciente Uno" (DNI 99000001) y `recepcion`.
const SESION_PACIENTE = { autenticado: true, username: 'paciente.uno@example.com', rol: 'PACIENTE' };
const SESION_RECEPCION = { autenticado: true, username: 'recepcion', rol: 'RECEPCIONISTA' };

let servidor: ServidorFalso;
let app: AppDePrueba | undefined;

function raiz(): HTMLElement {
  if (app === undefined) {
    throw new Error('La aplicación no está abierta');
  }
  return app.raiz;
}

function titulo(): string | null {
  return app?.raiz.querySelector('main h1')?.textContent ?? null;
}

async function abrirMiCuenta(): Promise<void> {
  app = abrirApp(servidor, '#/mi-cuenta');
  await vi.waitFor(() => expect(titulo()).toBe('Mi cuenta'));
}

function formulario(nombre: 'Ingresar' | 'Crear cuenta'): HTMLFormElement {
  const form = raiz().querySelector<HTMLFormElement>(`form[aria-label="${nombre}"]`);
  if (form === null) {
    throw new Error(`No está el formulario ${nombre}`);
  }
  return form;
}

function ingresar(usuario: string, clave: string): void {
  escribir(campo(raiz(), 'Email'), usuario);
  escribir(campo(raiz(), 'Contraseña'), clave);
  formulario('Ingresar').requestSubmit();
}

function completarRegistro(datos: Partial<Record<string, string>> = {}): void {
  boton(raiz(), 'Crear cuenta').click();
  const valores: Record<string, string> = {
    Nombre: 'Paciente',
    Apellido: 'Uno',
    DNI: '99000001',
    Email: 'paciente.uno@example.com',
    Contraseña: 'clave-ficticia-1',
    'Repetí la contraseña': 'clave-ficticia-1',
    ...datos,
  };
  for (const [etiqueta, valor] of Object.entries(valores)) {
    escribir(campo(raiz(), etiqueta), valor);
  }
  formulario('Crear cuenta').requestSubmit();
}

function alerta(): string {
  return [...raiz().querySelectorAll('[role="alert"], [role="status"]')].map((e) => e.textContent ?? '').join(' ');
}

beforeEach(() => {
  servidor = new ServidorFalso();
  servidor.sinSesion();
  servidor.responder('GET /paciente/turnos', () => json(200, []));
  document.cookie = 'XSRF-TOKEN=token-ficticio-123; path=/';
  sessionStorage.clear();
});

afterEach(() => {
  app?.router.detener();
  app = undefined;
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  sessionStorage.clear();
});

/** Después de un login correcto, el servidor ya informa la sesión del paciente. */
function loginDePaciente(): void {
  servidor.responder('POST /public/login', () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_PACIENTE));
    return json(200, SESION_PACIENTE);
  });
}

describe('Ingreso del paciente', () => {
  it('Ingreso correcto', async () => {
    loginDePaciente();
    await abrirMiCuenta();

    ingresar('paciente.uno@example.com', 'clave-ficticia-1');

    await vi.waitFor(() => expect(titulo()).toBe('Mis turnos'));
    expect(window.location.hash).toBe('#/mis-turnos');
    expect(servidor.ultimo('POST', '/public/login').cuerpo).toEqual({
      username: 'paciente.uno@example.com',
      password: 'clave-ficticia-1',
    });
  });

  it('Campos vacíos en el ingreso', async () => {
    await abrirMiCuenta();

    ingresar('', 'clave-ficticia-1');

    expect(servidor.a('POST', '/public/login')).toHaveLength(0);
    expect(errorDeCampo(raiz(), 'Email')).toBe('Completá el email.');
  });
});

describe('Credenciales del paciente rechazadas', () => {
  it('Contraseña incorrecta', async () => {
    servidor.responder('POST /public/login', () => json(401, { codigo: 'CREDENCIALES_INVALIDAS' }));
    await abrirMiCuenta();

    ingresar('paciente.uno@example.com', 'otra-clave-ficticia');

    await vi.waitFor(() => expect(alerta()).toContain('Email o contraseña incorrectos.'));
    expect(campo(raiz(), 'Email').value).toBe('paciente.uno@example.com');
    expect(campo(raiz(), 'Contraseña').value).toBe('');
  });
});

describe('Cuenta del personal en el ingreso del paciente', () => {
  it('Recepción ingresa desde la página del paciente', async () => {
    servidor.responder('POST /public/login', () => {
      servidor.responder('GET /public/sesion', () => json(200, SESION_RECEPCION));
      return json(200, SESION_RECEPCION);
    });
    await abrirMiCuenta();

    ingresar('recepcion', 'clave-ficticia-1');

    await vi.waitFor(() => expect(titulo()).toBe('Agenda'));
    expect(window.location.hash).toBe('#/agenda');
  });
});

describe('Personal con sesión en Mi cuenta (C-37)', () => {
  it('Personal con sesión abre su cuenta', async () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_RECEPCION));
    app = abrirApp(servidor, '#/mi-cuenta');

    await vi.waitFor(() => expect(titulo()).toBe('Agenda'));
    expect(window.location.hash).toBe('#/agenda');
  });

  it('Personal con sesión abre la dirección vieja del ingreso', async () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_RECEPCION));
    app = abrirApp(servidor, '#/ingreso');

    await vi.waitFor(() => expect(titulo()).toBe('Agenda'));
    expect(window.location.hash).toBe('#/agenda');
  });
});

describe('Indicación para el personal (C-37)', () => {
  it('Se ve la indicación', async () => {
    await abrirMiCuenta();

    expect(texto(raiz())).toContain(
      '¿Sos del personal del consultorio? Ingresá con tu usuario en este mismo formulario.',
    );
    expect(campo(raiz(), 'Email')).toBeDefined();
    boton(raiz(), 'Crear cuenta').click();
    expect(texto(raiz())).not.toContain('¿Sos del personal del consultorio?');
  });
});

describe('Registro del paciente', () => {
  it('Registro con datos válidos', async () => {
    servidor.responder('POST /public/pacientes/registro', () => json(201, { username: 'paciente.uno@example.com' }));
    loginDePaciente();
    await abrirMiCuenta();

    completarRegistro();

    await vi.waitFor(() => expect(servidor.a('POST', '/public/pacientes/registro')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/public/pacientes/registro').cuerpo).toEqual({
      nombre: 'Paciente',
      apellido: 'Uno',
      dni: '99000001',
      email: 'paciente.uno@example.com',
      password: 'clave-ficticia-1',
    });
  });

  it('DNI con formato inválido', async () => {
    await abrirMiCuenta();

    completarRegistro({ DNI: '12345' });

    expect(servidor.a('POST', '/public/pacientes/registro')).toHaveLength(0);
    expect(errorDeCampo(raiz(), 'DNI')).toBe('El DNI debe tener 7 u 8 dígitos.');
  });

  it('Contraseñas distintas', async () => {
    await abrirMiCuenta();

    completarRegistro({ 'Repetí la contraseña': 'otra-clave-ficticia' });

    expect(servidor.a('POST', '/public/pacientes/registro')).toHaveLength(0);
    expect(errorDeCampo(raiz(), 'Repetí la contraseña')).toBe('Las contraseñas no coinciden.');
  });
});

describe('Sesión iniciada después del registro', () => {
  it('Registro e ingreso seguidos', async () => {
    servidor.responder('POST /public/pacientes/registro', () => json(201, {}));
    loginDePaciente();
    await abrirMiCuenta();

    completarRegistro();

    await vi.waitFor(() => expect(titulo()).toBe('Mis turnos'));
    expect(servidor.ultimo('POST', '/public/login').cuerpo).toMatchObject({ username: 'paciente.uno@example.com' });
  });

  it('Falla el ingreso después del registro', async () => {
    servidor.responder('POST /public/pacientes/registro', () => json(201, {}));
    servidor.responder('POST /public/login', () => json(500));
    await abrirMiCuenta();

    completarRegistro();

    await vi.waitFor(() => expect(alerta()).toContain('Tu cuenta se creó. Ingresá con tu email y tu contraseña.'));
    expect(formulario('Ingresar')).toBeDefined();
    expect(campo(raiz(), 'Email').value).toBe('paciente.uno@example.com');
    expect(campo(raiz(), 'Contraseña').value).toBe('');
  });
});

describe('Rechazos del registro', () => {
  it('DNI ya registrado', async () => {
    servidor.responder('POST /public/pacientes/registro', () => json(409, { codigo: 'DNI_YA_REGISTRADO' }));
    await abrirMiCuenta();

    completarRegistro();

    await vi.waitFor(() =>
      expect(errorDeCampo(raiz(), 'DNI')).toBe(
        'Ya hay un paciente registrado con ese DNI. Si es tuyo, ingresá con tu cuenta o contactá al consultorio.',
      ),
    );
    expect(campo(raiz(), 'Nombre').value).toBe('Paciente');
    expect(campo(raiz(), 'Apellido').value).toBe('Uno');
    expect(campo(raiz(), 'DNI').value).toBe('99000001');
    expect(campo(raiz(), 'Email').value).toBe('paciente.uno@example.com');
    expect(campo(raiz(), 'Contraseña').value).toBe('');
  });

  it('Email ya registrado', async () => {
    servidor.responder('POST /public/pacientes/registro', () => json(409, { codigo: 'EMAIL_YA_REGISTRADO' }));
    await abrirMiCuenta();

    completarRegistro();

    await vi.waitFor(() =>
      expect(errorDeCampo(raiz(), 'Email')).toBe('Ya existe una cuenta con ese email. Ingresá con tu contraseña.'),
    );
  });

  it('Error de validación del servidor', async () => {
    servidor.responder('POST /public/pacientes/registro', () =>
      json(422, {
        codigo: 'DATOS_INVALIDOS',
        detail: 'Hay datos inválidos en el pedido',
        errores: [{ campo: 'email', mensaje: 'debe ser una dirección de correo válida' }],
      }),
    );
    await abrirMiCuenta();

    completarRegistro();

    await vi.waitFor(() => expect(errorDeCampo(raiz(), 'Email')).toBe('debe ser una dirección de correo válida'));
  });
});

describe('Páginas del paciente solo con sesión de paciente', () => {
  it('Mis turnos sin sesión', async () => {
    app = abrirApp(servidor, '#/mis-turnos');

    await vi.waitFor(() => expect(titulo()).toBe('Mi cuenta'));
    expect(window.location.hash).toBe('#/mi-cuenta');
    expect(servidor.a('GET', '/paciente/turnos')).toHaveLength(0);
  });

  it('El personal abre Mis turnos', async () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_RECEPCION));

    app = abrirApp(servidor, '#/mis-turnos');

    await vi.waitFor(() => expect(texto(raiz())).toContain('Esta sección es para pacientes.'));
    expect(servidor.a('GET', '/paciente/turnos')).toHaveLength(0);
  });
});

describe('Cuenta del paciente con sesión iniciada', () => {
  // C-49 (delta de frontend-cuenta-paciente): con sesión de paciente, "Mi cuenta" muestra "Mi cobertura".
  it('Paciente con sesión abre su cuenta', async () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_PACIENTE));
    servidor.responder('GET /paciente/cobertura', () => json(200, { particular: true, afiliacion: null }));

    app = abrirApp(servidor, '#/mi-cuenta');

    await vi.waitFor(() => expect(raiz().querySelector('main h2')?.textContent).toBe('Mi cobertura'));
    expect(titulo()).toBe('Mi cuenta');
    expect(window.location.hash).toBe('#/mi-cuenta');
    expect(raiz().querySelector('form[aria-label="Ingresar"]')).toBeNull();
    expect(raiz().querySelector('form[aria-label="Crear cuenta"]')).toBeNull();
  });
});

describe('Sesión del paciente vencida', () => {
  it('La sesión vence en Mis turnos', async () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_PACIENTE));
    servidor.responder('GET /paciente/turnos', () =>
      json(200, [
        {
          numeroReserva: 'R-0002',
          inicio: '2099-12-03T10:00:00-03:00',
          fin: '2099-12-03T10:30:00-03:00',
          estado: 'RESERVADO',
          prestacion: { id: 1, nombre: 'Consulta' },
          profesional: { id: 1, nombre: 'Ana Ejemplo' },
          modificableOnline: true,
        },
      ]),
    );
    servidor.responder('POST /paciente/reservas/R-0002/cancelar', () => {
      servidor.sinSesion();
      return json(401, { codigo: 'NO_AUTENTICADO' });
    });
    app = abrirApp(servidor, '#/mis-turnos');
    await vi.waitFor(() => expect(texto(raiz())).toContain('R-0002'));

    boton(raiz(), 'Cancelar turno').click();
    boton(raiz(), 'Sí, cancelar turno').click();

    await vi.waitFor(() => expect(titulo()).toBe('Mi cuenta'));
    expect(window.location.hash).toBe('#/mi-cuenta');
    expect(texto(raiz())).toContain('Tu sesión terminó. Ingresá de nuevo.');
  });
});

describe('Salida del paciente', () => {
  it('El paciente sale', async () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_PACIENTE));
    servidor.responder('GET /public/prestaciones', () => json(200, []));
    app = abrirApp(servidor, '#/mis-turnos');
    await vi.waitFor(() => expect(titulo()).toBe('Mis turnos'));
    sessionStorage.setItem(
      'turnos.reservaPendiente',
      JSON.stringify({ prestacionId: 1, profesionalId: null, fecha: '2099-12-01', inicio: '2099-12-01T10:30:00-03:00' }),
    );
    servidor.sinSesion();

    boton(raiz(), 'Salir').click();

    await vi.waitFor(() => expect(document.title).toBe('Turnos odontología'));
    expect(window.location.hash).toBe('#/inicio');
    expect(servidor.a('POST', '/logout')).toHaveLength(1);
    expect(raiz().querySelector<HTMLElement>('.usuario-nombre')?.hidden).toBe(true);
    expect(sessionStorage.getItem('turnos.reservaPendiente')).toBeNull();
  });
});
