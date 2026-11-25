# reserva-online Specification

## Purpose

Permitir que un paciente con sesión reserve un turno online eligiendo un profesional o "sin preferencia", con la asignación automática de profesional y sillón, las mismas reglas y garantías contra solapamientos que la agenda del personal, y rechazos con códigos estables (US-021, US-023, RN-RO-03, RN-RO-05, RN-RO-06, RN-RO-08, RN-AG-12). En los escenarios, con datos ficticios y salvo que digan otra cosa: "Paciente Uno" (DNI `99000001`) y "Paciente Dos" (DNI `99000002`) tienen cuenta de paciente; la Dra. A (sillón habitual: Sillón 1, franja del martes de 08:00 a 13:00) y el Dr. B (sillón habitual: Sillón 2, franja del martes de 10:00 a 18:00) están activos y habilitados para "Consulta" (30 minutos); solo el Dr. B está habilitado para "Tratamiento de conducto" (30 minutos en estos escenarios); el Sillón 1 y el Sillón 2 están activos; el margen es de 10 minutos; los horarios son del martes 2026-12-01 y el momento actual es ese día a las 08:00, en `America/Argentina/Buenos_Aires`. Todo `POST` lleva un token CSRF válido.

## Requirements

### Requirement: Reservar un turno online
`POST /api/v1/paciente/reservas` con `prestacionId`, `inicio` y, opcionalmente, `profesionalId` SHALL crear un turno `RESERVADO` para el paciente de la sesión con las reglas de la agenda, y responder `201` con `numeroReserva`, `inicio`, `fin`, `estado`, la prestación y el profesional asignado (`id` y `nombre`). La respuesta MUST NOT incluir el sillón ni datos de otros pacientes.

#### Scenario: Reserva con profesional elegido
- **GIVEN** "Paciente Uno" con sesión iniciada y una agenda sin turnos
- **WHEN** pide `POST /api/v1/paciente/reservas` con "Consulta", la Dra. A e inicio `2026-12-01T10:00:00-03:00`
- **THEN** la respuesta es `201` con `estado` `RESERVADO`, `fin` `2026-12-01T10:30:00-03:00`, el profesional Dra. A y un `numeroReserva` con el formato `XXXX-XXXX`
- **AND** existe un turno activo de "Paciente Uno" a las 10:00 con la Dra. A

### Requirement: El paciente de la reserva es el de la sesión
La reserva MUST asignarse al paciente vinculado a la sesión, y MUST ignorar cualquier identificador de paciente enviado en el pedido.

#### Scenario: Intento de reservar a nombre de otro paciente
- **GIVEN** "Paciente Uno" con sesión iniciada
- **WHEN** pide `POST /api/v1/paciente/reservas` con "Consulta", la Dra. A, inicio a las 10:00 y un `pacienteId` igual al id de "Paciente Dos"
- **THEN** la respuesta es `201`
- **AND** el turno creado es de "Paciente Uno"
- **AND** "Paciente Dos" no tiene turnos

### Requirement: Asignación del profesional sin preferencia
Sin `profesionalId`, el sistema SHALL asignar, entre los profesionales activos, habilitados para la prestación y libres en ese horario con algún sillón libre, el que tenga menos turnos activos ese día. Si hay empate, MUST elegir uno al azar entre los empatados (RN-RO-05). Los turnos cancelados no cuentan.

#### Scenario: Sin preferencia con dos profesionales libres
- **GIVEN** "Paciente Uno" con sesión iniciada
- **AND** la Dra. A tiene dos turnos activos el 2026-12-01 (08:00 y 09:00) y el Dr. B tiene uno (14:00)
- **WHEN** pide una reserva de "Consulta" a las 11:00 sin profesional
- **THEN** la respuesta es `201` con el profesional Dr. B

#### Scenario: Empate resuelto al azar
- **GIVEN** "Paciente Uno" con sesión iniciada
- **AND** la Dra. A y el Dr. B tienen un turno activo cada uno el 2026-12-01, fuera de las 11:00
- **AND** un sorteo que, entre los empatados ordenados por id, elige el último
- **WHEN** pide una reserva de "Consulta" a las 11:00 sin profesional
- **THEN** la respuesta es `201` con el profesional Dr. B
- **AND** con un sorteo que elige el primero, la misma reserva sobre una agenda igual asigna a la Dra. A

