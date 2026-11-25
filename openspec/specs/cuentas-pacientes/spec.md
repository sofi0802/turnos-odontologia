# cuentas-pacientes Specification

## Purpose

Permitir que un paciente cree una única vez su cuenta propia (US-024, RN-RO-08), que lo vincula a sus datos de paciente y le da el rol `PACIENTE`, con reglas de unicidad y validación de datos claras. En los escenarios, con datos ficticios, el registro de ejemplo es de "Paciente Uno" (DNI `99000001`, email `paciente.uno@example.com`) con una contraseña válida, y el pedido lleva un token CSRF válido.

## Requirements

### Requirement: Registro del paciente con su cuenta
El sistema SHALL registrar un paciente con `POST /api/v1/public/pacientes/registro`, que recibe nombre, apellido, DNI, email, teléfono opcional y contraseña. Crea, en una sola operación, el paciente y su cuenta con rol `PACIENTE`, y responde `201` con el usuario y el rol, sin la contraseña. *(Propuesta, a confirmar)*: el usuario de la cuenta es el email en minúsculas, y el registro no inicia la sesión.

#### Scenario: Registro correcto
- **GIVEN** que no existe un paciente con DNI `99000001` ni una cuenta `paciente.uno@example.com`
- **WHEN** se envía el registro de "Paciente Uno" con el email `Paciente.Uno@Example.com`
- **THEN** la respuesta es `201` con `username` igual a `paciente.uno@example.com` y `rol` igual a `PACIENTE`
- **AND** la respuesta no incluye la contraseña ni su hash
- **AND** "Paciente Uno" puede iniciar sesión con ese email y su contraseña, y su perfil muestra el DNI `99000001`

#### Scenario: El registro no inicia la sesión
- **GIVEN** un navegador sin sesión
- **WHEN** se registra "Paciente Uno" correctamente desde ese navegador
- **THEN** un pedido posterior a `GET /api/v1/public/sesion` informa que no hay sesión iniciada

### Requirement: Registro único por DNI
El sistema MUST rechazar con `409` y el código `DNI_YA_REGISTRADO` un registro cuyo DNI ya pertenece a un paciente, tenga o no cuenta, sin crear ni modificar datos. *(Propuesta, a confirmar)*: vincular una cuenta a un paciente cargado por el consultorio queda fuera de este registro.

#### Scenario: DNI de un paciente con cuenta
- **GIVEN** "Paciente Uno" ya registrado con su cuenta
- **WHEN** se envía otro registro con el DNI `99000001` y el email `otro.email@example.com`
- **THEN** la respuesta es `409` con el código `DNI_YA_REGISTRADO`
- **AND** no existe una cuenta `otro.email@example.com`

#### Scenario: DNI de un paciente cargado por el consultorio
- **GIVEN** un paciente con DNI `99000001` cargado por el consultorio, sin cuenta
- **WHEN** se envía el registro de "Paciente Uno" con ese DNI
- **THEN** la respuesta es `409` con el código `DNI_YA_REGISTRADO`
- **AND** el paciente existente sigue sin cuenta y sin cambios

### Requirement: Email único como usuario
El sistema MUST rechazar con `409` y el código `EMAIL_YA_REGISTRADO` un registro cuyo email, sin distinguir mayúsculas, ya es el usuario de una cuenta, sin crear ni modificar datos.

#### Scenario: Email ya usado por otra cuenta
- **GIVEN** la cuenta `paciente.uno@example.com` existente
- **WHEN** se envía un registro con el DNI `99000002` y el email `PACIENTE.UNO@example.com`
- **THEN** la respuesta es `409` con el código `EMAIL_YA_REGISTRADO`
- **AND** no existe un paciente con DNI `99000002`

### Requirement: Validación de los datos del registro
El sistema MUST responder `422` con el código `DATOS_INVALIDOS` y la lista de campos con error, sin crear datos, cuando falta el nombre, el apellido, el DNI, el email o la contraseña; cuando el DNI no tiene 7 u 8 dígitos; cuando el email no es válido; o cuando la contraseña no tiene entre 8 y 72 caracteres *(política propuesta, a confirmar)*.

#### Scenario: Contraseña demasiado corta
- **WHEN** se envía el registro de "Paciente Uno" con una contraseña de 7 caracteres
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** la lista de errores incluye el campo `password`
- **AND** no existe un paciente con DNI `99000001`

#### Scenario: Contraseña en el límite mínimo
- **WHEN** se envía el registro de "Paciente Uno" con una contraseña de 8 caracteres
- **THEN** la respuesta es `201`

#### Scenario: Contraseña demasiado larga
- **WHEN** se envía el registro de "Paciente Uno" con una contraseña de 73 caracteres
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** la lista de errores incluye el campo `password`

#### Scenario: DNI y email con formato inválido
- **WHEN** se envía el registro de "Paciente Uno" con el DNI `99-000` y el email `no-es-un-email`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** la lista de errores incluye los campos `dni` y `email`

#### Scenario: Faltan datos obligatorios
- **WHEN** se envía un registro sin nombre ni apellido
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** la lista de errores incluye los campos `nombre` y `apellido`

### Requirement: Consentimiento inicial en el registro
`POST /api/v1/public/pacientes/registro` SHALL aceptar `aceptaMensajes`, opcional y `false` si falta. Si es `true`, el paciente MUST quedar con el consentimiento otorgado y un registro de historial con origen `REGISTRO`, en la misma operación que crea el paciente y su cuenta. Si es `false` o falta, MUST quedar sin consentimiento y sin historial.

#### Scenario: Registro aceptando mensajes
- **GIVEN** que no existe un paciente con DNI `99000001`
- **WHEN** se envía el registro de "Paciente Uno" con `aceptaMensajes` en `true`
- **THEN** la respuesta es `201`
- **AND** "Paciente Uno" tiene el consentimiento otorgado y un registro de historial con origen `REGISTRO`

#### Scenario: Registro sin indicar mensajes
- **GIVEN** que no existe un paciente con DNI `99000001`
- **WHEN** se envía el registro de "Paciente Uno" sin `aceptaMensajes`
- **THEN** la respuesta es `201`
- **AND** "Paciente Uno" queda sin consentimiento y sin historial

#### Scenario: Un registro rechazado no deja consentimiento
- **GIVEN** un paciente ya registrado con DNI `99000001`
- **WHEN** se envía otro registro con el DNI `99000001` y `aceptaMensajes` en `true`
- **THEN** la respuesta es el rechazo del DNI repetido
- **AND** no se agrega ningún registro de historial
