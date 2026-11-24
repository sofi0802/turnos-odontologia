# Tasks

> Rama de implementación `change/C-20-liquidaciones-apply` (el propose va en `change/C-20-propose`). Governance **ALTO** (dinero): **no se escribe código hasta que la líder apruebe este propose** (grupo 0), y hay checkpoint con ella antes del PR (grupo 9). TDD estricto en cada grupo: primero el test en **RED** (falla por la razón esperada), después el código mínimo en **GREEN** y el refactor con los tests en verde. Cada escenario de `specs/liquidaciones-obras-sociales/spec.md` y `specs/conciliacion-liquidaciones/spec.md` tiene su test con `@DisplayName` igual al nombre del escenario (regla dura 7). Los tests de integración llevan `@Tag("integration")` y usan PostgreSQL real (nunca H2). Solo datos ficticios del seed de la KB (regla dura 1). No se toca `agenda/`, `usuarios/`, `caja/` ni `shared/`.

## 0. Aprobación de la líder y avisos (governance ALTO)

- [ ] 0.1 Pedir a la líder (Sofia) la aprobación explícita del propose (proposal, specs, design y tasks) y la respuesta a las 9 decisiones "(a confirmar)" de `design.md` §Open Questions; registrar las respuestas en una sección "Resolución de decisiones" de `design.md`; si alguna difiere de la recomendación, actualizar el change con `openspec-update-change` y verificar con `openspec validate liquidaciones-obras-sociales --strict`. Sin esta aprobación no se empieza el grupo 1
- [ ] 0.2 Avisar a Sofia (dueña de `agenda/`) que `coberturas/` lee `turno` (estado, inicio, paciente y prestación) con un adaptador JDBC de solo lectura y que `liquidacion_linea` y `liquidacion_turno_quitado` tienen FK a `turno`; a Juan Cruz (dueño de `paciente`) que se leen nombre, apellido y DNI y que hay FK a `paciente`; a Enzo (C-22) que C-20 usará el `cargo` de cada turno (precio, porcentaje y cobertura) por el puerto `CargosDeTurno`, que hoy no lo encuentra, y que el adaptador real se conecta cuando C-22 se mergee (design D5). Verificar que los tres avisos queden en la descripción del PR
- [ ] 0.3 Crear la rama `change/C-20-liquidaciones-apply` desde `master` actualizado (con este propose mergeado) y verificar con `git status` que parte limpia y con `git grep -n "cargo" -- backend/src/main` si C-22 ya llegó (si llegó, avisar a la líder antes de seguir: cambia la tarea 4.2)

## 1. Migración V14

- [ ] 1.1 RED: escribir `V14MigrationIntegrationTest` que verifica las tres tablas, los índices y los triggers, y que la base rechaza: liquidación repetida por obra social y período, `periodo` que no es día 1, estado desconocido, `total_cobrado` mayor que `total_solicitado`, `PRESENTADA` sin `presentada_en`; línea con el mismo `turno_id` en dos liquidaciones, con un plan de otra obra social (FK compuesta), con código sin porcentaje, con `monto_solicitado` 12500.07 para 50 % de 25000.15 (y acepta 12500.08), con `monto_cobrado` mayor que el solicitado o negativo, `COBRADA` con monto menor, `PARCIAL` con 0, `RECHAZADA` sin motivo o con motivo en blanco, motivo en una `COBRADA`, conciliación sin código; y los triggers: `DELETE` de una liquidación, `BORRADOR` → `CERRADA`, `PRESENTADA` → `BORRADOR`, cambiar `total_solicitado` o `periodo` en una presentada, cualquier `UPDATE` en una cerrada, `INSERT`/`DELETE` de líneas o de turnos quitados fuera de `BORRADOR`, cambiar el monto solicitado de una línea presentada y cambiar el monto cobrado de una línea cerrada (escenarios "La base rechaza modificar una línea presentada" y "La base rechaza cambiar una liquidación cerrada"); y que acepta conciliar una línea presentada. Verificar que falla porque las tablas no existen
- [ ] 1.2 GREEN: crear `backend/src/main/resources/db/migration/V14__liquidaciones.sql` según design D2 y D3 (comentarios del redondeo, las FK compuestas y los triggers; mensajes fijos sin valores) y verificar con `./gradlew integrationTest --tests '*V14MigrationIntegrationTest'`, y que Flyway la aplica fuera de orden sobre una base con V22 ya aplicada

