# agenda-turnos Specification

## Purpose
Decidir si un turno nuevo puede entrar en la agenda del consultorio: calcular su intervalo y rechazarlo si choca con otro turno activo del mismo profesional o del mismo sillón, respetando el margen de limpieza y preparación, si empieza en el pasado o si el profesional no atiende en ese horario (ausencia, bloqueo o fuera de sus franjas); y liberar el horario de un turno cuando el paciente no se presenta. En los escenarios, con datos ficticios y salvo que digan otra cosa, los profesionales atienden todo el día, la prestación "Consulta" dura 30 minutos, el margen es de 10 minutos, los horarios son del 2026-12-01 y el momento actual es ese día a las 08:00, en `America/Argentina/Buenos_Aires`.

## Requirements

### Requirement: Intervalo del turno
El sistema SHALL calcular el fin de un turno nuevo como su inicio más la duración de la prestación, sin aceptar un fin ingresado a mano. El turno ocupa el intervalo semiabierto `[inicio, fin)`. Un turno aceptado queda en estado `RESERVADO`.

#### Scenario: Turno sin conflictos
- **GIVEN** una agenda sin turnos
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 a las 10:00
- **THEN** el turno se crea en estado `RESERVADO`
- **AND** su fin es las 10:30

### Requirement: Solo los turnos activos ocupan la agenda
El sistema SHALL considerar en la verificación de conflictos solo los turnos activos, es decir, en estado `RESERVADO` o `CONFIRMADO`. Un turno `CANCELADO` MUST NOT impedir la creación de otro turno.

#### Scenario: Un turno cancelado no bloquea el horario
- **GIVEN** un turno `CANCELADO` de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 a las 10:00
- **THEN** el turno se crea en estado `RESERVADO`

### Requirement: Sin superposición por profesional
El sistema MUST rechazar un turno nuevo cuyo intervalo se superpone con el de un turno activo del mismo profesional, aunque sea en otro sillón, con el código `SOLAPAMIENTO_PROFESIONAL`.

#### Scenario: El profesional ya atiende en otro sillón
- **GIVEN** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 2 a las 10:15
- **THEN** el turno se rechaza con `SOLAPAMIENTO_PROFESIONAL`

### Requirement: Sin superposición por sillón
El sistema MUST rechazar un turno nuevo cuyo intervalo se superpone con el de un turno activo en el mismo sillón, aunque sea con otro profesional, con el código `SOLAPAMIENTO_SILLON`.

#### Scenario: El sillón ya está ocupado por otro profesional
- **GIVEN** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** se crea un turno de "Consulta" con el Dr. B en el Sillón 1 a las 10:15
- **THEN** el turno se rechaza con `SOLAPAMIENTO_SILLON`

### Requirement: Margen entre turnos que comparten recurso
Entre un turno nuevo y un turno activo que comparten profesional o sillón MUST quedar libre al menos el margen configurado, antes y después. Si los intervalos no se superponen pero el margen no se cumple, el sistema MUST rechazar el turno con `MARGEN_INSUFICIENTE`. Los turnos que no comparten profesional ni sillón no tienen restricción de margen.

#### Scenario: Turno contiguo sin margen
- **GIVEN** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 2 a las 10:30
- **THEN** el turno se rechaza con `MARGEN_INSUFICIENTE`

#### Scenario: Turno que respeta el margen exacto
- **GIVEN** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 a las 10:40
- **THEN** el turno se crea en estado `RESERVADO`

#### Scenario: Turno contiguo sin recursos compartidos
- **GIVEN** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** se crea un turno de "Consulta" con el Dr. B en el Sillón 2 a las 10:30
- **THEN** el turno se crea en estado `RESERVADO`

### Requirement: Margen configurable con un mínimo de 10 minutos
El margen entre turnos SHALL ser configurable. El sistema MUST rechazar un margen menor a 10 minutos con el código `MARGEN_INVALIDO`. Un margen mayor amplía el tiempo libre exigido entre turnos que comparten recurso.

