# Spec Delta

## ADDED Requirements

### Requirement: Casilla de mensajes en el registro
El formulario de registro de `#/mi-cuenta` SHALL tener la casilla "Acepto recibir mensajes del consultorio sobre mis turnos (fecha, hora y links, sin datos de salud)", sin marcar por defecto. El pedido de registro MUST enviar `aceptaMensajes` con el valor de la casilla. Dejarla sin marcar MUST NOT impedir el registro.

#### Scenario: Registro con la casilla marcada
- **GIVEN** la página `#/mi-cuenta` abierta sin sesión
- **WHEN** se completa el registro de "Paciente Uno" con datos válidos, se marca la casilla de mensajes y se envía
- **THEN** se envía `POST /api/v1/public/pacientes/registro` con `aceptaMensajes` en `true`

#### Scenario: Registro sin marcar la casilla
- **GIVEN** la página `#/mi-cuenta` abierta sin sesión
- **WHEN** se completa el registro con datos válidos sin marcar la casilla y se envía
- **THEN** se envía el registro con `aceptaMensajes` en `false`

### Requirement: Datos del paciente en Mi cuenta
La página "Mi cuenta" del paciente con sesión SHALL empezar con la sección "Tus datos": nombre, apellido, DNI, email y teléfono, de solo lectura, obtenidos de `GET /api/v1/paciente/perfil`, seguida de "Mensajes del consultorio" y después de las demás secciones de la página. Un teléfono vacío MUST mostrarse como "Sin teléfono". Si el perfil no se puede cargar, la sección MUST mostrar un error con "Reintentar" sin ocultar las demás.

#### Scenario: El paciente ve sus datos
- **GIVEN** una sesión de `paciente.uno@example.com`
- **WHEN** se abre la aplicación en `#/mi-cuenta`
- **THEN** la página "Mi cuenta" empieza con "Tus datos", con "Paciente Uno", el DNI `99000001` y el email `paciente.uno@example.com`
- **AND** los datos no se pueden editar y después siguen "Mensajes del consultorio" y "Mi cobertura"

#### Scenario: Falla al cargar el perfil
- **GIVEN** una sesión de `paciente.uno@example.com` y `GET /api/v1/paciente/perfil` respondiendo con un error
- **WHEN** se abre `#/mi-cuenta`
- **THEN** "Tus datos" muestra "No pudimos cargar tus datos." con el botón "Reintentar"
- **AND** "Mensajes del consultorio" y "Mi cobertura" se siguen mostrando
