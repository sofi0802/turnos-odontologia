# Tasks

> Rama de implementación `change/C-19-presupuestos-y-cobertura` (el propose va en `change/C-19-propose`). Governance **MEDIO**: checkpoints con la líder en los grupos 0 y 8. TDD estricto en cada grupo: primero el test en **RED** (falla por la razón esperada), después el código mínimo en **GREEN** y el refactor con los tests en verde. Cada escenario de `specs/aranceles-y-cobertura-planes/spec.md` y `specs/presupuestos/spec.md` tiene su test con `@DisplayName` igual al nombre del escenario (regla dura 7). Los tests de integración llevan `@Tag("integration")` y usan PostgreSQL real (nunca H2). Solo datos ficticios del seed de la KB (regla dura 1). No se toca `clinica/`, `configuracion/` ni `shared/`.

## 0. Aprobación y avisos

- [ ] 0.1 Pedir a la líder la aprobación del propose (proposal, specs, design y tasks) y la respuesta a las 9 decisiones "(a confirmar)" de `design.md` §Open Questions; registrar las respuestas en una sección "Resolución de decisiones" de `design.md`; si alguna difiere de la recomendación, actualizar el change con `openspec-update-change` y verificar con `openspec validate presupuestos-y-cobertura --strict`
- [ ] 0.2 Avisar a Emanuel (dueño de `configuracion/` y `clinica/`) que `arancel` y `presupuesto_item` referencian `prestacion` por FK y que `coberturas/` lee `odontograma_registro` con un adaptador JDBC de solo lectura sin tocar su módulo; avisar a Enzo (C-22) que el precio particular vive en `arancel` y que un presupuesto aceptado no es un cobro; verificar que los avisos queden en la descripción del PR
- [ ] 0.3 Crear la rama `change/C-19-presupuestos-y-cobertura` desde `master` actualizado (con C-18 y este propose mergeados) y verificar con `git status` que parte limpia

## 1. Migración V13

- [ ] 1.1 RED: escribir `V13MigrationIntegrationTest` que verifica las 4 tablas, los triggers y que la base rechaza: arancel con precio 0 y repetido por prestación; cobertura con 101 y repetida por plan y código; presupuesto con `vence_el` distinto de `fecha + 30`, con totales que no suman, con la afiliación de otro paciente (FK compuesta) y con nombres de cobertura sin `afiliacion_id`; ítem con `importe` mal calculado, con cobertura 12500.07 para 50 % de 25000.15 (y acepta 12500.08), con porcentaje mayor que 0 sin código, con pieza 19, cara sin pieza, cara `P` en la 36 y cara `L` en la 16; `UPDATE` de un ítem, `DELETE` de un presupuesto, cambio de `total_precio` y de `ACEPTADO` a `RECHAZADO` (escenario "La base rechaza modificar un ítem emitido"); y que acepta `PENDIENTE` → `ACEPTADO`. Incluir un caso que inserta las mismas piezas y caras válidas e inválidas en `odontograma_registro` y en `presupuesto_item` y compara el resultado. Verificar que falla porque las tablas no existen
- [ ] 1.2 GREEN: crear `backend/src/main/resources/db/migration/V13__presupuestos.sql` según design D2 y D5 (con comentarios del redondeo, la FK compuesta y los triggers) y verificar con `./gradlew integrationTest --tests '*V13MigrationIntegrationTest'`

## 2. Dominio

- [ ] 2.1 RED → GREEN: ampliar `CodigoCobertura` con `ARANCEL_INEXISTENTE`, `PRESTACION_INACTIVA`, `PRESUPUESTO_VENCIDO`, `PRESUPUESTO_CERRADO` (`409`) y `CODIGO_NOMENCLADOR_REQUERIDO`, `CODIGO_NOMENCLADOR_INVALIDO` (`422`), con un test que fija el estado de cada código; verificar con `./gradlew test`
- [ ] 2.2 RED → GREEN: `Precio` y `Porcentaje` con tests unitarios de los escenarios de "Precio válido" y "Porcentaje válido" (cero, negativo, tres decimales, máximo, 101, decimal); verificar con `./gradlew test`
- [ ] 2.3 RED → GREEN: `PiezaCara` con tests unitarios de los escenarios de "Ítems válidos" sobre pieza y cara (pieza 19, `P` en la 36, cara sin pieza, válidas) y las mismas reglas FDI de V8; verificar con `./gradlew test`
- [ ] 2.4 RED → GREEN: `CalculoItem` con tests unitarios de "Cálculo de cobertura y copago de cada ítem" (100 %, 80 %, 50 %, 0 %, cantidad 2, redondeo 25000.15 → 12500.08 / 12500.07) con `BigDecimal` escala 2 y `RoundingMode.HALF_UP`, más una propiedad `cobertura + copago = importe` sobre precios y porcentajes variados; verificar con `./gradlew test`
- [ ] 2.5 RED → GREEN: agregado `Presupuesto` y `EstadoPresupuesto` con tests unitarios de totales, particular, vencimiento (día de `vence_el` aceptable, día siguiente vencido, aceptado o rechazado nunca vencido) y transiciones (`PRESUPUESTO_CERRADO`, `PRESUPUESTO_VENCIDO`, rechazar vencido); verificar con `./gradlew test` y con `git grep -n "import org.springframework\|import jakarta.persistence" -- backend/src/main/java/ar/turnos/odontologia/coberturas/domain` vacío

