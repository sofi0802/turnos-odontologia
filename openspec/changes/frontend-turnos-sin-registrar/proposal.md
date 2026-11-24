# Proposal

## Why

En "Mis turnos", un turno que ya pasó y que nadie marcó como atendido o ausente se muestra en "Turnos anteriores" como "Reservado" o "Confirmado", y queda así para siempre. Para la paciente es confuso: parece un turno pendiente. Pasa porque RN-AG-17 deja la ausencia en manos del odontólogo, desde la ficha clínica, y hoy ninguna pantalla permite marcar un turno como atendido o ausente. Lo detectó Juan Cruz al revisar C-39 (2026-11-23).

## What Changes

- En `#/mis-turnos`, un turno `RESERVADO` o `CONFIRMADO` cuyo horario ya terminó se muestra en "Turnos anteriores" con la etiqueta **"Sin registrar"**, en lugar de su estado, y con el texto "El consultorio todavía no registró la asistencia.".
- La etiqueta la calcula el frontend con la hora de fin del turno. No es un estado: el backend, la API, la base y la agenda del personal no cambian.
- Un turno que empezó y todavía no terminó conserva su estado ("Reservado" o "Confirmado").
- Los turnos `ATENDIDO`, `AUSENTE` y `CANCELADO` se muestran igual que hoy.
- Fuera de alcance, decidido por la líder el 2026-11-23: un estado `VENCIDO` en el backend o el paso automático a `AUSENTE`, que contradice RN-AG-17. El registro de la asistencia por el personal ("Llegó", "Ausente" y "Deshacer ausencia") es C-43 `registro-asistencia`; cuando exista, la mayoría de los turnos pasados va a mostrar "Atendido" o "Ausente" y "Sin registrar" queda para los que nadie registró.
- La etiqueta se llamaba "Vencido" en la primera versión de esta propuesta. La líder la cambió por "Sin registrar" (2026-11-23) porque a una paciente que sí fue le sugería que había faltado.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `frontend-turnos-paciente`: la lista de "Mis turnos" muestra "Sin registrar" para los turnos activos cuyo horario ya terminó.

## Impact

- Frontend: `frontend/src/paciente/turnos.ts` y `frontend/src/pages/mis-turnos.ts`, más una clase de estado en los estilos (dueño Enzo; se le avisa en el PR).
- Sin cambios en `backend/`, sin migración y sin tokens nuevos del design system: "Sin registrar" reusa los colores neutros de "Cancelado".
- Tests: escenarios nuevos en `frontend/test/turnos-paciente.test.ts`; los de C-12 siguen pasando.
- Mockup: cambia lo que ve la paciente, así que la política `system-and-mockups` exige un mockup aprobado antes del apply.
- Coordinación: se apoya en las pantallas del paciente de la etapa C de C-39, ya mergeada. C-43 cambia la agenda del personal, no Mis turnos.
