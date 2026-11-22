# Spec Delta

## Purpose

Definir cuándo atiende cada profesional y cuándo no: sus franjas semanales de trabajo, sus ausencias y licencias, y los bloqueos de su agenda. Al registrar un período sin atención, el consultorio se entera de qué turnos quedaron afectados. Los escenarios usan datos ficticios:
- Profesionales: la Dra. Ana Ejemplo y el Dr. Bruno Prueba.
- Usuarios: `admin` (`ADMIN`), `recepcion` (`RECEPCIONISTA`), `ana.ejemplo` (`ODONTOLOGO` vinculado a la Dra. Ana Ejemplo) y `bruno.prueba` (`ODONTOLOGO` vinculado al Dr. Bruno Prueba).
- Fechas: el 2026-12-01 es martes y todo se expresa en `America/Argentina/Buenos_Aires`.

## ADDED Requirements

### Requirement: Definir los horarios de trabajo
El administrador SHALL poder definir el conjunto completo de franjas semanales de un profesional, cada una con un día de la semana (1 = lunes a 7 = domingo), una hora de inicio y una hora de fin. Un día puede tener varias franjas. Cada definición reemplaza el conjunto anterior.

#### Scenario: Dos franjas el mismo día
- **WHEN** `admin` define para la Dra. Ana Ejemplo las franjas del martes de 08:00 a 12:00 y de 16:00 a 20:00
- **THEN** la respuesta es `200`
- **AND** los horarios de la Dra. Ana Ejemplo son esas dos franjas del martes

#### Scenario: Redefinir reemplaza las franjas anteriores
- **GIVEN** la Dra. Ana Ejemplo con franjas de lunes a viernes de 08:00 a 13:00
- **WHEN** `admin` define para ella solo la franja del miércoles de 09:00 a 12:00
- **THEN** los horarios de la Dra. Ana Ejemplo son solo la franja del miércoles de 09:00 a 12:00

#### Scenario: Quitar todos los horarios
- **GIVEN** la Dra. Ana Ejemplo con franjas de lunes a viernes de 08:00 a 13:00
- **WHEN** `admin` define para ella un conjunto vacío
- **THEN** la Dra. Ana Ejemplo no tiene horarios de trabajo

### Requirement: Franjas válidas
El sistema MUST rechazar con `422` y el código `HORARIO_INVALIDO` una franja cuya hora de fin no es posterior a la de inicio, o un día fuera de 1 a 7. MUST rechazar con `422` y el código `FRANJAS_SUPERPUESTAS` dos franjas del mismo día que se superponen. En los dos casos los horarios anteriores quedan sin cambios.

#### Scenario: Franja con el fin antes del inicio
- **WHEN** `admin` define para la Dra. Ana Ejemplo la franja del martes de 12:00 a 08:00
- **THEN** la respuesta es `422` con el código `HORARIO_INVALIDO`

#### Scenario: Franjas superpuestas en el mismo día
- **GIVEN** la Dra. Ana Ejemplo con franjas de lunes a viernes de 08:00 a 13:00
- **WHEN** `admin` define para ella las franjas del martes de 08:00 a 12:00 y de 11:00 a 14:00
- **THEN** la respuesta es `422` con el código `FRANJAS_SUPERPUESTAS`
- **AND** los horarios de la Dra. Ana Ejemplo siguen siendo de lunes a viernes de 08:00 a 13:00

#### Scenario: Franjas contiguas
- **WHEN** `admin` define para la Dra. Ana Ejemplo las franjas del martes de 08:00 a 12:00 y de 12:00 a 14:00
- **THEN** la respuesta es `200`

### Requirement: Permisos sobre los horarios
Solo el `ADMIN` SHALL modificar los horarios. El `ADMIN` y la recepción SHALL poder leer los de cualquier profesional, y un `ODONTOLOGO` solo los de su propio profesional. Cualquier otro caso MUST responder `403` con el código `ACCESO_DENEGADO`.

