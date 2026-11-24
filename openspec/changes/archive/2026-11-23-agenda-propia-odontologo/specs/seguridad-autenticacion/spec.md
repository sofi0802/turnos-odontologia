## MODIFIED Requirements

### Requirement: Estado de la sesión
El sistema SHALL exponer `GET /api/v1/public/sesion`, que responde `200` indicando si hay una sesión iniciada y, si la hay, el usuario y el rol. Si el rol es `ODONTOLOGO`, la respuesta SHALL incluir también `profesionalId`, el profesional vinculado a ese usuario; para los demás roles MUST NOT incluirlo. Esta respuesta MUST incluir la cookie del token CSRF para que el frontend pueda enviar pedidos que modifican datos, incluido el login.

#### Scenario: Sin sesión iniciada
- **GIVEN** un navegador sin sesión
- **WHEN** pide `GET /api/v1/public/sesion`
- **THEN** la respuesta es `200` e indica que no hay sesión iniciada
- **AND** la respuesta fija la cookie `XSRF-TOKEN`

#### Scenario: Sesión de un odontólogo
- **GIVEN** el usuario `ana.ejemplo` (rol `ODONTOLOGO`), vinculado a la Dra. A, con sesión iniciada
- **WHEN** pide `GET /api/v1/public/sesion`
- **THEN** la respuesta es `200` con `rol` igual a `ODONTOLOGO` y `profesionalId` igual al id de la Dra. A

#### Scenario: Sesión de recepción sin profesional
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `GET /api/v1/public/sesion`
- **THEN** la respuesta es `200` con `rol` igual a `RECEPCIONISTA`
- **AND** la respuesta no incluye `profesionalId`