#### Scenario: Profesional no habilitado excluido de la asignación
- **GIVEN** "Paciente Uno" con sesión iniciada
- **AND** la Dra. A no tiene turnos el 2026-12-01 y el Dr. B tiene tres turnos activos ese día, fuera de las 11:00
- **WHEN** pide una reserva de "Tratamiento de conducto" a las 11:00 sin profesional
- **THEN** la respuesta es `201` con el profesional Dr. B

#### Scenario: Solo se asigna un profesional libre
- **GIVEN** "Paciente Uno" con sesión iniciada
- **AND** un turno activo de 11:00 a 11:30 con la Dra. A en el Sillón 1
- **AND** el Dr. B tiene tres turnos activos más ese día, fuera de las 11:00
- **WHEN** pide una reserva de "Consulta" a las 11:00 sin profesional
- **THEN** la respuesta es `201` con el profesional Dr. B

#### Scenario: Los turnos cancelados no cuentan
- **GIVEN** "Paciente Uno" con sesión iniciada
- **AND** la Dra. A tiene tres turnos `CANCELADO` el 2026-12-01 y el Dr. B tiene un turno activo ese día, fuera de las 11:00
- **WHEN** pide una reserva de "Consulta" a las 11:00 sin profesional
- **THEN** la respuesta es `201` con el profesional Dra. A

### Requirement: Asignación del sillón
En toda reserva online el sistema SHALL asignar el sillón: primero el sillón habitual del profesional si está activo y libre con margen; si no, el primer sillón activo libre con margen en orden por id (RN-RO-06). El pedido MUST NOT permitir elegir el sillón.

#### Scenario: Sillón habitual libre
- **GIVEN** "Paciente Uno" con sesión iniciada y una agenda sin turnos
- **WHEN** pide una reserva de "Consulta" con el Dr. B a las 10:00
- **THEN** la respuesta es `201`
- **AND** el turno creado está en el Sillón 2

#### Scenario: Sillón habitual ocupado
- **GIVEN** "Paciente Uno" con sesión iniciada
- **AND** un turno activo de 10:00 a 10:30 con el Dr. B en el Sillón 1
- **WHEN** pide una reserva de "Consulta" con la Dra. A a las 10:00
- **THEN** la respuesta es `201`
- **AND** el turno creado está en el Sillón 2

#### Scenario: Profesional sin sillón habitual
- **GIVEN** "Paciente Uno" con sesión iniciada y una agenda sin turnos
- **AND** el Dr. B no tiene sillón habitual
- **WHEN** pide una reserva de "Consulta" con el Dr. B a las 10:00
- **THEN** la respuesta es `201`
- **AND** el turno creado está en el Sillón 1

#### Scenario: Sillón enviado en el pedido
- **GIVEN** "Paciente Uno" con sesión iniciada y una agenda sin turnos
- **WHEN** pide una reserva de "Consulta" con la Dra. A a las 10:00 y un `sillonId` igual al id del Sillón 2
- **THEN** la respuesta es `201`
- **AND** el turno creado está en el Sillón 1

### Requirement: Horario no disponible
Si el horario pedido no está entre los que ofrecería la disponibilidad pública para esa prestación y ese criterio (ocupado, sin margen, fuera de franja, fuera del paso de la grilla, con ausencia o bloqueo, o sin sillón libre), el sistema MUST responder `409` con el código `SIN_DISPONIBILIDAD`, sin crear el turno ni revelar el motivo, y con la propiedad `alternativas`: hasta 3 horarios libres del mismo día posteriores al pedido *(cantidad propuesta, a confirmar)*.

#### Scenario: Profesional elegido ocupado
- **GIVEN** "Paciente Uno" con sesión iniciada
- **AND** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** pide una reserva de "Consulta" con la Dra. A a las 10:15
- **THEN** la respuesta es `409` con el código `SIN_DISPONIBILIDAD`
- **AND** `alternativas` tiene los horarios de inicio 10:45, 11:00 y 11:15
- **AND** "Paciente Uno" no tiene turnos