#### Scenario: La recepción consulta los horarios
- **GIVEN** la Dra. Ana Ejemplo con franjas de lunes a viernes de 08:00 a 13:00
- **WHEN** `recepcion` consulta sus horarios
- **THEN** la respuesta es `200` con esas franjas

#### Scenario: La recepción intenta modificar los horarios
- **WHEN** `recepcion` define franjas para la Dra. Ana Ejemplo
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

#### Scenario: Un odontólogo consulta los horarios de otro
- **WHEN** `bruno.prueba` consulta los horarios de la Dra. Ana Ejemplo
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

### Requirement: Registrar ausencias y licencias
El `ADMIN` y la recepción SHALL poder registrar una ausencia o una licencia de un profesional, con tipo (`AUSENCIA` o `LICENCIA`), inicio, fin y un motivo opcional de hasta 255 caracteres. El fin MUST ser posterior al inicio; si no, la respuesta es `422` con el código `PERIODO_INVALIDO`. Se pueden consultar las ausencias de un profesional y borrar una registrada por error.

#### Scenario: Registrar una licencia
- **WHEN** `recepcion` registra una `LICENCIA` de la Dra. Ana Ejemplo del 2026-12-07 a las 00:00 al 2026-12-12 a las 00:00
- **THEN** la respuesta es `201`
- **AND** las ausencias de la Dra. Ana Ejemplo incluyen esa licencia

#### Scenario: Período con el fin antes del inicio
- **WHEN** `recepcion` registra una `AUSENCIA` de la Dra. Ana Ejemplo del 2026-12-01 a las 12:00 al 2026-12-01 a las 10:00
- **THEN** la respuesta es `422` con el código `PERIODO_INVALIDO`

#### Scenario: Borrar una ausencia
- **GIVEN** una `AUSENCIA` registrada de la Dra. Ana Ejemplo el 2026-12-01 de 10:00 a 12:00
- **WHEN** `admin` la borra
- **THEN** la respuesta es `204`
- **AND** las ausencias de la Dra. Ana Ejemplo no la incluyen

### Requirement: Permisos sobre las ausencias
El `ADMIN` y la recepción SHALL registrar, consultar y borrar ausencias de cualquier profesional. Un `ODONTOLOGO` SHALL poder consultar solo las de su propio profesional y MUST recibir `403` con el código `ACCESO_DENEGADO` en cualquier otra operación.

#### Scenario: El odontólogo consulta sus ausencias
- **GIVEN** una `LICENCIA` registrada de la Dra. Ana Ejemplo
- **WHEN** `ana.ejemplo` consulta las ausencias de la Dra. Ana Ejemplo
- **THEN** la respuesta es `200` con esa licencia

#### Scenario: El odontólogo intenta registrar una ausencia
- **WHEN** `ana.ejemplo` registra una `AUSENCIA` de la Dra. Ana Ejemplo
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

### Requirement: Registrar bloqueos de agenda
Un `ODONTOLOGO` SHALL poder bloquear períodos de la agenda de su propio profesional, y el `ADMIN` de cualquier profesional, con inicio, fin y un motivo opcional de hasta 255 caracteres. El fin MUST ser posterior al inicio; si no, la respuesta es `422` con el código `PERIODO_INVALIDO`. Los mismos roles pueden consultar y borrar esos bloqueos.

#### Scenario: El odontólogo bloquea su agenda
- **WHEN** `ana.ejemplo` bloquea la agenda de la Dra. Ana Ejemplo el 2026-12-01 de 11:00 a 12:00
- **THEN** la respuesta es `201`
- **AND** los bloqueos de la Dra. Ana Ejemplo incluyen ese período

#### Scenario: Borrar un bloqueo
- **GIVEN** un bloqueo de la Dra. Ana Ejemplo el 2026-12-01 de 11:00 a 12:00
- **WHEN** `ana.ejemplo` lo borra
- **THEN** la respuesta es `204`
- **AND** los bloqueos de la Dra. Ana Ejemplo no lo incluyen

