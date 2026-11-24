# reportes Specification

## Purpose

Dar al administrador y a la recepción cifras agregadas del consultorio en un período: ocupación de la agenda, origen de los turnos, ausencias y cancelaciones (RN-RE-01 a RN-RE-07). En los escenarios, con datos ficticios, la Dra. Ana Ejemplo (profesional 1) tiene el Sillón 1 como habitual y atiende de lunes a viernes de 09:00 a 13:00; el Dr. Bruno Prueba (profesional 2) tiene el Sillón 2 como habitual y atiende los lunes de 09:00 a 10:00; el Sillón 3 no es habitual de nadie. La semana de referencia va del lunes 2026-12-07 al domingo 2026-12-13. Las cuentas son `admin` (`ADMIN`), `recepcion` (`RECEPCIONISTA`), `ana.ejemplo` (`ODONTOLOGO`) y `paciente.uno@example.com` (`PACIENTE`).

## Requirements

### Requirement: Consultar los reportes de un período
`GET /api/v1/reportes` con `desde` y `hasta` (fechas `AAAA-MM-DD` del consultorio, las dos incluidas) y un `profesionalId` opcional SHALL responder `200` con los bloques `ocupacion`, `origen`, `ausencias` y `cancelaciones`. Un turno MUST contar en el período si su `inicio` cae entre las 00:00 de `desde` y las 24:00 de `hasta` en `America/Argentina/Buenos_Aires`. Con `profesionalId`, todos los bloques MUST contar solo los turnos, ausencias y licencias de ese profesional.

#### Scenario: Reporte de una semana
- **GIVEN** la sesión de `admin`
- **WHEN** pide `GET /api/v1/reportes?desde=2026-12-07&hasta=2026-12-13`
- **THEN** la respuesta es `200` con `desde` `2026-12-07`, `hasta` `2026-12-13` y los bloques `ocupacion`, `origen`, `ausencias` y `cancelaciones`

#### Scenario: Un turno cuenta por su inicio
- **GIVEN** un turno de la Dra. Ana Ejemplo que empieza el domingo 2026-12-13 a las 23:30 y otro que empieza el lunes 2026-12-14 a las 00:10
- **WHEN** se piden los reportes del 2026-12-07 al 2026-12-13
- **THEN** cuenta el primero y no el segundo

#### Scenario: Reporte de un profesional
- **GIVEN** turnos de la Dra. Ana Ejemplo y del Dr. Bruno Prueba en la semana
- **WHEN** se piden los reportes de la semana con `profesionalId` 2
- **THEN** la ocupación por profesional tiene solo al Dr. Bruno Prueba
- **AND** el origen, las ausencias y las cancelaciones cuentan solo sus turnos

### Requirement: Período válido
El período MUST tener `desde` y `hasta`, con `hasta` igual o posterior a `desde`, y como máximo 92 días contando los dos extremos. Si no, la respuesta MUST ser `422` con el código `DATOS_INVALIDOS` y el campo en error. Un `profesionalId` que no existe MUST responder `422` con el campo `profesionalId`.

#### Scenario: Fin anterior al inicio
- **WHEN** `admin` pide los reportes con `desde` `2026-12-13` y `hasta` `2026-12-07`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `hasta`

#### Scenario: Período de 93 días
- **WHEN** `admin` pide los reportes del 2026-12-01 al 2027-03-03
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `hasta`

#### Scenario: Período de 92 días
- **WHEN** `admin` pide los reportes del 2026-12-01 al 2027-03-02
- **THEN** la respuesta es `200`

#### Scenario: Falta una fecha
- **WHEN** `admin` pide `GET /api/v1/reportes?desde=2026-12-07`
- **THEN** la respuesta es `422` con el campo `hasta`

#### Scenario: Profesional inexistente
- **WHEN** `admin` pide los reportes de la semana con `profesionalId` 999999
- **THEN** la respuesta es `422` con el campo `profesionalId`

### Requirement: Permisos de los reportes
Los reportes SHALL estar disponibles para `ADMIN` y `RECEPCIONISTA`. Una sesión `ODONTOLOGO` o `PACIENTE` MUST recibir `403`, y un pedido sin sesión, `401`.

#### Scenario: La recepción ve los reportes
- **WHEN** `recepcion` pide los reportes de la semana
- **THEN** la respuesta es `200`

