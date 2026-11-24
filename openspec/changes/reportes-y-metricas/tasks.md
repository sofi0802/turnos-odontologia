# Tasks

Acceptance criteria: los escenarios de los deltas `reportes`, `frontend-reportes` y `frontend-navegacion`, y el mockup aprobado en `mockups/` (ver `design.md`, sección UI Mockups).

## 0. Mockups (antes del apply)

- [x] 0.1 Mockups de la pantalla Reportes con los tokens de la Revision 5: con datos, con filtro de profesional, período vacío, error de período y aviso para el odontólogo, a 390 y 1440 px; capturas en `mockups/previews/`
- [x] 0.2 Aprobación de los mockups registrada en `design.md` (`Approval: approved`) antes de empezar el grupo 1

## 1. Dominio de reportes (backend, unitarios sin base)

- [x] 1.1 Test primero: `Periodo` acepta 1 y 92 días, rechaza 93 días y un fin anterior al inicio con el campo `hasta`, y da el intervalo `[desde 00:00, hasta + 1 día 00:00)` en la zona del consultorio
- [x] 1.2 Implementar `reportes/domain/Periodo` e `Intervalos` (unión, resta y minutos), con tests de bordes: intervalos contiguos, contenidos y disjuntos
- [x] 1.3 Test primero, un caso por escenario de cálculo del delta `reportes`: ocupación de una semana, ausencias y bloqueos que descuentan atención, más del 100 %, sin horario, sillón habitual, sillón que no es habitual de nadie, sin sillones con filtro, origen (online, consultorio, sin dato, reprogramación), tasa de ausentismo y tasa `null`, licencia que empieza antes del período, cancelaciones
- [x] 1.4 Implementar `reportes/domain/CalculoReportes` con el redondeo `HALF_UP` de design D4 y la clasificación por actor de design D5

## 2. Lectura, caso de uso y API (backend)

- [x] 2.1 Implementar el puerto `LecturaReportes` y `LecturaReportesJdbc` (design D2), solo lectura y sin entidades JPA de otros módulos
- [x] 2.2 Implementar `ConsultaReportes` con el `Clock` y la `ZoneId` existentes, y `ReportesController` en `GET /api/v1/reportes` con `@PreAuthorize` (design D8) y los records de design D9
- [x] 2.3 Implementar `ReportesExceptionHandler`: `422 DATOS_INVALIDOS` con `errores` por campo para fecha faltante o ilegible, período inválido y profesional inexistente
- [x] 2.4 Tests de integración (`@Tag("integration")`, PostgreSQL real), un `@Test` por escenario de "Consultar los reportes de un período", "Período válido", "Permisos de los reportes" y "Sin datos de pacientes", más uno que crea un turno por la reserva online real y lo cuenta como `online` (riesgo de DD-16)
- [x] 2.5 `./gradlew build` en verde (unitarios e integración); guardar la evidencia en `docs/evidencia/c32-backend.txt`

## 3. Navegación (frontend)

- [x] 3.1 Test primero, un `it` por escenario del delta de `frontend-navegacion` ("Menú del personal", "Menú del administrador", "El odontólogo no ve Reportes", "El odontólogo abre la dirección", "Sin sesión en Reportes"); actualizar los tests que esperaban solo "Agenda" y "Configuración" para la recepción
- [x] 3.2 Sumar `avisoSinRol` a `Pagina` y usarlo en `app.ts` (design D10), el ícono `'reportes'` y el registro de la página entre "Pacientes" y "Configuración"

## 4. Pantalla Reportes (frontend)

- [x] 4.1 Test primero, un `it` por escenario de `frontend-reportes` en `frontend/test/reportes.test.ts`
- [x] 4.2 Implementar `frontend/src/reportes/periodo.ts` (semana en curso y validación) y `consulta.ts` (lectura de la respuesta sin `any`)
- [x] 4.3 Implementar `frontend/src/reportes/vista.ts` y `pages/reportes.ts`: campos, bloques, barras con `role="meter"`, estado vacío y errores (design D10)
- [x] 4.4 Estilos de la pantalla en un módulo nuevo de `frontend/src/styles/`, registrado en `test/estilos.ts`, con los tokens del sistema visual

## 5. Verificación

- [x] 5.1 Verificar que cada escenario de los tres deltas tiene un test con su nombre (`grep`), sin duplicados
- [x] 5.2 `npx tsc --noEmit`, `npm test` y `npm run build` en verde; la cantidad de tests no baja
- [x] 5.3 Verificación visual contra el mockup aprobado, a 390 y 1440 px, con el backend de desarrollo y datos ficticios; capturas en `mockups/verify/` y resultado en `design.md`. Hecha a mano por Enzo el 2026-11-24 (sin capturas automatizadas; ver `Visual verify` en `design.md`)
- [x] 5.4 `openspec validate reportes-y-metricas --strict` en verde, C-32 marcado en `CHANGES.md` y, en el PR, aviso a Sofia, Emanuel y Juan Sebastián (design, Impact de la propuesta)

## Workflow follow-up

- Archive del change (`openspec archive reportes-y-metricas`) después del merge, en un PR aparte.
