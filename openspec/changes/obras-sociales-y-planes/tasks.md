# Tasks

> Rama de implementación `change/C-18-obras-sociales-y-planes` (el propose va en `change/C-18-propose`). Governance **MEDIO**: checkpoints con la líder en los grupos 0 y 7. TDD estricto en cada grupo: primero el test en **RED** (falla por la razón esperada), después el código mínimo en **GREEN** y el refactor con los tests en verde. Cada escenario de `specs/catalogo-coberturas/spec.md` y `specs/afiliaciones-pacientes/spec.md` tiene su test con `@DisplayName` igual al nombre del escenario (regla dura 7). Los tests de integración llevan `@Tag("integration")` y usan PostgreSQL real (nunca H2). Solo datos ficticios del seed de la KB (regla dura 1).

## 0. Aprobación y avisos

- [ ] 0.1 Pedir a la líder la aprobación del propose (proposal, specs, design y tasks) y la respuesta a las 6 decisiones "(a confirmar)" de `design.md` §Open Questions; si alguna respuesta difiere de la recomendación, actualizar el change con `openspec-update-change` antes de seguir, y verificar con `openspec validate obras-sociales-y-planes --strict`
- [ ] 0.2 Avisar a Emanuel (dueño de `configuracion/`) que `nomenclador.prestacion_id` referencia `prestacion` por FK sin tocar su módulo, y a Juan Cruz (dueño de V1 y `shared/persistence`) que `afiliacion_paciente` referencia `paciente` y que el seed dev inserta dos pacientes ficticios; verificar que los avisos queden en la descripción del PR
- [ ] 0.3 Crear la rama `change/C-18-obras-sociales-y-planes` desde `master` actualizado y verificar con `git status` que parte limpia

## 1. Migración V12

- [ ] 1.1 RED: escribir `V12MigrationIntegrationTest` (`@Tag("integration")`) que verifica las 4 tablas y que la base rechaza: nombre de obra social repetido con otras mayúsculas, plan repetido en la misma obra social, código de nomenclador repetido, tipo inválido, `vigente_hasta < vigente_desde`, número con caracteres no permitidos, `obra_social_id` distinto del de su plan (FK compuesta), dos vigencias superpuestas del mismo paciente (escenario "La base rechaza una superposición escrita directo") y el mismo número en la misma obra social para otro paciente; y acepta el mismo número del mismo paciente en otro plan de la obra social, y vigencias contiguas. Verificar que falla porque las tablas no existen
- [ ] 1.2 GREEN: crear `backend/src/main/resources/db/migration/V12__coberturas.sql` según design D2 y D3 (con comentarios de la FK compuesta y de la `EXCLUDE` con `<>`), y verificar con `./gradlew integrationTest --tests '*V12MigrationIntegrationTest'`

## 2. Dominio

- [ ] 2.1 RED → GREEN: `CodigoCobertura`, `CoberturaException` y `TipoCobertura` en `coberturas/domain`, sin imports de Spring ni JPA; verificar con `./gradlew test`
- [ ] 2.2 RED → GREEN: `NumeroAfiliado` con tests unitarios de los escenarios de "Número de afiliado válido" (espacios en los extremos, caracteres no permitidos, vacío, 31 caracteres) y del límite de 30; verificar con `./gradlew test`
- [ ] 2.3 RED → GREEN: `Vigencia` (`contiene`, `seSuperponeCon`, días inclusivos, `hasta` abierta) con tests unitarios de "Vigencia válida" y de "Una sola afiliación vigente por fecha" a nivel dominio (abierta, borde de un día, contiguas); verificar con `./gradlew test`
- [ ] 2.4 Verificar con `git grep -n "import org.springframework\|import jakarta.persistence" -- backend/src/main/java/ar/turnos/odontologia/coberturas/domain` que el dominio no tenga imports de Spring ni JPA

## 3. Catálogo de coberturas

- [ ] 3.1 RED: crear `IntegracionCoberturasBase` (perfil `test`, limpia las tablas de coberturas, pacientes y prestaciones, crea los usuarios `admin`, `recepcion`, `ana.ejemplo`, `paciente.uno` y `paciente.dos`, reloj ajustable en 2026-11-24 de Buenos Aires) y `CatalogoCoberturasIntegrationTest` con un test por escenario de "Alta y modificación de obras sociales con nombre único", "Planes con nombre único dentro de su obra social", "Desactivar en lugar de borrar" y "Consulta del catálogo de coberturas"; verificar que fallan con `404` de ruta inexistente
- [ ] 3.2 GREEN: puertos y servicios de obras sociales y planes, entidades JPA y repositorios, `ObrasSocialesController` y `PlanesController` (design D5, plan buscado junto con su obra social) y `CoberturasExceptionHandler` con la traducción de `obra_social_nombre_unico` y `plan_nombre_unico` (design D6); verificar con `./gradlew integrationTest --tests '*CatalogoCoberturasIntegrationTest'`
- [ ] 3.3 RED → GREEN: `NomencladorIntegrationTest` con un test por escenario de "Nomenclador con código único" y el de la lista por código; implementar puerto `PrestacionesExistentes`, servicio, entidad y `NomencladorController` con la traducción de `nomenclador_codigo_unico`; verificar con `./gradlew integrationTest --tests '*NomencladorIntegrationTest'`
- [ ] 3.4 RED → GREEN: tests de "Validación de los datos del catálogo" y "Permisos sobre el catálogo de coberturas" (incluido que el `422` trae el campo y que el `403` no cambia nada); agregar Bean Validation a los DTOs y `@PreAuthorize("hasRole('ADMIN')")` a cada escritura; verificar con `./gradlew integrationTest --tests '*Coberturas*'`

