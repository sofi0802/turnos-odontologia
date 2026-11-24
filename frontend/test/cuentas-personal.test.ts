import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  abrirApp as abrirAppSinRegistrar,
  boton,
  campo,
  errorDeCampo,
  escribir,
  json,
  ServidorFalso,
  texto,
  type AppDePrueba,
} from './apoyo.ts';
import {
  abrirConfiguracion as abrirConfiguracionSinRegistrar,
  dialogoAbierto,
  elegirPestana,
  enviar,
  SESION_ADMIN,
  SESION_RECEPCION_CONFIG,
  servidorCon,
} from './apoyo-configuracion.ts';

/** Spec frontend-cuentas-personal (C-41). Datos ficticios: admin, recepcion, bruno.prueba y Ana Ejemplo sin cuenta. */

const CUENTAS = [
  { id: 1, username: 'admin', rol: 'ADMIN', profesionalId: null, activo: true, debeCambiarContrasena: false },
  { id: 2, username: 'recepcion', rol: 'RECEPCIONISTA', profesionalId: null, activo: true, debeCambiarContrasena: false },
  { id: 3, username: 'bruno.prueba', rol: 'ODONTOLOGO', profesionalId: 2, activo: true, debeCambiarContrasena: false },
];
const TEMPORAL = 'Hq7tRm4zkW9pXa';
const SESION_ANA_TEMPORAL = {
  autenticado: true,
  username: 'ana.ejemplo',
  rol: 'ODONTOLOGO',
  profesionalId: 1,
  debeCambiarContrasena: true,
};

/** Apps abiertas en el test: se detienen al final para que no sigan escuchando los cambios de dirección. */
const abiertas: AppDePrueba[] = [];

function abrirApp(servidor: ServidorFalso, hash: string): AppDePrueba {
  const app = abrirAppSinRegistrar(servidor, hash);
  abiertas.push(app);
  return app;
}

async function abrirConfiguracion(servidor: ServidorFalso): Promise<AppDePrueba> {
  const app = await abrirConfiguracionSinRegistrar(servidor);
  abiertas.push(app);
  return app;
}

afterEach(() => {
  abiertas.splice(0).forEach((app) => app.router.detener());
  document.querySelectorAll('dialog').forEach((d) => d.remove());
  localStorage.clear();
  sessionStorage.clear();
});

function servidorAdmin(): ServidorFalso {
  const servidor = servidorCon(SESION_ADMIN);
  servidor.responder('GET /usuarios', () => json(200, CUENTAS));
  return servidor;
}

async function abrirUsuarios(servidor: ServidorFalso): Promise<HTMLElement> {
  const { raiz } = await abrirConfiguracion(servidor);
  const panel = elegirPestana(raiz, 'Usuarios');
  await vi.waitFor(() => expect(panel.textContent).toContain('bruno.prueba'));
  return panel;
}

function fila(panel: HTMLElement, usuario: string): HTMLElement {
  const encontrada = [...panel.querySelectorAll<HTMLElement>('.lista-config > li')].find(
    (li) => li.querySelector('.item-nombre')?.textContent === usuario,
  );
  if (encontrada === undefined) {
    throw new Error(`No está la fila de ${usuario}`);
  }
  return encontrada;
}

function botonesDe(elemento: HTMLElement): string[] {
  return [...elemento.querySelectorAll('button')].map((b) => b.textContent?.trim() ?? '');
}

async function abrirAlta(panel: HTMLElement): Promise<HTMLDialogElement> {
  boton(panel, 'Agregar usuario').click();
  return dialogoAbierto();
}

function radio(dialogo: HTMLElement, rol: string): HTMLInputElement {
  return campo<HTMLInputElement>(dialogo, rol);
}

