import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cargarCatalogos } from '../src/agenda/catalogos.ts';
import { horariosSinChoqueDeSillon } from '../src/agenda/nuevo-turno.ts';
import { json, ServidorFalso } from './apoyo.ts';

function horario(hora: string, minutos = 30): { inicio: string; fin: string } {
  const [h, m] = hora.split(':').map(Number);
  const fin = (h ?? 0) * 60 + (m ?? 0) + minutos;
  const dos = (n: number): string => String(n).padStart(2, '0');
  return {
    inicio: `2026-12-01T${hora}:00-03:00`,
    fin: `2026-12-01T${dos(Math.floor(fin / 60))}:${dos(fin % 60)}:00-03:00`,
  };
}

function turnoDelSillon(desde: string, hasta: string, estado = 'RESERVADO'): { inicio: string; fin: string; estado: string } {
  return { inicio: `2026-12-01T${desde}:00-03:00`, fin: `2026-12-01T${hasta}:00-03:00`, estado };
}

function horas(lista: ReadonlyArray<{ inicio: string }>): string[] {
  return lista.map((h) => h.inicio.slice(11, 16));
}

let servidor: ServidorFalso;

beforeEach(() => {
  servidor = new ServidorFalso();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('Horarios sin choque con el sillón habitual (RN-AG-02 a RN-AG-05)', () => {
  const ofrecidos = ['10:00', '10:30', '11:00', '11:30', '11:45'].map((h) => horario(h));

  it('oculta los que se superponen o no dejan el margen', () => {
    const libres = horariosSinChoqueDeSillon(ofrecidos, [turnoDelSillon('11:00', '11:30')], 10);
    expect(horas(libres)).toEqual(['10:00', '11:45']);
  });

  it('contiguo sin margen se oculta y a 10 minutos se muestra', () => {
    const turnos = [turnoDelSillon('11:00', '11:30')];
    expect(horas(horariosSinChoqueDeSillon([horario('11:30')], turnos, 10))).toEqual([]);
    expect(horas(horariosSinChoqueDeSillon([horario('11:40')], turnos, 10))).toEqual(['11:40']);
    expect(horas(horariosSinChoqueDeSillon([horario('10:20')], turnos, 10))).toEqual(['10:20']);
    expect(horas(horariosSinChoqueDeSillon([horario('10:21')], turnos, 10))).toEqual([]);
  });

  it('un margen mayor oculta más horarios', () => {
    const libres = horariosSinChoqueDeSillon(ofrecidos, [turnoDelSillon('11:00', '11:30')], 20);
    expect(horas(libres)).toEqual(['10:00']);
  });

  it('un turno cancelado no cuenta', () => {
    const libres = horariosSinChoqueDeSillon(ofrecidos, [turnoDelSillon('11:00', '11:30', 'CANCELADO')], 10);
    expect(horas(libres)).toEqual(['10:00', '10:30', '11:00', '11:30', '11:45']);
  });

  it('sin turnos en el sillón no se oculta nada', () => {
    expect(horas(horariosSinChoqueDeSillon(ofrecidos, [], 10))).toEqual(['10:00', '10:30', '11:00', '11:30', '11:45']);
  });
});

describe('Sillón habitual en el catálogo de profesionales', () => {
  it('lee el sillón habitual de cada profesional', async () => {
    const catalogos = await cargarCatalogos(servidor.api);

    expect(catalogos.profesionales.map((p) => [p.nombre, p.sillonHabitualId])).toEqual([
      ['Ana Ejemplo', 1],
      ['Bruno Prueba', 2],
    ]);
  });

  it('un profesional sin sillón habitual queda con null', async () => {
    servidor.responder('GET /profesionales', () =>
      json(200, [
        { id: 1, nombre: 'Ana', apellido: 'Ejemplo', activo: true, sillonHabitualId: null },
        { id: 2, nombre: 'Bruno', apellido: 'Prueba', activo: true },
      ]),
    );

    const catalogos = await cargarCatalogos(servidor.api);

    expect(catalogos.profesionales.map((p) => p.sillonHabitualId)).toEqual([null, null]);
  });

  it('los sillones y las prestaciones no traen sillón habitual', async () => {
    const catalogos = await cargarCatalogos(servidor.api);

    expect(catalogos.sillones.every((s) => s.sillonHabitualId === null)).toBe(true);
    expect(catalogos.prestaciones.every((p) => p.sillonHabitualId === null)).toBe(true);
  });
});
