# agenda-api Specification

## Purpose
Definir el contrato HTTP con el que el personal del consultorio crea, consulta, cancela y reprograma turnos bajo `/api/v1/turnos`, con los códigos de respuesta y de error que el frontend puede usar, y la garantía de que dos reservas simultáneas nunca dejan dos turnos superpuestos. En los escenarios, con datos ficticios, existen los usuarios `admin` (`ADMIN`), `recepcion` (`RECEPCIONISTA`) y `ana.ejemplo` (`ODONTOLOGO`), "Paciente Uno" (DNI `99000001`) con cuenta de paciente, la Dra. A, el Dr. B, el Sillón 1, el Sillón 2 y la prestación "Consulta" de 30 minutos. El margen es de 10 minutos y los horarios son del 2026-12-01, en `America/Argentina/Buenos_Aires`, con el momento actual ese día a las 08:00.

## Requirements

### Requirement: Crear un turno por la API
`POST /api/v1/turnos` con `pacienteId`, `profesionalId`, `sillonId`, `prestacionId` e `inicio` SHALL crear el turno con las reglas de la capacidad `agenda-turnos` y responder `201` con el id, los cuatro ids recibidos, `inicio`, `fin`, `estado` y `numeroReserva`. Las fechas MUST viajar en ISO-8601 con zona horaria.

#### Scenario: Recepción crea un turno
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **AND** una agenda sin turnos
- **WHEN** pide `POST /api/v1/turnos` para "Paciente Uno" con la Dra. A, el Sillón 1, "Consulta" e inicio `2026-12-01T10:00:00-03:00`
- **THEN** la respuesta es `201`
- **AND** el cuerpo tiene `estado` `RESERVADO`, `fin` `2026-12-01T10:30:00-03:00` y un `numeroReserva`

### Requirement: Rechazos de negocio como 409
Cuando la agenda rechaza una operación, el sistema MUST responder `409` con un `ProblemDetail` que incluya la propiedad `codigo` con el código estable del rechazo (por ejemplo `SOLAPAMIENTO_PROFESIONAL`, `SOLAPAMIENTO_SILLON`, `MARGEN_INSUFICIENTE`, `INICIO_EN_EL_PASADO`, `RECURSO_INACTIVO`, `PROFESIONAL_NO_HABILITADO`, `DATOS_DE_RESERVA_INVALIDOS` o `TURNO_NO_ACTIVO`) y un detalle legible en español.

#### Scenario: Turno que choca con la agenda del profesional
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **AND** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** pide `POST /api/v1/turnos` con la Dra. A, el Sillón 2 e inicio a las 10:15
- **THEN** la respuesta es `409` con el código `SOLAPAMIENTO_PROFESIONAL`

#### Scenario: Turno contiguo sin margen
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **AND** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** pide `POST /api/v1/turnos` con el Dr. B, el Sillón 1 e inicio a las 10:30
- **THEN** la respuesta es `409` con el código `MARGEN_INSUFICIENTE`

#### Scenario: Turno con un profesional no habilitado
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **AND** que la Dra. A no está habilitada para "Tratamiento de conducto"
- **WHEN** pide `POST /api/v1/turnos` con la Dra. A, el Sillón 1, "Tratamiento de conducto" e inicio a las 10:00
- **THEN** la respuesta es `409` con el código `PROFESIONAL_NO_HABILITADO`
- **AND** no se crea ningún turno

### Requirement: Datos de entrada inválidos como 422
El sistema MUST responder `422` con el código `DATOS_INVALIDOS` y la lista de campos con error cuando falta un dato obligatorio, una fecha no tiene formato ISO-8601 con zona horaria, o un id no corresponde a un paciente, profesional, sillón o prestación existente.

#### Scenario: Falta el inicio
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `POST /api/v1/turnos` sin `inicio`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** la lista de errores incluye el campo `inicio`

#### Scenario: Profesional inexistente
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `POST /api/v1/turnos` con un `profesionalId` que no existe
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** la lista de errores incluye el campo `profesionalId`

### Requirement: Turno inexistente
Las operaciones sobre `/api/v1/turnos/{id}` MUST responder `404` con el código `TURNO_INEXISTENTE` cuando no existe un turno con ese id.

#### Scenario: Cancelar un turno que no existe
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `POST /api/v1/turnos/999999/cancelar` con un DNI y un número de reserva
- **THEN** la respuesta es `404` con el código `TURNO_INEXISTENTE`

