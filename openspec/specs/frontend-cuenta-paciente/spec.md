# frontend-cuenta-paciente Specification

## Purpose

Definir cómo el paciente crea su cuenta, entra y sale de la aplicación web en `#/mi-cuenta`, que es también el ingreso del personal, y cómo sus páginas verifican que la sesión sea de un paciente antes de mostrar datos. En los escenarios, con datos ficticios, "Paciente Uno" (DNI `99000001`, email `paciente.uno@example.com`) es un paciente y `recepcion` es un usuario `RECEPCIONISTA`.

## Requirements

### Requirement: Ingreso del paciente
La aplicación SHALL ofrecer en `#/mi-cuenta`, sin sesión, un formulario para ingresar con email y contraseña. Al enviarlo con los dos completos, SHALL pedir el token CSRF con `GET /api/v1/public/sesion` si todavía no existe y enviar `POST /api/v1/public/login`. Si responde `200` con el rol `PACIENTE`, MUST mostrar la página "Mis turnos", salvo que haya una reserva pendiente de confirmar.

#### Scenario: Ingreso correcto
- **GIVEN** la página `#/mi-cuenta` abierta sin sesión
- **WHEN** se ingresa `paciente.uno@example.com` con su contraseña correcta y se envía
- **THEN** se envía `POST /api/v1/public/login` con `username` `paciente.uno@example.com`
- **AND** se muestra la página "Mis turnos" en la dirección `#/mis-turnos`

#### Scenario: Campos vacíos en el ingreso
- **GIVEN** la página `#/mi-cuenta` abierta sin sesión
- **WHEN** se envía el ingreso con el email vacío
- **THEN** no se envía ningún pedido de login
- **AND** el campo email muestra el mensaje "Completá el email."

### Requirement: Credenciales del paciente rechazadas
Si el login responde `401`, la página MUST mostrar "Email o contraseña incorrectos." sin indicar cuál de los dos falló, conservar el email escrito y vaciar la contraseña.

#### Scenario: Contraseña incorrecta
- **GIVEN** la página `#/mi-cuenta` abierta sin sesión
- **WHEN** se ingresa `paciente.uno@example.com` con una contraseña incorrecta y la API responde `401` con el código `CREDENCIALES_INVALIDAS`
- **THEN** se muestra "Email o contraseña incorrectos."
- **AND** el email sigue escrito y la contraseña queda vacía

### Requirement: Cuenta del personal en el ingreso del paciente
Si el login desde `#/mi-cuenta` responde `200` con un rol del personal, la aplicación MUST mostrar la página "Agenda" en lugar de las páginas del paciente.

#### Scenario: Recepción ingresa desde la página del paciente
- **GIVEN** la página `#/mi-cuenta` abierta sin sesión
- **WHEN** se ingresa `recepcion` con su contraseña correcta y la API responde `200` con el rol `RECEPCIONISTA`
- **THEN** se muestra la página "Agenda" en la dirección `#/agenda`

### Requirement: Registro del paciente
`#/mi-cuenta` SHALL ofrecer crear la cuenta con nombre, apellido, DNI, email, teléfono opcional, contraseña y su repetición. Antes de enviar, MUST marcar en su campo los obligatorios vacíos, un DNI que no tenga 7 u 8 dígitos, una contraseña de menos de 8 caracteres y una repetición distinta, sin enviar el pedido. Con datos válidos SHALL enviar `POST /api/v1/public/pacientes/registro` sin la repetición.

#### Scenario: Registro con datos válidos
- **GIVEN** la página `#/mi-cuenta` abierta sin sesión
- **WHEN** se completa el registro de "Paciente Uno" con DNI `99000001`, email `paciente.uno@example.com` y la misma contraseña en los dos campos, y se envía
- **THEN** se envía `POST /api/v1/public/pacientes/registro` con nombre, apellido, `dni` `99000001`, `email` `paciente.uno@example.com` y la contraseña
- **AND** el pedido no incluye la repetición de la contraseña

#### Scenario: DNI con formato inválido
- **GIVEN** la página `#/mi-cuenta` abierta sin sesión
- **WHEN** se completa el registro con el DNI `12345` y se envía
- **THEN** no se envía ningún pedido de registro
- **AND** el campo DNI muestra "El DNI debe tener 7 u 8 dígitos."

#### Scenario: Contraseñas distintas
- **GIVEN** la página `#/mi-cuenta` abierta sin sesión
- **WHEN** se completa el registro con contraseñas distintas en los dos campos y se envía
- **THEN** no se envía ningún pedido de registro
- **AND** el campo de repetición muestra "Las contraseñas no coinciden."

### Requirement: Sesión iniciada después del registro
Como el registro no inicia la sesión, cuando responde `201` la aplicación SHALL enviar `POST /api/v1/public/login` con el email y la contraseña recién registrados y seguir como en un ingreso correcto. Si ese login falla, MUST mostrar "Tu cuenta se creó. Ingresá con tu email y tu contraseña." en el formulario de ingreso.

