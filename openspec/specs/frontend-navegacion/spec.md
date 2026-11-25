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
Con una sesión iniciada, el menú SHALL ser una barra lateral: en pantallas de menos de 768 px, un cajón oculto detrás del botón "Menú" (que informa `aria-expanded`) y que se cierra al elegir una página, con "Escape" o al tocar fuera; desde 768 px, siempre visible a la izquierda. Desde 320 px la página MUST NOT tener desplazamiento horizontal.

#### Scenario: Abrir el menú en el celular
- **GIVEN** la aplicación abierta con una sesión de `paciente.uno@example.com` en una pantalla de 390 × 844 px con el menú cerrado
- **WHEN** se toca el botón "Menú"
- **THEN** el cajón se ve con los enlaces "Reservar turno" y "Mis turnos" y no con los de "Agenda" ni "Configuración"
- **AND** el botón informa que el menú está expandido

#### Scenario: Elegir una página en el celular
- **GIVEN** el cajón abierto con una sesión de paciente en una pantalla de 390 × 844 px
- **WHEN** se elige el enlace "Reservar turno"
- **THEN** se muestra la página "Reservar turno"
- **AND** el cajón se cierra

#### Scenario: Cerrar el cajón con Escape o tocando fuera
- **GIVEN** el cajón abierto en una pantalla de 390 × 844 px
- **WHEN** se oprime "Escape"
- **THEN** el cajón se cierra y el botón "Menú" informa que está contraído
- **AND** tocar fuera del cajón con el cajón abierto también lo cierra

#### Scenario: Menú siempre visible en la computadora
- **WHEN** se abre la aplicación con una sesión del personal en una pantalla de 1440 × 900 px
- **THEN** los enlaces "Agenda" y "Configuración" se ven en la barra lateral sin tocar ningún botón
- **AND** la página no tiene desplazamiento horizontal

### Requirement: Menú según la sesión
La barra lateral SHALL mostrar al personal "Agenda", "Configuración" y las páginas de su rol, en este orden: "Agenda", "Lista de espera", "Pacientes", "Coberturas", "Reportes", "Caja" y "Configuración" ("Lista de espera", "Coberturas", "Reportes" y "Caja" para `ADMIN` y `RECEPCIONISTA`; "Pacientes" para `ADMIN` y `ODONTOLOGO`). Al paciente le SHALL mostrar "Reservar turno", "Mis turnos" y "Mi cuenta". Sin sesión MUST NOT haber barra lateral: el encabezado ofrece "Reservar turno" y un único botón "Ingresar" a `#/mi-cuenta`, también en la portada. Al iniciar o cerrar una sesión, la barra lateral MUST actualizarse sin recargar el documento.

#### Scenario: Menú de un visitante
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la aplicación en `#/reserva`
- **THEN** no hay barra lateral
- **AND** el encabezado tiene el enlace "Reservar turno" y el enlace "Ingresar" a `#/mi-cuenta`

#### Scenario: Menú del personal
- **GIVEN** una sesión de `recepcion` con rol `RECEPCIONISTA`
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** la barra lateral tiene solo los enlaces "Agenda", "Lista de espera", "Coberturas", "Reportes", "Caja" y "Configuración", en ese orden
- **AND** el enlace de la página abierta, "Agenda", está marcado como activo

#### Scenario: El menú cambia al ingresar como paciente
- **GIVEN** la página `#/mi-cuenta` abierta sin sesión
- **WHEN** "Paciente Uno" ingresa correctamente
- **THEN** aparece la barra lateral con los enlaces "Reservar turno", "Mis turnos" y "Mi cuenta"
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

### Requirement: Sección de la cuenta en el menú del personal
Con una sesión del personal, la barra lateral SHALL mostrar, debajo del menú principal y en una navegación aparte llamada "Tu cuenta", el enlace "Mi contraseña" a `#/cambiar-contrasena`, marcado como página actual cuando está abierta. Los enlaces del menú principal no cambian. Un paciente o un visitante MUST NOT ver esta sección.

#### Scenario: El personal ve "Mi contraseña" aparte
- **GIVEN** una sesión de `recepcion` con rol `RECEPCIONISTA`
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** el menú principal tiene solo los enlaces "Agenda" y "Configuración"
- **AND** la navegación "Tu cuenta" tiene el enlace "Mi contraseña"

#### Scenario: Un paciente no ve la sección de la cuenta
- **GIVEN** una sesión de `paciente.uno@example.com` con rol `PACIENTE`
- **WHEN** se abre la aplicación en `#/mis-turnos`
- **THEN** la barra lateral no muestra "Mi contraseña"

### Requirement: Página "Reportes" para administración y recepción
La aplicación SHALL ofrecer la página "Reportes" en `#/reportes`, con su opción en la barra lateral después de "Pacientes" (o de "Agenda" si no está "Pacientes") solo con sesión `ADMIN` o `RECEPCIONISTA`. Con sesión `ODONTOLOGO` o `PACIENTE`, abrir la dirección MUST mostrar "Esta sección es solo para administración y recepción." sin pedir los reportes. Sin sesión, lleva a Mi cuenta, como las demás páginas privadas.

#### Scenario: Menú del administrador
- **GIVEN** la sesión de `admin` con rol `ADMIN`
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** la barra lateral tiene los enlaces "Agenda", "Pacientes", "Reportes", "Caja" y "Configuración", en ese orden

#### Scenario: El odontólogo no ve Reportes
- **GIVEN** la sesión de `ana.ejemplo` con rol `ODONTOLOGO`
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** la barra lateral no tiene el enlace "Reportes"

#### Scenario: El odontólogo abre la dirección
- **GIVEN** la sesión de `ana.ejemplo`
- **WHEN** se abre la aplicación en `#/reportes`
- **THEN** se muestra "Esta sección es solo para administración y recepción."
- **AND** no se pide `GET /api/v1/reportes`

#### Scenario: Sin sesión en Reportes
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la aplicación en `#/reportes`
- **THEN** se muestra "Mi cuenta"

### Requirement: Página "Caja" para administración y recepción
La aplicación SHALL ofrecer la página "Caja" en `#/caja`, con su opción en la barra lateral después de "Reportes", solo con sesión `ADMIN` o `RECEPCIONISTA`. Con sesión `ODONTOLOGO` o `PACIENTE`, abrir la dirección MUST mostrar "Esta sección es solo para administración y recepción." sin pedir datos de la caja. Sin sesión, lleva a Mi cuenta.

#### Scenario: El odontólogo no ve Caja
- **GIVEN** la sesión de `ana.ejemplo` con rol `ODONTOLOGO`
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** la barra lateral no tiene el enlace "Caja"

#### Scenario: El odontólogo abre la dirección de la caja
- **GIVEN** la sesión de `ana.ejemplo`
- **WHEN** se abre la aplicación en `#/caja`
- **THEN** se muestra "Esta sección es solo para administración y recepción."
- **AND** no se pide `GET /api/v1/cajas/abierta`

#### Scenario: Sin sesión en Caja
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la aplicación en `#/caja`
- **THEN** se muestra "Mi cuenta"
