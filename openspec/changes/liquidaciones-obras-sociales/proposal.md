# Proposal

## Why

El consultorio ya sabe con qué obra social se atiende cada paciente (C-18) y cuánto cubre cada plan (C-19), pero no puede armar lo que le pide a cada obra social por mes ni controlar lo que le pagaron. La KB del dominio "Liquidaciones" quedó aprobada por la líder el 2026-11-24 (PR #120: RN-LQ-01 a RN-LQ-08, US-049, DD-19 y las respuestas a PA-38 a PA-42), así que C-20 pasa a propuesta. Es governance **ALTO** (dinero): la líder revisa este propose antes de escribir código.

## What Changes

- **Generar la liquidación** (RN-LQ-01, RN-LQ-02, DD-19): el `ADMIN` elige una obra social y un **mes cerrado** (en `America/Argentina/Buenos_Aires`) y el sistema arma una liquidación en `BORRADOR` con una línea por **turno `ATENDIDO`** de un paciente afiliado a un plan de esa obra social en la fecha del turno, cuya prestación tiene un código del nomenclador que el plan cubre con más de 0 %. Entran también los turnos atendidos de meses anteriores que todavía no se liquidaron. Una liquidación por obra social y mes.
- **Monto de cada línea** (RN-LQ-03, PA-40): precio × porcentaje / 100 a centavos *half up* (como RN-PR-03). Si el turno ya tiene su **cargo de caja** (C-22) se usan su precio y su porcentaje; si no, el arancel y la cobertura vigentes al generar. La línea guarda una **foto** (fecha, paciente, DNI, número de afiliado, plan, código, prestación, precio, porcentaje y monto).
- **Prestación con varios códigos** (PA-38): la línea queda **sin código** en el borrador y el `ADMIN` elige uno de los que cubre el plan; no se puede presentar con líneas sin código.
- **Borrador**: se puede **regenerar** (agrega turnos nuevos y recalcula los montos) y **quitar líneas**; un turno quitado queda libre para otra liquidación.
- **Presentar** (RN-LQ-04, PA-42): `BORRADOR` → `PRESENTADA`; desde ahí las líneas y los montos solicitados no cambian y la liquidación no se reabre.
- **Exportar a CSV** (RN-LQ-05, PA-41): una liquidación presentada o cerrada se descarga en CSV UTF-8, una fila por línea más el total, sin caché.
- **Conciliar** (RN-LQ-06): el `ADMIN` registra por línea el **monto cobrado** (0 a lo solicitado); el sistema deriva `COBRADA`, `PARCIAL` o `RECHAZADA` (con motivo obligatorio). Se puede conciliar por partes; se informan el total cobrado y la diferencia.
- **Cerrar**: `PRESENTADA` → `CERRADA` cuando todas las líneas tienen resultado. Nada se borra.
- **Solo el `ADMIN`** (PA-39) en `/api/v1/liquidaciones`; los demás roles reciben `403`. Nombres, DNI, números de afiliado, prestaciones e importes no van a logs, mensajes ni errores (RN-LQ-08).
- Errores con código estable en `ProblemDetail`: `409` (`LIQUIDACION_EXISTENTE`, `PERIODO_NO_CERRADO`, `LIQUIDACION_PRESENTADA`, `LIQUIDACION_CERRADA`, `LIQUIDACION_NO_PRESENTADA`, `LIQUIDACION_VACIA`, `LINEA_SIN_CODIGO`, `CONCILIACION_INCOMPLETA`), `422` (`DATOS_INVALIDOS`, `MONTO_COBRADO_INVALIDO`, `MOTIVO_REQUERIDO`, `CODIGO_NOMENCLADOR_INVALIDO`) y `404 RECURSO_NO_ENCONTRADO`.
- **Migración V14** (`liquidacion`, `liquidacion_linea`, `liquidacion_turno_quitado`) con `UNIQUE`, `CHECK` del cálculo y de la conciliación, y triggers de inmutabilidad por estado.
- **Solo backend**, como C-18 y C-19: las pantallas van en una ficha de frontend posterior.

**Fuera de alcance**: exportación a PDF (PA-41); facturación electrónica ARCA; cobrarle al paciente la diferencia rechazada (C-22, RN-LQ-07); validación online, topes y autorizaciones (C-21); reabrir una liquidación presentada (PA-42); registro de "práctica realizada" (DD-19); pantallas.

## Capabilities

### New Capabilities
- `liquidaciones-obras-sociales`: generación de la liquidación mensual por obra social con los turnos atendidos, cálculo y foto de cada línea, código del nomenclador, borrador (regenerar y quitar), presentación, exportación CSV, permisos y privacidad.
- `conciliacion-liquidaciones`: registro por línea de lo cobrado, resultado derivado, totales y diferencia, conciliación parcial y cierre de la liquidación.

### Modified Capabilities
<!-- Ninguna. catalogo-coberturas y afiliaciones-pacientes no cambian (C-20 solo las lee). Las specs de C-19 (aranceles-y-cobertura-planes, presupuestos) todavía no están en openspec/specs y no se modifican: C-20 solo lee arancel y cobertura_plan. agenda-turnos no cambia: el turno se lee con un puerto de solo lectura. seguridad-autorizacion ya cubre /api/v1/** por rol con @PreAuthorize por operación. -->

## Impact

- **Código**: se amplía `backend/.../coberturas/` (dueño: Juan Sebastián) con dominio, aplicación, infraestructura y API de liquidaciones. No modifica otros módulos: lee `turno` (agenda, Sofia) y `paciente` (Juan Cruz) con un puerto JDBC de solo lectura, y `arancel`, `cobertura_plan`, `nomenclador` y `afiliacion_paciente` del mismo módulo.
- **Dependencia con C-22** (Enzo): en `master` no existe la tabla `cargo` ni código de caja (verificado el 2026-11-24). C-20 define un puerto `CargosDeTurno` con un adaptador que hoy no encuentra cargos; el adaptador que lee `cargo` se conecta cuando C-22 se mergee (design D5, a confirmar).
- **Base de datos**: `V14__liquidaciones.sql` (número reservado para C-20; Flyway corre con out-of-order porque V22 ya está aplicada). Sin seed de desarrollo.
- **API**: endpoints nuevos bajo `/api/v1/liquidaciones`. Ninguno existente cambia. `SecurityConfig` no cambia.
- **Avisos**: a Sofia (lectura de `turno` y FK a `turno`), a Juan Cruz (lectura y FK a `paciente`) y a Enzo (uso futuro del `cargo` de C-22).
