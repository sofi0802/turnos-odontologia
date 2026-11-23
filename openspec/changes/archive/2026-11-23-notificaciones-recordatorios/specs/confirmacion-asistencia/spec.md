# Spec Delta

## Purpose

Permitir que el paciente confirme su asistencia desde el link del recordatorio, sin iniciar sesión, con un token de un solo uso, para que el consultorio sepa que va a ir (US-031). En los escenarios, con datos ficticios, "Paciente Uno" (DNI `99000001`) tiene un turno `RESERVADO` con la Dra. A en el Sillón 1 el 2026-12-02 a las 07:30 y recibió un recordatorio con un token.

## ADDED Requirements

### Requirement: Confirmar la asistencia con el token del recordatorio
El sistema SHALL pasar un turno `RESERVADO` a `CONFIRMADO` cuando se presenta el token de su recordatorio, sin exigir sesión iniciada.

#### Scenario: Confirmación con un token válido
- **GIVEN** un turno `RESERVADO` de "Paciente Uno" con un recordatorio enviado y su token sin usar
- **WHEN** se pide `POST /api/v1/public/confirmaciones/{token}` sin sesión
- **THEN** la respuesta es `200`
- **AND** el turno queda `CONFIRMADO`

#### Scenario: La respuesta solo trae lo necesario
- **GIVEN** el mismo turno y token
- **WHEN** se confirma
- **THEN** la respuesta incluye la fecha, la hora, el nombre del profesional y el estado `CONFIRMADO`
- **AND** no incluye el DNI, el email, el teléfono, la prestación ni datos de otros pacientes

### Requirement: El token es de un solo uso
El sistema SHALL aceptar cada token una única vez. Un segundo uso MUST responder `409` con el código `TOKEN_USADO` y no cambiar nada.

#### Scenario: Segundo uso del mismo token
- **GIVEN** un token que ya confirmó el turno
- **WHEN** se pide otra vez `POST /api/v1/public/confirmaciones/{token}`
- **THEN** la respuesta es `409` con el código `TOKEN_USADO`
- **AND** el turno sigue `CONFIRMADO` y no se crea un segundo registro de auditoría

### Requirement: Tokens inexistentes y turnos que ya no se pueden confirmar
El sistema SHALL responder `404` con el código `TOKEN_INEXISTENTE` si el token no existe, y `409` con el código `TURNO_NO_CONFIRMABLE` si el turno ya no está `RESERVADO`.

#### Scenario: Token que no existe
- **WHEN** se pide `POST /api/v1/public/confirmaciones/{token}` con un token que nunca se emitió
- **THEN** la respuesta es `404` con el código `TOKEN_INEXISTENTE`
- **AND** no cambia ningún turno

#### Scenario: Turno cancelado después del recordatorio
- **GIVEN** un turno de "Paciente Uno" que se canceló después de enviarse el recordatorio, con su token sin usar
- **WHEN** se intenta confirmar con ese token
- **THEN** la respuesta es `409` con el código `TURNO_NO_CONFIRMABLE`
- **AND** el turno sigue `CANCELADO`

#### Scenario: Turno ya confirmado
- **GIVEN** un turno que ya está `CONFIRMADO`, con un token sin usar
- **WHEN** se intenta confirmar con ese token
- **THEN** la respuesta es `409` con el código `TURNO_NO_CONFIRMABLE`

### Requirement: El token no se guarda en claro
El sistema SHALL generar tokens aleatorios de al menos 128 bits y guardar solo su huella, de modo que el token que viaja en el link no pueda leerse de la base.

#### Scenario: El token no aparece en la base
- **GIVEN** un recordatorio enviado con su link de confirmación
- **WHEN** se consulta la tabla de notificaciones
- **THEN** ningún valor guardado es igual al token del link

### Requirement: Endpoint público con CSRF activo
El endpoint de confirmación SHALL ser público, pero MUST seguir exigiendo la protección CSRF y MUST NOT aceptar otros métodos que `POST`.

#### Scenario: Confirmación sin el token CSRF
- **GIVEN** un token válido sin usar
- **WHEN** se pide `POST /api/v1/public/confirmaciones/{token}` sin el encabezado CSRF
- **THEN** la respuesta es `403`
- **AND** el turno sigue `RESERVADO`

#### Scenario: Confirmar con GET no cambia nada
- **GIVEN** un token válido sin usar
- **WHEN** se pide `GET /api/v1/public/confirmaciones/{token}`
- **THEN** el turno sigue `RESERVADO` y el token sigue sin usar