#### Scenario: Margen menor al mínimo
- **GIVEN** una configuración de margen de 8 minutos
- **WHEN** se intenta usar ese margen para crear turnos
- **THEN** la configuración se rechaza con `MARGEN_INVALIDO`

#### Scenario: Margen mayor al mínimo
- **GIVEN** un margen configurado de 15 minutos
- **AND** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 a las 10:40
- **THEN** el turno se rechaza con `MARGEN_INSUFICIENTE`

### Requirement: Un solo error con prioridad fija
Si un turno nuevo tiene más de un conflicto, el sistema SHALL informar uno solo, con esta prioridad: `PROFESIONAL_AUSENTE`, `AGENDA_BLOQUEADA`, `FUERA_DE_HORARIO`, después `SOLAPAMIENTO_PROFESIONAL`, `SOLAPAMIENTO_SILLON` y por último `MARGEN_INSUFICIENTE`.

#### Scenario: Choque por profesional y por sillón a la vez
- **GIVEN** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 a las 10:15
- **THEN** el turno se rechaza con `SOLAPAMIENTO_PROFESIONAL`

#### Scenario: Ausencia y solapamiento a la vez
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 13:00
- **AND** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **AND** una `AUSENCIA` de la Dra. A el 2026-12-01 de 10:00 a 12:00
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 a las 10:15
- **THEN** el turno se rechaza con `PROFESIONAL_AUSENTE`

#### Scenario: Fuera de horario y solapamiento a la vez
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 10:30
- **AND** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 a las 10:15
- **THEN** el turno se rechaza con `FUERA_DE_HORARIO`

### Requirement: Sin turnos en el pasado
El sistema MUST rechazar un turno nuevo cuyo inicio es anterior al momento actual, con el código `INICIO_EN_EL_PASADO`. Un turno que empieza exactamente en el momento actual SHALL aceptarse si no tiene otros conflictos.

#### Scenario: Inicio anterior al momento actual
- **GIVEN** que el momento actual es el 2026-12-01 a las 09:00
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 el 2026-12-01 a las 08:30
- **THEN** el turno se rechaza con `INICIO_EN_EL_PASADO`

#### Scenario: Inicio igual al momento actual
- **GIVEN** que el momento actual es el 2026-12-01 a las 09:00
- **AND** una agenda sin turnos
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 el 2026-12-01 a las 09:00
- **THEN** el turno se crea en estado `RESERVADO`

### Requirement: Número de reserva
El sistema SHALL asignar a cada turno nuevo un número de reserva único, de 8 caracteres aleatorios con el formato `XXXX-XXXX`, tomados de letras mayúsculas y dígitos sin los caracteres ambiguos `0`, `O`, `1`, `I` y `L`. El número de reserva MUST NOT derivarse del id del turno ni de otro dato predecible.

#### Scenario: Turno nuevo con número de reserva
- **GIVEN** una agenda sin turnos
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 a las 10:00
- **THEN** el turno tiene un número de reserva con el formato `XXXX-XXXX` y sin caracteres ambiguos

#### Scenario: Dos turnos tienen números de reserva distintos
- **GIVEN** una agenda sin turnos
- **WHEN** se crean un turno con la Dra. A en el Sillón 1 a las 10:00 y otro con el Dr. B en el Sillón 2 a las 10:00
- **THEN** los dos turnos tienen números de reserva distintos

### Requirement: Sin turnos con recursos inactivos
El sistema MUST rechazar un turno nuevo con el código `RECURSO_INACTIVO` si su profesional, su sillón o su prestación están inactivos.

#### Scenario: Profesional inactivo
- **GIVEN** que la Dra. A está inactiva
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 a las 10:00
- **THEN** el turno se rechaza con `RECURSO_INACTIVO`

