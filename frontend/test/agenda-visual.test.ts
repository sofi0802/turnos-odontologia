import { afterEach, describe, expect, it } from 'vitest';
import { CATALOGOS_VACIOS } from '../src/agenda/catalogos.ts';
import type { TurnoAgenda } from '../src/agenda/consulta.ts';
import { periodoDe } from '../src/agenda/periodo.ts';
import { dibujarAgenda } from '../src/agenda/vista.ts';
import { textoDeModulo } from './estilos.ts';

// C-39, etapa B: el borde de cada turno lleva el color de su estado (mockups aprobados) y la semana
// se acomoda en columnas sin huecos cuando unos días tienen más turnos que otros.

const ACCIONES = { cancelar: () => undefined, reprogramar: () => undefined };

function turno(id: number, dia: string, datos: Partial<TurnoAgenda> = {}): TurnoAgenda {
  return {
    id,
    pacienteId: 1,
    pacienteNombre: 'Paciente',
    pacienteApellido: 'Uno',
    profesionalId: 1,
    sillonId: 1,
    prestacionId: 1,
    inicio: `${dia}T10:00:00-03:00`,
    fin: `${dia}T10:30:00-03:00`,
    estado: 'RESERVADO',
    esSobreturno: false,
    llegadaEn: null,
    ...datos,
  };
}

function regla(css: string, selector: string): string {
  const escapado = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`(^|\\n\\s*|,\\s*)${escapado}\\s*(,[^{]*)?\\{([^}]*)\\}`).exec(css)?.[3] ?? '';
}

afterEach(() => {
  document.body.replaceChildren();
});

describe('Agenda con el color de cada turno', () => {
  it('El borde del turno según su estado', () => {
    const contenedor = document.createElement('div');
    document.body.append(contenedor);

    dibujarAgenda(
      contenedor,
      periodoDe('dia', '2026-12-01'),
      [
        turno(1, '2026-12-01'),
        turno(2, '2026-12-01', { estado: 'CONFIRMADO' }),
        turno(3, '2026-12-01', { esSobreturno: true }),
      ],
      CATALOGOS_VACIOS,
      ACCIONES,
    );

    const clases = [...contenedor.querySelectorAll('.turno')].map((t) => [...t.classList].filter((c) => c.startsWith('turno-') && c !== 'turno-horario'));
    expect(clases).toEqual([['turno-reservado'], ['turno-confirmado'], ['turno-sobreturno']]);

    const agenda = textoDeModulo('03-agenda.css');
    expect(regla(agenda, '.turno')).toMatch(/border-left:\s*4px solid var\(--ds-turno-reservado\)/);
    expect(regla(agenda, '.turno-confirmado')).toMatch(/border-left-color:\s*var\(--ds-turno-confirmado\)/);
    expect(regla(agenda, '.turno-sobreturno')).toMatch(/border-left-color:\s*var\(--ds-turno-sobreturno\)/);
  });
});

describe('Agenda semanal', () => {
  it('Semana con días de distinta cantidad de turnos', () => {
    const contenedor = document.createElement('div');
    document.body.append(contenedor);
    const periodo = periodoDe('semana', '2026-12-01');

    dibujarAgenda(
      contenedor,
      periodo,
      [turno(1, '2026-11-30'), turno(2, '2026-12-02'), turno(3, '2026-12-02'), turno(4, '2026-12-02'), turno(5, '2026-12-03')],
      CATALOGOS_VACIOS,
      ACCIONES,
    );

    // Los siete días siguen en orden, de lunes a domingo, y la semana se marca como grilla de columnas fijas.
    const dias = [...contenedor.querySelectorAll('.semana-encabezado')].map((h) => h.textContent);
    expect(dias).toHaveLength(7);
    expect(dias[0]).toMatch(/^lun/i);
    expect(dias[6]).toMatch(/^dom/i);
    expect(contenedor.classList.contains('semana')).toBe(true);

    const agenda = textoDeModulo('03-agenda.css');
    expect(regla(agenda, '.semana-grilla')).toMatch(/grid-template-columns:\s*var\(--semana-escala\) repeat\(7,/);

    // Al volver a la vista del día, la marca se quita.
    dibujarAgenda(contenedor, periodoDe('dia', '2026-12-01'), [turno(1, '2026-12-01')], CATALOGOS_VACIOS, ACCIONES);
    expect(contenedor.classList.contains('semana')).toBe(false);
  });
});
