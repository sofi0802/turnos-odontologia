# Spec Delta

## ADDED Requirements

### Requirement: Profesional del odontólogo en la sesión
Cuando el usuario es un `ODONTOLOGO` vinculado a un profesional, la respuesta de `POST /api/v1/public/login` y la de `GET /api/v1/public/sesion` MUST incluir `profesionalId` con el id de ese profesional. Para los demás usuarios, y sin sesión, MUST NOT incluirlo.

#### Scenario: Login de un odontólogo vinculado a un profesional
- **GIVEN** el usuario `ana.ejemplo` (rol `ODONTOLOGO`), vinculado a la Dra. Ana Ejemplo (id 1), con su contraseña correcta
- **WHEN** envía `POST /api/v1/public/login` con esas credenciales
- **THEN** la respuesta es `200` con `rol` igual a `ODONTOLOGO` y `profesionalId` igual a `1`

#### Scenario: Sesión de un odontólogo
- **GIVEN** el usuario `ana.ejemplo` con sesión iniciada
- **WHEN** pide `GET /api/v1/public/sesion`
- **THEN** la respuesta es `200` con `rol` igual a `ODONTOLOGO` y `profesionalId` igual a `1`

#### Scenario: Sesión de recepción sin profesional
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `GET /api/v1/public/sesion`
- **THEN** la respuesta es `200` y no incluye `profesionalId`
