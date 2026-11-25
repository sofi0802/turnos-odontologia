import { afterEach, describe, expect, it, vi } from 'vitest';
import { CATALOGOS_VACIOS, type Catalogos } from '../src/agenda/catalogos.ts';
import type { TurnoAgenda } from '../src/agenda/consulta.ts';
import { periodoDe } from '../src/agenda/periodo.ts';
import { ubicarTurnos } from '../src/agenda/semana.ts';
import { dibujarAgenda, type AccionesTurno } from '../src/agenda/vista.ts';
import { textoDeModulo } from './estilos.ts';

// C-39, etapa E: la agenda semanal como grilla horaria (pedido del equipo, sin mockups previos): siete columnas fijas,
// una escala horaria compartida, turnos simultáneos lado a lado y el detalle con las acciones en un diálogo.

const PERIODO = periodoDe('semana', '2026-12-01');

const CATALOGOS: Catalogos = {
  ...CATALOGOS_VACIOS,
  profesionales: [{ id: 1, nombre: 'Ana Ejemplo', activo: true, sillonHabitualId: 1 }],
  sillones: [{ id: 1, nombre: 'Sillón 1', activo: true, sillonHabitualId: null }],
  prestaciones: [{ id: 1, nombre: 'Consulta o control', activo: true, sillonHabitualId: null }],
};

