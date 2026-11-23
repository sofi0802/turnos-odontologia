import { afterEach, describe, expect, it, vi } from 'vitest';
import { crearSelectorHora } from '../src/configuracion/ui.ts';
import { boton, escribir, json, texto } from './apoyo.ts';
import {
  abrirConfiguracion,
  dialogoAbierto,
  elegirPestana,
  opcionesDe,
  SESION_ADMIN,
  SESION_RECEPCION_CONFIG,
  selectoresDeHora,
  servidorCon,
} from './apoyo-configuracion.ts';

const CINCO_EN_CINCO = ['00', '05', '10', '15', '20', '25', '30', '35', '40', '45', '50', '55'];
const HORAS = Array.from({ length: 24 }, (_, hora) => String(hora).padStart(2, '0'));

afterEach(() => {
  document.querySelectorAll('dialog').forEach((d) => d.remove());
});

describe('Selector de hora (design D13)', () => {
  it('ofrece las horas de 00 a 23 y los minutos de 5 en 5', () => {
    const selector = crearSelectorHora('desde', 'Desde');

    expect(opcionesDe(selector.hora)).toEqual(HORAS);
    expect(opcionesDe(selector.minutos)).toEqual(CINCO_EN_CINCO);
  });

  it('devuelve "HH:MM" con el valor inicial o con lo que se elige', () => {
    const selector = crearSelectorHora('desde', 'Desde', { valor: '09:45' });
    expect(selector.valor()).toBe('09:45');

    selector.hora.value = '14';
    selector.minutos.value = '05';
    expect(selector.valor()).toBe('14:05');
  });

  it('muestra tal cual un minuto que no es múltiplo de 5, sin redondearlo', () => {
    const selector = crearSelectorHora('desde', 'Desde', { valor: '09:07' });

    expect(opcionesDe(selector.minutos)).toEqual(['00', '05', '07', '10', ...CINCO_EN_CINCO.slice(3)]);
    expect(selector.valor()).toBe('09:07');
    // Un valor múltiplo de 5 no suma opciones.
    expect(opcionesDe(crearSelectorHora('desde', 'Desde', { valor: '09:10' }).minutos)).toEqual(CINCO_EN_CINCO);
  });

  it('el selector opcional empieza sin hora y devuelve texto vacío hasta que se elige', () => {
    const selector = crearSelectorHora('inicio', 'Otra hora (opcional)', { opcional: true });

    expect(opcionesDe(selector.hora)).toEqual(['--', ...HORAS]);
    expect(selector.valor()).toBe('');
    selector.hora.value = '11';
    expect(selector.valor()).toBe('11:00');
  });

  it('marca el error en la hora, con aria-invalid y su mensaje', () => {
    const selector = crearSelectorHora('desde', 'Desde');
    document.body.replaceChildren(selector.contenedor);

    selector.mostrarError('Mensaje');

    expect(selector.hora.getAttribute('aria-invalid')).toBe('true');
    expect(document.getElementById(selector.hora.getAttribute('aria-describedby') ?? '')?.textContent).toBe('Mensaje');
    selector.mostrarError('');
    expect(selector.hora.hasAttribute('aria-invalid')).toBe(false);
  });
});

describe('Horas de 5 en 5', () => {
  it('Los minutos avanzan de 5 en 5', async () => {
    const servidor = servidorCon(SESION_ADMIN);
    servidor.responder('GET /profesionales/{id}/horarios', () => json(200, { profesionalId: 1, franjas: [] }));
    const { raiz } = await abrirConfiguracion(servidor);
    const panel = elegirPestana(raiz, 'Horarios');
    await vi.waitFor(() => expect(panel.querySelector('.franjas')).not.toBeNull());

    const { hora, minutos } = selectoresDeHora(panel, 'Desde');

    expect(opcionesDe(minutos)).toEqual(CINCO_EN_CINCO);
    expect(opcionesDe(hora)).toEqual(HORAS);
    expect(opcionesDe(selectoresDeHora(panel, 'Hasta').minutos)).toEqual(CINCO_EN_CINCO);
  });

  it('Minutos del inicio de una ausencia', async () => {
    const servidor = servidorCon(SESION_RECEPCION_CONFIG);
    servidor.responder('GET /profesionales/{id}/ausencias', () => json(200, []));
    const { raiz } = await abrirConfiguracion(servidor);
    const panel = elegirPestana(raiz, 'Ausencias');
    await vi.waitFor(() => expect(texto(panel)).toContain('No hay ausencias registradas.'));

    boton(panel, 'Registrar ausencia').click();
    const dialogo = await dialogoAbierto();

    expect(opcionesDe(selectoresDeHora(dialogo, 'Hora de inicio').minutos)).toEqual(CINCO_EN_CINCO);
    expect(opcionesDe(selectoresDeHora(dialogo, 'Hora de fin').minutos)).toEqual(CINCO_EN_CINCO);
  });

  it('El inicio opcional de una reasignación también va de 5 en 5', async () => {
    const servidor = servidorCon(SESION_RECEPCION_CONFIG);
    servidor.responder('GET /profesionales/{id}/ausencias', () =>
      json(200, [
        { id: 4, profesionalId: 1, tipo: 'AUSENCIA', inicio: '2026-12-07T08:00:00-03:00', fin: '2026-12-07T13:00:00-03:00', motivo: null },
      ]),
    );
    servidor.responder('GET /turnos', () =>
      json(200, [
        {
          id: 10,
          profesionalId: 1,
          prestacionId: 1,
          inicio: '2026-12-07T09:00:00-03:00',
          fin: '2026-12-07T09:30:00-03:00',
          estado: 'RESERVADO',
        },
      ]),
    );
    servidor.responder('GET /profesionales/{id}/prestaciones', () =>
      json(200, [{ id: 1, nombre: 'Consulta o control', duracionMinutos: 30, activo: true }]),
    );
    const { raiz } = await abrirConfiguracion(servidor);
    const panel = elegirPestana(raiz, 'Ausencias');
    const ver = await vi.waitFor(() => boton(panel, 'Ver turnos afectados'));
    ver.click();
    const reasignar = await vi.waitFor(() => boton(panel, 'Reasignar'));

    reasignar.click();
    const dialogo = await dialogoAbierto();

    const { hora, minutos } = selectoresDeHora(dialogo, 'Otra hora (opcional)');
    expect(opcionesDe(minutos)).toEqual(CINCO_EN_CINCO);
    expect(opcionesDe(hora)[0]).toBe('--');
    escribir(hora, '');
    expect(hora.value).toBe('');
  });
});
