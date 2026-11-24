import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { boton, campo, escribir, json, texto } from './apoyo.ts';
import { imagen, prepararEntorno, RX36, SESION_ANA, type Entorno } from './apoyo-imagenes.ts';

/**
 * C-47 frontend-imagenes-y-radiografias, tarea 5.1: "Cargar una imagen", "Validar el archivo antes de enviarlo" y
 * "Errores de la API al cargar" del delta `frontend-imagenes-clinicas`. Datos ficticios. Hoy es el 2026-12-01.
 */

let entorno: Entorno;

beforeEach(() => {
  entorno = prepararEntorno(SESION_ANA, []);
});

afterEach(() => {
  entorno.cerrar();
});

function dialogo(): HTMLDialogElement {
  const abierto = [...document.querySelectorAll<HTMLDialogElement>('dialog')].find((d) => d.querySelector('input[type="file"]'));
  if (abierto === undefined) {
    throw new Error('No está el diálogo de carga');
  }
  return abierto;
}

function hayDialogo(): boolean {
  return [...document.querySelectorAll('dialog')].some((d) => d.querySelector('input[type="file"]'));
}

async function abrirCarga(): Promise<HTMLDialogElement> {
  await entorno.abrirFicha();
  await entorno.seccionLista();
  boton(entorno.seccion(), 'Cargar imagen').click();
  return dialogo();
}

function archivo(nombre: string, tipo: string, bytes = 300): File {
  return new File([new Uint8Array(bytes)], nombre, { type: tipo });
}

function elegirArchivo(elegido: File): void {
  const entrada = dialogo().querySelector<HTMLInputElement>('input[type="file"]');
  if (entrada === null) {
    throw new Error('No está el campo del archivo');
  }
  Object.defineProperty(entrada, 'files', { configurable: true, value: [elegido] });
  entrada.dispatchEvent(new Event('change', { bubbles: true }));
}

function errorDe(elemento: HTMLElement): string {
  const ids = (elemento.getAttribute('aria-describedby') ?? '').split(/\s+/).filter((id) => id !== '');
  return ids.map((id) => document.getElementById(id)).find((e) => e?.classList.contains('campo-error'))?.textContent ?? '';
}

function entradaDeArchivo(): HTMLInputElement {
  return dialogo().querySelector('input[type="file"]') as HTMLInputElement;
}

function cargar(): void {
  boton(dialogo(), 'Cargar').click();
}

function pedidosDeCarga(): number {
  return entorno.servidor.a('POST', '/pacientes/1/imagenes').length;
}

