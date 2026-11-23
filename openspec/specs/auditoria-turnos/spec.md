# auditoria-turnos Specification

## Purpose
Dejar un registro permanente de las operaciones sobre turnos: qué se hizo, sobre qué turno, quién lo hizo y cuándo (RN-GL-03), para que el consultorio pueda reconstruir la historia de cada turno. En los escenarios, con datos ficticios, el usuario `recepcion` (`RECEPCIONISTA`) opera sobre turnos de "Paciente Uno" (DNI `99000001`) con la Dra. A en el Sillón 1, el 2026-12-01.

## Requirements

### Requirement: Registro de cada operación sobre un turno
El sistema SHALL registrar cada creación, cancelación y reprogramación de un turno con la acción (`CREAR`, `CANCELAR` o `REPROGRAMAR`), el id del turno, el nombre del usuario de la sesión que la hizo y el momento en que ocurrió.

#### Scenario: Se registra la creación
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** crea un turno de "Paciente Uno" a las 10:00
- **THEN** existe un registro de auditoría con la acción `CREAR`, el id de ese turno y el actor `recepcion`

#### Scenario: Se registra la cancelación
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **AND** un turno activo de "Paciente Uno" a las 10:00
- **WHEN** lo cancela con el DNI y el número de reserva correctos
- **THEN** existe un registro de auditoría con la acción `CANCELAR`, el id de ese turno y el actor `recepcion`

### Requirement: La reprogramación vincula los dos turnos
Al reprogramar, el sistema SHALL registrar la acción `REPROGRAMAR` sobre el turno original, indicando el id del turno nuevo, y la acción `CREAR` sobre el turno nuevo.

#### Scenario: Se registra la reprogramación
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **AND** un turno activo de "Paciente Uno" a las 10:00
- **WHEN** lo reprograma a las 11:00
- **THEN** existe un registro `REPROGRAMAR` sobre el turno original que indica el id del turno nuevo
- **AND** existe un registro `CREAR` sobre el turno nuevo

### Requirement: Solo se auditan las operaciones que ocurrieron
El registro de auditoría MUST guardarse junto con la operación: si la operación se rechaza, no queda ningún registro de ella.

#### Scenario: Reprogramación rechazada sin registro
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **AND** un turno activo de "Paciente Uno" a las 10:00 y otro turno activo a las 11:00 en el Sillón 1
- **WHEN** reprograma el primero a las 11:10 en el Sillón 1 y la operación se rechaza
- **THEN** no existe ningún registro `REPROGRAMAR` para el turno original

### Requirement: Sin datos sensibles en la auditoría
El registro de auditoría MUST NOT incluir datos de salud, el DNI del paciente ni el número de reserva (RN-NO-01, Ley 25.326).

#### Scenario: La cancelación no guarda el DNI ni el número de reserva
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **AND** un turno activo de "Paciente Uno" a las 10:00
- **WHEN** lo cancela con el DNI `99000001` y el número de reserva del turno
- **THEN** el registro `CANCELAR` no contiene `99000001` ni el número de reserva

### Requirement: Registro de sobreturnos y extensiones

El sistema SHALL registrar cada sobreturno con la acción `SOBRETURNO` y cada extensión con la acción `EXTENDER`, sobre el id del turno, con el `username` del actor y la fecha del momento. El registro SHALL guardarse en la misma transacción que la operación: si se rechaza, no queda ningún registro. La extensión MUST NOT crear un turno nuevo ni un registro `CREAR`.

#### Scenario: Se registra el sobreturno
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** crea un sobreturno de "Paciente Uno" a las 10:00 con un sillón libre
- **THEN** existe un registro de auditoría con la acción `SOBRETURNO`, el id de ese turno y el actor `recepcion`
- **AND** no existe un registro `CREAR` para ese turno

#### Scenario: Se registra la extensión
- **GIVEN** el usuario `ana.ejemplo` con sesión iniciada
- **AND** un turno activo de "Paciente Uno" de 10:00 a 10:30
- **WHEN** extiende el turno hasta las 10:45 con un motivo administrativo
- **THEN** existe un registro de auditoría con la acción `EXTENDER`, el id de ese turno y el actor `ana.ejemplo`
- **AND** no se crea un turno nuevo