## 2. Dominio

- [ ] 2.1 RED → GREEN: ampliar `CodigoCobertura` con `LIQUIDACION_EXISTENTE`, `PERIODO_NO_CERRADO`, `LIQUIDACION_PRESENTADA`, `LIQUIDACION_CERRADA`, `LIQUIDACION_NO_PRESENTADA`, `LIQUIDACION_VACIA`, `LINEA_SIN_CODIGO`, `CONCILIACION_INCOMPLETA` (`409`), `MONTO_COBRADO_INVALIDO` y `MOTIVO_REQUERIDO` (`422`), con un test que fija el estado de cada código; verificar con `./gradlew test`
- [ ] 2.2 RED → GREEN: `Periodo` con tests unitarios de cerrado/no cerrado (2026-10 con hoy 2026-10-31, 2026-11-01 y 2026-11-24), primer y último día, fin exclusivo como instante de Buenos Aires y formato `yyyy-MM`; verificar con `./gradlew test`
- [ ] 2.3 RED → GREEN: `MontoCobrado` y `ResultadoConciliacion.de` con tests unitarios de los escenarios de "Monto cobrado y motivo válidos" y "Registrar lo cobrado de cada línea" (15000.00 → `COBRADA`, 10000.00 → `PARCIAL`, 0.00 → `RECHAZADA`, 15000.01, -1.00, 100.005); verificar con `./gradlew test`
- [ ] 2.4 RED → GREEN: `LineaLiquidacion.desde(...)` con tests unitarios de las reglas de design D4 paso 3 (un código, varios con y sin cobertura, ninguno, 0 %, sin arancel, con cargo y cargo al 0 %, código elegido conservado) y de los montos del seed (15000.00, 12500.00, 12000.00, 20000.00, 24000.00, 25000.15 al 50 % → 12500.08) reusando `CalculoItem`; verificar con `./gradlew test`
- [ ] 2.5 RED → GREEN: agregado `Liquidacion` y `EstadoLiquidacion` con tests unitarios de presentar (vacía, sin código, dos veces), conciliar (borrador, cerrada, corrección), cerrar (incompleta, completa) y totales de design D8 (27500.00 / 25000.00 / 2500.00); verificar con `./gradlew test` y con `git grep -n "import org.springframework\|import jakarta.persistence" -- backend/src/main/java/ar/turnos/odontologia/coberturas/domain` vacío

## 3. Generar la liquidación

- [ ] 3.1 RED: crear `IntegracionLiquidacionesBase` (extiende `IntegracionPresupuestosBase`; reloj fijo en 2026-11-24 de Buenos Aires, ayuda para insertar turnos por SQL con estado e inicio, `TRUNCATE` de las tablas de V14) y `GenerarLiquidacionIntegrationTest` con un test por escenario de "Generar la liquidación de una obra social para un mes cerrado"; verificar que fallan con `404` de ruta inexistente
- [ ] 3.2 GREEN: puertos `TurnosLiquidables` y `Liquidaciones`, adaptadores `TurnosLiquidablesJdbc` (consulta de design D4 paso 2) y `LiquidacionesJdbc`, `OperacionesLiquidaciones.generar`, `ServicioLiquidaciones` con el reintento de design D4, `LiquidacionesController` (`POST` con `201` + `Location`, `@PreAuthorize("hasRole('ADMIN')")` en la clase) y la traducción de `liquidacion_periodo_unico`; verificar con `./gradlew integrationTest --tests '*GenerarLiquidacionIntegrationTest'`
- [ ] 3.3 RED → GREEN: `TurnosLiquidablesIntegrationTest` con un test por escenario de "Turnos que entran en la liquidación" y "Un turno se liquida una sola vez y los atrasados entran después" (incluido el borde 2026-10-31 23:30 / 2026-11-01 00:00); verificar con `./gradlew integrationTest --tests '*TurnosLiquidablesIntegrationTest'` y con `git diff master --stat -- backend/src/main/java/ar/turnos/odontologia/agenda` vacío
- [ ] 3.4 RED → GREEN: `GeneracionSimultaneaIntegrationTest` con el escenario "Dos generaciones a la vez" (dos hilos con `CountDownLatch`, como `CarreraAfiliacionesIntegrationTest`) y un caso de dos obras sociales que se disputan un turno tras corregir una afiliación (un solo `turno_id` en `liquidacion_linea`); verificar que pasa 5 veces seguidas

