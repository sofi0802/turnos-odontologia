# frontend-portada Specification

## Purpose

Dar a quien abre la aplicación una pantalla de inicio que explique qué es el consultorio y cómo reservar un turno, con acceso directo a reservar, a la cuenta y al acceso del personal. En los escenarios, con datos ficticios, "Paciente Uno" (`paciente.uno@example.com`) es un paciente y `recepcion` es una recepcionista del personal.

## Requirements

### Requirement: Portada pública
La aplicación SHALL mostrar en `#/inicio`, sin sesión, una portada con el nombre del consultorio, una descripción breve de la aplicación y los tres pasos para reservar un turno, y MUST NOT pedir ningún dato ni enviar ningún pedido que modifique algo.

#### Scenario: Abrir la portada sin sesión
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la aplicación en `#/inicio`
- **THEN** se muestra la portada con el título "Turnos odontología"
- **AND** se ven los tres pasos para reservar un turno
- **AND** la portada tiene un único título principal (`h1`)

#### Scenario: La portada no envía datos
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la portada
- **THEN** el único pedido a la API es `GET /api/v1/public/sesion`

### Requirement: Botones de la portada
La portada SHALL ofrecer, sin sesión, el botón "Reservar turno", que lleva a `#/reserva` sin pedir registro, y el botón "Ingresar a mi cuenta", que lleva a `#/mi-cuenta`.

#### Scenario: Reservar sin registrarse
- **GIVEN** la portada abierta sin sesión
- **WHEN** se elige "Reservar turno"
- **THEN** se muestra la página "Reservar turno" en `#/reserva`
- **AND** no se pidió ningún dato de cuenta

#### Scenario: Ingresar a la cuenta
- **GIVEN** la portada abierta sin sesión
- **WHEN** se elige "Ingresar a mi cuenta"
- **THEN** se muestra la página "Mi cuenta" en `#/mi-cuenta`

### Requirement: Acceso del personal
La portada SHALL mostrar arriba a la derecha, en el encabezado, el botón "Ingresar" (el mismo de las demás páginas sin sesión), que lleva a `#/mi-cuenta`, por donde también ingresa el personal, también en pantallas de celular.

#### Scenario: El personal entra desde la portada
- **GIVEN** la portada abierta sin sesión
- **WHEN** se elige "Ingresar"
- **THEN** se muestra la página "Mi cuenta" en `#/mi-cuenta`

#### Scenario: El botón del personal en el celular
- **WHEN** se abre la portada en una pantalla de 390 × 844 px
- **THEN** "Ingresar" sigue visible en la parte superior derecha, con una altura de al menos 44 px
- **AND** la portada no tiene desplazamiento horizontal

### Requirement: Portada con sesión iniciada
Con una sesión iniciada, la portada MUST NOT redirigir sola y SHALL ofrecer ir al espacio del rol: "Reservar turno" y "Mis turnos" para el paciente, y "Ir a la agenda" para el personal. No MUST mostrar "Ingresar a mi cuenta" ni "Ingresar" en el encabezado.

#### Scenario: Paciente con sesión en la portada
- **GIVEN** una sesión de `paciente.uno@example.com` con rol `PACIENTE`
- **WHEN** se abre la aplicación en `#/inicio`
- **THEN** se ven los botones "Reservar turno" y "Mis turnos"
- **AND** no se ven "Ingresar a mi cuenta" ni "Ingresar" en el encabezado
- **AND** la dirección sigue siendo `#/inicio`

#### Scenario: Personal con sesión en la portada
- **GIVEN** una sesión de `recepcion` con rol `RECEPCIONISTA`
- **WHEN** se abre la aplicación en `#/inicio`
- **THEN** se ve el botón "Ir a la agenda", que lleva a `#/agenda`
- **AND** no se ven "Reservar turno", "Ingresar a mi cuenta" ni "Ingresar" en el encabezado

### Requirement: El logo lleva a la portada
El nombre y el logo del encabezado SHALL ser un enlace a `#/inicio` en todas las páginas, con o sin sesión.

#### Scenario: Volver a la portada desde otra página
- **GIVEN** la página "Reservar turno" abierta sin sesión
- **WHEN** se elige el nombre "Turnos odontología" del encabezado
- **THEN** se muestra la portada en `#/inicio`

### Requirement: Solo recursos propios
La portada MUST usar ilustraciones propias en línea, sin fotos de personas, y MUST NOT pedir imágenes, fuentes ni scripts a otros orígenes. Las ilustraciones decorativas MUST estar ocultas para los lectores de pantalla.

#### Scenario: Sin recursos de otros orígenes
- **WHEN** se abre la portada
- **THEN** ningún pedido de la página va a un origen distinto del de la aplicación y la API
- **AND** las ilustraciones tienen `aria-hidden="true"`
