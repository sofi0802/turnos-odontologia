# Spec Delta

## MODIFIED Requirements

### Requirement: Páginas disponibles
La aplicación SHALL ofrecer cuatro páginas de menú, cada una con su dirección: Agenda (`#/agenda`) y Configuración (`#/configuracion`) para el personal, y Reservar turno (`#/reserva`) y Mis turnos (`#/mis-turnos`) para el paciente. Además ofrece el ingreso del personal (`#/ingreso`) y la cuenta del paciente (`#/mi-cuenta`), que no figuran en el menú. Configuración sigue mostrando solo su título.

#### Scenario: Abrir la página de reserva por su dirección
- **WHEN** se abre la aplicación en la dirección `#/reserva`
- **THEN** se muestra la página con el título "Reservar turno"

#### Scenario: Abrir la página de ingreso por su dirección
- **WHEN** se abre la aplicación en la dirección `#/ingreso`
- **THEN** se muestra la página con el título "Ingresar"
- **AND** el menú no tiene un enlace a "Ingresar"

#### Scenario: Abrir la cuenta del paciente por su dirección
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la aplicación en la dirección `#/mi-cuenta`
- **THEN** se muestra la página con el título "Mi cuenta"
- **AND** el menú no tiene un enlace a "Mi cuenta"

### Requirement: Navegación sin recargar
El menú SHALL tener un enlace por cada página que corresponde a la sesión actual. Al elegir uno, la aplicación MUST mostrar esa página sin recargar el documento y marcar el enlace actual como la página activa para tecnologías de asistencia (`aria-current="page"`). Los botones Atrás y Adelante del navegador SHALL funcionar.

#### Scenario: Ir de Agenda a Configuración
- **GIVEN** la página "Agenda" abierta con una sesión del personal
- **WHEN** se elige el enlace "Configuración" del menú
- **THEN** se muestra la página "Configuración" sin recargar el documento
- **AND** solo el enlace "Configuración" queda marcado como página activa

#### Scenario: Volver con el botón Atrás
- **GIVEN** que se navegó de "Agenda" a "Configuración"
- **WHEN** se usa el botón Atrás del navegador
- **THEN** se muestra otra vez la página "Agenda"

#### Scenario: Ir de Reservar turno a Mis turnos
- **GIVEN** la página "Reservar turno" abierta con una sesión de paciente
- **WHEN** se elige el enlace "Mis turnos" del menú
- **THEN** se muestra la página "Mis turnos" sin recargar el documento
- **AND** solo el enlace "Mis turnos" queda marcado como página activa

### Requirement: Menú adaptable a pantallas chicas
En pantallas de menos de 768 px de ancho, el menú SHALL quedar oculto detrás de un botón que lo abre y lo cierra e informa su estado (`aria-expanded`); al elegir una página, el menú se cierra. Desde 768 px el menú SHALL verse siempre, sin botón. En ningún ancho desde 320 px la página MUST NOT tener desplazamiento horizontal.

#### Scenario: Abrir el menú en el celular
- **GIVEN** la aplicación abierta sin sesión en una pantalla de 390 × 844 px con el menú cerrado
- **WHEN** se toca el botón del menú
- **THEN** se ve el enlace "Reservar turno" y no el de "Mis turnos"
- **AND** el botón informa que el menú está expandido

#### Scenario: Elegir una página en el celular
- **GIVEN** el menú abierto sin sesión en una pantalla de 390 × 844 px
- **WHEN** se elige el enlace "Reservar turno"
- **THEN** se muestra la página "Reservar turno"
- **AND** el menú se cierra

#### Scenario: Menú siempre visible en la computadora
- **WHEN** se abre la aplicación con una sesión del personal en una pantalla de 1440 × 900 px
- **THEN** los enlaces "Agenda" y "Configuración" se ven sin tocar ningún botón
- **AND** la página no tiene desplazamiento horizontal

## ADDED Requirements

### Requirement: Menú según la sesión
El menú SHALL mostrar "Agenda" y "Configuración" con una sesión del personal, "Reservar turno" y "Mis turnos" con una sesión de paciente, y solo "Reservar turno" sin sesión. Al iniciar o cerrar una sesión, el menú MUST actualizarse sin recargar el documento. Sin sesión, el encabezado SHALL ofrecer además el enlace "Ingresar" a `#/mi-cuenta`.

#### Scenario: Menú de un visitante
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la aplicación en `#/reserva`
- **THEN** el menú tiene solo el enlace "Reservar turno"
- **AND** el encabezado tiene el enlace "Ingresar" a `#/mi-cuenta`

#### Scenario: Menú del personal
- **GIVEN** una sesión de `recepcion` con rol `RECEPCIONISTA`
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** el menú tiene solo los enlaces "Agenda" y "Configuración"

#### Scenario: El menú cambia al ingresar como paciente
- **GIVEN** la página `#/mi-cuenta` abierta sin sesión
- **WHEN** "Paciente Uno" ingresa correctamente
- **THEN** el menú pasa a tener los enlaces "Reservar turno" y "Mis turnos"
- **AND** el encabezado muestra `paciente.uno@example.com` y "Salir" en lugar de "Ingresar"
