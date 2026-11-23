## MODIFIED Requirements

### Requirement: Agenda de la semana
La agenda SHALL permitir cambiar entre la vista del día y la de la semana. La semana va de lunes 00:00 al lunes siguiente 00:00 y muestra los turnos agrupados por día. Los botones "Anterior", "Hoy" y "Siguiente" SHALL mover el período mostrado un día o una semana según la vista. "Anterior" y "Siguiente" SHALL mostrarse como flechas hacia la izquierda y hacia la derecha, sin texto visible, y MUST conservar "Anterior" y "Siguiente" como nombre accesible y como texto de ayuda al pasar el mouse, para todos los roles del personal.

#### Scenario: Cambiar a la vista semanal
- **GIVEN** la agenda del martes 2026-12-01 abierta
- **WHEN** se elige la vista "Semana"
- **THEN** se pide `GET /api/v1/turnos?desde=2026-11-30T00:00:00-03:00&hasta=2026-12-07T00:00:00-03:00`
- **AND** los turnos se ven agrupados bajo cada día de lunes a domingo

#### Scenario: Ir al día siguiente
- **GIVEN** la agenda del día 2026-12-01 abierta
- **WHEN** se usa "Siguiente"
- **THEN** se pide la agenda con `desde=2026-12-02T00:00:00-03:00` y `hasta=2026-12-03T00:00:00-03:00`

#### Scenario: Volver a hoy
- **GIVEN** la agenda de la semana del 2026-12-14 abierta
- **WHEN** se usa "Hoy"
- **THEN** se muestra la semana que contiene el 2026-12-01

#### Scenario: Anterior y Siguiente como flechas
- **GIVEN** la agenda del día abierta con cualquier rol del personal
- **WHEN** se mira la barra del período
- **THEN** "Anterior" se ve como una flecha hacia la izquierda y "Siguiente" como una flecha hacia la derecha, sin texto visible
- **AND** los dos botones tienen el nombre accesible "Anterior" y "Siguiente"

### Requirement: Filtros por profesional y por sillón
La agenda SHALL ofrecer los filtros "Profesional" y "Sillón" con las opciones de `GET /api/v1/profesionales` y `GET /api/v1/sillones`, más la opción "Todos". Al elegir una opción SHALL volver a pedir la agenda con `profesionalId` o `sillonId`. Si esas listas no se pueden obtener, los filtros SHALL quedar solo con "Todos" y un aviso. Con sesión `ODONTOLOGO` (RN-AG-18), la agenda MUST NOT ofrecer el filtro "Profesional": SHALL mostrar el título "Mi agenda" con el nombre de su profesional debajo, pedir siempre `GET /api/v1/turnos` con el `profesionalId` de la sesión y conservar el filtro "Sillón".

#### Scenario: Filtrar por profesional
- **GIVEN** la agenda del día abierta
- **WHEN** se elige "Ana Ejemplo" en el filtro "Profesional"
- **THEN** se pide `GET /api/v1/turnos` del mismo período con `profesionalId=1`

#### Scenario: Filtrar por sillón y profesional a la vez
- **GIVEN** la agenda filtrada por "Ana Ejemplo"
- **WHEN** se elige "Sillón 2" en el filtro "Sillón"
- **THEN** se pide `GET /api/v1/turnos` del mismo período con `profesionalId=1` y `sillonId=2`

#### Scenario: Catálogos no disponibles
- **GIVEN** que `GET /api/v1/profesionales` responde `404`
- **WHEN** se abre la agenda
- **THEN** el filtro "Profesional" solo tiene la opción "Todos"
- **AND** se ve el aviso "No pudimos cargar la lista de profesionales."
- **AND** los turnos del día se muestran igual

#### Scenario: El odontólogo ve solo su agenda
- **GIVEN** la sesión de `ana.ejemplo` con rol `ODONTOLOGO` y `profesionalId=1` ("Ana Ejemplo")
- **WHEN** se abre la agenda del día
- **THEN** no se ve el filtro "Profesional"
- **AND** se ve el título "Mi agenda" con el nombre "Ana Ejemplo"
- **AND** se pide `GET /api/v1/turnos` del día con `profesionalId=1`

#### Scenario: El odontólogo filtra por sillón
- **GIVEN** la agenda del día abierta con la sesión de `ana.ejemplo` (`profesionalId=1`)
- **WHEN** se elige "Sillón 2" en el filtro "Sillón"
- **THEN** se pide `GET /api/v1/turnos` del mismo período con `profesionalId=1` y `sillonId=2`

#### Scenario: Recepción conserva el filtro por profesional
- **GIVEN** la sesión de `recepcion` con rol `RECEPCIONISTA`
- **WHEN** se abre la agenda del día
- **THEN** se ven los filtros "Profesional" y "Sillón"
