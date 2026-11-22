# Spec Delta

## MODIFIED Requirements

### Requirement: Rechazos de negocio como 409
Cuando la agenda rechaza una operación, el sistema MUST responder `409` con un `ProblemDetail` que incluya la propiedad `codigo` con el código estable del rechazo (por ejemplo `SOLAPAMIENTO_PROFESIONAL`, `SOLAPAMIENTO_SILLON`, `MARGEN_INSUFICIENTE`, `INICIO_EN_EL_PASADO`, `RECURSO_INACTIVO`, `PROFESIONAL_NO_HABILITADO`, `DATOS_DE_RESERVA_INVALIDOS` o `TURNO_NO_ACTIVO`) y un detalle legible en español.

#### Scenario: Turno que choca con la agenda del profesional
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **AND** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** pide `POST /api/v1/turnos` con la Dra. A, el Sillón 2 e inicio a las 10:15
- **THEN** la respuesta es `409` con el código `SOLAPAMIENTO_PROFESIONAL`

#### Scenario: Turno contiguo sin margen
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **AND** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** pide `POST /api/v1/turnos` con el Dr. B, el Sillón 1 e inicio a las 10:30
- **THEN** la respuesta es `409` con el código `MARGEN_INSUFICIENTE`

#### Scenario: Turno con un profesional no habilitado
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **AND** que la Dra. A no está habilitada para "Tratamiento de conducto"
- **WHEN** pide `POST /api/v1/turnos` con la Dra. A, el Sillón 1, "Tratamiento de conducto" e inicio a las 10:00
- **THEN** la respuesta es `409` con el código `PROFESIONAL_NO_HABILITADO`
- **AND** no se crea ningún turno