describe('Lista de cuentas en la pestaña Usuarios', () => {
  it('Ver las cuentas del consultorio', async () => {
    const servidor = servidorAdmin();
    const panel = await abrirUsuarios(servidor);

    expect(servidor.a('GET', '/usuarios')).toHaveLength(1);
    expect(texto(fila(panel, 'bruno.prueba'))).toContain('Odontólogo');
    expect(texto(fila(panel, 'bruno.prueba'))).toContain('Bruno Prueba');
    expect(texto(fila(panel, 'bruno.prueba'))).toContain('Activo');
    expect(texto(fila(panel, 'recepcion'))).toContain('Recepción');
    expect(texto(fila(panel, 'recepcion'))).not.toContain('Prueba');
  });

  it('Marca la contraseña temporal pendiente', async () => {
    const servidor = servidorCon(SESION_ADMIN);
    servidor.responder('GET /usuarios', () =>
      json(200, [...CUENTAS, { id: 4, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1, activo: true, debeCambiarContrasena: true }]),
    );
    const panel = await abrirUsuarios(servidor);

    expect(texto(fila(panel, 'ana.ejemplo'))).toContain('Contraseña temporal pendiente');
    expect(texto(fila(panel, 'bruno.prueba'))).not.toContain('Contraseña temporal pendiente');
  });
});

describe('Alta de una cuenta del personal', () => {
  it('Alta de un odontólogo', async () => {
    const servidor = servidorAdmin();
    servidor.responder('POST /usuarios', () =>
      json(201, { id: 4, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1, activo: true, debeCambiarContrasena: true, contrasenaTemporal: TEMPORAL }),
    );
    const panel = await abrirUsuarios(servidor);
    const dialogo = await abrirAlta(panel);

    escribir(campo(dialogo, 'Usuario'), 'ana.ejemplo');
    radio(dialogo, 'Odontólogo').click();
    escribir(campo<HTMLSelectElement>(dialogo, 'Profesional'), '1');
    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('POST', '/usuarios')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/usuarios').cuerpo).toEqual({ username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1 });
    await vi.waitFor(() => expect(texto(fila(panel, 'ana.ejemplo'))).toContain('Contraseña temporal pendiente'));
  });

  it('Profesionales ofrecidos', async () => {
    const panel = await abrirUsuarios(servidorAdmin());
    const dialogo = await abrirAlta(panel);

    radio(dialogo, 'Odontólogo').click();
    const opciones = [...campo<HTMLSelectElement>(dialogo, 'Profesional').options].map((o) => o.textContent);

    expect(opciones).toContain('Ana Ejemplo');
    expect(opciones).not.toContain('Bruno Prueba');
  });

  it('Odontólogo sin profesional elegido', async () => {
    const servidor = servidorAdmin();
    const panel = await abrirUsuarios(servidor);
    const dialogo = await abrirAlta(panel);

    escribir(campo(dialogo, 'Usuario'), 'ana.ejemplo');
    radio(dialogo, 'Odontólogo').click();
    enviar(dialogo);

    expect(errorDeCampo(dialogo, 'Profesional')).toBe('Elegí el profesional de esta cuenta.');
    expect(servidor.a('POST', '/usuarios')).toHaveLength(0);
  });

  it('Recepción no elige profesional', async () => {
    const panel = await abrirUsuarios(servidorAdmin());
    const dialogo = await abrirAlta(panel);

    radio(dialogo, 'Recepción').click();

    expect(() => campo(dialogo, 'Profesional')).toThrow();
    expect(() => campo(dialogo, 'Profesional (opcional, si también atiende)')).toThrow();
  });

  it('Administrador con profesional opcional', async () => {
    const panel = await abrirUsuarios(servidorAdmin());
    const dialogo = await abrirAlta(panel);

    radio(dialogo, 'Administrador').click();

    expect(campo<HTMLSelectElement>(dialogo, 'Profesional (opcional, si también atiende)').value).toBe('');
  });
});