## 4. Montos, cargo de caja y arancel

- [ ] 4.1 RED → GREEN: `MontosLiquidacionIntegrationTest` con un test por escenario de "Monto solicitado de cada línea" (Obra Social Ejemplo 27500.00, Prepaga Prueba 56000.00, redondeo 12500.08, grupos por plan) y que la suma de las líneas leída de la base coincide con `total_solicitado`; verificar con `./gradlew integrationTest --tests '*MontosLiquidacionIntegrationTest'`
- [ ] 4.2 RED → GREEN: puerto `CargosDeTurno`, `SinCargosDeCaja` (`@ConditionalOnMissingBean`) y `CargoDeCajaIntegrationTest` con los escenarios de "Precio del cargo de caja del turno" usando un `@TestConfiguration` que devuelve el cargo ficticio; verificar con `./gradlew integrationTest --tests '*CargoDeCajaIntegrationTest'` y que sin la configuración de prueba todas las líneas salen con `origen_precio = 'ARANCEL'`
- [ ] 4.3 RED → GREEN: `TurnosSinArancelIntegrationTest` con los escenarios de "Turnos sin arancel" (`turnosSinArancel` en la respuesta); verificar con `./gradlew integrationTest --tests '*TurnosSinArancelIntegrationTest'`

## 5. Borrador: código, regenerar y quitar

- [ ] 5.1 RED → GREEN: `CodigoLineaIntegrationTest` con los escenarios de "Código del nomenclador de cada línea" (código ficticio `N-0102` insertado por SQL); implementar `PUT .../lineas/{lineaId}/codigo` y `codigosDisponibles` en el detalle (design D6); verificar con `./gradlew integrationTest --tests '*CodigoLineaIntegrationTest'`
- [ ] 5.2 RED → GREEN: `RegenerarLiquidacionIntegrationTest` con los escenarios de "Regenerar una liquidación en borrador"; implementar `POST /{id}/regenerar` con la sincronización de design D4 paso 4 y el `FOR UPDATE` de la cabecera; verificar con `./gradlew integrationTest --tests '*RegenerarLiquidacionIntegrationTest'`
- [ ] 5.3 RED → GREEN: `QuitarLineaIntegrationTest` con los escenarios de "Quitar líneas de una liquidación en borrador" (incluido el de `2026-11` con el reloj en 2026-12-02); implementar `DELETE .../lineas/{lineaId}` con `liquidacion_turno_quitado` (design D7); verificar con `./gradlew integrationTest --tests '*QuitarLineaIntegrationTest'`

## 6. Presentar, consultar y exportar

- [ ] 6.1 RED → GREEN: `PresentarLiquidacionIntegrationTest` con los escenarios de "Presentar la liquidación" y "Una liquidación presentada no cambia y ninguna se borra" (cambio de arancel y de nombre del plan después de presentar; `DELETE` → `405`); implementar `POST /{id}/presentar`; verificar con `./gradlew integrationTest --tests '*PresentarLiquidacionIntegrationTest'`
- [ ] 6.2 RED → GREEN: `ConsultaLiquidacionesIntegrationTest` con los escenarios de "Consulta de las liquidaciones"; implementar `GET` de lista (filtros, más recientes primero, sin datos de pacientes en el JSON) y de detalle (grupos por plan con subtotales); verificar que el JSON de la lista no tiene las claves de paciente
- [ ] 6.3 RED → GREEN: tests unitarios del escritor CSV (separador, comillas, `\r\n`, BOM, prefijo `'` ante `=`, `+`, `-`, `@`) y `ExportarCsvIntegrationTest` con los escenarios de "Exportar la liquidación en CSV" (encabezados HTTP y contenido exacto de la liquidación de 27500.00); implementar `GET /{id}/csv` según design D10; verificar con `./gradlew test integrationTest --tests '*Csv*'`