### Requirement: Permisos sobre los bloqueos
Un `ODONTOLOGO` MUST recibir `403` con el código `ACCESO_DENEGADO` al operar sobre los bloqueos de otro profesional. La recepción MUST recibir `403` con el código `ACCESO_DENEGADO` en toda operación sobre bloqueos.

#### Scenario: Un odontólogo bloquea la agenda de otro
- **WHEN** `bruno.prueba` bloquea la agenda de la Dra. Ana Ejemplo
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

#### Scenario: La recepción consulta los bloqueos
- **WHEN** `recepcion` consulta los bloqueos de la Dra. Ana Ejemplo
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

### Requirement: Turnos afectados por un período sin atención
Al registrar una ausencia, una licencia o un bloqueo, el sistema SHALL registrarlo aunque el profesional tenga turnos dados en ese período. La respuesta SHALL listar los turnos activos del profesional que se cruzan con el período, con su id, inicio, fin y prestación, para que el personal los reasigne o reprograme. La lista MUST NOT incluir datos del paciente ni turnos cancelados.

#### Scenario: Licencia con turnos dados
- **GIVEN** un turno activo de la Dra. Ana Ejemplo el 2026-12-09 de 10:00 a 10:30
- **AND** un turno `CANCELADO` de la Dra. Ana Ejemplo el 2026-12-09 de 11:00 a 11:30
- **WHEN** `recepcion` registra una `LICENCIA` de la Dra. Ana Ejemplo del 2026-12-07 al 2026-12-12
- **THEN** la respuesta es `201`
- **AND** los turnos afectados son solo el del 2026-12-09 de 10:00 a 10:30

#### Scenario: Bloqueo sin turnos dados
- **WHEN** `ana.ejemplo` bloquea la agenda de la Dra. Ana Ejemplo el 2026-12-01 de 11:00 a 12:00
- **THEN** la respuesta es `201` con la lista de turnos afectados vacía

#### Scenario: Los turnos afectados no muestran al paciente
- **GIVEN** un turno activo de "Paciente Uno" con la Dra. Ana Ejemplo el 2026-12-09 de 10:00 a 10:30
- **WHEN** `recepcion` registra una `LICENCIA` de la Dra. Ana Ejemplo del 2026-12-07 al 2026-12-12
- **THEN** la respuesta no contiene el nombre ni el DNI de "Paciente Uno"

### Requirement: Reasignar un turno afectado por una ausencia
El `ADMIN` y la recepción SHALL poder reasignar un turno activo afectado por una ausencia o licencia (RN-AG-16). Puede ser a otro profesional en el mismo horario o a otro horario, sin pedir DNI ni número de reserva, porque la inicia el consultorio. El turno original pasa a `CANCELADO` y se crea uno nuevo con las reglas de la agenda y un número de reserva nuevo, en una sola operación.

#### Scenario: Reasignar a otro profesional en el mismo horario
- **GIVEN** una `LICENCIA` de la Dra. Ana Ejemplo del 2026-12-07 al 2026-12-12
- **AND** un turno activo de "Paciente Uno" de "Consulta o control" con la Dra. Ana Ejemplo el 2026-12-09 de 10:00 a 10:30 en el Sillón 1
- **AND** el Dr. Bruno Prueba habilitado para "Consulta o control" y con franja ese día
- **WHEN** `recepcion` reasigna ese turno al Dr. Bruno Prueba
- **THEN** la respuesta es `200`
- **AND** el turno original queda en estado `CANCELADO`
- **AND** existe un turno nuevo de "Paciente Uno" con el Dr. Bruno Prueba el 2026-12-09 de 10:00 a 10:30 en el Sillón 1, con otro número de reserva

