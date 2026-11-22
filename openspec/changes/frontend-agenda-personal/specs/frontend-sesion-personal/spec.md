# Spec Delta

## Purpose

Definir cómo el personal del consultorio entra y sale de la aplicación web, y cómo las pantallas privadas verifican la sesión y el rol antes de mostrar datos. En los escenarios, con datos ficticios, existen el usuario `recepcion` (rol `RECEPCIONISTA`) y la cuenta de paciente `paciente.uno@example.com` (rol `PACIENTE`).

## ADDED Requirements

### Requirement: Página de ingreso del personal
La aplicación SHALL ofrecer la página de ingreso en `#/ingreso`, con los campos usuario y contraseña. Al enviarla con los dos completos, SHALL pedir el token CSRF con `GET /api/v1/public/sesion` si todavía no existe, enviar `POST /api/v1/public/login` y, si responde `200` con un rol del personal, mostrar la página Agenda.

#### Scenario: Recepción ingresa con credenciales correctas
- **GIVEN** la página de ingreso abierta sin sesión
- **WHEN** se ingresa el usuario `recepcion` con su contraseña correcta y se envía el formulario
- **THEN** se envía `POST /api/v1/public/login` con `username` `recepcion` y la contraseña
- **AND** se muestra la página "Agenda" en la dirección `#/agenda`

#### Scenario: Campos vacíos
- **GIVEN** la página de ingreso abierta
- **WHEN** se envía el formulario con la contraseña vacía
- **THEN** no se envía ningún pedido de login
- **AND** el campo contraseña muestra el mensaje "Completá la contraseña."

### Requirement: Credenciales rechazadas
Si el login responde `401` con el código `CREDENCIALES_INVALIDAS`, la página de ingreso MUST mostrar el mensaje "Usuario o contraseña incorrectos." sin indicar cuál de los dos falló, conservar el usuario escrito y vaciar la contraseña.

#### Scenario: Contraseña incorrecta
- **GIVEN** la página de ingreso abierta
- **WHEN** se ingresa el usuario `recepcion` con una contraseña incorrecta y la API responde `401` con el código `CREDENCIALES_INVALIDAS`
- **THEN** se muestra "Usuario o contraseña incorrectos."
- **AND** el campo usuario sigue con `recepcion` y el campo contraseña queda vacío

### Requirement: Páginas privadas solo con sesión del personal
Al abrir la página Agenda, la aplicación SHALL consultar `GET /api/v1/public/sesion`. Sin sesión, MUST llevar a `#/ingreso` sin pedir datos de turnos. Con una sesión de rol `PACIENTE`, MUST mostrar el aviso "Esta sección es solo para el personal del consultorio." sin pedir datos de turnos.

#### Scenario: Abrir la agenda sin sesión
- **GIVEN** que `GET /api/v1/public/sesion` informa que no hay sesión
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** se muestra la página de ingreso en la dirección `#/ingreso`
- **AND** no se envía ningún pedido a `/api/v1/turnos`

#### Scenario: Un paciente abre la agenda
- **GIVEN** una sesión de `paciente.uno@example.com` con rol `PACIENTE`
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** se muestra "Esta sección es solo para el personal del consultorio."
- **AND** no se envía ningún pedido a `/api/v1/turnos`

### Requirement: Sesión vencida
Si un pedido de una página privada recibe `401`, la aplicación MUST llevar a `#/ingreso` con el aviso "Tu sesión terminó. Ingresá de nuevo." Si recibe `403` con el código `ACCESO_DENEGADO`, MUST mostrar "No tenés permiso para esta acción." sin salir de la página.

#### Scenario: La sesión vence mientras se usa la agenda
- **GIVEN** la agenda abierta con una sesión de `recepcion`
- **WHEN** se pide la semana siguiente y la API responde `401`
- **THEN** se muestra la página de ingreso con el aviso "Tu sesión terminó. Ingresá de nuevo."

#### Scenario: Acción sin permiso
- **GIVEN** la agenda abierta con una sesión del personal
- **WHEN** se crea un turno y la API responde `403` con el código `ACCESO_DENEGADO`
- **THEN** se muestra "No tenés permiso para esta acción."
- **AND** la agenda sigue a la vista

### Requirement: Cierre de sesión
Con una sesión del personal, el encabezado SHALL mostrar el usuario y un botón "Salir". Al usarlo, la aplicación SHALL enviar `POST /api/v1/logout` con el token CSRF y mostrar la página de ingreso aunque la respuesta falle.

#### Scenario: Recepción cierra la sesión
- **GIVEN** la agenda abierta con una sesión de `recepcion`
- **WHEN** se usa el botón "Salir"
- **THEN** se envía `POST /api/v1/logout` con el encabezado `X-XSRF-TOKEN`
- **AND** se muestra la página de ingreso
- **AND** el encabezado ya no muestra el usuario `recepcion`
