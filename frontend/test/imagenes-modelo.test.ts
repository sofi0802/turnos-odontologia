import { describe, expect, it } from 'vitest';
import {
  CATEGORIAS,
  LIMITE_DESCRIPCION,
  TAMANO_MAXIMO,
  esPiezaFdi,
  etiquetaDeFormato,
  nombreDeArchivo,
  fechaDeToma,
  formatearPeso,
  nombreAccesible,
  nombreDeBoton,
  puedeVerse,
  tipoDeArchivo,
  validarArchivo,
  validarDescripcion,
  validarFechaDeToma,
  validarMotivo,
  validarPieza,
} from '../src/ficha/imagenes-modelo.ts';

/**
 * C-47 frontend-imagenes-y-radiografias, tarea 3.1: funciones puras del modelo (design D2 y D6). Datos ficticios.
 * El día actual de los ejemplos es el 2026-12-01.
 */

function archivo(nombre: string, tipo: string, bytes = 10): File {
  return new File([new Uint8Array(bytes)], nombre, { type: tipo });
}

describe('Tipo del archivo por MIME y extensión', () => {
  it.each([
    ['rx.jpg', 'image/jpeg', 'JPEG'],
    ['foto.png', 'image/png', 'PNG'],
    ['informe.pdf', 'application/pdf', 'PDF'],
    // Sin MIME (algunos sistemas no lo informan) se usa la extensión, sin distinguir mayúsculas.
    ['RX.JPEG', '', 'JPEG'],
    ['FOTO.PNG', '', 'PNG'],
    ['informe.PDF', '', 'PDF'],
  ])('%s (%s) es %s', (nombre, tipo, esperado) => {
    expect(tipoDeArchivo(archivo(nombre, tipo))).toBe(esperado);
  });

  it.each([
    ['animacion.gif', 'image/gif'],
    ['radiografia.jpg', 'text/plain'],
    ['sin-extension', ''],
    ['nota.txt', ''],
  ])('%s (%s) no está permitido', (nombre, tipo) => {
    expect(tipoDeArchivo(archivo(nombre, tipo))).toBeNull();
  });
});

describe('Validar el archivo', () => {
  it('sin archivo pide elegir uno', () => {
    expect(validarArchivo(null)).toBe('Elegí un archivo.');
  });

  it('un tipo no permitido', () => {
    expect(validarArchivo(archivo('animacion.gif', 'image/gif'))).toBe('Solo se aceptan archivos JPEG, PNG o PDF.');
  });

  it('el borde de 10 MB: 10 485 760 bytes pasa y 10 485 761 no', () => {
    expect(TAMANO_MAXIMO).toBe(10_485_760);
    expect(validarArchivo(archivo('rx.jpg', 'image/jpeg', 10_485_760))).toBeNull();
    expect(validarArchivo(archivo('rx.jpg', 'image/jpeg', 10_485_761))).toBe('El archivo supera los 10 MB.');
  });

  it('un archivo vacío no se envía', () => {
    expect(validarArchivo(archivo('rx.jpg', 'image/jpeg', 0))).toBe('El archivo está vacío.');
  });

  it('el tipo se revisa antes que el tamaño', () => {
    expect(validarArchivo(archivo('a.gif', 'image/gif', 10_485_761))).toBe('Solo se aceptan archivos JPEG, PNG o PDF.');
  });
});

describe('Pieza FDI', () => {
  it.each([11, 18, 21, 28, 31, 38, 41, 48, 51, 55, 61, 65, 71, 75, 81, 85])('%i existe', (pieza) => {
    expect(esPiezaFdi(pieza)).toBe(true);
  });

  it.each([0, 10, 19, 29, 39, 49, 50, 56, 66, 76, 86, 90, 111, -11])('%i no existe', (pieza) => {
    expect(esPiezaFdi(pieza)).toBe(false);
  });

  it('un texto vacío es válido porque la pieza es opcional', () => {
    expect(validarPieza('')).toBeNull();
    expect(validarPieza('   ')).toBeNull();
  });

  it('una pieza válida, con espacios alrededor', () => {
    expect(validarPieza(' 36 ')).toBeNull();
    expect(validarPieza('55')).toBeNull();
  });

  it.each(['19', '90', '48a', 'abc', '3.6', '036'])('%s dice que no existe en la notación FDI', (texto) => {
    expect(validarPieza(texto)).toBe('La pieza no existe en la notación FDI.');
  });
});

describe('Fecha de la toma', () => {
  it('hoy es válida y mañana no', () => {
    expect(validarFechaDeToma('2026-12-01', '2026-12-01')).toBeNull();
    expect(validarFechaDeToma('2026-12-02', '2026-12-01')).toBe('La fecha de la toma no puede ser futura.');
  });

  it('una fecha pasada es válida', () => {
    expect(validarFechaDeToma('2026-11-30', '2026-12-01')).toBeNull();
  });

  it('sin fecha pide elegirla', () => {
    expect(validarFechaDeToma('', '2026-12-01')).toBe('Elegí la fecha de la toma.');
  });

  it('una fecha que no tiene el formato AAAA-MM-DD no es válida', () => {
    expect(validarFechaDeToma('01/12/2026', '2026-12-01')).toBe('Elegí la fecha de la toma.');
  });

  it('se muestra como DD/MM/AAAA', () => {
    expect(fechaDeToma('2026-11-30')).toBe('30/11/2026');
    expect(fechaDeToma('2026-01-05')).toBe('05/01/2026');
  });
});