#### Scenario: Sillón inactivo
- **GIVEN** que el Sillón 1 está inactivo
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 a las 10:00
- **THEN** el turno se rechaza con `RECURSO_INACTIVO`

#### Scenario: Prestación inactiva
- **GIVEN** que la prestación "Consulta" está inactiva
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 a las 10:00
- **THEN** el turno se rechaza con `RECURSO_INACTIVO`

### Requirement: Validación de DNI y número de reserva
Para cancelar o reprogramar un turno, el personal MUST informar el DNI del paciente y el número de reserva del turno. Si alguno de los dos no coincide con el turno, el sistema MUST rechazar la operación con el código `DATOS_DE_RESERVA_INVALIDOS`, sin indicar cuál de los dos falló, y el turno no cambia.

#### Scenario: DNI que no corresponde al turno
- **GIVEN** un turno activo de "Paciente Uno" (DNI `99000001`) de 10:00 a 10:30
- **WHEN** el personal lo cancela informando el DNI `99000002` y el número de reserva correcto
- **THEN** la operación se rechaza con `DATOS_DE_RESERVA_INVALIDOS`
- **AND** el turno sigue en estado `RESERVADO`

#### Scenario: Número de reserva que no corresponde al turno
- **GIVEN** un turno activo de "Paciente Uno" (DNI `99000001`) de 10:00 a 10:30
- **WHEN** el personal lo cancela informando el DNI `99000001` y un número de reserva de otro turno
- **THEN** la operación se rechaza con `DATOS_DE_RESERVA_INVALIDOS`
- **AND** el turno sigue en estado `RESERVADO`

### Requirement: Cancelar un turno
El personal SHALL poder cancelar un turno activo en cualquier momento. El turno pasa a `CANCELADO` y su horario queda libre de inmediato para otro turno. Cancelar un turno que no está activo MUST rechazarse con el código `TURNO_NO_ACTIVO`.

#### Scenario: Cancelar libera el horario
- **GIVEN** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** el personal lo cancela con el DNI y el número de reserva correctos
- **THEN** el turno queda en estado `CANCELADO`
- **AND** se puede crear otro turno de "Consulta" con la Dra. A en el Sillón 1 a las 10:00

#### Scenario: Cancelar un turno ya cancelado
- **GIVEN** un turno `CANCELADO` de "Paciente Uno" de 10:00 a 10:30
- **WHEN** el personal lo cancela con el DNI y el número de reserva correctos
- **THEN** la operación se rechaza con `TURNO_NO_ACTIVO`

### Requirement: Reprogramar un turno
El personal SHALL poder reprogramar un turno activo a un nuevo inicio y, opcionalmente, a otro profesional o sillón. El turno original pasa a `CANCELADO` y se crea un turno nuevo, con las mismas reglas que la creación y un número de reserva nuevo. Los dos cambios ocurren juntos o no ocurre ninguno. El turno original no cuenta como conflicto para el nuevo.

#### Scenario: Reprogramar sobre el propio horario
- **GIVEN** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** el personal lo reprograma a las 10:15 con la Dra. A en el Sillón 1
- **THEN** el turno original queda en estado `CANCELADO`
- **AND** existe un turno nuevo de "Paciente Uno" de 10:15 a 10:45 en estado `RESERVADO`
- **AND** el turno nuevo tiene un número de reserva distinto al original

#### Scenario: Reprogramar a un horario en conflicto
- **GIVEN** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **AND** otro turno activo de 11:00 a 11:30 con la Dra. A en el Sillón 2
- **WHEN** el personal reprograma el primero a las 11:15 con la Dra. A en el Sillón 1
- **THEN** la operación se rechaza con `SOLAPAMIENTO_PROFESIONAL`
- **AND** el turno original sigue en estado `RESERVADO` de 10:00 a 10:30
- **AND** no se crea ningún turno nuevo