function turno(id: number, dia: string, desde: string, hasta: string, datos: Partial<TurnoAgenda> = {}): TurnoAgenda {
  return {
    id,
    pacienteId: 1,
    pacienteNombre: 'Paciente',
    pacienteApellido: `Número ${id}`,
    profesionalId: 1,
    sillonId: 1,
    prestacionId: 1,
    inicio: `${dia}T${desde}:00-03:00`,
    fin: `${dia}T${hasta}:00-03:00`,
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

function dibujar(
  turnos: TurnoAgenda[],
  acciones: Partial<AccionesTurno> = {},
  nombre: 'profesional' | 'paciente' = 'profesional',
): HTMLElement {
  const contenedor = document.createElement('div');
  document.body.append(contenedor);
  dibujarAgenda(
    contenedor,
    PERIODO,
    turnos,
    CATALOGOS,
    { cancelar: () => undefined, reprogramar: () => undefined, ...acciones },
    nombre,
  );
  return contenedor;
}

afterEach(() => {
  document.body.replaceChildren();
});

describe('Ubicación de los turnos', () => {
  it('los turnos que se superponen comparten carriles y los demás ocupan todo el ancho', () => {
    const ubicados = ubicarTurnos([
      turno(1, '2026-12-01', '09:00', '10:00'),
      turno(2, '2026-12-01', '09:30', '10:30'),
      turno(3, '2026-12-01', '10:00', '10:30', {}),
      turno(4, '2026-12-01', '12:00', '12:30'),
    ]);
    const por = (id: number) => ubicados.find((u) => u.turno.id === id);
    expect([por(1)?.carril, por(1)?.carriles]).toEqual([0, 2]);
    expect([por(2)?.carril, por(2)?.carriles]).toEqual([1, 2]);
    // Empieza cuando termina el primero: reutiliza su carril.
    expect([por(3)?.carril, por(3)?.carriles]).toEqual([0, 2]);
    expect([por(4)?.carril, por(4)?.carriles]).toEqual([0, 1]);
  });
});

describe('Agenda semanal en grilla', () => {
  it('Siete columnas fijas con encabezados', () => {
    const contenedor = dibujar([turno(1, '2026-12-01', '09:00', '09:30'), turno(2, '2026-12-03', '10:00', '10:30')]);

    expect(contenedor.classList.contains('semana')).toBe(true);
    const encabezados = [...contenedor.querySelectorAll('.semana-encabezado')].map((h) => h.textContent);
    expect(encabezados).toHaveLength(7);
    expect(encabezados[0]).toMatch(/^lun/i);
    expect(encabezados[6]).toMatch(/^dom/i);
    expect(contenedor.querySelectorAll('.agenda-dia')).toHaveLength(7);
    // Los turnos van en la columna de su día, y los días vacíos lo dicen.
    const columnas = [...contenedor.querySelectorAll('.agenda-dia')];
    expect(columnas[1]?.querySelectorAll('.turno-celda')).toHaveLength(1);
    expect(columnas[3]?.querySelectorAll('.turno-celda')).toHaveLength(1);
    // Un día sin turnos queda en blanco: sin carteles ni ilustraciones.
    expect(columnas[2]?.textContent).toBe('');
    expect(columnas[2]?.children).toHaveLength(0);
    expect(contenedor.textContent).not.toContain('Sin turnos');

    const agenda = textoDeModulo('03-agenda.css');
    expect(regla(agenda, '.semana-grilla')).toMatch(/grid-template-columns:\s*var\(--semana-escala\) repeat\(7,\s*minmax\(/);
  });

  it('Escala horaria compartida y posición según la hora y la duración', () => {
    const contenedor = dibujar([turno(1, '2026-12-01', '10:00', '10:45')]);

    const horas = [...contenedor.querySelectorAll('.semana-hora')].map((h) => h.textContent);
    expect(horas[0]).toBe('08:00');
    expect(horas[horas.length - 1]).toBe('18:00');
    const celda = contenedor.querySelector<HTMLElement>('.turno-celda');
    expect(celda?.style.getPropertyValue('--desde')).toBe('120');
    expect(celda?.style.getPropertyValue('--dur')).toBe('45');
    expect(regla(textoDeModulo('03-agenda.css'), '.turno-celda')).toMatch(/top:\s*calc\(var\(--desde\) \* var\(--semana-minuto\)\)/);
  });

  it('La escala se extiende si hay turnos fuera del horario habitual', () => {
    const contenedor = dibujar([turno(1, '2026-12-01', '06:30', '07:00'), turno(2, '2026-12-01', '19:30', '20:15')]);

    const horas = [...contenedor.querySelectorAll('.semana-hora')].map((h) => h.textContent);
    expect(horas[0]).toBe('06:00');
    expect(horas[horas.length - 1]).toBe('20:00');
  });

  it('Dos turnos a la misma hora se agrupan en un bloque', () => {
    const contenedor = dibujar([
      turno(1, '2026-12-01', '09:00', '10:00'),
      turno(2, '2026-12-01', '09:30', '10:30', { profesionalId: 2, sillonId: 2 }),
      turno(3, '2026-12-01', '12:00', '12:30'),
    ]);

    // Los dos que se superponen son un solo bloque que cubre de 09:00 a 10:30; el tercero queda suelto.
    const grupo = contenedor.querySelector<HTMLButtonElement>('.turno-grupo');
    expect(grupo?.textContent).toBe('2 turnos');
    expect(grupo?.closest<HTMLElement>('.turno-celda')?.style.getPropertyValue('--desde')).toBe('60');
    expect(grupo?.closest<HTMLElement>('.turno-celda')?.style.getPropertyValue('--dur')).toBe('90');
    expect(contenedor.querySelectorAll('.turno-tarjeta')).toHaveLength(1);
    grupo?.click();
    expect(document.querySelectorAll('dialog .turno')).toHaveLength(2);
  });

  it('Todas las tarjetas muestran lo mismo', () => {
    const contenedor = dibujar([
      turno(1, '2026-12-01', '09:00', '09:15'),
      turno(2, '2026-12-01', '10:00', '10:30'),
      turno(3, '2026-12-01', '11:00', '13:00'),
    ]);

    const tarjetas = [...contenedor.querySelectorAll('.turno-tarjeta')];
    expect(tarjetas).toHaveLength(3);
    for (const t of tarjetas) {
      expect([...t.querySelectorAll('span')].map((x) => x.className).filter((c) => c !== 'tarjeta-icono' && c !== 'tarjeta-linea')).toEqual([
        'tarjeta-hora',
        'tarjeta-nombre',
      ]);
    }
    expect(contenedor.querySelector('.tarjeta-meta, .tarjeta-estado, [data-tam]')).toBeNull();
  });

  it('Nombre de la tarjeta según quién mira', () => {
    const turnos = [turno(1, '2026-12-01', '09:00', '10:00', { estado: 'CONFIRMADO' })];

    const deRecepcion = dibujar(turnos).querySelector('.turno-tarjeta');
    expect(deRecepcion?.querySelector('.tarjeta-hora')?.textContent).toBe('09:00');
    expect(deRecepcion?.querySelector('.tarjeta-nombre')?.textContent).toBe('Ana Ejemplo');
    expect(deRecepcion?.textContent).not.toContain('Número 1');

    document.body.replaceChildren();
    const deOdontologo = dibujar(turnos, {}, 'paciente').querySelector('.turno-tarjeta');
    expect(deOdontologo?.querySelector('.tarjeta-hora')?.textContent).toBe('09:00');
    expect(deOdontologo?.querySelector('.tarjeta-nombre')?.textContent).toBe('Paciente Número 1');
    expect(deOdontologo?.textContent).not.toContain('Ana Ejemplo');

    // En los dos casos, el nombre accesible lleva todo el detalle y el estado por escrito.
    for (const tarjeta of [deRecepcion, deOdontologo]) {
      expect(tarjeta?.getAttribute('aria-label')).toBe(
        'Paciente Número 1, 09:00 a 10:00, Ana Ejemplo, Sillón 1, Consulta o control, Confirmado',
      );
      expect(tarjeta?.className).toContain('turno-confirmado');
    }
  });

  it('El detalle del turno ofrece las acciones y cierra antes de ejecutarlas', () => {
    const cancelar = vi.fn();
    const contenedor = dibujar([turno(1, '2026-12-01', '09:00', '10:00')], { cancelar });

    contenedor.querySelector<HTMLButtonElement>('.turno-celda button')?.click();
    const dialogo = document.querySelector<HTMLDialogElement>('dialog.dialogo');
    expect(dialogo?.open).toBe(true);
    expect(dialogo?.textContent).toContain('Paciente Número 1');
    const botones = [...(dialogo?.querySelectorAll('.turno-acciones button') ?? [])].map((b) => b.textContent);
    expect(botones).toEqual(['Cancelar turno', 'Reprogramar']);

    [...(dialogo?.querySelectorAll<HTMLButtonElement>('.turno-acciones button') ?? [])][0]?.click();
    expect(cancelar).toHaveBeenCalledTimes(1);
    expect(document.querySelector('dialog.dialogo')).toBeNull();
  });

  it('Extender desde la semana', () => {
    const extender = vi.fn();
    const contenedor = dibujar([turno(7, '2026-12-01', '10:00', '10:30')], { extender });

    contenedor.querySelector<HTMLButtonElement>('.turno-celda button')?.click();
    const dialogo = document.querySelector<HTMLDialogElement>('dialog.dialogo');
    const botones = [...(dialogo?.querySelectorAll<HTMLButtonElement>('.turno-acciones button') ?? [])];
    expect(botones.map((b) => b.textContent)).toEqual(['Cancelar turno', 'Reprogramar', 'Extender']);

    botones[2]?.click();
    expect(document.querySelector('dialog.dialogo')).toBeNull();
    expect(extender).toHaveBeenCalledTimes(1);
    expect(extender.mock.calls[0]?.[0]).toMatchObject({ id: 7 });
  });

  it('Un turno atendido no ofrece cancelar ni reprogramar', () => {
    const contenedor = dibujar([turno(1, '2026-12-01', '09:00', '10:00', { estado: 'ATENDIDO' })]);

    contenedor.querySelector<HTMLButtonElement>('.turno-celda button')?.click();
    expect(document.querySelector('dialog .turno-acciones')).toBeNull();
    expect(document.querySelector('dialog')?.textContent).toContain('Atendido');
  });

  it('Con muchos turnos a la vez se agrupan y se consultan todos', () => {
    const turnos = [1, 2, 3, 4, 5].map((n) => turno(n, '2026-12-02', '09:00', '09:30', { profesionalId: n }));
    const contenedor = dibujar(turnos);

    const grupo = contenedor.querySelector<HTMLButtonElement>('.turno-grupo');
    expect(contenedor.querySelectorAll('.turno-celda:not(.turno-celda-grupo)')).toHaveLength(0);
    expect(grupo?.textContent).toContain('5 turnos');
    grupo?.click();
    const dialogo = document.querySelector('dialog.dialogo');
    expect(dialogo?.querySelectorAll('.turno')).toHaveLength(5);
  });

  it('Los encabezados y la escala quedan visibles al desplazarse', () => {
    const agenda = textoDeModulo('03-agenda.css');
    expect(regla(agenda, '.semana-encabezado')).toMatch(/position:\s*sticky/);
    expect(regla(agenda, '.semana-encabezado')).toMatch(/top:\s*0/);
    expect(regla(agenda, '.semana-escala')).toMatch(/position:\s*sticky/);
    expect(regla(agenda, '.semana-escala')).toMatch(/left:\s*0/);
    // En pantallas chicas la grilla se desplaza dentro de su contenedor, sin comprimir las columnas.
    expect(regla(agenda, '.semana-grilla')).toMatch(/overflow:\s*auto/);
    expect(agenda).toMatch(/minmax\(var\(--semana-columna\),\s*1fr\)/);
  });

  it('Cada estado lleva su color suave, con texto', () => {
    const agenda = textoDeModulo('03-agenda.css');
    for (const estado of ['reservado', 'confirmado', 'sobreturno', 'atendido', 'ausente', 'cancelado']) {
      expect(regla(agenda, `.turno-celda .turno-${estado}`), estado).toMatch(new RegExp(`--ds-turno-${estado}-fondo`));
    }
  });

  it('La vista del día sigue siendo la lista', () => {
    const contenedor = document.createElement('div');
    document.body.append(contenedor);
    dibujarAgenda(contenedor, periodoDe('dia', '2026-12-01'), [turno(1, '2026-12-01', '09:00', '09:30')], CATALOGOS, {
      cancelar: () => undefined,
      reprogramar: () => undefined,
    });

    expect(contenedor.classList.contains('semana')).toBe(false);
    expect(contenedor.querySelectorAll('ul.turnos > li.turno')).toHaveLength(1);
  });
});