#### Scenario: El odontólogo no ve los reportes
- **WHEN** `ana.ejemplo` pide los reportes de la semana
- **THEN** la respuesta es `403`

#### Scenario: El paciente no ve los reportes
- **WHEN** `paciente.uno@example.com` pide los reportes de la semana
- **THEN** la respuesta es `403`

#### Scenario: Sin sesión
- **WHEN** un navegador sin sesión pide los reportes de la semana
- **THEN** la respuesta es `401`

### Requirement: Sin datos de pacientes
La respuesta MUST tener solo cifras e identificadores y nombres de profesionales y sillones. MUST NOT tener ningún nombre, DNI, contacto ni id de paciente, ni prestaciones, piezas o motivos (RN-RE-02).

#### Scenario: La respuesta no identifica pacientes
- **GIVEN** turnos de "Paciente Uno" (DNI 99000001) en la semana, uno de ellos un sobreturno con motivo
- **WHEN** `admin` pide los reportes de la semana
- **THEN** el cuerpo de la respuesta no contiene "Paciente Uno", "Uno", "99000001", `pacienteId`, el email del paciente ni el texto del motivo

### Requirement: Ocupación por profesional
Para cada profesional activo, y cada inactivo con turnos en el período, la respuesta SHALL informar `minutosOcupados`: la suma de `fin − inicio` de sus turnos `RESERVADO`, `CONFIRMADO`, `ATENDIDO` y `AUSENTE`, sobreturnos incluidos y sin el margen. También SHALL informar `minutosAtencion`: sus horarios de trabajo en los días del período, menos lo que se superpone con sus ausencias, licencias y bloqueos de agenda.

#### Scenario: Ocupación de una semana
- **GIVEN** la Dra. Ana Ejemplo con tres turnos de 30 minutos y uno `CANCELADO` de 30 minutos en la semana, sin ausencias ni bloqueos
- **WHEN** `admin` pide los reportes de la semana
- **THEN** la Dra. Ana Ejemplo tiene `minutosOcupados` 90, `minutosAtencion` 1200 y `porcentaje` 8

#### Scenario: Las ausencias y los bloqueos descuentan atención
- **GIVEN** la Dra. Ana Ejemplo con una licencia todo el miércoles 2026-12-09 y un bloqueo de agenda el jueves 2026-12-10 de 10:00 a 11:00
- **WHEN** `admin` pide los reportes de la semana
- **THEN** su `minutosAtencion` es 900

### Requirement: Porcentaje de ocupación
El `porcentaje` SHALL ser minutos ocupados / minutos de atención, en entero redondeado hacia arriba desde 0,5, y puede superar 100 por los sobreturnos. Sin minutos de atención, MUST ser `null`.

#### Scenario: Los sobreturnos pueden superar el 100 %
- **GIVEN** el Dr. Bruno Prueba con dos turnos de 30 minutos y un sobreturno de 30 minutos el lunes 2026-12-07
- **WHEN** `admin` pide los reportes del 2026-12-07 al 2026-12-07
- **THEN** el Dr. Bruno Prueba tiene `minutosOcupados` 90, `minutosAtencion` 60 y `porcentaje` 150

#### Scenario: Profesional sin horario
- **GIVEN** un profesional activo sin horarios de trabajo
- **WHEN** `admin` pide los reportes de la semana
- **THEN** ese profesional tiene `minutosAtencion` 0 y `porcentaje` `null`

### Requirement: Ocupación por sillón
Sin `profesionalId`, para cada sillón activo y cada sillón inactivo con turnos en el período, la respuesta SHALL informar `minutosOcupados` de los turnos no cancelados de ese sillón y `minutosAtencion` de los profesionales que lo tienen como sillón habitual, con el mismo `porcentaje`. Con `profesionalId`, la lista de sillones MUST venir vacía, porque un sillón lo comparten varios profesionales.

#### Scenario: Sillón habitual
- **GIVEN** en la semana, turnos no cancelados por 120 minutos en el Sillón 1, de la Dra. Ana Ejemplo y del Dr. Bruno Prueba
- **WHEN** `admin` pide los reportes de la semana
- **THEN** el Sillón 1 tiene `minutosOcupados` 120, `minutosAtencion` 1200 y `porcentaje` 10

