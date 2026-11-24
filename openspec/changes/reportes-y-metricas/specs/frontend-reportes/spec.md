## Purpose

Definir la pantalla "Reportes" con la que el administrador y la recepción ven las cifras del consultorio de un período (US-210). En los escenarios, con datos ficticios, hoy es el miércoles 2026-12-09 en el consultorio, la sesión es de `recepcion` y existen la Dra. Ana Ejemplo (profesional 1), el Dr. Bruno Prueba (profesional 2) y los sillones 1 y 2.

## ADDED Requirements

### Requirement: Período y filtro de los reportes
La página `#/reportes` SHALL mostrar los campos "Desde", "Hasta" y "Profesional" ("Todos" o un profesional activo). Al abrirla MUST proponer la semana en curso, de lunes a domingo, y pedir `GET /api/v1/reportes` con ese período. Cada cambio válido de un campo SHALL volver a pedir los reportes. Si "Hasta" es anterior a "Desde", o el período tiene más de 92 días, MUST mostrar el error junto al campo "Hasta" y no pedir nada.

#### Scenario: Semana en curso al abrir
- **WHEN** se abre `#/reportes`
- **THEN** "Desde" es `2026-12-07`, "Hasta" es `2026-12-13` y "Profesional" es "Todos"
- **AND** se pide `GET /api/v1/reportes?desde=2026-12-07&hasta=2026-12-13`

#### Scenario: Filtrar por profesional
- **GIVEN** la página abierta
- **WHEN** se elige "Bruno Prueba" en "Profesional"
- **THEN** se pide `GET /api/v1/reportes?desde=2026-12-07&hasta=2026-12-13&profesionalId=2`

#### Scenario: Fin anterior al inicio
- **WHEN** se elige "Hasta" `2026-12-01` con "Desde" `2026-12-07`
- **THEN** el campo "Hasta" muestra "La fecha final tiene que ser igual o posterior a la inicial."
- **AND** no se pide ningún reporte nuevo

#### Scenario: Período de más de 92 días
- **WHEN** se elige "Desde" `2026-12-01` y "Hasta" `2027-03-03`
- **THEN** el campo "Hasta" muestra "Elegí un período de hasta 92 días."
- **AND** no se pide ningún reporte nuevo

### Requirement: Bloque de ocupación
La página SHALL mostrar "Ocupación" con una fila por profesional y otra por sillón: nombre, porcentaje con una barra y "X de Y min". La barra MUST tener el porcentaje como texto, no solo como largo o color, y se llena hasta el 100 % aunque el porcentaje sea mayor. Con `porcentaje` `null` MUST mostrar "Sin horario" y los minutos ocupados. Con un profesional elegido, en lugar de los sillones SHALL mostrar "La ocupación por sillón se ve sin filtro de profesional."

#### Scenario: Ocupación por profesional
- **GIVEN** la API responde para la Dra. Ana Ejemplo `minutosOcupados` 90, `minutosAtencion` 1200 y `porcentaje` 8
- **WHEN** se muestran los reportes
- **THEN** la fila de "Ana Ejemplo" dice "8 %" y "90 de 1.200 min"
- **AND** su barra tiene `aria-valuenow` 8

#### Scenario: Más del 100 %
- **GIVEN** la API responde para el Dr. Bruno Prueba `porcentaje` 150
- **WHEN** se muestran los reportes
- **THEN** la fila de "Bruno Prueba" dice "150 %"
- **AND** su barra está llena y tiene `aria-valuenow` 150

#### Scenario: Sin horario
- **GIVEN** la API responde para el Sillón 3 `minutosOcupados` 30, `minutosAtencion` 0 y `porcentaje` `null`
- **WHEN** se muestran los reportes
- **THEN** la fila de "Sillón 3" dice "Sin horario" y "30 min ocupados"

#### Scenario: Sillones con filtro de profesional
- **GIVEN** "Ana Ejemplo" elegida en "Profesional"
- **WHEN** se muestran los reportes
- **THEN** en lugar de los sillones se ve "La ocupación por sillón se ve sin filtro de profesional."

### Requirement: Bloques de origen, ausencias y cancelaciones
La página SHALL mostrar "Origen de los turnos" (online, del consultorio y, si es mayor que cero, sin dato, cada uno con su cantidad y su porcentaje del total), "Ausencias" (pacientes ausentes, tasa de ausentismo, turnos sin registrar, y las ausencias y licencias de profesionales en horas) y "Cancelaciones" (por el paciente, por el consultorio, por reprogramación y, si es mayor que cero, sin dato).

#### Scenario: Origen de los turnos
- **GIVEN** la API responde `origen` con `online` 3, `consultorio` 3 y `sinDato` 0
- **WHEN** se muestran los reportes
- **THEN** se ve "Online: 3 (50 %)" y "Del consultorio: 3 (50 %)"
- **AND** no se ve "Sin dato"

#### Scenario: Ausencias
- **GIVEN** la API responde para los pacientes `ausentes` 2, `atendidos` 6, `tasa` 25 y `sinRegistrar` 1, y para los profesionales al Dr. Bruno Prueba con `LICENCIA`, `cantidad` 1 y `minutos` 1440
- **WHEN** se muestran los reportes
- **THEN** se ve "Ausentes: 2", "Ausentismo: 25 %" y "Sin registrar: 1"
- **AND** se ve "Bruno Prueba · Licencia · 24 h"

#### Scenario: Sin ausentismo calculable
- **GIVEN** la API responde `tasa` `null`
- **WHEN** se muestran los reportes
- **THEN** se ve "Ausentismo: sin turnos atendidos ni ausentes"

#### Scenario: Cancelaciones
- **GIVEN** la API responde `cancelaciones` con `porPaciente` 2, `porConsultorio` 1, `porReprogramacion` 1 y `sinDato` 0
- **WHEN** se muestran los reportes
- **THEN** se ve "Por el paciente: 2", "Por el consultorio: 1" y "Por reprogramación: 1"

### Requirement: Período sin turnos y errores
Si el período no tiene turnos ni ausencias de profesionales, la página SHALL mostrar el estado vacío "No hay turnos en este período." en lugar de los bloques. Si la API responde con un error, MUST mostrar su mensaje y conservar el período elegido. Una sesión vencida MUST llevar a Mi cuenta, como en las demás páginas del personal.

#### Scenario: Período vacío
- **GIVEN** la API responde todos los conteos en cero y sin ausencias de profesionales
- **WHEN** se muestran los reportes
- **THEN** se ve "No hay turnos en este período."

#### Scenario: Error de la API
- **GIVEN** la API responde `422` con el campo `hasta` y el mensaje "El período no puede superar los 92 días."
- **WHEN** se piden los reportes
- **THEN** el campo "Hasta" muestra "El período no puede superar los 92 días."
- **AND** los campos conservan las fechas elegidas
