# Spec Delta

## Purpose

Mostrar al público los horarios libres de un día para una prestación, con un profesional elegido o "sin preferencia", aplicando las mismas reglas que la agenda del personal y sin exponer datos de otros pacientes ni del consultorio (US-020, US-023, RN-RO-01, RN-RO-02, RN-RO-04, RN-RO-07). En los escenarios, con datos ficticios y salvo que digan otra cosa: la Dra. A (sillón habitual: Sillón 1) y el Dr. B (sillón habitual: Sillón 2) están activos y habilitados para "Consulta" (30 minutos); solo el Dr. B está habilitado para "Tratamiento de conducto" (30 minutos en estos escenarios); el Sillón 1 y el Sillón 2 están activos; el margen es de 10 minutos; la fecha consultada es el martes 2026-12-01 y el momento actual es ese día a las 08:00, en `America/Argentina/Buenos_Aires`. Las franjas de trabajo se indican en cada escenario.

## ADDED Requirements

### Requirement: Horarios libres de un profesional elegido
`GET /api/v1/public/disponibilidad` con `prestacionId`, `fecha` (`AAAA-MM-DD`) y `profesionalId` SHALL responder `200` con los horarios libres de ese profesional ese día, ordenados por inicio, cada uno con `inicio` y `fin` en ISO-8601 con zona horaria. Los inicios candidatos van cada 15 minutos desde el comienzo de cada franja *(paso propuesto, a confirmar)*, y el turno completo debe caber en la franja.

#### Scenario: Profesional con una franja libre
- **GIVEN** la Dra. A trabaja el 2026-12-01 de 10:00 a 11:00 y no tiene turnos
- **WHEN** se pide la disponibilidad de "Consulta" del 2026-12-01 con la Dra. A
- **THEN** la respuesta es `200` con los horarios de inicio 10:00, 10:15 y 10:30
- **AND** el último horario termina a las `2026-12-01T11:00:00-03:00`

#### Scenario: Excluye los horarios que chocan o no respetan el margen
- **GIVEN** la Dra. A trabaja el 2026-12-01 de 10:00 a 12:00
- **AND** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** se pide la disponibilidad de "Consulta" del 2026-12-01 con la Dra. A
- **THEN** la respuesta es `200` con los horarios de inicio 10:45, 11:00, 11:15 y 11:30

#### Scenario: Un turno cancelado no ocupa el horario
- **GIVEN** la Dra. A trabaja el 2026-12-01 de 10:00 a 11:00
- **AND** un turno `CANCELADO` de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** se pide la disponibilidad de "Consulta" del 2026-12-01 con la Dra. A
- **THEN** la respuesta es `200` con los horarios de inicio 10:00, 10:15 y 10:30

#### Scenario: Día sin franja de trabajo
- **GIVEN** la Dra. A no tiene franjas los martes
- **WHEN** se pide la disponibilidad de "Consulta" del 2026-12-01 con la Dra. A
- **THEN** la respuesta es `200` con la lista de horarios vacía

### Requirement: Unión de horarios sin preferencia
Sin `profesionalId`, el sistema SHALL responder la unión de los horarios libres de los profesionales activos y habilitados para la prestación que trabajan ese día, ordenada por inicio y con cada horario una sola vez. Un profesional no habilitado para la prestación o inactivo MUST NOT aportar horarios.

#### Scenario: Unión sin horarios repetidos
- **GIVEN** la Dra. A trabaja el 2026-12-01 de 10:00 a 11:00 y el Dr. B de 10:30 a 11:30, sin turnos
- **WHEN** se pide la disponibilidad de "Consulta" del 2026-12-01 sin profesional
- **THEN** la respuesta es `200` con los horarios de inicio 10:00, 10:15, 10:30, 10:45 y 11:00
- **AND** el horario de las 10:30 aparece una sola vez

#### Scenario: Profesional no habilitado excluido
- **GIVEN** la Dra. A trabaja el 2026-12-01 de 10:00 a 11:00 y el Dr. B de 11:00 a 12:00, sin turnos
- **WHEN** se pide la disponibilidad de "Tratamiento de conducto" del 2026-12-01 sin profesional
- **THEN** la respuesta es `200` con los horarios de inicio 11:00, 11:15 y 11:30
- **AND** no aparece ningún horario anterior a las 11:00

#### Scenario: Profesional inactivo excluido
- **GIVEN** la Dra. A trabaja el 2026-12-01 de 10:00 a 11:00 y está inactiva
- **AND** el Dr. B trabaja el 2026-12-01 de 11:00 a 12:00, sin turnos
- **WHEN** se pide la disponibilidad de "Consulta" del 2026-12-01 sin profesional
- **THEN** la respuesta es `200` con los horarios de inicio 11:00, 11:15 y 11:30

### Requirement: Un horario necesita un sillón libre
Un horario SHALL mostrarse solo si existe al menos un sillón activo libre con margen para ese profesional en ese intervalo (RN-RO-07). Un profesional libre sin sillón disponible MUST NOT aportar el horario.

#### Scenario: Profesional libre pero sin sillón
- **GIVEN** solo el Sillón 1 está activo
- **AND** la Dra. A trabaja el 2026-12-01 de 10:00 a 11:30, sin turnos
- **AND** un turno activo de 10:00 a 10:30 con el Dr. B en el Sillón 1
- **WHEN** se pide la disponibilidad de "Consulta" del 2026-12-01 con la Dra. A
- **THEN** la respuesta es `200` con los horarios de inicio 10:45 y 11:00

