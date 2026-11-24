import { describe, expect, it } from 'vitest';
import type { TurnoPaciente } from '../src/paciente/reserva.ts';
import { estadoVisible } from '../src/paciente/turnos.ts';

/** C-42: estado que se muestra en Mis turnos. Ahora: martes 2026-12-01 a las 08:00 en Buenos Aires. */
const AHORA = new Date('2026-12-01T08:00:00-03:00');

function turno(estado: string, inicio: string, fin: string): TurnoPaciente {
  return {
    numeroReserva: 'R-0001',
    inicio,
    fin,
    estado,
    prestacion: { id: 1, nombre: 'Consulta' },
    profesional: { id: 1, nombre: 'Ana Ejemplo' },
    modificableOnline: false,
  };
}

describe('estadoVisible', () => {
  it.each(['RESERVADO', 'CONFIRMADO'])('un turno %s que ya terminó se muestra Sin registrar', (estado) => {
    const visible = estadoVisible(turno(estado, '2026-11-28T10:00:00-03:00', '2026-11-28T10:30:00-03:00'), AHORA);

    expect(visible).toEqual({ texto: 'Sin registrar', clase: 'e-sin-registrar', sinRegistrar: true });
  });

  it('un turno que termina justo ahora ya está sin registrar', () => {
    const visible = estadoVisible(turno('RESERVADO', '2026-12-01T07:30:00-03:00', '2026-12-01T08:00:00-03:00'), AHORA);

    expect(visible.texto).toBe('Sin registrar');
  });

  it('un turno en curso conserva su estado', () => {
    const visible = estadoVisible(turno('CONFIRMADO', '2026-12-01T07:45:00-03:00', '2026-12-01T08:15:00-03:00'), AHORA);

    expect(visible).toEqual({ texto: 'Confirmado', clase: 'e-confirmado', sinRegistrar: false });
  });

  it('un turno futuro conserva su estado', () => {
    const visible = estadoVisible(turno('RESERVADO', '2026-12-03T10:00:00-03:00', '2026-12-03T10:30:00-03:00'), AHORA);

    expect(visible.texto).toBe('Reservado');
  });

  it.each([
    ['ATENDIDO', 'Atendido'],
    ['AUSENTE', 'Ausente'],
    ['CANCELADO', 'Cancelado'],
  ])('un turno %s pasado se muestra como %s', (estado, texto) => {
    const visible = estadoVisible(turno(estado, '2026-11-25T10:00:00-03:00', '2026-11-25T10:30:00-03:00'), AHORA);

    expect(visible.texto).toBe(texto);
    expect(visible.sinRegistrar).toBe(false);
  });
});