#### Scenario: Reprogramar al pasado
- **GIVEN** que el momento actual es el 2026-12-01 a las 09:00
- **AND** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** el personal lo reprograma a las 08:30
- **THEN** la operación se rechaza con `INICIO_EN_EL_PASADO`
- **AND** el turno original sigue en estado `RESERVADO`

#### Scenario: Reprogramar un turno que no está activo
- **GIVEN** un turno `CANCELADO` de "Paciente Uno" de 10:00 a 10:30
- **WHEN** el personal lo reprograma a las 11:00 con el DNI y el número de reserva correctos
- **THEN** la operación se rechaza con `TURNO_NO_ACTIVO`

### Requirement: Sobreturno con motivo obligatorio

El sistema SHALL permitir cargar un sobreturno solo desde el personal y MUST exigir un `motivo` administrativo no vacío. El motivo es administrativo, nunca clínico. El turno resultante MUST quedar marcado como sobreturno. Un motivo vacío MUST rechazarse con `DATOS_INVALIDOS`. La excepción no se aplica a los turnos normales.

#### Scenario: Sobreturno con motivo válido
- **GIVEN** una agenda sin otros turnos y un motivo administrativo "Urgencia con el Paciente Uno"
- **WHEN** el personal crea un sobreturno de "Consulta" con la Dra. A a las 10:00
- **THEN** el turno se crea en estado `RESERVADO` con `esSobreturno` verdadero
- **AND** el motivo queda asociado al turno, sin exponerse en la respuesta

#### Scenario: Sobreturno sin motivo
- **GIVEN** el personal con sesión iniciada
- **WHEN** crea un sobreturno con el motivo vacío o solo espacios
- **THEN** la operación se rechaza con `DATOS_INVALIDOS`
- **AND** no se crea ningún turno

### Requirement: Asignación automática de sillón para sobreturnos

Para un sobreturno, el sistema SHALL asignar el sillón en el servidor: prefiere el sillón habitual del profesional si está libre con margen, y si no, cualquier sillón activo libre en orden determinista. Mientras C-04/V2 no exponga el sillón habitual, se aplica directamente el orden determinista. El cliente MUST NOT elegir el sillón. Si ningún sillón está libre con el margen de limpieza, el sistema MUST rechazar con `SIN_SILLON_DISPONIBLE`.

#### Scenario: Elige el sillón libre en orden determinista
- **GIVEN** el Sillón 1 y el Sillón 2 activos y libres, sin información de sillón habitual disponible
- **WHEN** el personal crea un sobreturno de "Consulta" con la Dra. A a las 10:00
- **THEN** el sillón asignado es el Sillón 1
- **AND** una operación idéntica repetida más tarde vuelve a elegir el Sillón 1 si sigue libre

#### Scenario: Usa otro sillón cuando el primero está ocupado
- **GIVEN** un turno activo de 10:00 a 10:30 con el Dr. B en el Sillón 1
- **AND** el Sillón 2 activo y libre
- **WHEN** el personal crea un sobreturno de "Consulta" con la Dra. A a las 10:10
- **THEN** el sobreturno se crea en el Sillón 2 con fin 10:40

#### Scenario: Sin sillón libre con margen de limpieza
- **GIVEN** turnos activos de 10:00 a 10:30 en el Sillón 1 y en el Sillón 2
- **AND** un margen de 10 minutos
- **WHEN** el personal crea un sobreturno de "Consulta" a las 10:35
- **THEN** la operación se rechaza con `SIN_SILLON_DISPONIBLE`
- **AND** no se crea ningún turno

#### Scenario: El margen de limpieza del sillón es obligatorio
- **GIVEN** turnos activos que terminan a las 10:30 en el Sillón 1 y en el Sillón 2
- **WHEN** el personal crea un sobreturno de "Consulta" a las 10:30 con margen de 10 minutos
- **THEN** la operación se rechaza con `SIN_SILLON_DISPONIBLE`