### Requirement: Ausencias, bloqueos y horarios pasados
La disponibilidad MUST excluir los horarios que se cruzan con una ausencia, licencia o bloqueo del profesional, y los que empiezan antes del momento actual. Un horario que termina justo cuando empieza un bloqueo SHALL mostrarse. Una fecha pasada responde la lista vacía.

#### Scenario: Bloqueo de agenda
- **GIVEN** la Dra. A trabaja el 2026-12-01 de 10:00 a 11:00
- **AND** un bloqueo de su agenda de 10:00 a 10:30
- **WHEN** se pide la disponibilidad de "Consulta" del 2026-12-01 con la Dra. A
- **THEN** la respuesta es `200` con un solo horario, de inicio 10:30

#### Scenario: Licencia del profesional
- **GIVEN** la Dra. A trabaja el 2026-12-01 de 10:00 a 11:00
- **AND** una licencia de la Dra. A que cubre todo el 2026-12-01
- **WHEN** se pide la disponibilidad de "Consulta" del 2026-12-01 con la Dra. A
- **THEN** la respuesta es `200` con la lista de horarios vacía
- **AND** la respuesta no informa la licencia ni su motivo

#### Scenario: Horarios anteriores al momento actual
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:20
- **AND** la Dra. A trabaja el 2026-12-01 de 10:00 a 11:00, sin turnos
- **WHEN** se pide la disponibilidad de "Consulta" del 2026-12-01 con la Dra. A
- **THEN** la respuesta es `200` con un solo horario, de inicio 10:30

#### Scenario: Fecha pasada
- **GIVEN** la Dra. A trabaja los lunes de 10:00 a 11:00
- **WHEN** se pide la disponibilidad de "Consulta" del lunes 2026-11-30 con la Dra. A
- **THEN** la respuesta es `200` con la lista de horarios vacía

### Requirement: Disponibilidad pública sin datos de terceros
La disponibilidad SHALL responder sin sesión. Cada horario MUST contener solo `inicio` y `fin`: la respuesta MUST NOT incluir pacientes, números de reserva, profesionales, sillones ni cantidades de turnos.

#### Scenario: Consulta sin sesión con la agenda ocupada
- **GIVEN** un navegador sin sesión
- **AND** la Dra. A trabaja el 2026-12-01 de 10:00 a 12:00
- **AND** un turno activo de "Paciente Dos" (DNI `99000002`) de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** pide la disponibilidad de "Consulta" del 2026-12-01 sin profesional
- **THEN** la respuesta es `200`
- **AND** el cuerpo no contiene "Paciente Dos", `99000002`, el número de reserva de ese turno ni ids de profesionales o sillones

### Requirement: Validación de la consulta de disponibilidad
El sistema MUST responder `422` con el código `DATOS_INVALIDOS` y la lista de campos con error cuando falta `prestacionId` o `fecha`, cuando `fecha` no tiene el formato `AAAA-MM-DD`, o cuando `prestacionId` o `profesionalId` no corresponden a un registro existente.

#### Scenario: Falta la fecha
- **WHEN** se pide la disponibilidad de "Consulta" sin `fecha`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** la lista de errores incluye el campo `fecha`

#### Scenario: Fecha con formato inválido
- **WHEN** se pide la disponibilidad de "Consulta" con `fecha` igual a `01/12/2026`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** la lista de errores incluye el campo `fecha`

#### Scenario: Prestación inexistente
- **WHEN** se pide la disponibilidad del 2026-12-01 con un `prestacionId` que no existe
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** la lista de errores incluye el campo `prestacionId`

### Requirement: Combinaciones que no se ofrecen
Cuando la prestación está inactiva, o el profesional elegido está inactivo o no está habilitado para la prestación, la disponibilidad SHALL responder `200` con la lista de horarios vacía, sin revelar el motivo.

#### Scenario: Profesional elegido no habilitado
- **GIVEN** la Dra. A trabaja el 2026-12-01 de 10:00 a 11:00, sin turnos
- **WHEN** se pide la disponibilidad de "Tratamiento de conducto" del 2026-12-01 con la Dra. A
- **THEN** la respuesta es `200` con la lista de horarios vacía

#### Scenario: Prestación inactiva
- **GIVEN** la Dra. A trabaja el 2026-12-01 de 10:00 a 11:00, sin turnos
- **AND** la prestación "Consulta" está inactiva
- **WHEN** se pide la disponibilidad de "Consulta" del 2026-12-01 con la Dra. A
- **THEN** la respuesta es `200` con la lista de horarios vacía

### Requirement: Catálogo público para reservar
*(Propuesta, a confirmar)*. `GET /api/v1/public/prestaciones` SHALL responder sin sesión las prestaciones activas con `id`, `nombre` y `duracionMinutos`. `GET /api/v1/public/profesionales?prestacionId` SHALL responder los profesionales activos habilitados para esa prestación con `id` y `nombre`, sin matrícula ni otros datos.

#### Scenario: Prestaciones activas
- **GIVEN** un navegador sin sesión
- **AND** la prestación "Limpieza" está inactiva
- **WHEN** pide `GET /api/v1/public/prestaciones`
- **THEN** la respuesta es `200` e incluye "Consulta" con `duracionMinutos` 30
- **AND** no incluye "Limpieza"

#### Scenario: Profesionales habilitados para una prestación
- **GIVEN** un navegador sin sesión
- **WHEN** pide `GET /api/v1/public/profesionales` con el `prestacionId` de "Tratamiento de conducto"
- **THEN** la respuesta es `200` con un solo profesional, el Dr. B, con su `id` y su `nombre`
- **AND** la respuesta no incluye la matrícula

#### Scenario: Profesionales sin prestación
- **WHEN** se pide `GET /api/v1/public/profesionales` sin `prestacionId`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** la lista de errores incluye el campo `prestacionId`