describe('Descripción y motivo', () => {
  it('la descripción de 200 caracteres pasa y la de 201 no', () => {
    expect(LIMITE_DESCRIPCION).toBe(200);
    expect(validarDescripcion('a'.repeat(200))).toBeNull();
    expect(validarDescripcion('a'.repeat(201))).toBe('La descripción puede tener hasta 200 caracteres.');
  });

  it('la descripción vacía es válida', () => {
    expect(validarDescripcion('')).toBeNull();
  });

  it('el motivo es obligatorio', () => {
    expect(validarMotivo('')).toBe('Escribí el motivo de la anulación.');
    expect(validarMotivo('   ')).toBe('Escribí el motivo de la anulación.');
  });

  it('el motivo de 1 y de 200 caracteres pasa y el de 201 no', () => {
    expect(validarMotivo('x')).toBeNull();
    expect(validarMotivo('a'.repeat(200))).toBeNull();
    expect(validarMotivo('a'.repeat(201))).toBe('El motivo puede tener hasta 200 caracteres.');
  });
});

describe('Nombres accesibles', () => {
  it('con pieza', () => {
    const imagen = { categoria: 'RADIOGRAFIA', pieza: 36, tomadaEl: '2026-11-30' } as const;
    expect(nombreAccesible(imagen)).toBe('Radiografía de la pieza 36 del 30/11/2026');
    expect(nombreDeBoton('Ver', imagen, false)).toBe('Ver radiografía de la pieza 36 del 30/11/2026');
    expect(nombreDeBoton('Descargar', imagen, false)).toBe('Descargar radiografía de la pieza 36 del 30/11/2026');
  });

  it('sin pieza', () => {
    const imagen = { categoria: 'ESTUDIO', pieza: null, tomadaEl: '2026-10-01' } as const;
    expect(nombreAccesible(imagen)).toBe('Estudio del 01/10/2026');
    expect(nombreDeBoton('Descargar', imagen, false)).toBe('Descargar estudio del 01/10/2026');
  });

  it('una imagen anulada lo dice en el nombre del botón', () => {
    const imagen = { categoria: 'FOTOGRAFIA', pieza: 36, tomadaEl: '2026-11-30' } as const;
    expect(nombreDeBoton('Ver', imagen, true)).toBe('Ver fotografía anulada de la pieza 36 del 30/11/2026');
  });

  it('las tres categorías tienen su nombre', () => {
    expect(CATEGORIAS.map((c) => c.texto)).toEqual(['Radiografía', 'Fotografía', 'Estudio']);
    expect(CATEGORIAS.map((c) => c.valor)).toEqual(['RADIOGRAFIA', 'FOTOGRAFIA', 'ESTUDIO']);
  });
});

describe('Peso y formato', () => {
  it.each([
    [0, '0 B'],
    [900, '900 B'],
    [1024, '1 KB'],
    [319_488, '312 KB'],
    [860_160, '840 KB'],
    [1_048_576, '1 MB'],
    [1_258_291, '1,2 MB'],
    [2_516_582, '2,4 MB'],
    [10_485_760, '10 MB'],
  ])('%i bytes se muestran como %s', (bytes, texto) => {
    expect(formatearPeso(bytes)).toBe(texto);
  });

  it('un peso que redondea a 1024 KB pasa a MB', () => {
    expect(formatearPeso(1_048_300)).toBe('1 MB');
  });

  it('el formato sale del tipo de contenido', () => {
    expect(etiquetaDeFormato('image/jpeg')).toBe('JPEG');
    expect(etiquetaDeFormato('image/png')).toBe('PNG');
    expect(etiquetaDeFormato('application/pdf')).toBe('PDF');
    expect(etiquetaDeFormato('otro/raro')).toBe('');
  });

  it('el nombre de la descarga es genérico, igual que el que manda la API', () => {
    expect(nombreDeArchivo(7, 'application/pdf')).toBe('imagen-7.pdf');
    expect(nombreDeArchivo(5, 'image/jpeg')).toBe('imagen-5.jpg');
    expect(nombreDeArchivo(6, 'image/png')).toBe('imagen-6.png');
    expect(nombreDeArchivo(8, 'otro/raro')).toBe('imagen-8');
  });

  it('solo JPEG y PNG pueden verse; el PDF solo se descarga', () => {
    expect(puedeVerse('image/jpeg')).toBe(true);
    expect(puedeVerse('image/png')).toBe(true);
    expect(puedeVerse('application/pdf')).toBe(false);
  });
});
