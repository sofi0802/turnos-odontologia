# frontend-navegacion Specification

## Purpose
Definir la estructura navegable de la aplicación web del consultorio: qué páginas existen, cómo se pasa de una a otra sin recargar y cómo se adapta la navegación a celulares y computadoras. Las pantallas con datos (agenda, reserva, configuración) se suman en changes posteriores sobre esta estructura.

## Requirements

### Requirement: Páginas disponibles
La aplicación SHALL ofrecer cuatro páginas de menú, cada una con su dirección: Agenda (`#/agenda`) y Configuración (`#/configuracion`) para el personal, y Reservar turno (`#/reserva`) y Mis turnos (`#/mis-turnos`) para el paciente. Además ofrece la portada `#/inicio` y la cuenta `#/mi-cuenta`, que no figuran en el menú; `#/mi-cuenta` es la única página de ingreso, para pacientes y personal. La dirección vieja del ingreso del personal, `#/ingreso`, MUST llevar a `#/mi-cuenta`.

#### Scenario: Abrir la página de reserva por su dirección
- **WHEN** se abre la aplicación en la dirección `#/reserva`
- **THEN** se muestra la página con el título "Reservar turno"

#### Scenario: Abrir la portada por su dirección
- **WHEN** se abre la aplicación en la dirección `#/inicio`
- **THEN** se muestra la portada
- **AND** el menú no tiene un enlace a "Inicio"

#### Scenario: Abrir la página de ingreso por su dirección
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la aplicación en la dirección `#/ingreso`
- **THEN** se muestra la página con el título "Mi cuenta"
- **AND** la dirección pasa a ser `#/mi-cuenta`

#### Scenario: Abrir la cuenta del paciente por su dirección
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la aplicación en la dirección `#/mi-cuenta`
- **THEN** se muestra la página con el título "Mi cuenta"
- **AND** el menú no tiene un enlace a "Mi cuenta"

### Requirement: Página inicial y direcciones desconocidas
Sin dirección, o con una que no corresponde a ninguna página ni a la dirección vieja `#/ingreso`, la aplicación SHALL mostrar la portada y actualizar la dirección a `#/inicio`, con o sin sesión.

#### Scenario: Abrir la aplicación sin dirección
- **WHEN** se abre la aplicación sin dirección de página
- **THEN** se muestra la portada
- **AND** la dirección pasa a ser `#/inicio`

#### Scenario: Dirección desconocida
- **WHEN** se abre la aplicación en la dirección `#/no-existe`
- **THEN** se muestra la portada
- **AND** la dirección pasa a ser `#/inicio`

#### Scenario: Abrir la aplicación sin dirección con sesión del personal
- **GIVEN** una sesión de `recepcion` con rol `RECEPCIONISTA`
- **WHEN** se abre la aplicación sin dirección de página
- **THEN** se muestra la portada en `#/inicio`
- **AND** la portada ofrece "Ir a la agenda"

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

### Requirement: Página "Pacientes" para odontólogos y administradores
La aplicación SHALL ofrecer la página "Pacientes" en `#/pacientes`, con su opción en la barra lateral (después de "Agenda") solo con sesión `ADMIN` u `ODONTOLOGO`. Con sesión `RECEPCIONISTA` o `PACIENTE`, abrir la dirección MUST mostrar "Esta sección es solo para odontólogos y administradores." sin pedir ningún dato de pacientes. Sin sesión, lleva a Mi cuenta, como las demás páginas privadas.

#### Scenario: Menú del odontólogo
- **GIVEN** la sesión de `ana.ejemplo` con rol `ODONTOLOGO`
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** la barra lateral tiene los enlaces "Agenda", "Pacientes" y "Configuración", en ese orden

#### Scenario: Menú de la recepción
- **GIVEN** la sesión de `recepcion` con rol `RECEPCIONISTA`
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** la barra lateral no tiene el enlace "Pacientes"

#### Scenario: La recepción abre la dirección
- **GIVEN** la sesión de `recepcion`
- **WHEN** se abre la aplicación en `#/pacientes`
- **THEN** se muestra "Esta sección es solo para odontólogos y administradores."
- **AND** no se pide `GET /api/v1/pacientes` ni ninguna ficha

#### Scenario: Sin sesión
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la aplicación en `#/pacientes`
- **THEN** se muestra "Mi cuenta"