### Requirement: Consultar la agenda
`GET /api/v1/turnos?desde&hasta` SHALL responder `200` con los turnos activos, `ATENDIDO` y `AUSENTE` (no los `CANCELADO`) cuyo intervalo se cruza con `[desde, hasta)`, con su `estado` y la hora de llegada si se registró (`llegadaEn`), ordenados por inicio, con el nombre del paciente. Acepta los filtros opcionales `profesionalId` y `sillonId`. `desde` y `hasta` son obligatorios. La respuesta MUST NOT incluir el número de reserva ni datos de salud. El alcance depende del rol de la sesión (RN-AG-18): con sesión `ODONTOLOGO`, la respuesta MUST incluir solo los turnos del profesional vinculado a ese usuario, aunque el pedido traiga otro `profesionalId`, que se reemplaza sin error; `sillonId` se aplica sobre esos turnos. Con sesión `ADMIN` o `RECEPCIONISTA`, la respuesta SHALL incluir los turnos de todos los profesionales que cumplan los filtros, aunque el usuario tenga un profesional vinculado.

#### Scenario: La agenda muestra solo los turnos activos
- **GIVEN** el usuario `ana.ejemplo` con sesión iniciada
- **AND** un turno activo a las 10:00 y un turno `CANCELADO` a las 11:00, los dos con la Dra. A
- **WHEN** pide `GET /api/v1/turnos` desde las 00:00 hasta las 23:59 del 2026-12-01
- **THEN** la respuesta es `200` con un solo turno, el de las 10:00
- **AND** el turno no incluye el número de reserva

#### Scenario: Filtro por profesional
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **AND** un turno activo a las 10:00 con la Dra. A y otro a las 10:00 con el Dr. B
- **WHEN** pide `GET /api/v1/turnos` del 2026-12-01 con `profesionalId` de la Dra. A
- **THEN** la respuesta es `200` con un solo turno, el de la Dra. A