describe('Contraseña temporal mostrada una sola vez', () => {
  async function altaHecha(): Promise<{ panel: HTMLElement; dialogo: HTMLDialogElement }> {
    const servidor = servidorAdmin();
    servidor.responder('POST /usuarios', () =>
      json(201, { id: 4, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1, activo: true, debeCambiarContrasena: true, contrasenaTemporal: TEMPORAL }),
    );
    const panel = await abrirUsuarios(servidor);
    const alta = await abrirAlta(panel);
    escribir(campo(alta, 'Usuario'), 'ana.ejemplo');
    radio(alta, 'Odontólogo').click();
    escribir(campo<HTMLSelectElement>(alta, 'Profesional'), '1');
    enviar(alta);
    const dialogo = await vi.waitFor(() => {
      const abierto = document.querySelector<HTMLDialogElement>('dialog[open]');
      expect(abierto?.textContent).toContain(TEMPORAL);
      return abierto as HTMLDialogElement;
    });
    return { panel, dialogo };
  }

  it('Diálogo después del alta', async () => {
    const { dialogo } = await altaHecha();

    expect(texto(dialogo)).toContain('ana.ejemplo');
    expect(botonesDe(dialogo)).toContain('Copiar');
    expect(texto(dialogo)).toContain('Esta contraseña no se vuelve a mostrar. Pasásela a la persona por un medio seguro.');
  });

  it('Cerrar el diálogo', async () => {
    const { dialogo } = await altaHecha();

    boton(dialogo, 'Listo').click();

    await vi.waitFor(() => expect(document.body.textContent).not.toContain(TEMPORAL));
    const guardado = [...Object.values({ ...localStorage }), ...Object.values({ ...sessionStorage })].join(' ');
    expect(guardado).not.toContain(TEMPORAL);
  });

  it('Copiar usa el portapapeles', async () => {
    const escribirTexto = vi.fn<(valor: string) => Promise<void>>(() => Promise.resolve());
    Object.defineProperty(navigator, 'clipboard', { value: { writeText: escribirTexto }, configurable: true });
    const { dialogo } = await altaHecha();

    boton(dialogo, 'Copiar').click();

    await vi.waitFor(() => expect(escribirTexto).toHaveBeenCalledWith(TEMPORAL));
  });
});

describe('Dar acceso desde el detalle del profesional', () => {
  async function detalle(servidor: ServidorFalso, profesional: string): Promise<HTMLElement> {
    const { raiz } = await abrirConfiguracion(servidor);
    const panel = elegirPestana(raiz, 'Profesionales');
    await vi.waitFor(() => expect(panel.textContent).toContain(profesional));
    const filaProfesional = [...panel.querySelectorAll<HTMLElement>('.lista-config > li')].find((li) =>
      li.textContent?.includes(profesional),
    ) as HTMLElement;
    boton(filaProfesional, 'Ver detalle').click();
    return vi.waitFor(() => {
      const caja = filaProfesional.querySelector<HTMLElement>('.detalle-profesional');
      expect(caja).not.toBeNull();
      return caja as HTMLElement;
    });
  }

  it('Profesional sin cuenta', async () => {
    const servidor = servidorAdmin();
    servidor.responder('GET /profesionales/{id}/prestaciones', () => json(200, []));
    const caja = await detalle(servidor, 'Ana Ejemplo');
    await vi.waitFor(() => expect(botonesDe(caja)).toContain('Dar acceso'));

    boton(caja, 'Dar acceso').click();
    const dialogo = await dialogoAbierto();

    expect(dialogo.querySelector('h2')?.textContent).toBe('Dar acceso');
    expect(texto(dialogo)).toContain('Ana Ejemplo');
    expect(campo<HTMLSelectElement>(dialogo, 'Profesional').value).toBe('1');
  });

  it('Profesional con cuenta', async () => {
    const servidor = servidorAdmin();
    servidor.responder('GET /profesionales/{id}/prestaciones', () => json(200, []));
    const caja = await detalle(servidor, 'Bruno Prueba');

    await vi.waitFor(() => expect(texto(caja)).toContain('Acceso: bruno.prueba'));
    expect(texto(caja)).toContain('Activo');
    expect(botonesDe(caja)).not.toContain('Dar acceso');
  });

  it('Recepción en el detalle', async () => {
    const servidor = servidorCon(SESION_RECEPCION_CONFIG);
    servidor.responder('GET /profesionales/{id}/prestaciones', () => json(200, []));
    const caja = await detalle(servidor, 'Ana Ejemplo');
    await vi.waitFor(() => expect(caja.getAttribute('aria-busy')).toBeNull());

    expect(botonesDe(caja)).not.toContain('Dar acceso');
    expect(texto(caja)).not.toContain('Acceso');
    expect(servidor.a('GET', '/usuarios')).toHaveLength(0);
  });
});