#### Scenario: Horario fuera de la franja de trabajo
- **GIVEN** "Paciente Uno" con sesión iniciada
- **WHEN** pide una reserva de "Consulta" con la Dra. A a las 13:00
- **THEN** la respuesta es `409` con el código `SIN_DISPONIBILIDAD`
- **AND** no se crea ningún turno

#### Scenario: Profesional con ausencia
- **GIVEN** "Paciente Uno" con sesión iniciada
- **AND** una ausencia de la Dra. A de 09:00 a 12:00 con un motivo administrativo
- **WHEN** pide una reserva de "Consulta" con la Dra. A a las 10:00
- **THEN** la respuesta es `409` con el código `SIN_DISPONIBILIDAD`
- **AND** el cuerpo no contiene el motivo de la ausencia ni el código `PROFESIONAL_AUSENTE`

#### Scenario: Inicio fuera de la grilla
- **GIVEN** "Paciente Uno" con sesión iniciada y una agenda sin turnos
- **WHEN** pide una reserva de "Consulta" con la Dra. A a las 10:05
- **THEN** la respuesta es `409` con el código `SIN_DISPONIBILIDAD`

#### Scenario: Ningún profesional libre sin preferencia
- **GIVEN** "Paciente Uno" con sesión iniciada
- **AND** turnos activos de 11:00 a 11:30 con la Dra. A en el Sillón 1 y con el Dr. B en el Sillón 2
- **WHEN** pide una reserva de "Consulta" a las 11:00 sin profesional
- **THEN** la respuesta es `409` con el código `SIN_DISPONIBILIDAD`
- **AND** no se crea ningún turno

### Requirement: Reservas simultáneas online
Si dos reservas compiten por el mismo horario, el sistema MUST aceptar como máximo un turno por profesional y por sillón, y la otra reserva MUST probar otra combinación libre de profesional y sillón en ese horario antes de rechazarse con `409 SIN_DISPONIBILIDAD`, nunca con un error `500`. La garantía MUST valer aunque las dos pasen la verificación al mismo tiempo.

#### Scenario: Dos pacientes piden el mismo profesional a la misma hora
- **GIVEN** "Paciente Uno" y "Paciente Dos" con sesión iniciada y una agenda sin turnos
- **WHEN** los dos piden al mismo tiempo una reserva de "Consulta" con la Dra. A a las 10:00
- **THEN** una respuesta es `201`
- **AND** la otra es `409` con el código `SIN_DISPONIBILIDAD`
- **AND** la Dra. A tiene un solo turno activo a las 10:00

#### Scenario: Dos pacientes sin preferencia a la misma hora
- **GIVEN** "Paciente Uno" y "Paciente Dos" con sesión iniciada y una agenda sin turnos
- **WHEN** los dos piden al mismo tiempo una reserva de "Consulta" a las 11:00 sin profesional
- **THEN** las dos respuestas son `201`
- **AND** un turno es con la Dra. A y el otro con el Dr. B, en sillones distintos

### Requirement: Rechazos de negocio de la reserva
El sistema MUST responder `409` con un código estable cuando el inicio es anterior al momento actual (`INICIO_EN_EL_PASADO`), cuando el profesional elegido no está habilitado para la prestación (`PROFESIONAL_NO_HABILITADO`), o cuando la prestación o el profesional elegido están inactivos (`RECURSO_INACTIVO`). En todos los casos MUST NOT crear el turno.

#### Scenario: Reserva en el pasado
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:20
- **AND** "Paciente Uno" con sesión iniciada
- **WHEN** pide una reserva de "Consulta" con la Dra. A a las 10:00
- **THEN** la respuesta es `409` con el código `INICIO_EN_EL_PASADO`

#### Scenario: Profesional elegido no habilitado
- **GIVEN** "Paciente Uno" con sesión iniciada
- **WHEN** pide una reserva de "Tratamiento de conducto" con la Dra. A a las 10:00
- **THEN** la respuesta es `409` con el código `PROFESIONAL_NO_HABILITADO`
- **AND** no se crea ningún turno

