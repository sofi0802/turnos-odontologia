# Spec Delta

## MODIFIED Requirements

### Requirement: Sillón habitual del profesional
El sistema SHALL permitir asignar a un profesional un sillón habitual activo, o quitárselo. Un sillón inactivo se rechaza con `422` y el código `SILLON_INACTIVO`.

Un sillón MUST ser el habitual de un solo profesional (RN-RO-09), activo o inactivo. Si el sillón ya es habitual de otro profesional, el cambio se rechaza con `409` y el código `SILLON_HABITUAL_OCUPADO`, y ninguno de los dos profesionales cambia. Volver a asignar a un profesional el sillón que ya tiene MUST aceptarse.

#### Scenario: Asignar el sillón habitual
- **WHEN** el administrador asigna el "Sillón 1" como sillón habitual de la Dra. Ana Ejemplo
- **THEN** la Dra. Ana Ejemplo figura con el "Sillón 1" como sillón habitual

#### Scenario: Sillón habitual inactivo
- **GIVEN** el "Sillón 2" desactivado
- **WHEN** el administrador asigna el "Sillón 2" como sillón habitual del Dr. Bruno Prueba
- **THEN** el cambio se rechaza con `422` y el código `SILLON_INACTIVO`

#### Scenario: Quitar el sillón habitual
- **GIVEN** la Dra. Ana Ejemplo con el "Sillón 1" como sillón habitual
- **WHEN** el administrador le quita el sillón habitual
- **THEN** la Dra. Ana Ejemplo figura sin sillón habitual

#### Scenario: Sillón habitual de otro profesional
- **GIVEN** la Dra. Ana Ejemplo con el "Sillón 1" como sillón habitual
- **WHEN** el administrador asigna el "Sillón 1" como sillón habitual del Dr. Bruno Prueba
- **THEN** el cambio se rechaza con `409` y el código `SILLON_HABITUAL_OCUPADO`
- **AND** la Dra. Ana Ejemplo sigue con el "Sillón 1" y el Dr. Bruno Prueba conserva el sillón que tenía

#### Scenario: Sillón habitual de un profesional inactivo
- **GIVEN** un profesional inactivo con el "Sillón 2" como sillón habitual
- **WHEN** el administrador asigna el "Sillón 2" como sillón habitual de la Dra. Ana Ejemplo
- **THEN** el cambio se rechaza con `409` y el código `SILLON_HABITUAL_OCUPADO`

#### Scenario: Volver a asignar el mismo sillón
- **GIVEN** la Dra. Ana Ejemplo con el "Sillón 1" como sillón habitual
- **WHEN** el administrador le asigna otra vez el "Sillón 1"
- **THEN** el cambio se acepta y la Dra. Ana Ejemplo sigue con el "Sillón 1"

#### Scenario: Liberar un sillón y asignarlo a otro profesional
- **GIVEN** la Dra. Ana Ejemplo con el "Sillón 1" como sillón habitual
- **AND** el administrador le quitó el sillón habitual
- **WHEN** el administrador asigna el "Sillón 1" como sillón habitual del Dr. Bruno Prueba
- **THEN** el Dr. Bruno Prueba figura con el "Sillón 1" como sillón habitual