#### Scenario: Falta el período
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `GET /api/v1/turnos` sin `desde`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`

#### Scenario: El odontólogo sin filtro recibe solo sus turnos
- **GIVEN** el usuario `ana.ejemplo` con sesión iniciada, vinculado a la Dra. A
- **AND** un turno activo a las 10:00 con la Dra. A en el Sillón 1 y otro a las 10:00 con el Dr. B en el Sillón 2
- **WHEN** pide `GET /api/v1/turnos` del 2026-12-01 sin `profesionalId` ni `sillonId`
- **THEN** la respuesta es `200` con un solo turno, el de la Dra. A

#### Scenario: El odontólogo pide la agenda de otro profesional
- **GIVEN** el usuario `ana.ejemplo` con sesión iniciada, vinculado a la Dra. A
- **AND** un turno activo a las 10:00 con la Dra. A y otro a las 11:00 con el Dr. B
- **WHEN** pide `GET /api/v1/turnos` del 2026-12-01 con `profesionalId` del Dr. B
- **THEN** la respuesta es `200` con un solo turno, el de la Dra. A
- **AND** la respuesta no incluye ningún turno del Dr. B

#### Scenario: El odontólogo filtra por un sillón compartido
- **GIVEN** el usuario `ana.ejemplo` con sesión iniciada, vinculado a la Dra. A
- **AND** un turno activo a las 09:00 con la Dra. A en el Sillón 1 y otro a las 11:00 con el Dr. B en el Sillón 1
- **WHEN** pide `GET /api/v1/turnos` del 2026-12-01 con `sillonId` del Sillón 1
- **THEN** la respuesta es `200` con un solo turno, el de las 09:00 con la Dra. A

#### Scenario: Recepción y administración ven a todos los profesionales
- **GIVEN** un turno activo a las 10:00 con la Dra. A y otro a las 10:00 con el Dr. B
- **AND** el usuario `admin` vinculado al Dr. B
- **WHEN** `recepcion` y luego `admin` piden `GET /api/v1/turnos` del 2026-12-01 sin filtros
- **THEN** cada respuesta es `200` con los dos turnos

#### Scenario: La agenda incluye los atendidos y los ausentes
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **AND** el 2026-12-01 con la Dra. A: un turno `ATENDIDO` a las 09:00 con la llegada a las 08:55, uno `AUSENTE` a las 10:00 y uno `CANCELADO` a las 11:00
- **WHEN** pide `GET /api/v1/turnos` del 2026-12-01
- **THEN** la respuesta es `200` con los turnos de las 09:00 (`ATENDIDO`, `llegadaEn` 08:55) y de las 10:00 (`AUSENTE`)
- **AND** no incluye el de las 11:00

### Requirement: Cancelar y reprogramar por la API
`POST /api/v1/turnos/{id}/cancelar` con `dni` y `numeroReserva` SHALL aplicar la cancelación de `agenda-turnos` y responder `200` con el turno cancelado. `POST /api/v1/turnos/{id}/reprogramar` con `dni`, `numeroReserva`, `inicio` y, opcionalmente, `profesionalId` y `sillonId`, SHALL aplicar la reprogramación y responder `200` con el turno nuevo y su número de reserva.

#### Scenario: Recepción cancela un turno
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **AND** un turno activo de "Paciente Uno" a las 10:00 con la Dra. A en el Sillón 1
- **WHEN** pide `POST /api/v1/turnos/{id}/cancelar` con el DNI `99000001` y el número de reserva del turno
- **THEN** la respuesta es `200` con `estado` `CANCELADO`
- **AND** un `POST /api/v1/turnos` con la Dra. A, el Sillón 1 e inicio a las 10:00 responde `201`

#### Scenario: Recepción reprograma un turno
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **AND** un turno activo de "Paciente Uno" a las 10:00 con la Dra. A en el Sillón 1
- **WHEN** pide `POST /api/v1/turnos/{id}/reprogramar` con el DNI `99000001`, el número de reserva del turno e inicio a las 11:00
- **THEN** la respuesta es `200` con un turno de 11:00 a 11:30 en estado `RESERVADO` y un número de reserva nuevo
- **AND** el turno original queda en estado `CANCELADO`

#### Scenario: Reprogramación rechazada deja el turno intacto
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **AND** un turno activo de "Paciente Uno" a las 10:00 con la Dra. A en el Sillón 1
- **AND** otro turno activo a las 11:00 con el Dr. B en el Sillón 1
- **WHEN** pide `POST /api/v1/turnos/{id}/reprogramar` del primero a las 11:10 con la Dra. A en el Sillón 1
- **THEN** la respuesta es `409` con el código `SOLAPAMIENTO_SILLON`
- **AND** el turno original sigue en estado `RESERVADO` a las 10:00

### Requirement: Reservas simultáneas
Si dos pedidos compiten por el mismo horario, el sistema MUST aceptar como máximo uno. El otro MUST responder `409` con `SOLAPAMIENTO_PROFESIONAL` o `SOLAPAMIENTO_SILLON`, según el recurso que ya está ocupado, y nunca con un error `500`. Esta garantía MUST valer aunque los dos pedidos pasen la verificación de la agenda al mismo tiempo.

#### Scenario: Dos reservas al mismo tiempo para el mismo sillón
- **GIVEN** los usuarios `recepcion` y `ana.ejemplo` con sesión iniciada
- **AND** una agenda sin turnos
- **WHEN** los dos piden al mismo tiempo `POST /api/v1/turnos` para el Sillón 1 a las 10:00, uno con la Dra. A y otro con el Dr. B
- **THEN** una respuesta es `201`
- **AND** la otra es `409` con el código `SOLAPAMIENTO_SILLON`
- **AND** la agenda tiene un solo turno activo a las 10:00 en el Sillón 1

### Requirement: Roles de la API de turnos
Las operaciones de `/api/v1/turnos` SHALL estar permitidas a los roles `ADMIN`, `RECEPCIONISTA` y `ODONTOLOGO`. Una sesión `PACIENTE` MUST recibir `403` con el código `ACCESO_DENEGADO` en todas ellas.

#### Scenario: El odontólogo crea un turno
- **GIVEN** el usuario `ana.ejemplo` con sesión iniciada
- **AND** una agenda sin turnos
- **WHEN** pide `POST /api/v1/turnos` con la Dra. A, el Sillón 1 e inicio a las 10:00
- **THEN** la respuesta es `201`

#### Scenario: Un paciente intenta crear un turno por la API del personal
- **GIVEN** "Paciente Uno" con sesión iniciada
- **WHEN** pide `POST /api/v1/turnos` con la Dra. A, el Sillón 1 e inicio a las 10:00
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** no se crea ningún turno

### Requirement: Crear un sobreturno por la API

`POST /api/v1/turnos/sobreturno` con `pacienteId`, `profesionalId`, `prestacionId`, `inicio` y `motivo` SHALL crear el sobreturno con las reglas de `agenda-turnos` y responder `201` con el id, `sillonId` asignado, `inicio`, `fin`, `estado`, `numeroReserva` y `esSobreturno`. El contrato MUST NOT incluir el campo `sillonId`: el cliente nunca elige el sillón. Las fechas MUST viajar en ISO-8601 con zona horaria.

#### Scenario: Recepción crea un sobreturno
- **GIVEN** el usuario `recepcion` con sesión iniciada y los dos sillones activos y libres
- **WHEN** pide `POST /api/v1/turnos/sobreturno` para "Paciente Uno" con la Dra. A, "Consulta", inicio `2026-12-01T10:00:00-03:00` y un motivo administrativo
- **THEN** la respuesta es `201` con `estado` `RESERVADO`, `fin` `2026-12-01T10:30:00-03:00`, `esSobreturno` verdadero y un `numeroReserva`
- **AND** el cuerpo incluye el `sillonId` asignado por el servidor

#### Scenario: El pedido no declara sillonId
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** revisa el contrato del pedido de sobreturno
- **THEN** no existe el campo `sillonId`, ni siquiera como campo opcional
- **AND** el sillón se asigna en el servidor según las reglas de `agenda-turnos`

### Requirement: Motivo obligatorio del sobreturno

El sistema MUST responder `422` con `DATOS_INVALIDOS` y el campo `motivo` en la lista de errores cuando el motivo falta, está vacío, son solo espacios o supera los 500 caracteres. El motivo SHALL ser administrativo y la respuesta MUST NOT incluirlo.

#### Scenario: Sobreturno sin motivo
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `POST /api/v1/turnos/sobreturno` sin `motivo` o con `"   "`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** la lista de errores incluye el campo `motivo`

#### Scenario: La respuesta no expone el motivo
- **GIVEN** un sobreturno creado con un motivo administrativo
- **WHEN** el personal consulta `GET /api/v1/turnos` o recibe la respuesta de creación
- **THEN** ninguno de los cuerpos incluye el motivo

### Requirement: Extender un turno por la API

`POST /api/v1/turnos/{id}/extender` con `fin` absoluto y `motivo` SHALL extender un turno `RESERVADO` o `CONFIRMADO` y responder `200` con el turno actualizado, `extendido` verdadero y el mismo `numeroReserva`. Un `fin` sin zona horaria o un motivo inválido MUST responder `422 DATOS_INVALIDOS`. Un id inexistente MUST responder `404 TURNO_INEXISTENTE`. El sistema MUST NOT reprogramar automáticamente el turno siguiente.

#### Scenario: Recepción extiende un turno
- **GIVEN** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** pide `POST /api/v1/turnos/{id}/extender` con `fin` `2026-12-01T10:45:00-03:00` y un motivo administrativo
- **THEN** la respuesta es `200` con `fin` `2026-12-01T10:45:00-03:00`, `extendido` verdadero y el mismo `numeroReserva`

#### Scenario: Extensión con fin sin zona horaria
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide extender un turno con `fin` `2026-12-01T10:45:00`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `fin`

#### Scenario: Extensión de un turno inexistente
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `POST /api/v1/turnos/999999/extender` con un `fin` y un motivo válidos
- **THEN** la respuesta es `404` con el código `TURNO_INEXISTENTE`

#### Scenario: Extensión rechazada no reprograma el siguiente
- **GIVEN** un turno activo de 10:00 a 10:30 y otro de 11:00 a 11:30 en el mismo sillón
- **WHEN** pide extender el primero hasta las 11:00 y la operación se rechaza
- **THEN** la respuesta es `409` con el código `SOLAPAMIENTO_SILLON`
- **AND** el turno siguiente sigue en su horario original

### Requirement: Códigos estables de rechazo de sobreturnos y extensiones

Los rechazos de negocio de estos endpoints MUST responder `409` con un `ProblemDetail` que incluya la propiedad `codigo`: `SIN_SILLON_DISPONIBLE`, `SOLAPAMIENTO_SILLON`, `MARGEN_INSUFICIENTE`, `TURNO_NO_ACTIVO` o `TURNO_NO_EXTENSIBLE`, con detalle legible en español. La traducción de `SOLAPAMIENTO_SILLON` a `SIN_SILLON_DISPONIBLE` SHALL aplicarse solo a la operación de sobreturno.

#### Scenario: Sobreturno sin sillón libre
- **GIVEN** turnos activos que ocupan los dos sillones de 10:00 a 10:30
- **WHEN** el usuario `recepcion` pide un sobreturno de "Consulta" a las 10:10
- **THEN** la respuesta es `409` con el código `SIN_SILLON_DISPONIBLE`

#### Scenario: Extensión de un sobreturno
- **GIVEN** un sobreturno activo de 10:00 a 10:30
- **WHEN** el usuario `recepcion` pide extenderlo hasta las 10:45
- **THEN** la respuesta es `409` con el código `TURNO_NO_EXTENSIBLE`

#### Scenario: Un turno normal mantiene su código de sillón
- **GIVEN** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** el usuario `recepcion` pide `POST /api/v1/turnos` con el Dr. B en el Sillón 1 a las 10:15
- **THEN** la respuesta es `409` con el código `SOLAPAMIENTO_SILLON`, no `SIN_SILLON_DISPONIBLE`

### Requirement: Indicador de sobreturno en las respuestas

Las respuestas `TurnoResponse` y `TurnoAgendaResponse` SHALL incluir `esSobreturno: boolean`, de forma aditiva y retrocompatible. El indicador MUST reflejar la columna `es_sobreturno` ya existente, sin requerir una migración nueva. Las respuestas MUST NOT incluir el motivo ni datos de salud.

#### Scenario: La agenda distingue los sobreturnos
- **GIVEN** un turno normal y un sobreturno activos el 2026-12-01
- **WHEN** el personal pide `GET /api/v1/turnos` para ese día
- **THEN** ambos turnos aparecen
- **AND** solo el sobreturno tiene `esSobreturno` verdadero

#### Scenario: Respuesta de creación sin datos sensibles
- **GIVEN** un sobreturno creado con un motivo administrativo
- **WHEN** llega la respuesta `201` de `POST /api/v1/turnos/sobreturno`
- **THEN** el cuerpo no incluye `motivo`, `motivoSobreturno` ni datos de salud

### Requirement: Roles para sobreturno y extensión

Las operaciones nuevas SHALL estar permitidas a `ADMIN`, `RECEPCIONISTA` y `ODONTOLOGO`. Una sesión `PACIENTE` MUST recibir `403` con el código `ACCESO_DENEGADO`, sin crear ni modificar ningún turno. Se mantiene la sesión con cookie `HttpOnly` y CSRF activo; no se usa JWT.

#### Scenario: Un odontólogo crea un sobreturno
- **GIVEN** el usuario `ana.ejemplo` con sesión iniciada
- **WHEN** pide `POST /api/v1/turnos/sobreturno` con datos válidos y un sillón libre
- **THEN** la respuesta es `201`

#### Scenario: Un paciente intenta crear un sobreturno
- **GIVEN** "Paciente Uno" con sesión iniciada
- **WHEN** pide `POST /api/v1/turnos/sobreturno` con datos válidos
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** no se crea ningún turno

#### Scenario: Un paciente intenta extender un turno
- **GIVEN** "Paciente Uno" con sesión iniciada
- **WHEN** pide `POST /api/v1/turnos/{id}/extender` con datos válidos
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** el turno queda sin cambios

### Requirement: Sobreturnos simultáneos por el último sillón libre

Si dos sobreturnos compiten por el último sillón libre, el sistema MUST aceptar como máximo uno. El otro MUST responder `409 SIN_SILLON_DISPONIBLE` y nunca un error `500`, aunque los dos hayan pasado la verificación del dominio al mismo tiempo.

#### Scenario: Dos sobreturnos al mismo tiempo
- **GIVEN** los usuarios `recepcion` y `ana.ejemplo` con sesión iniciada
- **AND** solo el Sillón 1 libre a las 10:00, con un turno previo que ocupa el Sillón 2 con margen
- **WHEN** los dos piden al mismo tiempo un sobreturno de "Consulta" con inicio 10:00
- **THEN** una respuesta es `201`
- **AND** la otra es `409` con el código `SIN_SILLON_DISPONIBLE`
- **AND** solo hay un sobreturno activo a las 10:00

#### Scenario: Dos sobreturnos al mismo tiempo con dos sillones libres
- **GIVEN** los usuarios `recepcion` y `ana.ejemplo` con sesión iniciada
- **AND** el Sillón 1 y el Sillón 2 libres a las 10:00
- **WHEN** los dos piden al mismo tiempo un sobreturno de "Consulta" con inicio 10:00
- **THEN** las dos respuestas son `201`
- **AND** los dos sobreturnos quedan en sillones distintos
- **AND** hay un registro de auditoría `SOBRETURNO` por cada uno

### Requirement: Registrar la asistencia por la API
La recepción SHALL registrar la asistencia con `POST /api/v1/turnos/{id}/llegada`, `POST /api/v1/turnos/{id}/ausencia` y `POST /api/v1/turnos/{id}/deshacer-ausencia`, sin cuerpo. Cada uno responde `200` con el turno actualizado. Los rechazos de negocio responden `409` con su código estable, un turno inexistente `404` con `TURNO_INEXISTENTE`, y una sesión que no es `RECEPCIONISTA`, `403` con `ACCESO_DENEGADO`.

#### Scenario: La recepción registra la llegada
- **GIVEN** el usuario `recepcion` con sesión iniciada y el momento actual el 2026-12-01 a las 10:05
- **AND** un turno activo de 10:00 a 10:30 con la Dra. A
- **WHEN** pide `POST /api/v1/turnos/{id}/llegada`
- **THEN** la respuesta es `200` con el `estado` `RESERVADO` y `llegadaEn` a las 10:05

#### Scenario: Ausencia antes de la tolerancia por la API
- **GIVEN** el usuario `recepcion` con sesión iniciada y el momento actual el 2026-12-01 a las 10:10
- **AND** un turno activo de 10:00 a 10:30 con la Dra. A
- **WHEN** pide `POST /api/v1/turnos/{id}/ausencia`
- **THEN** la respuesta es `409` con el código `AUSENCIA_ANTES_DE_TOLERANCIA`

#### Scenario: El odontólogo usa la ausencia de la agenda
- **GIVEN** el usuario `ana.ejemplo` con sesión iniciada y el momento actual el 2026-12-01 a las 10:20
- **AND** un turno activo de 10:00 a 10:30 con la Dra. A
- **WHEN** pide `POST /api/v1/turnos/{id}/ausencia`
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** el turno sigue activo

#### Scenario: Deshacer la ausencia de un turno inexistente
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `POST /api/v1/turnos/999999/deshacer-ausencia`
- **THEN** la respuesta es `404` con el código `TURNO_INEXISTENTE`

### Requirement: Causa de la cancelación de un turno con seña
`POST /api/v1/turnos/{id}/cancelar` SHALL aceptar el campo opcional `causa`, con los valores `PACIENTE` o `CONSULTORIO`, y guardarlo en la auditoría de la cancelación. Si el turno tiene una seña pagada (spec `senas`), la `causa` MUST ser obligatoria: sin ella, la respuesta es `422` con `DATOS_INVALIDOS` y el campo `causa`, y el turno no se cancela. Un turno sin seña pagada MUST poder cancelarse sin `causa`, como antes.

#### Scenario: Cancelar un turno con seña indicando la causa
- **GIVEN** un turno de "Paciente Dos" con la seña pagada
- **WHEN** la `RECEPCIONISTA` lo cancela con su DNI, su número de reserva y `causa` `CONSULTORIO`
- **THEN** la respuesta es `200`, el turno está `CANCELADO` y la auditoría de la cancelación tiene la causa `CONSULTORIO`

#### Scenario: Falta la causa de un turno con seña
- **GIVEN** un turno de "Paciente Dos" con la seña pagada
- **WHEN** la `RECEPCIONISTA` lo cancela sin `causa`
- **THEN** la respuesta es `422` con `DATOS_INVALIDOS` y el campo `causa`, y el turno sigue `RESERVADO`

#### Scenario: Turno sin seña sin causa
- **GIVEN** un turno de "Paciente Dos" sin seña
- **WHEN** la `RECEPCIONISTA` lo cancela sin `causa`
- **THEN** la respuesta es `200` y el turno está `CANCELADO`

#### Scenario: Causa inválida
- **WHEN** la `RECEPCIONISTA` cancela un turno con `causa` `OTRA`
- **THEN** la respuesta es `422` con `DATOS_INVALIDOS` y el campo `causa`
