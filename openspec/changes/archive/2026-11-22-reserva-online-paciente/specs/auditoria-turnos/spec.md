# Spec Delta

## ADDED Requirements

### Requirement: Actor de las operaciones online del paciente
Las operaciones que el paciente hace online (reservar, cancelar y reprogramar) SHALL registrarse con las mismas acciones que las del personal (`CREAR`, `CANCELAR`, `REPROGRAMAR`) y con el actor `paciente-online:{id de su cuenta}`. El actor MUST NOT contener el email, el nombre ni el DNI del paciente.

#### Scenario: Se registra una reserva online
- **GIVEN** "Paciente Uno" (`paciente.uno@example.com`) con sesión iniciada
- **WHEN** reserva un turno de "Consulta" con la Dra. A el 2026-12-01 a las 10:00
- **THEN** existe un registro de auditoría `CREAR` para ese turno con el actor `paciente-online:` seguido del id de la cuenta de "Paciente Uno"
- **AND** el registro no contiene `paciente.uno@example.com`, `99000001` ni el número de reserva

#### Scenario: Se registra una reprogramación online
- **GIVEN** "Paciente Uno" con sesión iniciada y un turno activo suyo el 2026-12-02 a las 10:00
- **WHEN** lo reprograma online al 2026-12-02 a las 11:00
- **THEN** existe un registro `REPROGRAMAR` sobre el turno original que indica el id del turno nuevo, y un registro `CREAR` sobre el turno nuevo, los dos con el actor `paciente-online:` seguido del id de su cuenta

#### Scenario: Cancelación online rechazada sin registro
- **GIVEN** que el momento actual es el 2026-12-01 a las 11:00
- **AND** "Paciente Uno" con sesión iniciada y un turno activo suyo el 2026-12-02 a las 10:00
- **WHEN** pide cancelarlo online y la operación se rechaza por el plazo
- **THEN** no existe ningún registro `CANCELAR` para ese turno
