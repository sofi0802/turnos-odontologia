# Spec Delta

## ADDED Requirements

### Requirement: Consentimiento inicial en el registro
`POST /api/v1/public/pacientes/registro` SHALL aceptar `aceptaMensajes`, opcional y `false` si falta. Si es `true`, el paciente MUST quedar con el consentimiento otorgado y un registro de historial con origen `REGISTRO`, en la misma operación que crea el paciente y su cuenta. Si es `false` o falta, MUST quedar sin consentimiento y sin historial.

#### Scenario: Registro aceptando mensajes
- **GIVEN** que no existe un paciente con DNI `99000001`
- **WHEN** se envía el registro de "Paciente Uno" con `aceptaMensajes` en `true`
- **THEN** la respuesta es `201`
- **AND** "Paciente Uno" tiene el consentimiento otorgado y un registro de historial con origen `REGISTRO`

#### Scenario: Registro sin indicar mensajes
- **GIVEN** que no existe un paciente con DNI `99000001`
- **WHEN** se envía el registro de "Paciente Uno" sin `aceptaMensajes`
- **THEN** la respuesta es `201`
- **AND** "Paciente Uno" queda sin consentimiento y sin historial

#### Scenario: Un registro rechazado no deja consentimiento
- **GIVEN** un paciente ya registrado con DNI `99000001`
- **WHEN** se envía otro registro con el DNI `99000001` y `aceptaMensajes` en `true`
- **THEN** la respuesta es el rechazo del DNI repetido
- **AND** no se agrega ningún registro de historial
