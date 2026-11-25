## Purpose
Ofrece automáticamente los horarios que se liberan a los pacientes anotados en una lista de espera, de a uno por vez y por orden de llegada, para que el consultorio no pierda turnos cancelados y el paciente consiga un turno antes sin buscarlo todos los días.

## ADDED Requirements

### Requirement: Inscripción en la lista de espera
Un usuario `PACIENTE` SHALL poder anotarse a sí mismo con `POST /api/v1/paciente/lista-espera`. Un usuario `RECEPCIONISTA` o `ADMIN` SHALL poder anotar a cualquier paciente con `POST /api/v1/lista-espera`. La inscripción lleva la prestación, un profesional o `null` ("sin preferencia") y un rango de fechas `desde`–`hasta`, y queda `ACTIVA`, con la fecha y hora de inscripción como orden de llegada. La respuesta `201` devuelve la inscripción. Un rango que empieza antes de hoy, que termina antes de empezar o que dura más de 60 días MUST responder `422` con el código `DATOS_INVALIDOS`.

#### Scenario: El paciente se anota
- **GIVEN** "Paciente Uno", con cuenta y con consentimiento de comunicaciones
- **WHEN** se anota para "Limpieza" con "Dra. Ana Ejemplo" del 2026-12-01 al 2026-12-15
- **THEN** la respuesta es `201` con una inscripción `ACTIVA` para "Limpieza"

#### Scenario: La recepción anota a un paciente sin preferencia de profesional
- **GIVEN** "Paciente Dos", con consentimiento de comunicaciones y sin cuenta
- **WHEN** `recepcion.ejemplo` lo anota para "Limpieza" sin profesional, del 2026-12-01 al 2026-12-10
- **THEN** la respuesta es `201` con una inscripción `ACTIVA` sin profesional

#### Scenario: Rango de más de 60 días
- **WHEN** "Paciente Uno" se anota del 2026-12-01 al 2027-02-15
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** no se crea ninguna inscripción

