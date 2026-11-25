# Proposal: caja-y-cobros (C-22)

## Why

El consultorio no tiene forma de registrar lo que cobra ni lo que le deben los pacientes. Los turnos atendidos no dejan ninguna deuda, la recepción no puede abrir ni cerrar una caja, y no hay arqueo. La KB del dominio Caja (RN-CA-01 a RN-CA-08, US-207, DD-18, PA-32 a PA-37) ya está aprobada (PR #118). C-22 es además la base de las señas (C-23) y de la factura (C-24). Governance CRÍTICO (dinero): no se escribe código sin la aprobación explícita de esta propuesta.

## What Changes

- **Caja diaria** (`/api/v1/cajas`): la recepción abre la única caja con un monto inicial en efectivo, consulta la caja abierta con sus totales por medio de pago y la cierra con el efectivo contado; con diferencia, el motivo es obligatorio. El admin y la recepción consultan las cajas cerradas con su arqueo (RN-CA-02, RN-CA-06).
- **Cargos por turno atendido**: el sistema genera un cargo por el copago de cada turno `ATENDIDO` (cálculo de C-19 con la cobertura vigente en la fecha del turno), sin que `agenda/` cambie: el módulo `caja/` busca los turnos atendidos sin cargo antes de cada consulta y en una tarea periódica. Un turno sin arancel figura "Sin precio" hasta que se carga el precio (RN-CA-03, DD-18).
- **Cobros**: la recepción cobra un cargo en todo o en parte con efectivo, débito, crédito o transferencia, en la caja abierta y sin superar el saldo (RN-CA-04). El admin anula un cobro de la caja abierta con un motivo; nada se borra (RN-CA-07).
- **Cuenta corriente** del paciente (`/api/v1/pacientes/{id}/cuenta-corriente`): cargos, cobros y saldo (RN-CA-05). Lista de pendientes de cobro del consultorio.
- **Pantalla "Caja"** en `#/caja` para la recepción y el admin: estado de la caja, apertura, pendientes de cobro con "Cobrar", cobros de la caja (con "Anular" para el admin), cierre con arqueo, cuenta corriente de un paciente con el buscador de C-36, y el historial de cajas.
- **Migración V15** (reservada): tablas `caja`, `cargo` y `cobro` con sus restricciones.
- El menú del personal suma "Caja" para el admin y la recepción.

## Capabilities

### New Capabilities

- `caja`: la caja diaria: apertura, consulta, cierre con arqueo, historial y permisos.
- `cobros-pacientes`: cargos por turno atendido, cobros, anulaciones y cuenta corriente del paciente.
- `frontend-caja`: la pantalla "Caja".

### Modified Capabilities

- `frontend-navegacion`: "Menú según la sesión" y "Página Reportes" (el orden de la barra lateral) suman "Caja", y se agrega la página "Caja" con su acceso por rol.

## Impact

- **Backend**: módulo nuevo `backend/.../caja/` (`api`, `application`, `domain`, `infrastructure`), de Enzo. Migración `V15__caja_y_cobros.sql`. Lee por JDBC, sin escribir, `turno`, `prestacion`, `arancel`, `nomenclador`, `cobertura_plan`, `afiliacion_paciente` y `paciente`. Reusa la fórmula `CalculoItem` de `coberturas/domain` (C-19) para que el copago sea el mismo que en el presupuesto.
- **Avisos en el PR**: Sofia (`agenda/`: se leen los turnos `ATENDIDO`, sin cambiar su código) y Juan Sebastián (`coberturas/`: se reusa `CalculoItem` y se leen aranceles, coberturas y afiliaciones).
- **Frontend**: `frontend/src/pages/caja.ts` y `frontend/src/caja/` nuevos, un ícono y estilos nuevos. Cambian los tests del menú.
- **Visual**: modo `system-and-mockups`; mockups a 390 y 1440 px aprobados antes del apply.
- **Fuera de alcance**: señas y pagos en línea (C-23), facturas (C-24), saldo a favor, anular cargos, cobros sin turno, la cuenta corriente vista por el paciente, varias cajas a la vez y la liquidación a obras sociales (C-20).