## 3. Aranceles y cobertura por plan

- [ ] 3.1 RED: crear `IntegracionPresupuestosBase` (extiende `IntegracionCoberturasBase`; agrega `bruno.prueba`, "Paciente Tres", las cuatro prestaciones y, según el escenario, aranceles, cobertura y nomenclador del seed de la KB) y `ArancelesIntegrationTest` con un test por escenario de "Precio particular de cada prestación", "Precio válido" y "Consulta de los aranceles"; verificar que fallan con `404` de ruta inexistente
- [ ] 3.2 GREEN: puerto `Aranceles`, `ArancelesJdbc` (upsert `ON CONFLICT`, `actualizado_en` del `Clock`), `ServicioAranceles` y `ArancelesController` (`GET` y `PUT /{prestacionId}`, design D6 y D7), con la traducción de `arancel_precio_positivo`; verificar con `./gradlew integrationTest --tests '*ArancelesIntegrationTest'`
- [ ] 3.3 RED → GREEN: `CoberturaPlanIntegrationTest` con un test por escenario de "Porcentaje de cobertura por plan y código", "Porcentaje válido" y "Consulta de la cobertura de un plan"; implementar puerto `CoberturasPlan`, `CoberturasPlanJdbc` (upsert), servicio y `CoberturaPlanController` (plan buscado junto con su obra social, porcentaje recibido como `BigDecimal` según design D3); verificar con `./gradlew integrationTest --tests '*CoberturaPlanIntegrationTest'`
- [ ] 3.4 RED → GREEN: tests de "Sin borrado de aranceles ni coberturas" y "Permisos sobre aranceles y coberturas" (el `403` no cambia nada); `@PreAuthorize` de lectura para el personal y de escritura solo `ADMIN`; verificar con `./gradlew integrationTest --tests '*Aranceles*' --tests '*CoberturaPlan*'`

## 4. Emisión y cálculo del presupuesto

- [ ] 4.1 RED: `EmisionPresupuestoIntegrationTest` con un test por escenario de "Emitir un presupuesto", "Cálculo de cobertura y copago de cada ítem" y "Paciente particular" (incluido el ejemplo de "Paciente Uno": 40000.00 / 27500.00 / 12500.00 y el de "Paciente Dos": 40000.00 / 0.00 / 40000.00); verificar que fallan
- [ ] 4.2 GREEN: puertos `Presupuestos`, `PrestacionesPresupuestables`, `ProfesionalesExistentes` y sus adaptadores; `PresupuestoEntity` y `PresupuestoItemEntity` (`@Immutable`, cascada de ítems); `ServicioPresupuestos.emitir` con los pasos de design D4 (hoy de Buenos Aires con el `Clock`, cobertura con `LecturaCoberturas.vigenteEn`, foto con nombres, `saveAndFlush`); `PresupuestosController` `POST` con `201` + `Location` y profesional de `UsuarioAutenticado.profesionalId()`; verificar con `./gradlew integrationTest --tests '*EmisionPresupuestoIntegrationTest'` y que la suma de los ítems leída de la base coincide con los totales
- [ ] 4.3 RED → GREEN: tests de "Código del nomenclador de cada ítem", "Prestaciones presupuestables" e "Ítems válidos" (incluido que un error no crea ningún presupuesto y que el `422` trae solo el campo `items[i].…`); verificar con `./gradlew integrationTest --tests '*EmisionPresupuestoIntegrationTest'`

## 5. Foto, estados y vencimiento

- [ ] 5.1 RED → GREEN: `FotoPresupuestoIntegrationTest` con un test por escenario de "El presupuesto es una foto del cálculo" (cambio de precio, de cobertura del plan y corrección de la afiliación a "Plan Integral"); implementar `GET /api/v1/pacientes/{id}/presupuestos/{presupuestoId}` con `LecturaPresupuestos` leyendo solo las columnas de foto; verificar con `./gradlew integrationTest --tests '*FotoPresupuestoIntegrationTest'`
- [ ] 5.2 RED → GREEN: `EstadosPresupuestoIntegrationTest` con un test por escenario de "Estados del presupuesto" (sin el concurrente) y "Vencimiento del presupuesto" con el reloj ajustable (2026-12-24 23:59 y 2026-12-25 00:00 de Buenos Aires); implementar `aceptar` y `rechazar` con el `UPDATE` condicional de design D5 y la relectura para `404` / `PRESUPUESTO_CERRADO` / `PRESUPUESTO_VENCIDO`; verificar con `./gradlew integrationTest --tests '*EstadosPresupuestoIntegrationTest'`
- [ ] 5.3 RED → GREEN: test del escenario "Aceptar y rechazar a la vez" (dos hilos con un `CountDownLatch`, como los tests de carrera de C-18) que comprueba un `200`, un `409 PRESUPUESTO_CERRADO` y el estado final del ganador; verificar que pasa 5 veces seguidas

