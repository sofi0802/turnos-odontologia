# Tasks

> Governance **CRÍTICO** (seguridad): no empezar el grupo 1 hasta que la líder apruebe `proposal.md`, el spec y `design.md`, incluidas las decisiones *(a confirmar)* D1 y D2. Strict TDD: primero el test en rojo (RED), después el mínimo código para pasarlo (GREEN) y después el refactor con los tests en verde. Cada test de escenario lleva `@DisplayName` igual al nombre del escenario del spec. Solo datos ficticios. Los tests de integración usan PostgreSQL real y llevan `@Tag("integration")`.

## 0. Aprobación previa

- [x] 0.1 Registrar en `design.md` o en el PR la respuesta de la líder a D1, D2 y la Open Question (fix de C-05 o ficha propia en `CHANGES.md`). Si cambia alguna, actualizar spec y tareas antes de seguir. Verificar con `openspec validate errores-rutas-inexistentes --strict`

## 1. Línea de base y configuración

- [x] 1.1 Safety net: correr `./gradlew test integrationTest` en `backend/` sin cambios y anotar el baseline. Si algo falla, frenar y reportarlo como falla previa
- [x] 1.2 Fijar en `application.yml` `server.error.include-stacktrace: never`, `include-message: never`, `include-exception: false` e `include-binding-errors: never`. Verificar que el contexto arranca con `TurnosOdontologiaApplicationTest`

## 2. Ruta inexistente (spec `seguridad-autorizacion`)

- [x] 2.1 RED: en `AutorizacionIntegrationTest`, escribir "Ruta inexistente con sesión del personal" y "Ruta inexistente con sesión del administrador". Verificar que fallan con `403`
- [x] 2.2 GREEN: `ProblemDetailGlobalHandler` en `shared/` (design D1) con `NoResourceFoundException` → `404 RECURSO_INEXISTENTE`, y la regla `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` en `SecurityConfig` (design D2). Verificar que los 2 escenarios pasan
- [x] 2.3 RED → GREEN: "Ruta inexistente sin sesión" (`401`), "Ruta inexistente del personal pedida por un paciente" (`403`) y "Ruta interna de errores pedida directamente" (`403`). Verificar que pasan

## 3. Error no controlado

- [x] 3.1 RED → GREEN: controller solo de test en `src/test` que lanza `IllegalStateException("detalle-interno-ficticio")`, y el escenario "Error no controlado en una operación del personal" (`500 ERROR_INTERNO`, sin el mensaje ni la traza en el cuerpo). Verificar que pasa
- [x] 3.2 Verificar que el manejador global no captura `AccessDeniedException` ni `AuthenticationException` (design, Risks): siguen en verde "Operación de ADMIN pedida por recepción" y el resto de `AutorizacionIntegrationTest`

## 4. Verificación integral

- [x] 4.1 Correr `./gradlew build integrationTest` y verificar que todo está en verde, incluidos los 43 escenarios de C-05 y los de los demás módulos
- [x] 4.2 Verificar con `grep` en `backend/src/main` que no aparecen `csrf.disable`, `ignoringRequestMatchers`, `permitAll()` fuera de `/api/v1/public/**` y del despacho `ERROR`, ni `getQueryString` en los logs del manejador global
- [x] 4.3 Probar a mano con `curl` y sesión `admin`: `GET /api/v1/no-existe` → `404`, `GET /error` → `403`, y sin sesión `GET /api/v1/no-existe` → `401`. Guardar la salida, sin contraseñas, en `docs/evidencia/fix-c05-error-404.txt`
- [x] 4.4 Correr `openspec validate errores-rutas-inexistentes --strict` y verificar que es válido

## Workflow follow-up

- Abrir el PR de `fix/C-05-error-404` a `master`. Sofia revisa y mergea.
- Archivar con `openspec archive errores-rutas-inexistentes` y verificar el spec actualizado en `openspec/specs/seguridad-autorizacion/`.