describe('Cargar una imagen', () => {
  it('Cargar una radiografía', async () => {
    let enviado: FormData | undefined;
    entorno.servidor.responder('POST /pacientes/{id}/imagenes', (pedido) => {
      enviado = pedido.formulario;
      entorno.imagenes = [RX36];
      return json(201, RX36);
    });
    await abrirCarga();
    elegirArchivo(archivo('rx.jpg', 'image/jpeg'));
    escribir(campo<HTMLSelectElement>(dialogo(), 'Categoría'), 'RADIOGRAFIA');
    escribir(campo(dialogo(), 'Fecha de la toma'), '2026-11-30');
    escribir(campo(dialogo(), 'Pieza (opcional)'), '36');
    // El listado vacío del principio ya hizo sus pedidos (el de las vigentes y el de las anuladas).
    const listadosAntes = entorno.servidor.a('GET', '/pacientes/1/imagenes').length;

    cargar();

    await vi.waitFor(() => expect(hayDialogo()).toBe(false));
    expect((enviado?.get('archivo') as File).name).toBe('rx.jpg');
    expect(enviado?.get('categoria')).toBe('RADIOGRAFIA');
    expect(enviado?.get('tomadaEl')).toBe('2026-11-30');
    expect(enviado?.get('pieza')).toBe('36');
    expect(enviado?.has('descripcion')).toBe(false);
    expect(entorno.seccion().querySelector('[role="status"].aviso')?.textContent).toBe('Imagen cargada.');
    await vi.waitFor(() => expect(entorno.servidor.a('GET', '/pacientes/1/imagenes')).toHaveLength(listadosAntes + 1));
    await vi.waitFor(() => expect(entorno.seccion().querySelectorAll('.img-fila')).toHaveLength(1));
  });

  it('el diálogo tiene los campos, las categorías y el contador de la descripción', async () => {
    const abierto = await abrirCarga();

    expect(abierto.querySelector('h2')?.textContent).toBe('Cargar imagen');
    expect(texto(abierto)).toContain('Paciente Uno');
    const categoria = campo<HTMLSelectElement>(abierto, 'Categoría');
    expect([...categoria.options].map((o) => o.textContent)).toEqual(['Radiografía', 'Fotografía', 'Estudio']);
    expect(categoria.value).toBe('RADIOGRAFIA');
    const entrada = entradaDeArchivo();
    expect(entrada.accept).toBe('image/jpeg,image/png,application/pdf');
    expect(entrada.classList.contains('solo-lectores')).toBe(true);
    expect(abierto.querySelector('label.btn')?.textContent).toBe('Elegir archivo');
    expect(texto(abierto)).toContain('JPEG, PNG o PDF de hasta 10 MB.');
    expect(abierto.querySelector('.contador')?.textContent).toBe('0/200');
    const descripcion = campo<HTMLTextAreaElement>(abierto, 'Descripción (opcional)');
    descripcion.value = 'Control';
    descripcion.dispatchEvent(new Event('input', { bubbles: true }));
    expect(abierto.querySelector('.contador')?.textContent).toBe('7/200');
  });

  it('al elegir el archivo se muestra su nombre y peso', async () => {
    const abierto = await abrirCarga();

    elegirArchivo(archivo('radiografia-control.jpg', 'image/jpeg', 319_488));

    expect(texto(abierto.querySelector('.img-archivo') as HTMLElement)).toContain('radiografia-control.jpg · 312 KB');
    expect(abierto.querySelector('label.btn')?.textContent).toBe('Cambiar archivo');
  });

  it('un estudio en PDF sin pieza ni descripción se envía sin esas partes', async () => {
    let enviado: FormData | undefined;
    entorno.servidor.responder('POST /pacientes/{id}/imagenes', (pedido) => {
      enviado = pedido.formulario;
      return json(201, imagen({ id: 9, categoria: 'ESTUDIO', pieza: null, tipoContenido: 'application/pdf', tomadaEl: '2026-12-01' }));
    });
    await abrirCarga();
    elegirArchivo(archivo('informe.pdf', 'application/pdf'));
    escribir(campo<HTMLSelectElement>(dialogo(), 'Categoría'), 'ESTUDIO');

    cargar();

    await vi.waitFor(() => expect(hayDialogo()).toBe(false));
    expect(enviado?.get('categoria')).toBe('ESTUDIO');
    expect(enviado?.get('tomadaEl')).toBe('2026-12-01');
    expect(enviado?.has('pieza')).toBe(false);
    expect(enviado?.has('descripcion')).toBe(false);
  });

  it('con la descripción escrita se envía sin espacios de más', async () => {
    let enviado: FormData | undefined;
    entorno.servidor.responder('POST /pacientes/{id}/imagenes', (pedido) => {
      enviado = pedido.formulario;
      return json(201, RX36);
    });
    await abrirCarga();
    elegirArchivo(archivo('rx.png', 'image/png'));
    escribir(campo(dialogo(), 'Descripción (opcional)'), '  Control de la endodoncia  ');

    cargar();

    await vi.waitFor(() => expect(hayDialogo()).toBe(false));
    expect(enviado?.get('descripcion')).toBe('Control de la endodoncia');
  });

  it('Fecha por defecto', async () => {
    const abierto = await abrirCarga();

    const fecha = campo(abierto, 'Fecha de la toma');
    expect(fecha.value).toBe('2026-12-01');
    expect(fecha.getAttribute('max')).toBe('2026-12-01');
    expect(fecha.getAttribute('type')).toBe('date');
  });

  it('mientras se envía, "Cargando…" queda deshabilitado y no se envía dos veces', async () => {
    let terminar: (r: Response) => void = () => undefined;
    entorno.servidor.responder('POST /pacientes/{id}/imagenes', () => new Promise<Response>((resolver) => (terminar = resolver)));
    await abrirCarga();
    elegirArchivo(archivo('rx.jpg', 'image/jpeg'));

    cargar();

    const principal = dialogo().querySelector<HTMLButtonElement>('.dialogo-acciones .btn-principal') as HTMLButtonElement;
    await vi.waitFor(() => expect(principal.textContent).toBe('Cargando…'));
    expect(principal.disabled).toBe(true);
    dialogo().querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    expect(pedidosDeCarga()).toBe(1);

    terminar(json(201, RX36));
    await vi.waitFor(() => expect(hayDialogo()).toBe(false));
  });
});