#### Scenario: Un sillón tomado en una carrera se descarta y se prueba el siguiente
- **GIVEN** el Sillón 1 y el Sillón 2 activos y libres a las 10:00
- **AND** el Sillón 1 es tomado por otra operación después de la verificación del dominio
- **WHEN** el personal crea un sobreturno de "Consulta" con la Dra. A a las 10:00
- **THEN** el sobreturno se crea en el Sillón 2
- **AND** solo si no queda ningún sillón libre se rechaza con `SIN_SILLON_DISPONIBLE`

### Requirement: Excepción de sobreturno solo para la agenda del profesional

Un sobreturno SHALL poder superponerse con turnos activos del mismo profesional, pero MUST NOT superponerse con el intervalo de limpieza de ningún sillón. La excepción se aplica únicamente a la restricción del profesional; la del sillón, incluido su margen, sigue vigente. Los turnos normales conservan ambas restricciones.

#### Scenario: Sobreturno que pisa la agenda del profesional en otro sillón
- **GIVEN** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **AND** el Sillón 2 activo y libre
- **WHEN** el personal crea un sobreturno de "Consulta" con la Dra. A a las 10:15
- **THEN** el sobreturno se crea en el Sillón 2 con fin 10:45
- **AND** los dos turnos de la Dra. A siguen activos

#### Scenario: Sobreturno que pisa el sillón se rechaza
- **GIVEN** un turno activo de 10:00 a 10:30 con el Dr. B en el Sillón 1
- **WHEN** el personal pide un sobreturno de "Consulta" a las 10:10 que solo tiene libre el Sillón 1
- **THEN** la operación se rechaza con `SIN_SILLON_DISPONIBLE`

### Requirement: Extensión con fin absoluto y margen recalculado

El sistema SHALL permitir al personal extender el fin de un turno `RESERVADO` o `CONFIRMADO` informando un `fin` absoluto y un `motivo` administrativo. El nuevo fin MUST ser posterior al fin actual. La extensión MUST recalcular `fin_con_margen = fin + margen vigente`, validar profesional, sillón y margen como un turno nuevo, marcar `extendido` y conservar el `numeroReserva`. MUST NOT extenderse un sobreturno ni un turno inactivo.

#### Scenario: Extensión sin conflictos
- **GIVEN** un turno activo de "Consulta" de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **AND** un margen de 10 minutos
- **WHEN** el personal extiende el turno con un `fin` absoluto de 10:45 y un motivo administrativo
- **THEN** el turno pasa a terminar a las 10:45 con `fin_con_margen` 10:55 y `extendido` verdadero
- **AND** conserva su `numeroReserva` original y sigue en estado `RESERVADO`

#### Scenario: Extensión que choca con el sillón
- **GIVEN** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **AND** otro turno activo de 11:00 a 11:30 con el Dr. B en el Sillón 1
- **WHEN** el personal extiende el primero con un `fin` absoluto de 11:00
- **THEN** la operación se rechaza con `SOLAPAMIENTO_SILLON`
- **AND** el turno original sigue de 10:00 a 10:30 con su `numeroReserva`

#### Scenario: Extensión sin margen suficiente
- **GIVEN** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **AND** otro turno activo de 10:45 a 11:15 con el Dr. B en el Sillón 1
- **WHEN** el personal extiende el primero con un `fin` absoluto de 10:40
- **THEN** la operación se rechaza con `MARGEN_INSUFICIENTE`

#### Scenario: Extensión de un sobreturno
- **GIVEN** un sobreturno activo de 10:00 a 10:30
- **WHEN** el personal pide extenderlo con un `fin` absoluto de 10:45
- **THEN** la operación se rechaza con `TURNO_NO_EXTENSIBLE`
- **AND** el sobreturno queda sin cambios

#### Scenario: Extensión de un turno inactivo
- **GIVEN** un turno `CANCELADO` de 10:00 a 10:30
- **WHEN** el personal pide extenderlo con un `fin` absoluto de 10:45
- **THEN** la operación se rechaza con `TURNO_NO_ACTIVO`