#### Scenario: Reprogramar con el mismo profesional después de la licencia
- **GIVEN** una `LICENCIA` de la Dra. Ana Ejemplo del 2026-12-07 al 2026-12-12
- **AND** un turno activo de "Paciente Uno" con la Dra. Ana Ejemplo el 2026-12-09 a las 10:00
- **WHEN** `recepcion` reasigna ese turno al 2026-12-14 a las 10:00 con la Dra. Ana Ejemplo
- **THEN** existe un turno nuevo de "Paciente Uno" con la Dra. Ana Ejemplo el 2026-12-14 a las 10:00
- **AND** el turno original queda en estado `CANCELADO`

#### Scenario: Profesional no habilitado para la prestación
- **GIVEN** un turno activo de "Tratamiento de conducto" afectado por una licencia del Dr. Bruno Prueba
- **AND** la Dra. Ana Ejemplo no habilitada para "Tratamiento de conducto"
- **WHEN** `recepcion` reasigna ese turno a la Dra. Ana Ejemplo
- **THEN** la respuesta es `409` con el código `PROFESIONAL_NO_HABILITADO`
- **AND** el turno original sigue activo y no se pide ningún aviso

#### Scenario: El nuevo horario también cae en la licencia
- **GIVEN** una `LICENCIA` de la Dra. Ana Ejemplo del 2026-12-07 al 2026-12-12
- **AND** un turno activo de la Dra. Ana Ejemplo el 2026-12-09 a las 10:00 afectado por ella
- **WHEN** `recepcion` reasigna ese turno al 2026-12-10 a las 10:00 con la Dra. Ana Ejemplo
- **THEN** la respuesta es `409` con el código `PROFESIONAL_AUSENTE`
- **AND** el turno original sigue activo

#### Scenario: Turno que no está afectado por la ausencia
- **GIVEN** una `LICENCIA` de la Dra. Ana Ejemplo del 2026-12-07 al 2026-12-12
- **AND** un turno activo de la Dra. Ana Ejemplo el 2026-12-15 a las 10:00
- **WHEN** `recepcion` reasigna ese turno citando esa licencia
- **THEN** la respuesta es `409` con el código `TURNO_NO_AFECTADO`

#### Scenario: El odontólogo intenta reasignar
- **GIVEN** un turno activo afectado por una licencia de la Dra. Ana Ejemplo
- **WHEN** `ana.ejemplo` reasigna ese turno al Dr. Bruno Prueba
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

### Requirement: Aviso al paciente de la reasignación
Después de reasignar un turno, el sistema SHALL pedir el aviso de reasignación al paciente para el turno nuevo (RN-AG-16). Las reglas de consentimiento, canal y contenido las define `notificaciones-recordatorios`. Si el aviso falla, la reasignación MUST quedar hecha igual. Una reasignación rechazada MUST NOT pedir ningún aviso.

#### Scenario: Se pide el aviso para el turno nuevo
- **GIVEN** un turno activo de "Paciente Uno" afectado por una licencia de la Dra. Ana Ejemplo
- **WHEN** `recepcion` lo reasigna al Dr. Bruno Prueba en el mismo horario
- **THEN** se pide un aviso de reasignación para el turno nuevo

#### Scenario: Un aviso que falla no deshace la reasignación
- **GIVEN** un turno activo de "Paciente Uno" afectado por una licencia de la Dra. Ana Ejemplo
- **AND** el envío del aviso falla
- **WHEN** `recepcion` lo reasigna al Dr. Bruno Prueba en el mismo horario
- **THEN** la respuesta es `200`
- **AND** el turno nuevo existe y el original queda en estado `CANCELADO`

### Requirement: Profesional inexistente
Toda operación sobre los horarios, las ausencias o los bloqueos de un profesional que no existe MUST responder `404` con el código `RECURSO_NO_ENCONTRADO`.

#### Scenario: Horarios de un profesional inexistente
- **WHEN** `admin` consulta los horarios del profesional con id `999999`
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`