## 6. Sugerencias y consultas por rol

- [ ] 6.1 RED → GREEN: `SugerenciasPresupuestoIntegrationTest` con un test por escenario de "Sugerencias desde el odontograma" (registros insertados por SQL en `odontograma_registro`); implementar `OdontogramaVigente`, `OdontogramaVigenteJdbc` (consulta de design D8) y `GET .../presupuestos/sugerencias`; verificar con `./gradlew integrationTest --tests '*SugerenciasPresupuestoIntegrationTest'` y con `git diff master --stat -- backend/src/main/java/ar/turnos/odontologia/clinica` vacío
- [ ] 6.2 RED → GREEN: `ConsultaPresupuestosIntegrationTest` con un test por escenario de "Consulta de presupuestos por el personal"; implementar la lista (más reciente primero) y los records de recepción sin `pieza` ni `cara` (design D7); verificar que el JSON de `recepcion` no tiene esas claves (no basta con que valgan `null`)
- [ ] 6.3 RED → GREEN: tests de "El paciente consulta sus presupuestos"; implementar `PresupuestosPacienteController` en `/api/v1/paciente/presupuestos` con `@AuthenticationPrincipal UsuarioAutenticado`, buscando por `id` y paciente de la sesión; verificar que `SecurityConfig` no cambió (`git diff master -- backend/src/main/java/ar/turnos/odontologia/shared/security` vacío)
- [ ] 6.4 RED → GREEN: tests de "Permisos sobre los presupuestos" (el `403` no crea ni cambia nada); `@PreAuthorize` por operación (`ODONTOLOGO` para emitir, aceptar, rechazar y sugerencias; personal para consultar); verificar con `./gradlew integrationTest --tests '*Presupuesto*'`

## 7. Privacidad y seed de desarrollo

- [ ] 7.1 RED → GREEN: `PrivacidadPresupuestosIntegrationTest` con un test por escenario de "Privacidad del presupuesto" (`OutputCaptureExtension` en una emisión exitosa y en una con `ARANCEL_INEXISTENTE`; recordatorio de un paciente con presupuesto); verificar con `./gradlew integrationTest --tests '*PrivacidadPresupuestos*'` y con `git grep -n "log\." -- backend/src/main/java/ar/turnos/odontologia/coberturas` sin logs de piezas ni importes
- [ ] 7.2 Crear `backend/src/main/resources/db/seed/V13_1__seed_presupuestos_dev.sql` según design D11 (solo datos ficticios del seed de la KB) y verificar arrancando el backend con el perfil `dev` sobre una base nueva y sobre una con V22 ya aplicada: Flyway aplica V13 y V13.1 sin errores, `GET /api/v1/aranceles` muestra los cuatro precios y un presupuesto de "Paciente Uno" con "Consulta o control" y "Limpieza (profilaxis)" da 40000.00 / 27500.00 / 12500.00

## 8. Verificación final y PR

- [ ] 8.1 Correr `./gradlew clean build integrationTest` con PostgreSQL 15+ y verificar que todo pase sin fallas ni tests omitidos, incluidos los de otros módulos que hacen `TRUNCATE ... CASCADE`
- [ ] 8.2 Verificar que cada escenario de las dos specs tenga un test con su `@DisplayName` (listar los `#### Scenario:` y buscarlos con `git grep`), y que `openspec validate presupuestos-y-cobertura --strict` siga pasando
- [ ] 8.3 Abrir el PR de `change/C-19-presupuestos-y-cobertura` a `master` con los avisos del grupo 0, la tabla de endpoints y la nota de que las pantallas van en una ficha de frontend aparte; verificar que el PR quede asignado a la líder para revisión

## Workflow follow-up

- Después de que la líder mergee el PR: marcar `[x]` C-19 en `CHANGES.md` (con la fecha y el número de PR) en un PR propio, y archivar el change con `openspec-archive-change`. No marcar `CHANGES.md` antes del merge.
- Abrir la ficha de frontend de presupuestos (aranceles y cobertura en Configuración, presupuesto en la ficha del paciente y en "Mi cuenta"), con mockups antes del apply.
- C-20 (`liquidaciones-obras-sociales`, V14) parte de `presupuesto_item` (código, porcentaje e importes de la foto); C-22 puede leer el precio particular de `arancel`.
