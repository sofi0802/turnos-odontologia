import { describe, expect, it, vi } from 'vitest';
import { normalizarNumeroReserva } from '../src/agenda/numero-reserva.ts';
import { crearBuscadorHorarios } from '../src/agenda/reprogramacion.ts';
import { json, ServidorFalso } from './apoyo.ts';

describe('normalizarNumeroReserva', () => {
  it.each([
    ['abcd2345', 'ABCD-2345'],
    [' abcd2345 ', 'ABCD-2345'],
    ['abcd-2345', 'ABCD-2345'],
    ['ABCD 2345', 'ABCD-2345'],
    ['a b c d 2 3 4 5', 'ABCD-2345'],
    ['--abcd--2345--', 'ABCD-2345'],
  ])('"%s" se envía como %s', (ingresado, esperado) => {
    expect(normalizarNumeroReserva(ingresado)).toBe(esperado);
  });

  it.each([['ABC-2345'], ['ABCD-23456'], ['R-0001'], ['']])('"%s" no tiene 8 símbolos y queda como se ingresó', (ingresado) => {
    expect(normalizarNumeroReserva(ingresado)).toBe(ingresado);
  });

  it('no inventa símbolos: solo letras y dígitos cuentan', () => {
    expect(normalizarNumeroReserva('abcd/2345')).toBe('abcd/2345');
    expect(normalizarNumeroReserva('abc/2345')).toBe('abc/2345');
  });
});

const H11 = { inicio: '2026-12-01T11:00:00-03:00', fin: '2026-12-01T11:30:00-03:00' };
const H1130 = { inicio: '2026-12-01T11:30:00-03:00', fin: '2026-12-01T12:00:00-03:00' };

function diferido(): { promesa: Promise<Response>; resolver: (r: Response) => void } {
  let resolver: (r: Response) => void = () => undefined;
  const promesa = new Promise<Response>((alResolver) => {
    resolver = alResolver;
  });
  return { promesa, resolver };
}

function preparar(): { servidor: ServidorFalso; buscador: ReturnType<typeof crearBuscadorHorarios> } {
  const servidor = new ServidorFalso();
  return { servidor, buscador: crearBuscadorHorarios(servidor.api, 2, () => undefined) };
}

describe('Estado de los horarios al reprogramar', () => {
  it('pide los horarios del profesional elegido y no elige ninguno', async () => {
    const { servidor, buscador } = preparar();
    servidor.responder('GET /public/disponibilidad', () => json(200, [H11, H1130]));

    buscador.buscar('2026-12-01', 1);
    expect(buscador.estado()).toEqual({ fase: 'cargando' });
    await vi.waitFor(() => expect(buscador.estado().fase).toBe('listo'));

    expect(buscador.estado()).toEqual({ fase: 'listo', horarios: [H11, H1130] });
    expect(buscador.elegido()).toBeNull();
    const consulta = servidor.ultimo('GET', '/public/disponibilidad').query;
    expect(consulta.get('prestacionId')).toBe('2');
    expect(consulta.get('fecha')).toBe('2026-12-01');
    expect(consulta.get('profesionalId')).toBe('1');
  });

  it('buscar de nuevo descarta el horario elegido y repite la consulta', async () => {
    const { servidor, buscador } = preparar();
    servidor.responder('GET /public/disponibilidad', () => json(200, [H11, H1130]));
    buscador.buscar('2026-12-01', 1);
    await vi.waitFor(() => expect(buscador.estado().fase).toBe('listo'));
    buscador.elegir(H11);
    expect(buscador.elegido()).toEqual(H11);

    buscador.buscar('2026-12-01', 2);
    expect(buscador.elegido()).toBeNull();
    await vi.waitFor(() => expect(buscador.estado().fase).toBe('listo'));
    buscador.elegir(H1130);
    buscador.buscar('2026-12-02', 2);
    expect(buscador.elegido()).toBeNull();
    await vi.waitFor(() => expect(servidor.a('GET', '/public/disponibilidad')).toHaveLength(3));

    const ultima = servidor.ultimo('GET', '/public/disponibilidad').query;
    expect(ultima.get('fecha')).toBe('2026-12-02');
    expect(ultima.get('profesionalId')).toBe('2');
  });

  it('un día sin horarios queda como lista vacía', async () => {
    const { servidor, buscador } = preparar();
    servidor.responder('GET /public/disponibilidad', () => json(200, []));

    buscador.buscar('2026-12-01', 1);
    await vi.waitFor(() => expect(buscador.estado().fase).toBe('listo'));

    expect(buscador.estado()).toEqual({ fase: 'listo', horarios: [] });
  });

  it('una consulta que falla se puede reintentar con otra fecha', async () => {
    const { servidor, buscador } = preparar();
    servidor.responder('GET /public/disponibilidad', () => json(500));
    buscador.buscar('2026-12-01', 1);
    await vi.waitFor(() => expect(buscador.estado().fase).toBe('error'));

    servidor.responder('GET /public/disponibilidad', () => json(200, [H11]));
    buscador.buscar('2026-12-02', 1);
    await vi.waitFor(() => expect(buscador.estado().fase).toBe('listo'));
    expect(buscador.estado()).toEqual({ fase: 'listo', horarios: [H11] });
  });

  it('una respuesta tardía de una consulta anterior se descarta', async () => {
    const { servidor, buscador } = preparar();
    const lenta = diferido();
    // la primera consulta (profesional 1) queda pendiente; la segunda (profesional 2) responde antes
    servidor.responder('GET /public/disponibilidad', (pedido) =>
      pedido.query.get('profesionalId') === '1' ? lenta.promesa : json(200, [H1130]),
    );
    buscador.buscar('2026-12-01', 1);
    buscador.buscar('2026-12-01', 2);
    await vi.waitFor(() => expect(buscador.estado()).toEqual({ fase: 'listo', horarios: [H1130] }));

    lenta.resolver(json(200, [H11]));
    await new Promise((resolver) => setTimeout(resolver, 20));

    expect(buscador.estado()).toEqual({ fase: 'listo', horarios: [H1130] });
  });

  it('una respuesta tardía con error de una consulta anterior también se descarta', async () => {
    const { servidor, buscador } = preparar();
    const lenta = diferido();
    servidor.responder('GET /public/disponibilidad', (pedido) =>
      pedido.query.get('fecha') === '2026-12-01' ? lenta.promesa : json(200, [H11]),
    );
    buscador.buscar('2026-12-01', 1);
    buscador.buscar('2026-12-02', 1);
    await vi.waitFor(() => expect(buscador.estado().fase).toBe('listo'));

    lenta.resolver(json(500));
    await new Promise((resolver) => setTimeout(resolver, 20));

    expect(buscador.estado()).toEqual({ fase: 'listo', horarios: [H11] });
  });

  it('sin fecha no consulta y vuelve al estado inicial', async () => {
    const { servidor, buscador } = preparar();
    buscador.buscar('', 1);
    expect(buscador.estado()).toEqual({ fase: 'sin-consulta' });
    expect(servidor.a('GET', '/public/disponibilidad')).toHaveLength(0);
  });
});