#### Scenario: Rango que empieza en el pasado
- **GIVEN** hoy es 2026-12-01
- **WHEN** "Paciente Uno" se anota del 2026-11-28 al 2026-12-05
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`

### Requirement: Una inscripción activa por prestación y con consentimiento
Un paciente MUST NOT tener dos inscripciones `ACTIVA` para la misma prestación: la segunda MUST responder `409` con el código `YA_EN_LISTA_DE_ESPERA`. Un paciente sin consentimiento de comunicaciones MUST NOT anotarse, porque la oferta llega por mensaje: MUST responder `409` con el código `SIN_CONSENTIMIENTO`. Un profesional inactivo o no habilitado para la prestación MUST responder `409` con `RECURSO_INACTIVO` o `PROFESIONAL_NO_HABILITADO`.

#### Scenario: Segunda inscripción para la misma prestación
- **GIVEN** "Paciente Uno" con una inscripción `ACTIVA` para "Limpieza"
- **WHEN** se anota otra vez para "Limpieza"
- **THEN** la respuesta es `409` con el código `YA_EN_LISTA_DE_ESPERA`

#### Scenario: Otra prestación sí se acepta
- **GIVEN** "Paciente Uno" con una inscripción `ACTIVA` para "Limpieza"
- **WHEN** se anota para "Control"
- **THEN** la respuesta es `201`

#### Scenario: Paciente sin consentimiento
- **GIVEN** "Paciente Tres" sin consentimiento de comunicaciones
- **WHEN** `recepcion.ejemplo` lo anota para "Limpieza"
- **THEN** la respuesta es `409` con el código `SIN_CONSENTIMIENTO`
- **AND** no se crea ninguna inscripción

#### Scenario: Profesional no habilitado
- **GIVEN** "Dr. Bruno Ejemplo" no está habilitado para "Ortodoncia"
- **WHEN** "Paciente Uno" se anota para "Ortodoncia" con "Dr. Bruno Ejemplo"
- **THEN** la respuesta es `409` con el código `PROFESIONAL_NO_HABILITADO`

### Requirement: Un horario liberado se ofrece al primer paciente compatible
Cuando un turno activo que todavía no empezó se cancela (por el personal, por el paciente online o desde el link del recordatorio) o se reprograma, el sistema SHALL ofrecer ese horario, apenas se confirma la cancelación y sin poder deshacerla, a la **primera** inscripción `ACTIVA` compatible por orden de llegada. Es compatible si:
- la fecha del horario está dentro de su rango;
- pidió ese profesional o "sin preferencia";
- un turno de su prestación, con ese profesional y ese inicio, cumple todas las reglas de la agenda, con el sillón asignado como en la reserva online;
- el paciente no tiene otra oferta `PENDIENTE`.

La oferta queda `PENDIENTE` y se le avisa al paciente. Un horario liberado MUST tener como máximo una oferta `PENDIENTE`. La ausencia del paciente, la reasignación por ausencia o bloqueo del profesional y un horario ya empezado MUST NOT generar ofertas. Si no hay ninguna inscripción compatible, el horario queda libre sin oferta.

#### Scenario: Se ofrece por orden de llegada
- **GIVEN** "Paciente Uno" anotado para "Limpieza" con "Dra. Ana Ejemplo" a las 09:00
- **AND** "Paciente Dos" anotado para "Limpieza" sin preferencia a las 10:00 del mismo día
- **WHEN** se cancela el turno del 2026-12-03 a las 10:00 de "Dra. Ana Ejemplo"
- **THEN** hay una sola oferta `PENDIENTE`, para "Paciente Uno", del 2026-12-03 a las 10:00
- **AND** "Paciente Dos" no recibe ninguna oferta

#### Scenario: Una prestación distinta que entra en el horario
- **GIVEN** se cancela un turno de "Conducto" de 60 minutos con "Dra. Ana Ejemplo"
- **AND** "Paciente Uno" es el único anotado, para "Limpieza" de 30 minutos con "Dra. Ana Ejemplo"
- **WHEN** se procesa el horario liberado
- **THEN** se le ofrece a "Paciente Uno" ese inicio con "Dra. Ana Ejemplo"

#### Scenario: La prestación no entra en el horario
- **GIVEN** se cancela un turno de 30 minutos y la "Dra. Ana Ejemplo" tiene otro turno 40 minutos después
- **AND** "Paciente Uno" es el único anotado, para "Conducto" de 60 minutos
- **WHEN** se procesa el horario liberado
- **THEN** no se crea ninguna oferta y el horario queda libre

#### Scenario: Otro profesional elegido
- **GIVEN** "Paciente Uno" anotado solo con "Dr. Bruno Ejemplo"
- **WHEN** se cancela un turno de "Dra. Ana Ejemplo"
- **THEN** no se le ofrece a "Paciente Uno"

#### Scenario: Fuera del rango de fechas
- **GIVEN** "Paciente Uno" anotado del 2026-12-01 al 2026-12-05
- **WHEN** se cancela un turno del 2026-12-09
- **THEN** no se le ofrece a "Paciente Uno"

#### Scenario: La reprogramación libera el horario viejo
- **GIVEN** "Paciente Uno" anotado y compatible con el horario del 2026-12-03 a las 10:00
- **WHEN** se reprograma ese turno al 2026-12-10
- **THEN** se le ofrece a "Paciente Uno" el 2026-12-03 a las 10:00

#### Scenario: La ausencia del paciente no genera ofertas
- **GIVEN** "Paciente Uno" anotado y compatible
- **WHEN** la recepción marca ausente un turno que ya empezó
- **THEN** no se crea ninguna oferta

#### Scenario: El paciente con una oferta pendiente se saltea
- **GIVEN** "Paciente Uno" con una oferta `PENDIENTE` para otro horario
- **AND** "Paciente Dos", anotado después, también compatible
- **WHEN** se cancela un turno compatible con los dos
- **THEN** la oferta nueva es para "Paciente Dos"

### Requirement: Plazo para aceptar configurable
La oferta SHALL vencer al cumplirse el plazo configurado o al llegar el inicio del horario, lo que pase primero. Un usuario `ADMIN` SHALL poder leer y cambiar el plazo con `GET` y `PUT /api/v1/configuracion/lista-espera`, entre 1 y 24 horas enteras. Por defecto es 2. Un valor fuera de rango MUST responder `422` con el código `PLAZO_INVALIDO`. `RECEPCIONISTA` SHALL poder leerlo; los demás roles MUST recibir `403`.

#### Scenario: Plazo por defecto
- **GIVEN** el plazo no se cambió
- **WHEN** se crea una oferta a las 08:00 para un horario de las 15:00
- **THEN** la oferta vence a las 10:00

#### Scenario: El inicio llega antes que el plazo
- **GIVEN** el plazo es 2 horas
- **WHEN** se crea una oferta a las 09:00 para un horario de las 10:00
- **THEN** la oferta vence a las 10:00

#### Scenario: El administrador cambia el plazo
- **WHEN** `admin.ejemplo` pone el plazo en 4 horas
- **THEN** la respuesta es `200` con el plazo 4
- **AND** la siguiente oferta vence 4 horas después de creada

#### Scenario: Plazo fuera de rango
- **WHEN** `admin.ejemplo` pone el plazo en 0 horas o en 25 horas
- **THEN** la respuesta es `422` con el código `PLAZO_INVALIDO`

#### Scenario: La recepción no cambia el plazo
- **WHEN** `recepcion.ejemplo` pone el plazo en 4 horas
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

### Requirement: Ver la oferta desde el link
`GET /api/v1/public/ofertas/{token}` SHALL devolver, sin sesión, la fecha, la hora, el profesional, el vencimiento y el estado de la oferta, sin consumir el token y sin datos de salud: no nombra la prestación. Un token que no existe o mal formado MUST responder `404` con el código `OFERTA_INEXISTENTE`.

#### Scenario: Ver una oferta pendiente
- **GIVEN** una oferta `PENDIENTE` para "Paciente Uno" del 2026-12-03 a las 10:00 con "Dra. Ana Ejemplo"
- **WHEN** se pide la oferta con su token
- **THEN** la respuesta es `200` con la fecha 2026-12-03, la hora 10:00, "Dra. Ana Ejemplo", el vencimiento y el estado `PENDIENTE`
- **AND** la respuesta no incluye la prestación
- **AND** la oferta sigue `PENDIENTE`

#### Scenario: Token inexistente
- **WHEN** se pide una oferta con un token que nunca se emitió
- **THEN** la respuesta es `404` con el código `OFERTA_INEXISTENTE`

### Requirement: Aceptar la oferta
`POST /api/v1/public/ofertas/{token}/aceptar` SHALL crear, sin sesión y con CSRF, el turno `RESERVADO` del paciente con su prestación, el profesional y el inicio de la oferta, y un sillón libre asignado como en la reserva online. Las validaciones son las mismas que en cualquier turno y en la misma transacción. La auditoría lleva el actor `lista-espera:<id de la oferta>`. La oferta pasa a `ACEPTADA`, la inscripción a `ASIGNADA`, y la respuesta `200` devuelve la fecha, la hora, el profesional y el número de reserva. Si el horario ya no cumple las reglas, MUST responder `409` con el código `OFERTA_NO_DISPONIBLE`: la oferta pasa a `NO_DISPONIBLE`, la inscripción sigue `ACTIVA` y no se le ofrece a nadie más. Una oferta vencida MUST responder `409` con `OFERTA_VENCIDA`, y una ya respondida, `409` con `OFERTA_YA_RESPONDIDA`.

#### Scenario: Aceptar a tiempo
- **GIVEN** una oferta `PENDIENTE` para "Paciente Uno" del 2026-12-03 a las 10:00
- **WHEN** acepta con su token
- **THEN** la respuesta es `200` con el número de reserva
- **AND** "Paciente Uno" tiene un turno `RESERVADO` el 2026-12-03 a las 10:00 con "Dra. Ana Ejemplo"
- **AND** su inscripción está `ASIGNADA`

#### Scenario: El horario se ocupó durante la oferta
- **GIVEN** una oferta `PENDIENTE` para "Paciente Uno" del 2026-12-03 a las 10:00
- **AND** otro paciente reservó ese horario online
- **WHEN** "Paciente Uno" acepta
- **THEN** la respuesta es `409` con el código `OFERTA_NO_DISPONIBLE`
- **AND** la inscripción de "Paciente Uno" sigue `ACTIVA`
- **AND** no hay ninguna oferta `PENDIENTE` para ese horario

#### Scenario: Aceptar después del vencimiento
- **GIVEN** una oferta que venció a las 10:00
- **WHEN** el paciente acepta a las 10:05
- **THEN** la respuesta es `409` con el código `OFERTA_VENCIDA`
- **AND** no se crea ningún turno

#### Scenario: Aceptar dos veces
- **GIVEN** una oferta ya `ACEPTADA`
- **WHEN** se acepta otra vez con el mismo token
- **THEN** la respuesta es `409` con el código `OFERTA_YA_RESPONDIDA`
- **AND** el paciente sigue con un solo turno

### Requirement: Rechazar la oferta
`POST /api/v1/public/ofertas/{token}/rechazar` SHALL marcar la oferta como `RECHAZADA` sin cambiar la inscripción, que sigue `ACTIVA`. Enseguida, SHALL ofrecer el horario a la siguiente inscripción compatible, evaluada en ese momento. Una oferta vencida o ya respondida MUST responder igual que al aceptar.

#### Scenario: No me interesa
- **GIVEN** una oferta `PENDIENTE` para "Paciente Uno" y "Paciente Dos" compatible después en la lista
- **WHEN** "Paciente Uno" rechaza
- **THEN** la oferta de "Paciente Uno" queda `RECHAZADA` y su inscripción sigue `ACTIVA`
- **AND** hay una oferta `PENDIENTE` del mismo horario para "Paciente Dos"

#### Scenario: Rechazo sin nadie más en la lista
- **GIVEN** una oferta `PENDIENTE` para "Paciente Uno", el único anotado
- **WHEN** rechaza
- **THEN** no hay ninguna oferta `PENDIENTE` para ese horario

### Requirement: Vencimientos automáticos
Una tarea programada SHALL marcar como `VENCIDA` cada oferta `PENDIENTE` cuyo vencimiento pasó. Si el horario todavía no empezó, SHALL ofrecerlo a la siguiente inscripción compatible. También SHALL marcar como `VENCIDA` cada inscripción `ACTIVA` cuyo último día ya pasó. Cada oferta y cada inscripción se procesan una sola vez, aunque la tarea corra en paralelo.

#### Scenario: Vence y pasa al siguiente
- **GIVEN** una oferta `PENDIENTE` para "Paciente Uno" que venció a las 10:00, para un horario de las 15:00
- **AND** "Paciente Dos" compatible después en la lista
- **WHEN** corre la tarea a las 10:01
- **THEN** la oferta de "Paciente Uno" queda `VENCIDA` y su inscripción sigue `ACTIVA`
- **AND** hay una oferta `PENDIENTE` para "Paciente Dos"

#### Scenario: Vence cuando el horario ya empezó
- **GIVEN** una oferta `PENDIENTE` que venció al inicio del horario
- **WHEN** corre la tarea
- **THEN** la oferta queda `VENCIDA` y no se crea ninguna oferta nueva

#### Scenario: Vence la inscripción
- **GIVEN** la inscripción de "Paciente Uno" con último día 2026-12-05
- **WHEN** corre la tarea el 2026-12-06
- **THEN** la inscripción queda `VENCIDA`

### Requirement: Consultar y dar de baja
Un usuario `PACIENTE` SHALL ver con `GET /api/v1/paciente/lista-espera` solo sus inscripciones `ACTIVA`, con la prestación, el profesional o "sin preferencia", el rango y la oferta pendiente si la hay, y SHALL darse de baja de una propia con `POST /api/v1/paciente/lista-espera/{id}/baja`. `RECEPCIONISTA` y `ADMIN` SHALL ver con `GET /api/v1/lista-espera` todas las inscripciones `ACTIVA` en orden de llegada, con el nombre y el DNI del paciente y la oferta pendiente, y darlas de baja con `POST /api/v1/lista-espera/{id}/baja`. La baja deja la inscripción `BAJA` y una oferta pendiente suya `RECHAZADA`, y pasa ese horario al siguiente. La inscripción de otro paciente MUST responder `404` con el código `RECURSO_NO_ENCONTRADO`, y una que ya no está activa, `409` con `INSCRIPCION_NO_ACTIVA`.

#### Scenario: El paciente ve solo las suyas
- **GIVEN** "Paciente Uno" y "Paciente Dos" anotados
- **WHEN** "Paciente Uno" pide su lista de espera
- **THEN** ve solo su inscripción

#### Scenario: La recepción ve la lista en orden
- **GIVEN** "Paciente Dos" anotado antes que "Paciente Uno"
- **WHEN** `recepcion.ejemplo` pide la lista de espera
- **THEN** la lista empieza por "Paciente Dos"

#### Scenario: Baja con una oferta pendiente
- **GIVEN** "Paciente Uno" con una oferta `PENDIENTE` y "Paciente Dos" compatible después
- **WHEN** "Paciente Uno" se da de baja
- **THEN** su inscripción queda `BAJA` y su oferta `RECHAZADA`
- **AND** hay una oferta `PENDIENTE` del mismo horario para "Paciente Dos"

#### Scenario: Baja de una inscripción ajena
- **WHEN** "Paciente Uno" da de baja la inscripción de "Paciente Dos"
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

### Requirement: Acceso por rol
`ODONTOLOGO` MUST recibir `403` con el código `ACCESO_DENEGADO` en `/api/v1/lista-espera` y en `/api/v1/configuracion/lista-espera`. `PACIENTE` MUST recibir `403` en las rutas del personal. Sin sesión, las rutas del personal y del paciente MUST responder `401`. Toda escritura con sesión exige CSRF.

#### Scenario: El odontólogo no accede
- **WHEN** `ana.ejemplo` (`ODONTOLOGO`) pide `GET /api/v1/lista-espera`
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

#### Scenario: El paciente no accede a la lista completa
- **WHEN** "Paciente Uno" pide `GET /api/v1/lista-espera`
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

#### Scenario: Sin sesión
- **WHEN** se pide `GET /api/v1/paciente/lista-espera` sin sesión
- **THEN** la respuesta es `401`

### Requirement: Token y privacidad de la oferta
El token de la oferta SHALL tener 256 bits aleatorios en base64url y MUST guardarse solo como hash. Los logs y los mensajes de error MUST NOT incluir el token, la prestación ni datos de salud.

#### Scenario: El token no se guarda en claro
- **WHEN** se crea una oferta
- **THEN** la base guarda el hash del token y no el token

#### Scenario: Error sin datos de la oferta
- **WHEN** se acepta una oferta vencida
- **THEN** el cuerpo del error no incluye el token ni la prestación