#### Scenario: Sillón que no es habitual de nadie
- **GIVEN** un turno de 30 minutos en el Sillón 3
- **WHEN** `admin` pide los reportes de la semana
- **THEN** el Sillón 3 tiene `minutosOcupados` 30, `minutosAtencion` 0 y `porcentaje` `null`

#### Scenario: Sin sillones con filtro de profesional
- **WHEN** `admin` pide los reportes de la semana con `profesionalId` 1
- **THEN** la lista de sillones está vacía

### Requirement: Origen de los turnos
El bloque `origen` SHALL contar los turnos del período, salvo los cancelados por una reprogramación (que reemplaza otro turno), en `online` (el registro de creación de la auditoría, `CREAR` o `SOBRETURNO`, tiene un actor que empieza con `paciente-online:`), `consultorio` (cualquier otro actor) y `sinDato` (sin registro de creación) (RN-RE-05, DD-16).

#### Scenario: Turnos online y del consultorio
- **GIVEN** en la semana, tres turnos creados por `paciente-online:2`, dos creados por `recepcion` y un sobreturno de `admin`
- **WHEN** `admin` pide los reportes de la semana
- **THEN** `origen` tiene `online` 3, `consultorio` 3 y `sinDato` 0

#### Scenario: Turno sin registro de creación
- **GIVEN** un turno de la semana sin ningún registro `CREAR` ni `SOBRETURNO` en la auditoría
- **WHEN** `admin` pide los reportes de la semana
- **THEN** `origen` cuenta ese turno en `sinDato`

#### Scenario: Una reprogramación no cuenta dos veces
- **GIVEN** un turno creado por `paciente-online:2` que la recepción reprogramó dentro de la semana
- **WHEN** `admin` pide los reportes de la semana
- **THEN** `origen` cuenta un solo turno, en `consultorio`, el nuevo

### Requirement: Ausencias de pacientes
El bloque `ausencias` SHALL informar para los pacientes `ausentes` (turnos `AUSENTE`), `atendidos` (turnos `ATENDIDO`), `tasa` = ausentes / (atendidos + ausentes) en entero redondeado hacia arriba desde 0,5, o `null` si no hay ninguno, y `sinRegistrar` (turnos `RESERVADO` o `CONFIRMADO` cuyo fin ya pasó), que no entra en la tasa (RN-RE-06).

#### Scenario: Tasa de ausentismo
- **GIVEN** en la semana, 2 turnos `AUSENTE`, 6 `ATENDIDO` y 1 `RESERVADO` que ya terminó
- **WHEN** `admin` pide los reportes de la semana
- **THEN** los pacientes tienen `ausentes` 2, `atendidos` 6, `tasa` 25 y `sinRegistrar` 1

#### Scenario: Sin turnos atendidos ni ausentes
- **GIVEN** una semana sin turnos `ATENDIDO` ni `AUSENTE`
- **WHEN** `admin` pide los reportes de esa semana
- **THEN** la `tasa` es `null`

### Requirement: Ausencias de profesionales
El bloque `ausencias` SHALL listar, por profesional y por tipo (`AUSENCIA` o `LICENCIA`), la `cantidad` de registros que se superponen con el período y los `minutos` que caen dentro de él (RN-RE-06).

#### Scenario: Licencia que empieza antes del período
- **GIVEN** una licencia del Dr. Bruno Prueba del viernes 2026-12-04 a las 00:00 al martes 2026-12-08 a las 00:00
- **WHEN** `admin` pide los reportes de la semana
- **THEN** los profesionales tienen al Dr. Bruno Prueba con tipo `LICENCIA`, `cantidad` 1 y `minutos` 1440

### Requirement: Cancelaciones
El bloque `cancelaciones` SHALL contar los turnos `CANCELADO` del período en `porReprogramacion` (la auditoría tiene `REPROGRAMAR` para ese turno), `porPaciente` (el actor de `CANCELAR` empieza con `paciente-online:`), `porConsultorio` (otro actor de `CANCELAR`) y `sinDato` (sin ninguno de los dos registros) (RN-RE-07).

#### Scenario: Cancelaciones por quién canceló
- **GIVEN** en la semana, dos turnos cancelados por `paciente-online:2`, uno cancelado por `recepcion` y uno reprogramado por `recepcion`
- **WHEN** `admin` pide los reportes de la semana
- **THEN** `cancelaciones` tiene `porPaciente` 2, `porConsultorio` 1, `porReprogramacion` 1 y `sinDato` 0
