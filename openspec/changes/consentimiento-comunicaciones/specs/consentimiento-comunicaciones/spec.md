# Spec Delta

## Purpose

Guardar si cada paciente acepta recibir mensajes del consultorio y quién, cuándo y cómo registró cada cambio (Ley 25.326, RN-NO-03), y exponer ese estado al paciente y al personal. En los escenarios, con datos ficticios, "Paciente Uno" tiene cuenta (`paciente.uno@example.com`), "Paciente Dos" no tiene cuenta, `recepcion` es `RECEPCIONISTA` y `odontologo` es `ODONTOLOGO`.

## ADDED Requirements

### Requirement: Un solo consentimiento por paciente
Cada paciente SHALL tener un único consentimiento de comunicaciones, otorgado o no, que vale para todos los canales (WhatsApp y email). Un paciente sin ningún cambio registrado MUST considerarse sin consentimiento.

#### Scenario: Paciente nuevo sin consentimiento
- **GIVEN** "Paciente Dos", creado sin ningún cambio de consentimiento
- **WHEN** `recepcion` pide `GET /api/v1/pacientes/{id}/consentimiento-comunicaciones` de "Paciente Dos"
- **THEN** la respuesta es `200` con `otorgado` en `false`, sin fecha de actualización y con el historial vacío

### Requirement: Historial de cambios del consentimiento
Cada vez que el consentimiento cambia, el sistema SHALL agregar al historial un registro con el nuevo valor, la fecha y hora, el origen (`PACIENTE`, `REGISTRO` o `RECEPCION`) y, si el origen es `RECEPCION`, el usuario del personal y el medio (`TELEFONO` o `PRESENCIAL`). El historial MUST NOT modificarse ni borrarse, y el estado vigente MUST coincidir siempre con su último registro.

#### Scenario: Otorgar y revocar deja dos registros
- **GIVEN** "Paciente Uno" sin consentimiento y sin historial
- **WHEN** otorga el consentimiento el 2026-12-01 a las 10:00 y lo revoca el 2026-12-03 a las 18:00
- **THEN** el historial tiene dos registros, en ese orden: otorgado con origen `PACIENTE` y revocado con origen `PACIENTE`
- **AND** el estado vigente es sin consentimiento, actualizado el 2026-12-03 a las 18:00

#### Scenario: El historial no se pierde
- **GIVEN** "Paciente Uno" con el consentimiento otorgado por `recepcion` por teléfono y después revocado desde su cuenta
- **WHEN** lo vuelve a otorgar desde su cuenta
- **THEN** el historial conserva los tres registros, incluido el de `recepcion` con el medio `TELEFONO`

### Requirement: Un pedido sin cambio no agrega historial
Si un pedido indica el mismo valor que el consentimiento vigente, el sistema SHALL responder con el estado vigente y MUST NOT agregar un registro al historial.

#### Scenario: Otorgar dos veces
- **GIVEN** "Paciente Uno" con el consentimiento otorgado
- **WHEN** pide otra vez otorgarlo
- **THEN** la respuesta es `200` con `otorgado` en `true` y la fecha del cambio anterior
- **AND** el historial sigue teniendo un solo registro

### Requirement: El paciente consulta y cambia su consentimiento
Un usuario `PACIENTE` SHALL consultar su consentimiento con `GET /api/v1/paciente/consentimiento-comunicaciones` y cambiarlo con `PUT` al mismo recurso, enviando `otorgado`. El paciente MUST quedar identificado solo por la sesión, nunca por un id en el pedido. El cambio MUST registrarse con origen `PACIENTE`.

#### Scenario: El paciente otorga su consentimiento
- **GIVEN** una sesión de `paciente.uno@example.com` sin consentimiento
- **WHEN** envía `PUT /api/v1/paciente/consentimiento-comunicaciones` con `otorgado` en `true`
- **THEN** la respuesta es `200` con `otorgado` en `true` y la fecha del cambio
- **AND** el historial suma un registro con origen `PACIENTE`

#### Scenario: El personal no usa el recurso del paciente
- **GIVEN** una sesión de `recepcion`
- **WHEN** pide `GET /api/v1/paciente/consentimiento-comunicaciones`
- **THEN** la respuesta es `403`

### Requirement: La recepción consulta y registra el consentimiento
`ADMIN` y `RECEPCIONISTA` SHALL consultar el consentimiento de cualquier paciente, con su historial, con `GET /api/v1/pacientes/{id}/consentimiento-comunicaciones`, y cambiarlo con `PUT` al mismo recurso enviando `otorgado` y `medio`. El cambio MUST registrarse con origen `RECEPCION`, el usuario de la sesión y el medio, y sirve también para pacientes sin cuenta.

#### Scenario: La recepción registra un consentimiento telefónico
- **GIVEN** una sesión de `recepcion` y "Paciente Dos", sin cuenta y sin consentimiento
- **WHEN** envía `PUT /api/v1/pacientes/{id}/consentimiento-comunicaciones` de "Paciente Dos" con `otorgado` en `true` y `medio` `TELEFONO`
- **THEN** la respuesta es `200` con `otorgado` en `true`
- **AND** el historial suma un registro con origen `RECEPCION`, el usuario `recepcion` y el medio `TELEFONO`

#### Scenario: Falta el medio
- **GIVEN** una sesión de `recepcion`
- **WHEN** envía el `PUT` de "Paciente Dos" con `otorgado` en `true` y sin `medio`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** el consentimiento y el historial no cambian

#### Scenario: Paciente inexistente
- **GIVEN** una sesión de `recepcion`
- **WHEN** pide el consentimiento de un paciente que no existe
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

### Requirement: Solo la recepción y el administrador gestionan el de otros
El recurso `/api/v1/pacientes/{id}/consentimiento-comunicaciones` MUST responder `403` a `ODONTOLOGO` y a `PACIENTE`, y `401` sin sesión. Los pedidos `PUT` MUST llevar el token CSRF.

#### Scenario: El odontólogo no gestiona el consentimiento
- **GIVEN** una sesión de `odontologo`
- **WHEN** pide `GET /api/v1/pacientes/{id}/consentimiento-comunicaciones` de "Paciente Uno"
- **THEN** la respuesta es `403`

#### Scenario: Un paciente no consulta el de otro
- **GIVEN** una sesión de `paciente.uno@example.com`
- **WHEN** pide `GET /api/v1/pacientes/{id}/consentimiento-comunicaciones` de "Paciente Dos"
- **THEN** la respuesta es `403`

### Requirement: El consentimiento no expone otros datos
Las respuestas de consentimiento MUST traer solo el estado, las fechas, el origen, el medio y el nombre de usuario del personal que lo registró. MUST NOT incluir email, teléfono, DNI ni datos de salud, y los cambios de consentimiento MUST NOT escribir en los logs datos de contacto del paciente.

#### Scenario: La respuesta del personal no trae contacto
- **GIVEN** una sesión de `recepcion` y "Paciente Uno" con email `paciente.uno@example.com`
- **WHEN** pide su consentimiento
- **THEN** el cuerpo de la respuesta no contiene `paciente.uno@example.com` ni su DNI