describe('Desactivar, reactivar y restablecer desde la lista', () => {
  it('Desactivar una cuenta', async () => {
    const servidor = servidorAdmin();
    servidor.responder('POST /usuarios/{id}/desactivar', () =>
      json(200, { ...CUENTAS[1], activo: false }),
    );
    const panel = await abrirUsuarios(servidor);

    boton(fila(panel, 'recepcion'), 'Desactivar').click();
    const confirmacion = await dialogoAbierto();
    expect(texto(confirmacion)).toContain('se cierran sus sesiones abiertas');
    enviar(confirmacion);

    await vi.waitFor(() => expect(servidor.a('POST', '/usuarios/2/desactivar')).toHaveLength(1));
    await vi.waitFor(() => expect(texto(fila(panel, 'recepcion'))).toContain('Inactivo'));
    expect(botonesDe(fila(panel, 'recepcion'))).toContain('Activar');
  });

  it('Reactivar una cuenta', async () => {
    const servidor = servidorCon(SESION_ADMIN);
    servidor.responder('GET /usuarios', () => json(200, [CUENTAS[0], CUENTAS[2], { ...CUENTAS[1], activo: false }]));
    servidor.responder('POST /usuarios/{id}/activar', () => json(200, CUENTAS[1]));
    const panel = await abrirUsuarios(servidor);

    boton(fila(panel, 'recepcion'), 'Activar').click();

    await vi.waitFor(() => expect(servidor.a('POST', '/usuarios/2/activar')).toHaveLength(1));
    await vi.waitFor(() => expect(texto(fila(panel, 'recepcion'))).toContain('Activo'));
  });

  it('Restablecer una contraseña', async () => {
    const servidor = servidorAdmin();
    servidor.responder('POST /usuarios/{id}/restablecer-contrasena', () =>
      json(200, { ...CUENTAS[2], debeCambiarContrasena: true, contrasenaTemporal: TEMPORAL }),
    );
    const panel = await abrirUsuarios(servidor);

    boton(fila(panel, 'bruno.prueba'), 'Restablecer contraseña').click();
    enviar(await dialogoAbierto());

    await vi.waitFor(() => expect(servidor.a('POST', '/usuarios/3/restablecer-contrasena')).toHaveLength(1));
    await vi.waitFor(() => expect(document.querySelector('dialog[open]')?.textContent).toContain(TEMPORAL));
  });

  it('La propia cuenta', async () => {
    const panel = await abrirUsuarios(servidorAdmin());

    const propia = fila(panel, 'admin');
    expect(botonesDe(propia)).not.toContain('Desactivar');
    expect(botonesDe(propia)).not.toContain('Restablecer contraseña');
    expect(texto(propia)).toContain('Tu cuenta');
  });
});

describe('Errores de las cuentas', () => {
  it('Usuario repetido', async () => {
    const servidor = servidorAdmin();
    servidor.responder('POST /usuarios', () => json(409, { codigo: 'USUARIO_EXISTENTE', detail: 'Ya existe' }));
    const panel = await abrirUsuarios(servidor);
    const dialogo = await abrirAlta(panel);

    escribir(campo(dialogo, 'Usuario'), 'recepcion');
    radio(dialogo, 'Recepción').click();
    enviar(dialogo);

    await vi.waitFor(() => expect(texto(dialogo)).toContain('Ya existe una cuenta con ese usuario.'));
    expect(campo(dialogo, 'Usuario').value).toBe('recepcion');
    expect(radio(dialogo, 'Recepción').checked).toBe(true);
  });
});

