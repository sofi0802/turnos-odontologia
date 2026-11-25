## MODIFIED Requirements

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