describe('Validar el archivo antes de enviarlo', () => {
  it('Archivo de un tipo no permitido', async () => {
    await abrirCarga();
    elegirArchivo(archivo('animacion.gif', 'image/gif', 48));

    cargar();

    expect(pedidosDeCarga()).toBe(0);
    expect(errorDe(entradaDeArchivo())).toBe('Solo se aceptan archivos JPEG, PNG o PDF.');
    expect(entradaDeArchivo().getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(entradaDeArchivo());
    expect(dialogo().querySelector('[role="alert"]')?.textContent).toBe('Revisá los datos marcados.');
  });

  it('Archivo de más de 10 MB', async () => {
    await abrirCarga();
    elegirArchivo(archivo('rx.jpg', 'image/jpeg', 10_485_761));

    cargar();

    expect(pedidosDeCarga()).toBe(0);
    expect(errorDe(entradaDeArchivo())).toBe('El archivo supera los 10 MB.');
  });

  it('un archivo de exactamente 10 MB se envía', async () => {
    entorno.servidor.responder('POST /pacientes/{id}/imagenes', () => json(201, RX36));
    await abrirCarga();
    elegirArchivo(archivo('rx.jpg', 'image/jpeg', 10_485_760));

    cargar();

    await vi.waitFor(() => expect(pedidosDeCarga()).toBe(1));
  });

  it('Sin archivo', async () => {
    await abrirCarga();

    cargar();

    expect(pedidosDeCarga()).toBe(0);
    expect(errorDe(entradaDeArchivo())).toBe('Elegí un archivo.');
    expect(document.activeElement).toBe(entradaDeArchivo());
  });

  it('Pieza inexistente', async () => {
    await abrirCarga();
    elegirArchivo(archivo('rx.jpg', 'image/jpeg'));
    const pieza = campo(dialogo(), 'Pieza (opcional)');
    escribir(pieza, '19');

    cargar();

    expect(pedidosDeCarga()).toBe(0);
    expect(errorDe(pieza)).toBe('La pieza no existe en la notación FDI.');
    expect(pieza.getAttribute('aria-invalid')).toBe('true');
    // El archivo está bien: el foco va al primer campo con error, que es la pieza.
    expect(document.activeElement).toBe(pieza);
    expect(entradaDeArchivo().hasAttribute('aria-invalid')).toBe(false);
  });

  it('una fecha futura o vacía no se envía', async () => {
    await abrirCarga();
    elegirArchivo(archivo('rx.jpg', 'image/jpeg'));
    const fecha = campo(dialogo(), 'Fecha de la toma');

    escribir(fecha, '2026-12-02');
    cargar();
    expect(errorDe(fecha)).toBe('La fecha de la toma no puede ser futura.');

    escribir(fecha, '');
    cargar();
    expect(errorDe(fecha)).toBe('Elegí la fecha de la toma.');
    expect(pedidosDeCarga()).toBe(0);
  });

  it('una descripción de 201 caracteres no se envía y de 200 sí', async () => {
    entorno.servidor.responder('POST /pacientes/{id}/imagenes', () => json(201, RX36));
    await abrirCarga();
    elegirArchivo(archivo('rx.jpg', 'image/jpeg'));
    const descripcion = campo<HTMLTextAreaElement>(dialogo(), 'Descripción (opcional)');

    escribir(descripcion as unknown as HTMLInputElement, 'a'.repeat(201));
    cargar();
    expect(errorDe(descripcion)).toBe('La descripción puede tener hasta 200 caracteres.');
    expect(pedidosDeCarga()).toBe(0);

    escribir(descripcion as unknown as HTMLInputElement, 'a'.repeat(200));
    cargar();
    await vi.waitFor(() => expect(pedidosDeCarga()).toBe(1));
  });

  it('al corregir y volver a cargar se limpian los errores anteriores', async () => {
    entorno.servidor.responder('POST /pacientes/{id}/imagenes', () => json(201, RX36));
    await abrirCarga();
    cargar();
    expect(errorDe(entradaDeArchivo())).toBe('Elegí un archivo.');

    elegirArchivo(archivo('rx.jpg', 'image/jpeg'));
    cargar();

    await vi.waitFor(() => expect(hayDialogo()).toBe(false));
  });
});

describe('Errores de la API al cargar', () => {
  async function cargarConRespuesta(respuesta: Response): Promise<void> {
    entorno.servidor.responder('POST /pacientes/{id}/imagenes', () => respuesta);
    await abrirCarga();
    elegirArchivo(archivo('rx.jpg', 'image/jpeg'));
    escribir(campo(dialogo(), 'Pieza (opcional)'), '36');
    escribir(campo(dialogo(), 'Descripción (opcional)'), 'Control');
    cargar();
    await vi.waitFor(() => expect(dialogo().querySelector('.dialogo-acciones .btn-principal')?.textContent).toBe('Cargar'));
  }

  function avisoGeneral(): string {
    return dialogo().querySelector('form > [role="alert"]')?.textContent ?? '';
  }

  function datosIntactos(): void {
    expect(campo(dialogo(), 'Pieza (opcional)').value).toBe('36');
    expect(campo(dialogo(), 'Descripción (opcional)').value).toBe('Control');
    expect(dialogo().textContent).toContain('rx.jpg');
    expect((dialogo().querySelector('.dialogo-acciones .btn-principal') as HTMLButtonElement).disabled).toBe(false);
  }

  it('El servidor rechaza el tipo', async () => {
    await cargarConRespuesta(json(422, { status: 422, codigo: 'TIPO_ARCHIVO_NO_PERMITIDO', detail: 'x' }));

    expect(hayDialogo()).toBe(true);
    expect(avisoGeneral()).toBe('Solo se aceptan archivos JPEG, PNG o PDF.');
    datosIntactos();
  });

  it('El servidor rechaza el tamaño', async () => {
    await cargarConRespuesta(json(413, { status: 413, codigo: 'ARCHIVO_DEMASIADO_GRANDE', detail: 'x' }));

    expect(hayDialogo()).toBe(true);
    expect(avisoGeneral()).toBe('El archivo supera los 10 MB.');
    datosIntactos();
  });

  it('un 413 sin cuerpo también dice que el archivo supera los 10 MB', async () => {
    await cargarConRespuesta(new Response(null, { status: 413 }));
    expect(avisoGeneral()).toBe('El archivo supera los 10 MB.');
  });

  it('El servidor rechaza la pieza', async () => {
    await cargarConRespuesta(json(422, { status: 422, codigo: 'PIEZA_INVALIDA', detail: 'x' }));
    expect(avisoGeneral()).toBe('La pieza no existe en la notación FDI.');
    datosIntactos();
  });

  it('El servidor rechaza los datos y se muestra su detalle', async () => {
    await cargarConRespuesta(json(422, { status: 422, codigo: 'DATOS_INVALIDOS', detail: 'Hay datos de entrada inválidos' }));
    expect(avisoGeneral()).toBe('Hay datos de entrada inválidos');
    datosIntactos();
  });

  it('un error genérico muestra el mensaje genérico de la aplicación, sin la traza', async () => {
    await cargarConRespuesta(json(500, { detail: 'java.lang.NullPointerException' }));

    expect(avisoGeneral()).toBe('No pudimos completar la operación. Probá de nuevo en unos minutos.');
    expect(texto(dialogo())).not.toContain('NullPointerException');
    datosIntactos();
  });

  it('después de un error se puede volver a enviar con los mismos datos', async () => {
    await cargarConRespuesta(json(500, {}));
    entorno.servidor.responder('POST /pacientes/{id}/imagenes', () => json(201, RX36));

    cargar();

    await vi.waitFor(() => expect(hayDialogo()).toBe(false));
    expect(pedidosDeCarga()).toBe(2);
  });
});
