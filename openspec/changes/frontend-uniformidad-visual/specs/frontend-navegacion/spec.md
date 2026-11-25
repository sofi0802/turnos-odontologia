# Spec Delta

## MODIFIED Requirements

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
La barra lateral SHALL mostrar al personal "Agenda", "Configuración" y las páginas de su rol ("Pacientes" para `ADMIN` y `ODONTOLOGO`; "Reportes" y "Caja" para `ADMIN` y `RECEPCIONISTA`), y al paciente "Reservar turno" y "Mis turnos". Sin sesión MUST NOT haber barra lateral: el encabezado ofrece "Reservar turno" y un único botón "Ingresar" a `#/mi-cuenta`, también en la portada. Al iniciar o cerrar una sesión, la barra lateral MUST actualizarse sin recargar el documento.

#### Scenario: Menú de un visitante
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la aplicación en `#/reserva`
- **THEN** no hay barra lateral
- **AND** el encabezado tiene el enlace "Reservar turno" y el enlace "Ingresar" a `#/mi-cuenta`

#### Scenario: Menú del personal
- **GIVEN** una sesión de `recepcion` con rol `RECEPCIONISTA`
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** la barra lateral tiene solo los enlaces "Agenda", "Reportes", "Caja" y "Configuración", en ese orden
- **AND** el enlace de la página abierta, "Agenda", está marcado como activo

#### Scenario: El menú cambia al ingresar como paciente
- **GIVEN** la página `#/mi-cuenta` abierta sin sesión
- **WHEN** "Paciente Uno" ingresa correctamente
- **THEN** aparece la barra lateral con los enlaces "Reservar turno" y "Mis turnos"
- **AND** el encabezado muestra `paciente.uno@example.com` y "Salir" en lugar de "Ingresar"
