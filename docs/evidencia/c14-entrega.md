# C-14: verificación final de la entrega

Fecha: 2026-11-24. Rama `change/C-14-entrega-readme-y-evidencia`, desde `master` en `35fced3` (PR #90, archive de C-43).

## 1. Suite de tests

| Suite | Comando | Resultado |
|---|---|---|
| Backend, dominio y build | `./gradlew clean build` | 605 de 605, sin fallas ni omitidos |
| Backend, integración (PostgreSQL 16 real) | `./gradlew integrationTest` | 568 de 568, sin fallas ni omitidos. `BUILD SUCCESSFUL in 9m 24s` |
| Frontend, tests | `npm test` | 547 de 547 en 38 archivos |
| Frontend, build | `npm run build` (`tsc --noEmit` strict y Vite) | Sin errores |

Los comandos se corrieron desde una instalación limpia (`clean` en el backend y `npm ci` en el frontend). El detalle de cada test del backend queda en `backend/build/reports/tests/`.

## 2. Secretos en el repo

- `.env` está en `.gitignore` (junto con `.env.*`, salvo `.env.example`) y nunca se commiteó: `git log --all -- .env` no devuelve nada.
- Ningún archivo versionado es una clave, un certificado ni un archivo de credenciales (`.pem`, `.key`, `.p12`, `id_rsa`).
- No hay tokens con formato de GitHub, AWS ni de APIs de IA.
- Las coincidencias de `password` o `token` son nombres de campos y variables del código, o ejemplos de la documentación de terceros de las skills (`.agents/skills/`, `.claude/skills/`).
- Los únicos hashes bcrypt versionados están en dos tests de integración, para cuentas ficticias de la base de los tests. El seed de desarrollo no crea usuarios: la cuenta ADMIN inicial sale de `APP_ADMIN_USERNAME` y `APP_ADMIN_PASSWORD`.

## 3. Variables de entorno

El backend usa 18 variables (`application.yml`) y el frontend usa 1 (`VITE_API_BASE_URL`). El README las lista con su uso y su valor por defecto. Por la regla dura 4, el agente no leyó `.env.example`; la líder confirma que trae todos los nombres de la tabla del README, con valores de ejemplo.

## 4. Evidencias de la entrega

| Evidencia | Dónde |
|---|---|
| Informe de Discovery en PDF | [`docs/discovery/informe-discovery.pdf`](../discovery/informe-discovery.pdf) |
| Estado del orquestador | [`.active-orchestrator-state.json`](../../.active-orchestrator-state.json): versión 5, `step: done`, con las fases foundation, discovery, kb, visual_design, roadmap, skills, registry y agents |
| Ciclo OPSX completo de C-01 | [`etapa-6-c01-tests.txt`](etapa-6-c01-tests.txt) y `openspec/changes/archive/2026-11-21-crear-turno-sin-solapamientos/` |
| Memoria del agente (Engram) y guion del video | [`etapa-7-engram.md`](etapa-7-engram.md) y la consulta de abajo |

### Consulta a Engram para el video (2026-11-24)

- `mem_stats`: 43 observaciones del proyecto `turnos-odontologia` (el 2026-11-21 eran 13).
- `mem_search "registro de asistencia ausencia tolerancia"` devuelve la memoria #42 (apply de C-43: llegada en `turno.llegada_en`, ausencia desde los 15 minutos y paso automático a ATENDIDO) y la #40 (propuesta de C-43).
- `mem_search "margen entre turnos solapamiento sillón profesional"` devuelve la #6, la decisión del Discovery sobre el stack, el alcance y las reglas de la agenda.

Muestra que una sesión nueva recupera las decisiones de un change sin volver a leer el código.

## 5. Pendientes que no bloquean la entrega

- **Archives de OpenSpec**: C-33, C-37, C-38, C-39, C-40, C-41, C-42 e `ingreso-unico-mi-cuenta` están mergeados pero sin archivar. C-39 tiene abiertas las tareas A.5, 7.1 y 7.2 de su archive.
- **Design system**: `design-system/README.md` está en la Revision 3 con `Approval: pending` (las revisiones 2 y 3 no cambian los valores de los tokens), mientras `.active-orchestrator-state.json` registra la Revision 1 aprobada. Falta que la líder renueve la aprobación y que se alineen los dos registros.