#### Scenario: Extensión con un fin no posterior
- **GIVEN** un turno activo de 10:00 a 10:30
- **WHEN** el personal pide extenderlo con un `fin` absoluto de 10:30 o de 10:20
- **THEN** la operación se rechaza con `DATOS_INVALIDOS`
- **AND** el turno queda sin cambios

#### Scenario: Extensión de un turno ya iniciado
- **GIVEN** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1 y el reloj en las 10:20
- **WHEN** el personal pide extenderlo con un `fin` absoluto de 10:45 y un motivo administrativo
- **THEN** el turno pasa a terminar a las 10:45 con `extendido` verdadero
- **AND** conserva su `numeroReserva` original y su estado

#### Scenario: Extensión de un turno ya iniciado con un fin que no está en el futuro
- **GIVEN** un turno activo de 10:00 a 10:30 y el reloj en las 10:50
- **WHEN** el personal pide extenderlo con un `fin` absoluto de 10:45
- **THEN** la operación se rechaza con `DATOS_INVALIDOS`
- **AND** el turno queda sin cambios

#### Scenario: Extender un turno iniciado no habilita crear turnos en el pasado
- **GIVEN** el reloj en las 10:20 y un turno activo ya iniciado de 10:00 a 10:30
- **WHEN** el personal pide crear un turno nuevo con inicio 10:00
- **THEN** la operación se rechaza con `INICIO_EN_EL_PASADO`
- **AND** no se crea ningún turno

### Requirement: Atomicidad y doble garantía en sobreturnos y extensiones

Cada sobreturno y cada extensión SHALL ser atómicos junto con su registro de auditoría: si la operación se rechaza, no queda turno nuevo, ni extensión, ni auditoría. La verificación del dominio y las dos restricciones `EXCLUDE USING gist` de PostgreSQL MUST aplicarse simultáneamente: la de sillón incluye a los sobreturnos y la de profesional los excluye.

#### Scenario: Rechazo sin efectos parciales
- **GIVEN** turnos activos que ocupan los dos sillones de 10:00 a 10:30
- **WHEN** el personal intenta crear un sobreturno a las 10:10 y la operación se rechaza
- **THEN** no existe un turno sobreturno nuevo
- **AND** no existe ningún registro de auditoría de esa operación

#### Scenario: La restricción del sillón incluye a los sobreturnos
- **GIVEN** un sobreturno activo de 10:00 a 10:30 en el Sillón 1
- **WHEN** se intenta insertar otro turno activo en el Sillón 1 a las 10:25
- **THEN** la base rechaza la inserción por `turno_sin_solapamiento_sillon`

### Requirement: Turno dentro del horario de trabajo
El sistema MUST rechazar con el código `FUERA_DE_HORARIO` un turno nuevo o reprogramado que no queda completo, de inicio a fin, dentro de una misma franja de trabajo del profesional para ese día de la semana, en `America/Argentina/Buenos_Aires`. Un profesional sin franjas no recibe turnos.

#### Scenario: Turno dentro de la franja
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 13:00
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 el martes 2026-12-01 a las 12:30
- **THEN** el turno se crea en estado `RESERVADO`

#### Scenario: Turno que termina después de la franja
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 13:00
- **WHEN** se crea un turno de "Consulta" con la Dra. A el martes 2026-12-01 a las 12:45
- **THEN** el turno se rechaza con `FUERA_DE_HORARIO`

#### Scenario: Turno en un día sin franjas
- **GIVEN** la Dra. A con franjas solo de lunes a viernes
- **WHEN** se crea un turno de "Consulta" con la Dra. A el sábado 2026-12-05 a las 10:00
- **THEN** el turno se rechaza con `FUERA_DE_HORARIO`

