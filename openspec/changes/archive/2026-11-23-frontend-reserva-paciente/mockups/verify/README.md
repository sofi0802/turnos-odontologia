# Verificación visual de C-12 (tarea 7.2)

- Fecha: 2026-11-23.
- Entorno: backend local (perfil dev, seed de C-04 y C-07) y `npm run dev` con `VITE_API_BASE_URL=http://localhost:8080/api/v1`.
- Herramienta: Playwright, a 390 × 844 y 1440 × 900, con capturas de página completa.
- Design system: Revision 1.

## Recorrido

Hecho con un paciente ficticio creado desde la pantalla de registro (`paciente.prueba…@example.com`, DNI `98…`):

1. Reserva "Consulta o control" con "Sin preferencia" el jueves 26 de noviembre a las 08:30.
   - Sin sesión, "Confirmar reserva" lleva a `#/mi-cuenta` con el aviso.
   - El registro inicia la sesión y vuelve al resumen, sin confirmar solo.
2. Confirmación: "¡Turno reservado!" con el número de reserva, asignado a Ana Ejemplo.
3. "Mis turnos": el turno figura en Próximos con "Cambiar horario" y "Cancelar turno".
4. Cambio a las 10:15: "Cambiamos tu turno al jueves 26 de noviembre a las 10:15. Tu nuevo número de reserva es …".
5. Cancelación con el diálogo: "Tu turno se canceló." y el turno pasa a Anteriores como Cancelado.
6. `/confirmar/token-inventado-123`:
   - La dirección pasa a `/#/confirmar`.
   - El pedido sale solo al tocar el botón.
   - Muestra "El link no es válido. Revisá que esté completo."
7. `/mis-turnos/cancelar` pasa a `/#/mis-turnos`.

En el recorrido:

- **Desplazamiento horizontal:** ninguna pantalla tiene, en los dos anchos (`scrollWidth <= innerWidth`).
- **Altura de los horarios:** cada botón de horario mide 44 px.
- **Errores en la consola:** el único es el `404` de la API ante el token inventado, que es la respuesta esperada.

## Ajuste del menú (revisión 2 de los mockups, 2026-11-23)

Sin sesión, el menú muestra solo "Reservar turno" y el encabezado muestra "Ingresar". "Mis turnos" aparece al iniciar la sesión de paciente.

- **Capturas nuevas:** se volvieron a tomar `reserva-paso-1`, `mi-cuenta` y `confirmar-invalido`, sin sesión y con el menú abierto en el celular.
- **Resultado:** el menú tiene solo "Reservar turno" y se ve "Ingresar", en los dos anchos, sin desplazamiento horizontal ni errores en la consola.
- **Mockups:** los mockups sin sesión se actualizaron igual (ver `previews/`) y esperan la aprobación de la líder.
- **Capturas sin actualizar:** las de `reserva-paso-2`, `reserva-paso-3` y `mi-cuenta-registro` en esta carpeta son de la primera pasada y todavía muestran "Mis turnos" sin sesión. Se renuevan en la próxima verificación.

## Capturas

| Pantalla | Mockup aprobado | Capturas |
|---|---|---|
| Reserva, paso 1 | `reserva-paso-1.html` | `reserva-paso-1-390.png`, `reserva-paso-1-1440.png` |
| Reserva, paso 2 | `reserva-paso-2.html` | `reserva-paso-2-390.png`, `reserva-paso-2-1440.png` |
| Reserva, paso 3 | `reserva-paso-3.html` | `reserva-paso-3-390.png`, `reserva-paso-3-1440.png` |
| Reserva confirmada | `reserva-confirmada.html` | `reserva-confirmada-390.png`, `reserva-confirmada-1440.png` |
| Mi cuenta (ingresar) | `mi-cuenta.html` | `mi-cuenta-390.png`, `mi-cuenta-1440.png` |
| Mi cuenta (crear cuenta) | `mi-cuenta-registro.html` | `mi-cuenta-registro-390.png`, `mi-cuenta-registro-1440.png` |
| Mis turnos | `mis-turnos.html` | `mis-turnos-390.png`, `mis-turnos-1440.png` |
| Cambiar horario | `mis-turnos-cambiar.html` | `mis-turnos-cambiar-390.png`, `mis-turnos-cambiar-1440.png` |
| Cancelar (diálogo) | `mis-turnos-cancelar.html` | `mis-turnos-cancelar-390.png`, `mis-turnos-cancelar-1440.png` |
| Confirmar asistencia | `confirmar-asistencia.html` | `confirmar-390.png`, `confirmar-1440.png`, `confirmar-invalido-390.png`, `confirmar-invalido-1440.png` |

No revisadas con captura: `reserva-horario-ocupado.html` y `estados.html`. Sus estados (alternativas, día sin horarios, rechazos) los cubren los tests de `test/reserva-paciente.test.ts` y `test/turnos-paciente.test.ts`.

## Diferencias con los mockups

No hay blockers ni majors. Los pasos, los textos, las opciones, los horarios, el comprobante, las tarjetas, los estados y el diálogo coinciden con los mockups.

Las diferencias son todas minor:

1. **Encabezado** (todas las pantallas, los dos anchos):
   - Es el de C-09 (`.encabezado`), sin el logo provisorio, y el menú queda detrás del botón "Menú" en el celular.
   - El mockup usa `.app-encabezado` con pestañas siempre visibles.
   - No se cambió para no tocar el encabezado de la agenda del personal (non-goal del design).
   - Se puede alinear en un change propio del encabezado.
2. **Fondo de la página:** es blanco (`--ds-fondo`) en lugar de `--ds-superficie`. Las tarjetas se distinguen igual por su borde y su sombra.
3. **Tamaño de los títulos:** el `h1` y el `h2` de los pasos usan el tamaño por defecto del navegador, un poco más grande que `--ds-texto-xl` y `--ds-texto-lg` del mockup.

## Sin verificar acá

No se capturó la agenda del personal después de importar `tokens.css`. Los alias tienen los mismos valores que el `:root` anterior y los tests de C-09 siguen pasando, pero falta la comparación visual.