#### Scenario: Prestación inactiva
- **GIVEN** "Paciente Uno" con sesión iniciada
- **AND** la prestación "Consulta" está inactiva
- **WHEN** pide una reserva de "Consulta" con la Dra. A a las 10:00
- **THEN** la respuesta es `409` con el código `RECURSO_INACTIVO`

### Requirement: Validación de la reserva
El sistema MUST responder `422` con el código `DATOS_INVALIDOS` y la lista de campos con error, sin crear el turno, cuando falta `prestacionId` o `inicio`, cuando `inicio` no está en ISO-8601 con zona horaria, o cuando `prestacionId` o `profesionalId` no corresponden a un registro existente.

#### Scenario: Falta el inicio
- **GIVEN** "Paciente Uno" con sesión iniciada
- **WHEN** pide una reserva de "Consulta" sin `inicio`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** la lista de errores incluye el campo `inicio`

#### Scenario: Inicio sin zona horaria
- **GIVEN** "Paciente Uno" con sesión iniciada
- **WHEN** pide una reserva de "Consulta" con `inicio` igual a `2026-12-01T10:00:00`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** la lista de errores incluye el campo `inicio`

#### Scenario: Profesional inexistente
- **GIVEN** "Paciente Uno" con sesión iniciada
- **WHEN** pide una reserva de "Consulta" a las 10:00 con un `profesionalId` que no existe
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** la lista de errores incluye el campo `profesionalId`

### Requirement: Solo el paciente con sesión reserva
La reserva MUST exigir una sesión con rol `PACIENTE` y un token CSRF válido: sin sesión responde `401 NO_AUTENTICADO`, con una sesión del personal `403 ACCESO_DENEGADO` y sin token CSRF `403 CSRF_INVALIDO`. En ningún caso se crea el turno.

#### Scenario: Reserva sin sesión
- **GIVEN** un navegador sin sesión
- **WHEN** pide una reserva de "Consulta" con la Dra. A a las 10:00
- **THEN** la respuesta es `401` con el código `NO_AUTENTICADO`
- **AND** no se crea ningún turno

#### Scenario: Reserva pedida por el personal
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `POST /api/v1/paciente/reservas` con "Consulta", la Dra. A y las 10:00
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

#### Scenario: Reserva sin token CSRF
- **GIVEN** "Paciente Uno" con sesión iniciada
- **WHEN** pide una reserva de "Consulta" con la Dra. A a las 10:00 sin el encabezado `X-XSRF-TOKEN`
- **THEN** la respuesta es `403` con el código `CSRF_INVALIDO`
- **AND** no se crea ningún turno

### Requirement: Reserva con seña
La respuesta `201` de `POST /api/v1/paciente/reservas` SHALL incluir `sena`, con el formato de la spec `turnos-paciente`: el objeto de la seña pendiente si la reserva la creó (spec `senas`), o `null` si no. La seña MUST NOT cambiar las reglas de la reserva: el turno se crea `RESERVADO` y ocupa el hueco con las mismas garantías contra solapamientos.

#### Scenario: Reserva que crea una seña
- **GIVEN** las señas activas con el 30 % y 30 minutos
- **WHEN** "Paciente Dos" reserva online "Consulta o control" a las 10:00
- **THEN** la respuesta es `201` con el turno `RESERVADO` y `sena` con `estado` `PENDIENTE`, `importe` `4500.00` y `venceEn` a las 10:30

#### Scenario: Reserva sin seña
- **GIVEN** las señas desactivadas
- **WHEN** "Paciente Dos" reserva online "Consulta o control"
- **THEN** la respuesta es `201` con `sena` `null`

#### Scenario: Dos pacientes por el mismo hueco con señas activas
- **GIVEN** las señas activas
- **WHEN** "Paciente Uno" y "Paciente Dos" reservan a la vez el último hueco del mismo profesional y sillón
- **THEN** solo una reserva responde `201` con su seña, la otra recibe el rechazo de "Reservas simultáneas online" y hay una sola seña