#### Scenario: Turno que cruza el corte entre dos franjas
- **GIVEN** la Dra. A con franjas el martes de 08:00 a 12:00 y de 16:00 a 20:00
- **WHEN** se crea un turno de "Consulta" con la Dra. A el martes 2026-12-01 a las 11:45
- **THEN** el turno se rechaza con `FUERA_DE_HORARIO`

#### Scenario: Turno en la segunda franja del día
- **GIVEN** la Dra. A con franjas el martes de 08:00 a 12:00 y de 16:00 a 20:00
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 el martes 2026-12-01 a las 16:00
- **THEN** el turno se crea en estado `RESERVADO`

#### Scenario: Profesional sin horarios cargados
- **GIVEN** el Dr. B sin ninguna franja de trabajo
- **WHEN** se crea un turno de "Consulta" con el Dr. B el martes 2026-12-01 a las 10:00
- **THEN** el turno se rechaza con `FUERA_DE_HORARIO`

#### Scenario: Reprogramar fuera de horario
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 13:00
- **AND** un turno activo de "Paciente Uno" el martes 2026-12-01 de 10:00 a 10:30 con la Dra. A
- **WHEN** el personal lo reprograma a las 14:00 del mismo día
- **THEN** la operación se rechaza con `FUERA_DE_HORARIO`
- **AND** el turno original sigue en estado `RESERVADO`

### Requirement: Sin turnos durante una ausencia o licencia
El sistema MUST rechazar con el código `PROFESIONAL_AUSENTE` un turno nuevo, reprogramado, sobreturno o extensión cuyo intervalo se cruza con una ausencia o licencia registrada del profesional.

#### Scenario: Turno durante una licencia
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 13:00
- **AND** una `LICENCIA` de la Dra. A del 2026-12-01 a las 00:00 al 2026-12-02 a las 00:00
- **WHEN** se crea un turno de "Consulta" con la Dra. A el martes 2026-12-01 a las 10:00
- **THEN** el turno se rechaza con `PROFESIONAL_AUSENTE`

#### Scenario: Turno que termina justo cuando empieza la ausencia
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 13:00
- **AND** una `AUSENCIA` de la Dra. A el 2026-12-01 de 11:00 a 13:00
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 el martes 2026-12-01 a las 10:30
- **THEN** el turno se crea en estado `RESERVADO`

#### Scenario: Sobreturno durante una ausencia
- **GIVEN** una `AUSENCIA` de la Dra. A el 2026-12-01 de 10:00 a 12:00
- **WHEN** el personal crea un sobreturno con la Dra. A el 2026-12-01 a las 10:30
- **THEN** el sobreturno se rechaza con `PROFESIONAL_AUSENTE`

### Requirement: Sin turnos durante un bloqueo de agenda
El sistema MUST rechazar con el código `AGENDA_BLOQUEADA` un turno nuevo, reprogramado, sobreturno o extensión cuyo intervalo se cruza con un bloqueo de agenda del profesional.

#### Scenario: Turno durante un bloqueo
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 13:00
- **AND** un bloqueo de la Dra. A el 2026-12-01 de 11:00 a 12:00
- **WHEN** se crea un turno de "Consulta" con la Dra. A el martes 2026-12-01 a las 10:45
- **THEN** el turno se rechaza con `AGENDA_BLOQUEADA`

#### Scenario: Extensión que entra en un bloqueo
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 13:00
- **AND** un bloqueo de la Dra. A el 2026-12-01 de 11:00 a 12:00
- **AND** un turno activo de la Dra. A el 2026-12-01 de 10:00 a 10:30
- **WHEN** el personal extiende ese turno hasta las 11:15
- **THEN** la extensión se rechaza con `AGENDA_BLOQUEADA`
- **AND** el turno sigue terminando a las 10:30

### Requirement: Sobreturno y extensión fuera del horario de trabajo
El sobreturno (RN-AG-06) y la extensión de un turno (RN-AG-14) SHALL poder quedar fuera de las franjas de trabajo del profesional. Las ausencias, licencias y bloqueos sí los frenan.

