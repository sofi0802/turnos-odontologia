# Proposal

## Why

Nadie registra si el paciente vino: los turnos pasados quedan como `RESERVADO` o `CONFIRMADO` para siempre, la agenda no lo distingue y el paciente lo ve en "Mis turnos". Hoy solo el odontólogo puede marcar la ausencia, desde la ficha clínica, que no tiene pantalla, y nada pasa un turno a `ATENDIDO`. La líder cambió RN-AG-17 y sumó RN-AG-20 el 2026-11-24 para que la recepción, que es quien ve llegar al paciente, registre la asistencia desde la agenda.

## What Changes

- **Llegada** (RN-AG-20): la recepción registra en la agenda que el paciente llegó, el día del turno y aunque llegue tarde. El turno **sigue activo** y sigue ocupando profesional y sillón. Cuando termina su margen de limpieza, un proceso programado lo pasa a **`ATENDIDO`**. Un turno que terminó sin registro se puede corregir después registrando la llegada.
- **Ausencia** (RN-AG-17 cambiada): la marcan la recepción desde la agenda o el odontólogo del turno desde la ficha, recién **15 minutos después del inicio** (tolerancia fija) y solo si el turno no tiene la llegada registrada. Nunca la marca el sistema. **BREAKING** para la API de la ficha: antes de los 15 minutos el rechazo es `AUSENCIA_ANTES_DE_TOLERANCIA` (antes, `TURNO_NO_INICIADO` solo antes del inicio).
- **Deshacer la ausencia**: solo la recepción, mientras el turno no terminó y si su horario sigue libre. El turno vuelve a `RESERVADO` o `CONFIRMADO`, según estaba. Si el horario ya se ocupó, se rechaza con el código de superposición y la recepción le da otro turno. La ficha muestra la ausencia deshecha.
- **Agenda**: `GET /api/v1/turnos` devuelve también los turnos `ATENDIDO` y `AUSENTE` (siguen sin aparecer los `CANCELADO`) y la hora de llegada. La agenda del personal los muestra con su etiqueta, y los activos que ya terminaron sin registro como "Sin registrar".
- **Auditoría**: acciones nuevas `LLEGADA`, `DESHACER_AUSENCIA` y `ATENDIDO` (actor `sistema`).
- El administrador no registra llegada ni ausencia ni las deshace.
- **Migración V22**: columna `turno.llegada_en`. Las restricciones `EXCLUDE` no cambian (DD-03, regla dura 5).

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `agenda-turnos`: quién y cuándo marca la ausencia (tolerancia, llegada registrada), llegada del paciente, deshacer la ausencia y paso automático a `ATENDIDO`.
- `agenda-api`: endpoints de llegada, ausencia y deshacer para la recepción; la consulta de la agenda incluye `ATENDIDO` y `AUSENTE` con la hora de llegada.
- `auditoria-turnos`: registro de la llegada, del deshacer y del paso a `ATENDIDO`.
- `ficha-clinica`: la ficha muestra las ausencias deshechas.
- `frontend-agenda-personal`: botones "Llegó", "Ausente" y "Deshacer ausencia" para la recepción, y etiquetas de asistencia para todo el personal.

## Impact

- Backend `agenda/` (dueña Sofia): dominio `Turno`, casos de uso nuevos, `TurnosController`, consulta de la agenda, tarea programada y auditoría. Migración V22 (reservada en `CHANGES.md`).
- Backend `clinica/` (dueño Emanuel; se le avisa en el PR): la tolerancia de 15 minutos en la ausencia desde la ficha y las ausencias deshechas en la ficha.
- Frontend `frontend/src/agenda/` (dueño Enzo; se le avisa en el PR). Cambia lo que se ve: la política `system-and-mockups` exige mockups aprobados antes del apply.
- Tests: integración con PostgreSQL real (`@Tag("integration")`) para cada regla y permiso; unitarios del dominio y del frontend. Los escenarios de C-06, C-11, C-13 y C-35 siguen pasando, salvo los dos de ausencia que esta regla cambia (ver los deltas).
- Fuera de alcance: una pantalla para la ficha clínica (C-13 fue solo backend). "Mis turnos" del paciente se resuelve en C-42.
