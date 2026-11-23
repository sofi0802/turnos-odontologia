# Spec Delta

## Purpose

Definir la pantalla a la que lleva el link de confirmación del recordatorio (C-11): el paciente confirma su asistencia con un botón, sin iniciar sesión, y ve cuándo es su turno o por qué no se pudo confirmar. En los escenarios, con datos ficticios, el recordatorio de un turno `RESERVADO` del 2026-12-02 a las 07:30 con "Ana Ejemplo" lleva el link `/confirmar/abc123`.

## ADDED Requirements

### Requirement: Pantalla de confirmación sin confirmar sola
Al abrir la aplicación en `/confirmar/{token}`, SHALL mostrar, sin pedir sesión, la página "Confirmar asistencia" con el botón "Confirmar mi asistencia", y MUST NOT enviar ningún pedido de confirmación hasta que se elija ese botón, porque los revisores de links abren las direcciones solos. El token MUST NOT aparecer en el texto de la página.

#### Scenario: Abrir el link del recordatorio
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la aplicación en la dirección `/confirmar/abc123`
- **THEN** se muestra la página "Confirmar asistencia" con el botón "Confirmar mi asistencia"
- **AND** no se envía ningún pedido a `/api/v1/public/confirmaciones`
- **AND** el texto de la página no contiene `abc123`

### Requirement: Asistencia confirmada
Al elegir "Confirmar mi asistencia", la aplicación SHALL pedir el token CSRF con `GET /api/v1/public/sesion` si todavía no existe y enviar `POST /api/v1/public/confirmaciones/{token}` una sola vez. Ante `200` MUST mostrar "Listo, confirmaste tu turno del {fecha larga} a las {hora} con {profesional}." y quitar el botón.

#### Scenario: Confirmación correcta
- **GIVEN** la página "Confirmar asistencia" abierta desde `/confirmar/abc123`
- **WHEN** se elige "Confirmar mi asistencia" y la API responde `200` con `fecha` `2026-12-02`, `hora` `07:30` y `profesional` "Ana Ejemplo"
- **THEN** se envió `POST /api/v1/public/confirmaciones/abc123`
- **AND** se muestra "Listo, confirmaste tu turno del miércoles 2 de diciembre a las 07:30 con Ana Ejemplo."
- **AND** ya no está el botón "Confirmar mi asistencia"

### Requirement: Confirmación rechazada
Si la confirmación responde con un error, la página MUST mostrar el mensaje del código y quitar el botón:
- `TOKEN_USADO`: "Este turno ya estaba confirmado."
- `TOKEN_INEXISTENTE`: "El link no es válido. Revisá que esté completo."
- `TURNO_NO_CONFIRMABLE`: "Este turno ya no se puede confirmar. Si tenés dudas, contactá al consultorio."

#### Scenario: Link ya usado
- **GIVEN** la página "Confirmar asistencia" abierta desde `/confirmar/abc123`
- **WHEN** se confirma y la API responde `409` con el código `TOKEN_USADO`
- **THEN** se muestra "Este turno ya estaba confirmado."

#### Scenario: Link inválido
- **GIVEN** la página "Confirmar asistencia" abierta desde `/confirmar/abc123`
- **WHEN** se confirma y la API responde `404` con el código `TOKEN_INEXISTENTE`
- **THEN** se muestra "El link no es válido. Revisá que esté completo."

#### Scenario: Turno que ya no se puede confirmar
- **GIVEN** la página "Confirmar asistencia" abierta desde `/confirmar/abc123`
- **WHEN** se confirma y la API responde `409` con el código `TURNO_NO_CONFIRMABLE`
- **THEN** se muestra "Este turno ya no se puede confirmar. Si tenés dudas, contactá al consultorio."