## 7. Conciliación y cierre

- [ ] 7.1 RED → GREEN: `ConciliarLineaIntegrationTest` con un test por escenario de "Registrar lo cobrado de cada línea", "Monto cobrado y motivo válidos" y "Solo se concilia una liquidación presentada"; implementar `PUT .../lineas/{lineaId}/conciliacion` (monto como `BigDecimal`, actor de la sesión) y la traducción de `liquidacion_linea_cobrado` y `liquidacion_linea_motivo`; verificar con `./gradlew integrationTest --tests '*ConciliarLineaIntegrationTest'`
- [ ] 7.2 RED → GREEN: `TotalesYCierreIntegrationTest` con los escenarios de "Conciliación parcial y totales", "Cerrar la liquidación conciliada" y "Lo no cobrado no se le cobra al paciente" (sin filas nuevas en `notificacion`); implementar `POST /{id}/cerrar`; verificar con `./gradlew integrationTest --tests '*TotalesYCierreIntegrationTest'` y que `total_cobrado` coincide con la suma de las líneas
- [ ] 7.3 RED → GREEN: `CarreraConciliacionIntegrationTest` con el escenario "Cerrar mientras se concilia la última línea" (dos hilos); verificar que pasa 5 veces seguidas y que nunca queda `CERRADA` con una línea pendiente

## 8. Permisos y privacidad

- [ ] 8.1 RED → GREEN: `PermisosLiquidacionesIntegrationTest` con los escenarios de "Solo el administrador maneja las liquidaciones" y "Solo el administrador concilia" sobre los 10 endpoints (`recepcion`, `ana.ejemplo`, `paciente.demo` → `403` sin cambios en la base; sin sesión → `401`; sin token CSRF → `403`); verificar con `./gradlew integrationTest --tests '*PermisosLiquidaciones*'` y que `SecurityConfig` no cambió (`git diff master -- backend/src/main/java/ar/turnos/odontologia/shared/security` vacío)
- [ ] 8.2 RED → GREEN: `PrivacidadLiquidacionesIntegrationTest` con el escenario "Generar y fallar sin datos en los logs" (`OutputCaptureExtension`, también en conciliar y descargar el CSV); verificar con `./gradlew integrationTest --tests '*PrivacidadLiquidaciones*'` y con `git grep -n "log\." -- backend/src/main/java/ar/turnos/odontologia/coberturas` sin datos de pacientes ni importes

## 9. Verificación final, checkpoint y PR

- [ ] 9.1 Correr `./gradlew clean build integrationTest` con PostgreSQL 15+ y verificar que todo pase sin fallas ni tests omitidos, incluidos los de otros módulos que hacen `TRUNCATE ... CASCADE` de `turno` y `paciente`
- [ ] 9.2 Verificar que cada escenario de las dos specs tenga un test con su `@DisplayName` (listar los `#### Scenario:` y buscarlos con `git grep`), y que `openspec validate liquidaciones-obras-sociales --strict` siga pasando
- [ ] 9.3 Checkpoint con la líder (governance ALTO): mostrarle el resultado de 9.1 y 9.2 y una liquidación de ejemplo generada en `dev` (Obra Social Ejemplo, `2026-10`, 27500.00) con su CSV; registrar su visto bueno en la descripción del PR
- [ ] 9.4 Abrir el PR de `change/C-20-liquidaciones-apply` a `master` con los avisos del grupo 0, la tabla de endpoints y la nota de que las pantallas van en una ficha de frontend aparte y el adaptador del cargo de C-22 en un change de seguimiento; verificar que el PR quede asignado a la líder para revisión. `CHANGES.md` se marca `[x]` solo después del merge

## Workflow follow-up

- Después del merge del apply: marcar C-20 en `CHANGES.md`, sincronizar las specs y archivar el change (`openspec-archive-change`).
- Cuando C-22 (`caja-y-cobros`) esté en `master`: change de seguimiento con `CargosDeTurnoJdbc` y su test de integración con una fila real de `cargo` (design D5).
- Ficha de frontend de liquidaciones (con su gate de mockups).
