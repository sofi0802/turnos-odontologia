# Tasks

Acceptance criteria: los escenarios de los deltas `caja`, `cobros-pacientes`, `frontend-caja` y `frontend-navegacion`, y el mockup aprobado en `mockups/` (ver `design.md`, sección UI Mockups). Governance CRÍTICO: el grupo 1 empieza solo con la aprobación explícita de la propuesta y de los mockups.

## 0. Mockups y aprobación (antes del apply)

- [x] 0.1 Mockups de la pantalla Caja con los tokens de la Revision 5: sin caja (recepción y admin), caja abierta con pendientes y cobros, cobrar, cerrar con diferencia, cuenta corriente e historial, y la vista del admin con "Anular", a 390 y 1440 px; capturas en `mockups/previews/`
- [x] 0.2 Aprobación explícita de la propuesta y de los mockups registrada en `design.md` (`Approval: approved`) antes de empezar el grupo 1

## 1. Migración y dominio (backend)

- [x] 1.1 `V15__caja_y_cobros.sql` con `caja`, `cargo` y `cobro`, sus CHECK y el índice único parcial de la caja abierta (design D5); test de integración de la migración (restricciones y unicidad)
- [x] 1.2 Test primero: `Dinero`, `MedioPago` y `Arqueo` (diferencia, motivo obligatorio con diferencia distinta de cero, cuadrado sin motivo)
- [x] 1.3 Test primero: `CalculoCargo` con cobertura del 40 %, particular, sin código del nomenclador, varios códigos (el primero) y cobertura del 100 % (cargo saldado); implementar reusando `CalculoItem` de C-19 (design D3)

## 2. Casos de uso, persistencia y API (backend)

- [x] 2.1 Puertos y JDBC: `Cajas`, `Cargos`, `Cobros` y `TurnosACobrar`, con los bloqueos de design D4
- [x] 2.2 `AbrirCaja`, `ConsultaCaja`, `CerrarCaja` e historial, y `CajasController` con `@PreAuthorize` (design D7)
- [x] 2.3 `GeneradorCargos` y `TareaCargos` (design D2), y su llamada al empezar pendientes, cuenta corriente y cobro
- [x] 2.4 `Cobrar`, `AnularCobro`, `CuentaCorriente` y pendientes, con sus controladores y `CajaExceptionHandler`
- [x] 2.5 Logs sin importes ni datos de pacientes (design D8)
- [x] 2.6 Tests de integración (`@Tag("integration")`, PostgreSQL real), un `@Test` por escenario de `caja` y `cobros-pacientes`, incluidos los de concurrencia y el de logs, más uno que compara el cargo con un presupuesto de C-19 de la misma prestación
- [x] 2.7 `./gradlew build integrationTest` en verde; evidencia en `docs/evidencia/c22-backend.txt`

## 3. Navegación (frontend)

- [x] 3.1 Test primero, un `it` por escenario del delta de `frontend-navegacion`; actualizar los tests del menú que esperan el orden anterior
- [x] 3.2 Ícono `'caja'` y registro de la página después de Reportes

## 4. Pantalla Caja (frontend)

- [x] 4.1 Test primero, un `it` por escenario de `frontend-caja` en `frontend/test/caja.test.ts`, y tests de `dinero.ts` (formato y lectura de importes)
- [x] 4.2 `frontend/src/caja/api.ts` y `dinero.ts`
- [x] 4.3 Estado de la caja, apertura y cierre; pendientes y cobro; cobros y anulación; cuenta corriente e historial; mensajes de los `409` (design D10)
- [x] 4.4 Estilos en un módulo nuevo de `frontend/src/styles/`, registrado en `test/estilos.ts`, con los tokens del sistema visual

## 5. Verificación

- [x] 5.1 Cada escenario de los cuatro deltas tiene exactamente un test con su nombre (`grep`)
- [x] 5.2 `npx tsc --noEmit`, `npm test` y `npm run build` en verde; la cantidad de tests no baja
- [x] 5.3 Verificación visual contra el mockup aprobado, a 390 y 1440 px, con el backend de desarrollo y datos ficticios; resultado en `design.md`
- [x] 5.4 `openspec validate caja-y-cobros --strict` en verde, C-22 marcado en `CHANGES.md` y, en el PR, aviso a Sofia (`agenda/`) y a Juan Sebastián (`coberturas/`)

## Workflow follow-up

- Archive del change (`openspec archive caja-y-cobros`) después del merge, en un PR aparte.