describe('Cambio obligatorio de la contraseña temporal', () => {
  it('Ingreso con contraseña temporal', async () => {
    const servidor = new ServidorFalso();
    servidor.sinSesion();
    servidor.responder('POST /public/login', () => {
      servidor.responder('GET /public/sesion', () => json(200, SESION_ANA_TEMPORAL));
      return json(200, SESION_ANA_TEMPORAL);
    });
    const { raiz } = abrirApp(servidor, '#/mi-cuenta');
    await vi.waitFor(() => expect(boton(raiz, 'Ingresar')).toBeDefined());

    escribir(campo(raiz, 'Email'), 'ana.ejemplo');
    escribir(campo(raiz, 'Contraseña'), TEMPORAL);
    raiz.querySelector('form')?.requestSubmit();

    await vi.waitFor(() => expect(window.location.hash).toBe('#/cambiar-contrasena'));
    await vi.waitFor(() => expect(texto(raiz)).toContain('Antes de seguir, elegí una contraseña nueva.'));
    expect(raiz.querySelector<HTMLElement>('.lateral')?.hidden).toBe(true);
  });

  it('Intento de abrir la agenda', async () => {
    const servidor = servidorCon(SESION_ANA_TEMPORAL);
    const { raiz } = abrirApp(servidor, '#/agenda');

    await vi.waitFor(() => expect(window.location.hash).toBe('#/cambiar-contrasena'));
    await vi.waitFor(() => expect(texto(raiz)).toContain('Antes de seguir, elegí una contraseña nueva.'));
    expect(servidor.a('GET', '/turnos')).toHaveLength(0);
  });

  it('Un pedido rechazado por contraseña pendiente lleva al cambio', async () => {
    const servidor = servidorCon(SESION_ADMIN);
    servidor.responder('GET /usuarios', () => json(403, { codigo: 'CAMBIO_DE_CONTRASENA_PENDIENTE' }));
    const { raiz } = await abrirConfiguracion(servidor);

    elegirPestana(raiz, 'Usuarios');

    await vi.waitFor(() => expect(window.location.hash).toBe('#/cambiar-contrasena'));
  });
});