## 4. Afiliaciones

- [ ] 4.1 RED: `AfiliacionesIntegrationTest` con un test por escenario de "Afiliar un paciente a un plan", "Número de afiliado válido", "Vigencia válida", "Una sola afiliación vigente por fecha" (sin el concurrente), "Número de afiliado único por obra social" y "No se afilia a un plan o una obra social inactivos"; verificar que fallan
- [ ] 4.2 GREEN: puertos `Afiliaciones` y `PacientesExistentes`, `ServicioAfiliaciones.afiliar` con los pasos de design D4 (`saveAndFlush`), entidad y adaptador, `AfiliacionesController` (`POST`) y la traducción de `afiliacion_sin_superposicion`, `afiliacion_numero_unico_por_obra_social`, `afiliacion_vigencia_valida` y `afiliacion_numero_valido`; verificar con `./gradlew integrationTest --tests '*AfiliacionesIntegrationTest'`
- [ ] 4.3 RED → GREEN: test del escenario "Dos pedidos superpuestos a la vez" (dos hilos con un `CountDownLatch`, como los tests de concurrencia de la agenda) que demuestra que la `EXCLUDE` frena la carrera y responde `409 VIGENCIA_SUPERPUESTA`; verificar que pasa 5 veces seguidas
- [ ] 4.4 RED → GREEN: tests de "Cerrar una afiliación y cambiar de cobertura" y "Corregir una afiliación" (incluida la ruta de otro paciente → `404` y que no hay `DELETE`); implementar `cerrar` y `corregir` (excluyendo la propia afiliación al verificar superposición); verificar con `./gradlew integrationTest --tests '*AfiliacionesIntegrationTest'`
- [ ] 4.5 RED → GREEN: tests de "Historial de afiliaciones del paciente" y "Permisos sobre las afiliaciones"; implementar el `GET` del historial con un adaptador JDBC de lectura (join a `plan` y `obra_social`, orden por `vigente_desde DESC`) y los `@PreAuthorize` de escritura para `ADMIN` y `RECEPCIONISTA`; verificar con `./gradlew integrationTest --tests '*Afiliaciones*'`

## 5. Cobertura vigente y cobertura propia

- [ ] 5.1 RED → GREEN: `CoberturaIntegrationTest` con un test por escenario de "Cobertura vigente o particular", incluido el borde de medianoche con el reloj ajustable; implementar `ConsultaCobertura` (design D7) y `GET /api/v1/pacientes/{id}/cobertura?fecha=`; verificar con `./gradlew integrationTest --tests '*CoberturaIntegrationTest'`
- [ ] 5.2 RED → GREEN: tests de "El paciente consulta su propia cobertura"; implementar `CoberturaPacienteController` en `/api/v1/paciente/cobertura` con `@AuthenticationPrincipal UsuarioAutenticado`, sin `@PathVariable` ni `@RequestParam` de ids; verificar que el pedido con el id de otro paciente no cambia la respuesta y que `SecurityConfig` no necesitó cambios (`git diff master -- backend/src/main/java/ar/turnos/odontologia/shared/security` vacío)

## 6. Privacidad y seed de desarrollo

- [ ] 6.1 RED → GREEN: tests de "Privacidad de la cobertura": el recordatorio de un paciente afiliado no contiene la obra social, el plan ni el número; el turno de un paciente particular se crea igual; el `409 AFILIADO_DUPLICADO` y el `422` del número no repiten el valor; verificar con `./gradlew integrationTest --tests '*Privacidad*'` y con `git grep -n "log\." -- backend/src/main/java/ar/turnos/odontologia/coberturas` que no haya logs con datos de cobertura
- [ ] 6.2 Crear `backend/src/main/resources/db/seed/V12_1__seed_coberturas_dev.sql` según design D8 (solo datos ficticios del seed de la KB) y verificar arrancando el backend con el perfil `dev` sobre una base nueva y sobre una con V22 ya aplicada: Flyway aplica V12 y V12.1 sin errores, `GET /api/v1/pacientes/{id}/cobertura` del paciente `99000001` devuelve "Plan Base" con `OSE-000001` y el del `99000002` devuelve particular

## 7. Verificación final y PR

- [ ] 7.1 Correr `./gradlew clean build integrationTest` con PostgreSQL 15+ y verificar que todo pase sin fallas ni tests omitidos, incluidos los de otros módulos que hacen `TRUNCATE ... CASCADE`
- [ ] 7.2 Verificar que cada escenario de las dos specs tenga un test con su `@DisplayName` (listar los `#### Scenario:` y buscarlos con `git grep`), y que `openspec validate obras-sociales-y-planes --strict` siga pasando
- [ ] 7.3 Abrir el PR de `change/C-18-obras-sociales-y-planes` a `master` con los avisos del grupo 0, el resumen de endpoints y la nota de que las pantallas van en una ficha de frontend aparte; verificar que el PR quede asignado a la líder para revisión

## Workflow follow-up

- Después de que la líder mergee el PR: marcar `[x]` C-18 en `CHANGES.md` (con la fecha y el número de PR) en un PR propio, y archivar el change con `openspec-archive-change`.
- Abrir la ficha de frontend de coberturas (pestaña de Configuración, cobertura en el registro del paciente y "Mi cuenta"), con mockups antes del apply (`system-and-mockups`), como C-46 para C-25.
- C-19 (`presupuestos-y-cobertura`, V13) y C-21 parten de `ConsultaCobertura` y del vínculo `nomenclador → prestacion`.
