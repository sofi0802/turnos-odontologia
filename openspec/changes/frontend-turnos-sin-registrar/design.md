# Design

## Context

El motivo está en proposal.md (Why) y el requisito, en el delta de `specs/frontend-turnos-paciente`.

- `separarTurnos` (`frontend/src/paciente/turnos.ts`) manda a "Próximos turnos" los `RESERVADO` o `CONFIRMADO` cuyo `inicio` todavía no llegó, y todo el resto a "Turnos anteriores".
- `tarjeta` (`frontend/src/pages/mis-turnos.ts`) dibuja el estado con el mapa `ESTADOS` (texto y clase `.e-*`) y no ofrece acciones en los turnos anteriores.
- `TurnoPaciente` ya trae `fin`, así que no hace falta pedir nada más a la API.
- En el backend solo `MarcarAusencia` (C-13) cambia un turno pasado, y ninguna pantalla del frontend lo usa. Ningún caso de uso pasa un turno a `ATENDIDO`.
- `frontend/` es de Enzo; se le avisa en el PR. Mis turnos ya tiene el estilo de la etapa C de C-39 (mergeada el 2026-11-24).

## Goals / Non-Goals

**Goals:**
- Que la paciente no vea como pendiente un turno que ya terminó.
- Que el cambio sea reversible y no toque datos: si después se registra la asistencia, la etiqueta desaparece sola.

**Non-Goals:**
- La agenda del personal: sigue mostrando el estado real del turno.
- Registrar la asistencia ("Llegó", "Ausente" y "Deshacer ausencia" en la agenda del personal): es C-43 `registro-asistencia`, con backend.
- Cambiar cómo se separan "Próximos" y "Anteriores".

## Decisions

1. **"Sin registrar" se calcula con `fin`, no con `inicio`.** Un turno de 10:00 a 10:30 que la paciente mira a las 10:10 está en curso; decirle "Sin registrar" sería falso. Por eso el turno en curso conserva su estado.
   - *Alternativa*: usar `inicio`, como `separarTurnos`. Se descartó por el caso en curso.

2. **Una función pura decide el estado que se muestra.** `estadoVisible(turno, ahora)` en `paciente/turnos.ts` devuelve el texto y la clase: "Sin registrar" y `e-sin-registrar` si el turno es activo y `fin <= ahora`, y si no, lo del mapa `ESTADOS`. `tarjeta` la usa en lugar de leer `ESTADOS` directamente. Así se prueba sin DOM y la regla queda en un solo lugar.
   - `ahora` es el mismo `new Date()` con el que `dibujarListas` separa los turnos, para que las dos decisiones usen el mismo momento.

3. **El texto aclaratorio va dentro de la tarjeta.** Debajo de la fecha y la prestación, con la clase de texto secundario que ya usan las tarjetas: "El consultorio todavía no registró la asistencia.". Explica la etiqueta sin culpar a la paciente: no dice "faltaste".

4. **Sin token nuevo.** `.e-sin-registrar` reusa `--ds-turno-cancelado` y `--ds-turno-cancelado-fondo`, que son neutros. El design system no cambia de revisión. Se distingue de "Cancelado" por el texto, que es lo que lee el lector de pantalla.
   - *Alternativa*: los tokens ámbar del sobreturno. Se descartaron porque ese color en la agenda significa otra cosa.

5. **Sin pedidos nuevos.** La etiqueta no dispara ningún pedido a la API.

## Risks / Trade-offs

- [La agenda del personal y Mis turnos muestran estados distintos para el mismo turno] → Es intencional y queda escrito en Non-Goals. Se resuelve de verdad cuando exista el registro de asistencia.
- [La hora del navegador está mal] → La etiqueta podría salir antes o después. Es solo visual y no cambia datos.
- [Mientras C-43 no esté, casi todos los turnos pasados van a decir "Sin registrar"] → Es lo que pasa hoy con la asistencia; el texto aclaratorio explica que falta el registro del consultorio.

## Migration Plan

No hay migración. Para volver atrás alcanza con revertir el PR.

## UI Mockups

- Design system revision: ver `design-system/README.md` al volver a renderizar
- Decision: yes (2026-11-23, política `system-and-mockups`)
- Mockups: `openspec/changes/frontend-turnos-sin-registrar/mockups/` (ver su README)
- Approval: pending
- Preview: pending (las capturas del 2026-11-23 eran con "Vencido" y el estilo de la etapa B de C-39; se vuelven a hacer sobre la etapa C)
- Visual verify: pending