describe('Formulario de cambio de contraseña', () => {
  async function abrirCambio(servidor: ServidorFalso): Promise<HTMLElement> {
    const { raiz } = abrirApp(servidor, '#/cambiar-contrasena');
    await vi.waitFor(() => expect(boton(raiz, 'Guardar contraseña')).toBeDefined());
    return raiz;
  }

  it('Cambio correcto', async () => {
    const servidor = servidorCon(SESION_ANA_TEMPORAL);
    servidor.responder('POST /cuenta/contrasena', () => {
      servidor.responder('GET /public/sesion', () => json(200, { ...SESION_ANA_TEMPORAL, debeCambiarContrasena: false }));
      return json(204);
    });
    const raiz = await abrirCambio(servidor);

    escribir(campo(raiz, 'Contraseña temporal'), TEMPORAL);
    escribir(campo(raiz, 'Contraseña nueva'), 'NuevaClave2026');
    escribir(campo(raiz, 'Repetí la contraseña nueva'), 'NuevaClave2026');
    boton(raiz, 'Guardar contraseña').click();

    await vi.waitFor(() => expect(servidor.a('POST', '/cuenta/contrasena')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/cuenta/contrasena').cuerpo).toEqual({ actual: TEMPORAL, nueva: 'NuevaClave2026' });
    await vi.waitFor(() => expect(window.location.hash).toBe('#/agenda'));
    await vi.waitFor(() => expect(texto(raiz)).toContain('Tu contraseña se cambió.'));
  });

  it('Repetición distinta', async () => {
    const servidor = servidorCon(SESION_RECEPCION_CONFIG);
    const raiz = await abrirCambio(servidor);

    escribir(campo(raiz, 'Contraseña actual'), 'ClaveActual1');
    escribir(campo(raiz, 'Contraseña nueva'), 'NuevaClave2026');
    escribir(campo(raiz, 'Repetí la contraseña nueva'), 'OtraClave2026');
    boton(raiz, 'Guardar contraseña').click();

    expect(errorDeCampo(raiz, 'Repetí la contraseña nueva')).toBe('Las contraseñas nuevas no coinciden.');
    expect(servidor.a('POST', '/cuenta/contrasena')).toHaveLength(0);
  });

  it('Contraseña nueva demasiado corta', async () => {
    const servidor = servidorCon(SESION_RECEPCION_CONFIG);
    const raiz = await abrirCambio(servidor);

    escribir(campo(raiz, 'Contraseña actual'), 'ClaveActual1');
    escribir(campo(raiz, 'Contraseña nueva'), '1234567');
    escribir(campo(raiz, 'Repetí la contraseña nueva'), '1234567');
    boton(raiz, 'Guardar contraseña').click();

    expect(errorDeCampo(raiz, 'Contraseña nueva')).toBe('La contraseña nueva tiene que tener al menos 8 caracteres.');
    expect(servidor.a('POST', '/cuenta/contrasena')).toHaveLength(0);
  });

  it('Contraseña actual incorrecta', async () => {
    const servidor = servidorCon(SESION_RECEPCION_CONFIG);
    servidor.responder('POST /cuenta/contrasena', () => json(422, { codigo: 'CONTRASENA_ACTUAL_INCORRECTA', detail: 'x' }));
    const raiz = await abrirCambio(servidor);

    escribir(campo(raiz, 'Contraseña actual'), 'ClaveEquivocada');
    escribir(campo(raiz, 'Contraseña nueva'), 'NuevaClave2026');
    escribir(campo(raiz, 'Repetí la contraseña nueva'), 'NuevaClave2026');
    boton(raiz, 'Guardar contraseña').click();

    await vi.waitFor(() => expect(texto(raiz)).toContain('La contraseña actual no es correcta.'));
    expect(campo(raiz, 'Contraseña actual').value).toBe('');
    expect(campo(raiz, 'Contraseña nueva').value).toBe('');
    expect(campo(raiz, 'Repetí la contraseña nueva').value).toBe('');
  });

  it('Cambio voluntario desde el menú', async () => {
    const servidor = servidorCon(SESION_RECEPCION_CONFIG);
    const { raiz } = abrirApp(servidor, '#/agenda');
    const enlace = await vi.waitFor(() => {
      const encontrado = [...raiz.querySelectorAll<HTMLAnchorElement>('.lateral a')].find(
        (a) => a.textContent === 'Mi contraseña',
      );
      expect(encontrado).toBeDefined();
      return encontrado as HTMLAnchorElement;
    });

    window.location.hash = enlace.getAttribute('href') ?? '';

    await vi.waitFor(() => expect(boton(raiz, 'Guardar contraseña')).toBeDefined());
    expect(texto(raiz)).toContain('Volver a la agenda');
    expect(enlace.getAttribute('aria-current')).toBe('page');
  });

  it('Cambio voluntario desde el encabezado', async () => {
    const servidor = servidorCon(SESION_RECEPCION_CONFIG);
    const { raiz } = abrirApp(servidor, '#/agenda');
    const usuario = await vi.waitFor(() => {
      const enlace = raiz.querySelector<HTMLAnchorElement>('.cabecera a.usuario-enlace');
      expect(enlace?.textContent).toContain('recepcion');
      return enlace as HTMLAnchorElement;
    });

    expect(usuario.getAttribute('href')).toBe('#/cambiar-contrasena');
  });
});

describe('Sección de la cuenta en el menú del personal', () => {
  function enlaces(raiz: HTMLElement, nav: string): string[] {
    return [...raiz.querySelectorAll<HTMLAnchorElement>(`aside nav[aria-label="${nav}"] li:not([hidden]) a`)].map(
      (a) => a.textContent ?? '',
    );
  }

  it('El personal ve "Mi contraseña" aparte', async () => {
    const { raiz } = abrirApp(servidorCon(SESION_RECEPCION_CONFIG), '#/agenda');

    await vi.waitFor(() => expect(enlaces(raiz, 'Tu cuenta')).toEqual(['Mi contraseña']));
    expect(enlaces(raiz, 'Principal')).toEqual(['Agenda', 'Reportes', 'Configuración']);
  });

  it('Un paciente no ve la sección de la cuenta', async () => {
    const servidor = servidorCon({ autenticado: true, username: 'paciente.uno@example.com', rol: 'PACIENTE' });
    servidor.responder('GET /paciente/turnos', () => json(200, []));
    const { raiz } = abrirApp(servidor, '#/mis-turnos');

    await vi.waitFor(() => expect(enlaces(raiz, 'Principal')).toContain('Mis turnos'));
    expect(enlaces(raiz, 'Tu cuenta')).toEqual([]);
    expect(raiz.querySelector<HTMLElement>('nav[aria-label="Tu cuenta"]')?.hidden).toBe(true);
    expect(raiz.querySelector('.cabecera a.usuario-enlace:not([hidden])')).toBeNull();
  });
});