#### Scenario: Sobreturno rechazado sin registro
- **GIVEN** turnos activos que ocupan los dos sillones de 10:00 a 10:30
- **WHEN** el usuario `recepcion` intenta un sobreturno a las 10:10 y la operación se rechaza
- **THEN** no existe ningún registro `SOBRETURNO` para esa operación

#### Scenario: Extensión rechazada sin registro
- **GIVEN** un turno activo de 10:00 a 10:30 y otro de 11:00 en el mismo sillón
- **WHEN** el usuario `recepcion` intenta extender el primero hasta las 11:00 y la operación se rechaza
- **THEN** no existe ningún registro `EXTENDER` para ese turno
- **AND** el turno sigue terminando a las 10:30

### Requirement: Sin motivo en la auditoría ni en canales externos

El registro de auditoría MUST NOT incluir el motivo del sobreturno ni de la extensión, porque es un dato administrativo que puede describir la atención. La tabla SHALL guardar solo ids, acción, actor y fecha, sin columnas de texto libre. El motivo MUST NOT aparecer en logs ni en notificaciones (RN-NO-01, Ley 25.326).

#### Scenario: El motivo del sobreturno no se audita
- **GIVEN** un sobreturno creado con un motivo administrativo
- **WHEN** se consulta el registro `SOBRETURNO` de ese turno en la tabla de auditoría
- **THEN** ninguna columna del registro contiene el texto del motivo

#### Scenario: El motivo de la extensión no se audita
- **GIVEN** un turno extendido con un motivo administrativo
- **WHEN** se consulta el registro `EXTENDER` de ese turno en la tabla de auditoría
- **THEN** ninguna columna del registro contiene el texto del motivo

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

### Requirement: Registro de la ausencia del paciente
Al marcar un turno como ausente, el sistema SHALL registrar la acción `AUSENTE` con el id del turno, el usuario de la sesión y el momento, en la misma operación. Si la ausencia se rechaza, MUST NOT quedar ningún registro. El registro no incluye datos de la ficha clínica.

#### Scenario: Se registra la ausencia
- **GIVEN** el usuario `ana.ejemplo`, odontólogo de la Dra. A, con sesión iniciada
- **AND** un turno activo de "Paciente Uno" a las 10:00 con la Dra. A, cuya hora de inicio ya pasó
- **WHEN** lo marca como ausente
- **THEN** existe un registro de auditoría con la acción `AUSENTE`, el id de ese turno y el actor `ana.ejemplo`

#### Scenario: Ausencia rechazada sin registro
- **GIVEN** un turno activo de "Paciente Uno" a las 10:00 con la Dra. A, cuya hora de inicio todavía no llegó
- **WHEN** `ana.ejemplo` lo marca como ausente
- **THEN** la operación se rechaza con `TURNO_NO_INICIADO`
- **AND** no existe ningún registro de auditoría `AUSENTE` para ese turno

### Requirement: Registro de la confirmación de un turno
El sistema SHALL registrar la confirmación de un turno con la acción `CONFIRMAR`, el id del turno, el actor `paciente-token` y el momento en que ocurrió. MUST NOT guardar el token en la auditoría.

#### Scenario: Se registra la confirmación
- **GIVEN** un turno `RESERVADO` de "Paciente Uno" con un token sin usar
- **WHEN** el paciente confirma con ese token
- **THEN** existe un registro de auditoría con la acción `CONFIRMAR`, el id de ese turno y el actor `paciente-token`
- **AND** ningún campo del registro contiene el token

#### Scenario: Confirmación rechazada sin registro
- **GIVEN** un turno cancelado de "Paciente Uno" con un token sin usar
- **WHEN** se intenta confirmar con ese token y la operación se rechaza
- **THEN** no existe ningún registro `CONFIRMAR` para ese turno