#### Scenario: Sobreturno fuera de horario
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 13:00
- **WHEN** el personal crea un sobreturno con la Dra. A el martes 2026-12-01 a las 13:30
- **THEN** el sobreturno se crea

#### Scenario: Extensión que pasa el fin de la franja
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 13:00
- **AND** un turno activo de la Dra. A el 2026-12-01 de 12:30 a 13:00
- **WHEN** el personal extiende ese turno hasta las 13:20
- **THEN** el turno termina a las 13:20

### Requirement: Marcar la ausencia del paciente
El odontólogo del turno SHALL poder marcar que el paciente no se presentó a un turno activo cuya hora de inicio ya pasó. El turno pasa a `AUSENTE`, deja de ocupar la agenda y el horario que resta se puede volver a dar desde el momento actual (RN-AG-17).

#### Scenario: La ausencia libera el resto del horario
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:10
- **AND** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** el odontólogo de la Dra. A lo marca como ausente
- **THEN** el turno queda en estado `AUSENTE`
- **AND** se puede crear un turno de "Consulta" con la Dra. A en el Sillón 1 a las 10:15

#### Scenario: Ausencia en un turno que ya terminó
- **GIVEN** que el momento actual es el 2026-12-01 a las 11:00
- **AND** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A
- **WHEN** el odontólogo de la Dra. A lo marca como ausente
- **THEN** el turno queda en estado `AUSENTE`

### Requirement: La ausencia solo después del inicio
El sistema MUST rechazar marcar como ausente un turno cuya hora de inicio todavía no llegó, con el código `TURNO_NO_INICIADO`. El turno MUST quedar sin cambios.

#### Scenario: Ausencia antes del inicio
- **GIVEN** que el momento actual es el 2026-12-01 a las 09:50
- **AND** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A
- **WHEN** el odontólogo de la Dra. A lo marca como ausente
- **THEN** la operación se rechaza con `TURNO_NO_INICIADO`
- **AND** el turno sigue en estado `RESERVADO`

### Requirement: La ausencia solo sobre turnos activos
El sistema MUST rechazar marcar como ausente un turno que no está activo con el código `TURNO_NO_ACTIVO`.

#### Scenario: Ausencia en un turno cancelado
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:10
- **AND** un turno `CANCELADO` de "Paciente Uno" de 10:00 a 10:30 con la Dra. A
- **WHEN** el odontólogo de la Dra. A lo marca como ausente
- **THEN** la operación se rechaza con `TURNO_NO_ACTIVO`

### Requirement: Solo el odontólogo del turno marca la ausencia
El sistema MUST rechazar con `403` y el código `ACCESO_DENEGADO` que un usuario marque la ausencia si no es un `ODONTOLOGO` vinculado al profesional del turno. Esto incluye a otro odontólogo, al administrador y a la recepción.

#### Scenario: Otro odontólogo marca la ausencia
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:10
- **AND** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A
- **WHEN** el odontólogo del Dr. B lo marca como ausente
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** el turno sigue en estado `RESERVADO`

#### Scenario: La recepción marca la ausencia
- **GIVEN** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A
- **WHEN** `recepcion` lo marca como ausente
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

### Requirement: La ausencia corresponde al paciente del turno
El sistema MUST responder `404` con el código `TURNO_INEXISTENTE` cuando el turno no existe o no es de ese paciente, sin revelar de quién es.

#### Scenario: Turno de otro paciente
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:10
- **AND** un turno activo de "Paciente Dos" de 10:00 a 10:30 con la Dra. A
- **WHEN** el odontólogo de la Dra. A marca ese turno como ausencia de "Paciente Uno"
- **THEN** la respuesta es `404` con el código `TURNO_INEXISTENTE`
- **AND** el turno sigue en estado `RESERVADO`