#### Scenario: Registro e ingreso seguidos
- **GIVEN** la página `#/mi-cuenta` abierta sin sesión
- **WHEN** se registra "Paciente Uno" y la API responde `201`
- **THEN** se envía `POST /api/v1/public/login` con `username` `paciente.uno@example.com`
- **AND** con el login correcto se muestra la página "Mis turnos"

#### Scenario: Falla el ingreso después del registro
- **GIVEN** que el registro de "Paciente Uno" respondió `201`
- **WHEN** el login siguiente responde con un error del servidor
- **THEN** se muestra "Tu cuenta se creó. Ingresá con tu email y tu contraseña."
- **AND** el formulario de ingreso tiene escrito `paciente.uno@example.com`

### Requirement: Rechazos del registro
Si el registro responde `409` con `DNI_YA_REGISTRADO` o `EMAIL_YA_REGISTRADO`, la aplicación MUST mostrar el mensaje en el campo DNI o email, sin borrar los datos escritos salvo las contraseñas. Si responde `422`, MUST mostrar cada error en el campo que indica la API.

#### Scenario: DNI ya registrado
- **GIVEN** la página `#/mi-cuenta` abierta sin sesión
- **WHEN** se registra con el DNI `99000001` y la API responde `409` con el código `DNI_YA_REGISTRADO`
- **THEN** el campo DNI muestra "Ya hay un paciente registrado con ese DNI. Si es tuyo, ingresá con tu cuenta o contactá al consultorio."
- **AND** el nombre, el apellido, el DNI y el email siguen escritos

#### Scenario: Email ya registrado
- **GIVEN** la página `#/mi-cuenta` abierta sin sesión
- **WHEN** se registra con el email `paciente.uno@example.com` y la API responde `409` con el código `EMAIL_YA_REGISTRADO`
- **THEN** el campo email muestra "Ya existe una cuenta con ese email. Ingresá con tu contraseña."

#### Scenario: Error de validación del servidor
- **GIVEN** la página `#/mi-cuenta` abierta sin sesión
- **WHEN** se registra y la API responde `422` con un error en el campo `email`
- **THEN** el campo email muestra el mensaje que envió la API

### Requirement: Páginas del paciente solo con sesión de paciente
Al abrir `#/mis-turnos`, la aplicación SHALL consultar `GET /api/v1/public/sesion`. Sin sesión, MUST mostrar `#/mi-cuenta` sin pedir turnos. Con una sesión del personal, MUST mostrar "Esta sección es para pacientes." sin pedir turnos.

#### Scenario: Mis turnos sin sesión
- **GIVEN** que `GET /api/v1/public/sesion` informa que no hay sesión
- **WHEN** se abre la aplicación en `#/mis-turnos`
- **THEN** se muestra la página de la cuenta del paciente en `#/mi-cuenta`
- **AND** no se envía ningún pedido a `/api/v1/paciente/turnos`

#### Scenario: El personal abre Mis turnos
- **GIVEN** una sesión de `recepcion`
- **WHEN** se abre la aplicación en `#/mis-turnos`
- **THEN** se muestra "Esta sección es para pacientes."
- **AND** no se envía ningún pedido a `/api/v1/paciente/turnos`

### Requirement: Cuenta del paciente con sesión iniciada
Al abrir `#/mi-cuenta` con una sesión de paciente, la aplicación MUST mostrar la página "Mis turnos" en lugar de los formularios de ingreso y registro.

#### Scenario: Paciente con sesión abre su cuenta
- **GIVEN** una sesión de `paciente.uno@example.com`
- **WHEN** se abre la aplicación en `#/mi-cuenta`
- **THEN** se muestra la página "Mis turnos" en `#/mis-turnos`

### Requirement: Sesión del paciente vencida
Si un pedido de las rutas del paciente recibe `401`, la aplicación MUST mostrar `#/mi-cuenta` con el aviso "Tu sesión terminó. Ingresá de nuevo." y conservar la reserva pendiente, si la había.

#### Scenario: La sesión vence en Mis turnos
- **GIVEN** la página "Mis turnos" abierta con una sesión de paciente
- **WHEN** se cancela un turno y la API responde `401`
- **THEN** se muestra `#/mi-cuenta` con el aviso "Tu sesión terminó. Ingresá de nuevo."

### Requirement: Salida del paciente
Con una sesión de paciente, el encabezado SHALL mostrar el usuario y "Salir". Al elegir "Salir", la aplicación SHALL enviar `POST /api/v1/logout` y mostrar la página "Reservar turno", aunque el cierre falle en el servidor, y MUST borrar la reserva pendiente.

#### Scenario: El paciente sale
- **GIVEN** la página "Mis turnos" abierta con una sesión de `paciente.uno@example.com`
- **WHEN** se elige "Salir"
- **THEN** se envía `POST /api/v1/logout`
- **AND** se muestra la página "Reservar turno" en `#/reserva` sin el usuario en el encabezado
