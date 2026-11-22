# CHANGES: secuencia de implementación

> Índice canónico de todos los changes del proyecto **turnos-odontologia**.
> Cada change es atómico: un agente puede implementarlo en una sesión (~4-6 horas).
> **Leer este archivo antes de empezar un item.**

---

## Cómo usar este documento

1. Identificar el item a implementar y verificar que sus dependencias figuran completas en este roadmap.
2. Leer los docs de la knowledge-base indicados en "Leer antes".
3. Acordar el alcance y los criterios de aceptación del item. En este proyecto, eso es el `proposal.md` + `specs/` del change OpenSpec, revisado por la líder.
4. Implementar en la rama `change/C-NN-<nombre>`, verificar los criterios con tests y abrir un PR a `master`. La líder revisa y mergea.
5. Marcar el checkbox `[x]` en este archivo al completar la verificación técnica.

Conservá los IDs de las fichas al editar o reordenar. El seguimiento es **local** (`.active-stack/roadmap.json` → `mode: local`): el responsable y el estado viven en este archivo y en los PR de GitHub.

### Reglas del equipo para trabajar en paralelo

- **Un dueño por módulo** (ver la tabla de abajo). Para tocar archivos de un módulo ajeno, se avisa en el PR y aprueba su dueño.
- **Migraciones Flyway con número reservado por change**, para que dos ramas no creen el mismo `V{n}`.
- **Nunca commitear directo a `master`**: siempre por PR.
- **Solo datos ficticios** en código, tests, seeds y capturas.

| Módulo o carpeta | Dueño | Migraciones reservadas |
|---|---|---|
| `backend/` scaffolding, `agenda/domain`, `agenda/application` | Sofia | — |
| `backend/.../shared/persistence`, `docker-compose.yml`, `db/migration/V1` | Juan Cruz | V1 |
| `backend/.../configuracion/` | Emanuel | V2 |
| `backend/.../shared/security/`, `usuarios/` | Juan Sebastián | V3 |
| `backend/.../agenda/infrastructure`, `agenda/api` | Sofia | V4 |
| `backend/.../agenda/` reglas de horario y ausencias | Emanuel | V5 |
| `backend/.../agenda/` sobreturno y extensión | Juan Cruz | V6 |
| `backend/.../reserva/` | Juan Sebastián | — |
| `backend/.../notificaciones/` | Juan Cruz | V7 |
| `backend/.../clinica/` | Emanuel | V8 |
| `frontend/` | Enzo | — |
| **Post-MVP** | | |
| `backend/.../agenda/` lista de espera, `configuracion/` política de cancelación | Sofia (C-16 avisa a Emanuel) | V9, V10 |
| `backend/.../notificaciones/` consentimiento | Juan Cruz | V11 |
| `backend/.../coberturas/` (obras sociales, presupuestos, liquidaciones) | Juan Sebastián | V12, V13, V14 |
| `backend/.../caja/`, `pagos/`, `facturacion/`, `reportes/` | Enzo | V15 |
| `backend/.../clinica/` periodontograma, imágenes, consentimientos, FHIR | Emanuel | V16, V17, V18 |
| `backend/.../sucursales/` (transversal) | Sofia | V19 |
| `backend/.../integraciones/` Google Calendar, asistente IA | Juan Cruz | — |

---

## Árbol de dependencias

```
C-01 crear-turno-sin-solapamientos  [Sofia · OPSX]      ← tarea 1 = scaffolding backend (merge temprano)
  │
  ├── C-03 persistencia-base-postgres   [Juan Cruz]      ← solo necesita el scaffolding de C-01
  │     ├── C-04 configuracion-consultorio [Emanuel]
  │     │     └── C-07 horarios-ausencias-bloqueos [Emanuel]   ← + C-01
  │     ├── C-05 auth-sesion-roles       [Juan Sebastián]
  │     │     └── C-13 ficha-clinica-odontograma [Emanuel]     ← + C-06
  │     └── C-06 api-turnos              [Sofia]               ← + C-01 + C-05
  │           ├── C-08 sobreturno-y-extension [Juan Cruz]
  │           ├── C-11 notificaciones-recordatorios [Juan Cruz]
  │           ├── C-09 frontend-agenda-personal [Enzo]         ← + C-02
  │           └── C-10 reserva-online-paciente [Juan Sebastián] ← + C-07
  │                 └── C-12 frontend-reserva-paciente [Enzo]  ← + C-02
  │
C-02 frontend-base-ts  [Enzo]                            ← independiente: carpeta frontend/
  │
C-14 entrega-readme-y-evidencia  [Sofia]                 ← al final: README reproducible + evidencias

═══════════════ POST-MVP (arranca con C-01 a C-14 completos) ═══════════════

v1.x
  C-06 + C-11 ── C-15 lista-de-espera-automatica   [Sofia]
  C-04 + C-10 ── C-16 politica-cancelacion-configurable [Sofia]
  C-11 ───────── C-17 consentimiento-comunicaciones [Juan Cruz]

Obras sociales, cobros y facturación
  C-04 + C-05 ── C-18 obras-sociales-y-planes      [Juan Sebastián]
                   ├── C-19 presupuestos-y-cobertura [Juan Sebastián] ← + C-13
                   │     └── C-20 liquidaciones-obras-sociales [Juan Sebastián]
                   └── C-21 validacion-online-obras-sociales [Juan Sebastián]
  C-06 ───────── C-22 caja-y-cobros                [Enzo]
                   ├── C-23 senas-mercado-pago     [Enzo]   ← + C-10
                   └── C-24 facturacion-electronica-arca [Enzo]

Clínica avanzada (todas desde C-13)
  C-13 ──┬── C-25 periodontograma                  [Emanuel]
         ├── C-26 imagenes-y-radiografias          [Emanuel]
         ├── C-27 consentimientos-firma-electronica [Emanuel]
         └── C-28 exportacion-hl7-fhir             [Emanuel]

Escala, integraciones e inteligencia
  C-04 + C-06 + C-07 ── C-29 multisucursal         [Sofia]
  C-06 ──────────────── C-30 sincronizacion-google-calendar [Juan Cruz]
  C-10 + C-11 ───────── C-31 asistente-ia-whatsapp [Juan Cruz]
  C-06 + C-13 ───────── C-32 reportes-y-metricas   [Enzo]
```

### Paralelismo por fase

> Cada "gate" es un punto de sincronización. Los changes dentro de un grupo pueden ejecutarse en paralelo.

```
GATE 0: ninguna                                              ← FORK
  → C-01 crear-turno-sin-solapamientos       [Agente A · Sofia]
  → C-02 frontend-base-ts                    [Agente C · Enzo]
  (Emanuel, Juan Sebastián: verificación de fuentes; Juan Cruz: preparar Docker/PostgreSQL y leer KB 04/08)

GATE 1: C-01 · tarea 1 (scaffolding backend mergeado a master) ✓
  → C-03 persistencia-base-postgres          [Agente B · Juan Cruz]

GATE 2: C-03 ✓                                               ← FORK
  → C-04 configuracion-consultorio           [Agente B · Emanuel]
  → C-05 auth-sesion-roles                   [Agente B · Juan Sebastián]
  → C-06 api-turnos                          [Agente A · Sofia — si C-01 ✓ y C-05 ✓]

GATE 3: C-06 ✓ (y C-04 ✓ para C-07)                          ← FORK
  → C-07 horarios-ausencias-bloqueos         [Agente B · Emanuel — si C-04 ✓]
  → C-08 sobreturno-y-extension              [Agente A · Juan Cruz]
  → C-11 notificaciones-recordatorios        [Agente B · Juan Cruz — después de C-08]
  → C-09 frontend-agenda-personal            [Agente C · Enzo — si C-02 ✓]

GATE 4: C-07 ✓ + C-06 ✓ + C-05 ✓
  → C-10 reserva-online-paciente             [Agente B · Juan Sebastián]
  → C-13 ficha-clinica-odontograma           [Agente B · Emanuel]

GATE 5: C-10 ✓
  → C-12 frontend-reserva-paciente           [Agente C · Enzo]

GATE 6: changes de la entrega ✓
  → C-14 entrega-readme-y-evidencia          [Agente A · Sofia]

──────────── POST-MVP ────────────

GATE 7: MVP completo (C-01 a C-14) ✓ + KB ampliada            ← FORK (los 5 en paralelo)
  → C-15 lista-de-espera-automatica          [Agente A · Sofia]
  → C-17 consentimiento-comunicaciones       [Agente B · Juan Cruz]
  → C-18 obras-sociales-y-planes             [Agente B · Juan Sebastián]
  → C-22 caja-y-cobros                       [Agente C · Enzo]
  → C-25 periodontograma                     [Agente B · Emanuel]

GATE 8: primera tanda post-MVP ✓                              ← FORK
  → C-16 politica-cancelacion-configurable   [Agente A · Sofia]
  → C-30 sincronizacion-google-calendar      [Agente B · Juan Cruz]
  → C-19 presupuestos-y-cobertura            [Agente B · Juan Sebastián — si C-18 ✓]
  → C-21 validacion-online-obras-sociales    [Agente B · Juan Sebastián — después de C-19]
  → C-23 senas-mercado-pago                  [Agente C · Enzo — si C-22 ✓]
  → C-26 imagenes-y-radiografias             [Agente B · Emanuel]

GATE 9: C-19 ✓ + C-22 ✓                                       ← FORK
  → C-29 multisucursal                       [Agente A · Sofia]
  → C-31 asistente-ia-whatsapp               [Agente B · Juan Cruz]
  → C-20 liquidaciones-obras-sociales        [Agente B · Juan Sebastián]
  → C-24 facturacion-electronica-arca        [Agente C · Enzo]
  → C-27 consentimientos-firma-electronica   [Agente B · Emanuel]

GATE 10: resto del post-MVP
  → C-28 exportacion-hl7-fhir                [Agente B · Emanuel]
  → C-32 reportes-y-metricas                 [Agente C · Enzo]
```

*C-29 (multisucursal) cambia el modelo de forma transversal: conviene hacerlo cuando no haya otros changes abiertos sobre `agenda/`, o al principio del post-MVP si el negocio lo pide antes.*

### Camino crítico del MVP (7 changes — mínimo irreducible)

```
C-01 → C-03 → C-05 → C-06 → C-10 → C-12 → C-14
```

*C-07 (horarios) y C-04 también son prerrequisito de C-10; si se atrasan, pasan a ser el camino crítico (C-01 → C-03 → C-04 → C-07 → C-10 → C-12 → C-14).*

### MVP y post-MVP

| Bloque | Changes | Origen |
|---|---|---|
| **MVP v1.0** (fases 1 a 6) | C-01 a C-14 | Sección D.3 del informe, lista "imprescindible" |
| **Post-MVP v1.x** (fase 7) | C-15 a C-17 | Sección D.3, lista "diferenciador" |
| **Post-MVP: etapas posteriores** (fases 8 a 10) | C-18 a C-32 | Sección D.3, lista "etapas posteriores" |

> **C-01 es el change del ciclo OPSX** (explore → propose → apply → verify → archive) que se presenta en la Etapa 6 de la consigna.
> Los changes post-MVP **no se empiezan hasta que el MVP esté completo**. Antes de implementarlos, hay que **ampliar la KB** con sus reglas (hoy solo figuran en el backlog de `06_funcionalidades.md`), porque el Discovery no las detalló.

### Plan óptimo con 3 agentes

| Paso | Agente A (Backend core) | Agente B (Backend aux) | Agente C (Frontend) |
|---|---|---|---|
| 1 | C-01 crear-turno-sin-solapamientos | — | C-02 frontend-base-ts |
| 2 | C-01 (continúa) | C-03 persistencia-base-postgres | C-02 (continúa) |
| 3 | — | C-04 configuracion-consultorio | — |
| 4 | — | C-05 auth-sesion-roles | — |
| 5 | C-06 api-turnos | C-07 horarios-ausencias-bloqueos | — |
| 6 | C-08 sobreturno-y-extension | C-10 reserva-online-paciente | C-09 frontend-agenda-personal |
| 7 | C-11 notificaciones-recordatorios | C-13 ficha-clinica-odontograma | C-12 frontend-reserva-paciente |
| 8 | C-14 entrega-readme-y-evidencia | — | — |
| | **Post-MVP** | | |
| 9 | C-15 lista-de-espera-automatica | C-18 obras-sociales-y-planes | C-22 caja-y-cobros |
| 10 | C-16 politica-cancelacion-configurable | C-19 presupuestos-y-cobertura | C-23 senas-mercado-pago |
| 11 | C-17 consentimiento-comunicaciones | C-20 liquidaciones-obras-sociales | C-24 facturacion-electronica-arca |
| 12 | C-30 sincronizacion-google-calendar | C-21 validacion-online-obras-sociales | C-25 periodontograma |
| 13 | C-31 asistente-ia-whatsapp | C-26 imagenes-y-radiografias | C-27 consentimientos-firma-electronica |
| 14 | C-29 multisucursal | C-28 exportacion-hl7-fhir | C-32 reportes-y-metricas |

### Asignación al equipo de 5 (propuesta de la líder)

| Integrante | MVP | Post-MVP (sugerido, por afinidad con su módulo) |
|---|---|---|
| **Sofia Indovino** (líder) | **C-01** (OPSX), C-06, C-14 + revisión de todos los PR | C-15, C-16, C-29 |
| **Juan Cruz** | C-03, C-08, C-11 | C-17, C-30, C-31 |
| **Emanuel** | C-04, C-07, C-13 | C-25, C-26, C-27, C-28 |
| **Juan Sebastián** | C-05, C-10 | C-18, C-19, C-20, C-21 |
| **Enzo** | C-02, C-09, C-12 | C-22, C-23, C-24, C-32 (backend y pantallas de administración) |

---

## FASE 1: Núcleo de agenda y bases

> C-01 y C-02 arrancan en paralelo (backend y frontend no comparten archivos). C-03 arranca apenas el scaffolding de C-01 está en `master`.

### [C-01] `crear-turno-sin-solapamientos`
- **Estado**: `[x]` completo (2026-11-21, PR #6). Spec: `openspec/specs/agenda-turnos/spec.md`; change archivado en `openspec/changes/archive/2026-11-21-crear-turno-sin-solapamientos/`
- **Responsable**: Sofia Indovino (líder). **Change del ciclo OPSX** (Etapa 6 de la consigna).
- **Scope**:
  - Tarea 1 (merge temprano a `master`): scaffolding `backend/` con Gradle wrapper, `build.gradle` (Java 21, Spring Boot 3.x, JUnit 5, AssertJ), `settings.gradle`, paquetes base `ar.turnos.odontologia.{agenda,shared}`, `./gradlew test` en verde.
  - Dominio puro `agenda/domain`: `Intervalo` (semiabierto `[inicio, fin)`), `Turno`, `Agenda.verificarConflicto(...)`, `Margen` (≥ 10 min), excepciones `SolapamientoProfesionalException`, `SolapamientoSillonException`, `MargenInsuficienteException`.
  - Caso de uso `agenda/application/CrearTurno`: calcula `fin = inicio + duración` (RN-AG-01), valida el pasado (RN-AG-09) y los conflictos por profesional y por sillón con margen (RN-AG-02 a RN-AG-05). Puerto `TurnoRepository` con una implementación en memoria para los tests.
  - `Clock` inyectado para controlar "ahora" en los tests.
  - Tests JUnit, **uno por escenario del spec**: feliz; solapamiento por profesional; solapamiento por sillón; borde 10:30 rechazado; borde 10:40 aceptado; 10:30 con otro profesional y otro sillón aceptado; turno cancelado que no bloquea; inicio en el pasado.
  - **Fuera de scope**: persistencia (C-03/C-06), API REST (C-06), horarios (C-07), sobreturno (C-08).
- **Dependencias**: ninguna
- **Governance**: MEDIO (lógica de negocio central; la aprobación del spec en Propose es el checkpoint humano)
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §Definiciones, §Agenda (RN-AG-01 a 05, 09, 12)
  - `knowledge-base/06_funcionalidades.md` §US-010
  - `knowledge-base/07_flujos_principales.md` §Flujo 1
  - `knowledge-base/08_arquitectura_propuesta.md` §Patrones, §Estructura de directorios
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-02, §DD-03

### [C-02] `frontend-base-ts`
- **Estado**: `[x]` completo (2026-11-21, PR #11). Specs: `openspec/specs/frontend-cliente-api/spec.md` y `openspec/specs/frontend-navegacion/spec.md`; change archivado en `openspec/changes/archive/2026-11-21-frontend-base-ts/`
- **Responsable**: Enzo
- **Scope**:
  - `frontend/` con Vite (plantilla `vanilla-ts`), `tsconfig.json` estricto, scripts `dev`, `build`, `test`.
  - Layout responsivo base (mobile first), navegación entre las páginas vacías `agenda`, `reserva` y `configuracion`.
  - `shared/api/cliente.ts`: `fetch` con `credentials: 'include'` (sesión con cookie, DD-10), token CSRF y manejo de errores `409` y `422` con códigos de negocio.
  - `.env.example` con `VITE_API_BASE_URL`.
  - Tests: un test de unidad del cliente API (mock de `fetch`).
- **Dependencias**: ninguna
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/08_arquitectura_propuesta.md` §Estructura de directorios (frontend), §Variables de entorno
  - `knowledge-base/02_descripcion_general.md` §API REST (códigos de error)
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-10, §SU-05

### [C-03] `persistencia-base-postgres`
- **Estado**: `[x]` completo (2026-11-21, PR #12 y #19). Evidencia: `docs/evidencia/etapa-c03-tests.txt`
- **Responsable**: Juan Cruz
- **Scope**:
  - `docker-compose.yml` con PostgreSQL 15+. `.env.example` (`DB_URL`, `DB_USER`, `DB_PASSWORD`).
  - Dependencias Gradle: Spring Data JPA, Flyway, driver de PostgreSQL. `application.yml` con un perfil `dev`.
  - **Migración V1**: extensión `btree_gist`; tablas `profesional`, `sillon`, `prestacion`, `paciente`, `turno` (con `fin_con_margen`, `es_sobreturno`, `motivo_sobreturno` con CHECK, `version`); **las dos restricciones `EXCLUDE`** (por sillón incluyendo sobreturnos, y por profesional excluyéndolos; DD-03).
  - Entidades JPA en `shared/persistence` (sin tocar `agenda/domain`).
  - Tests de integración con `@Tag("integration")` que prueban la restricción: 10:30 rechazado y 10:40 aceptado a nivel base.
- **Dependencias**: `C-01` (solo la tarea 1, scaffolding)
- **Governance**: ALTO (garantía de integridad de datos)
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §Turno, §Profesional, §Sillon, §Prestacion, §Paciente
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-03, §SU-04
  - `knowledge-base/08_arquitectura_propuesta.md` §Variables de entorno

---

## FASE 2: Configuración y seguridad

> C-04 y C-05 corren en paralelo apenas C-03 está en `master`.

### [C-04] `configuracion-consultorio`
- **Estado**: `[x]` completo (2026-11-22, PR #15). Specs: `openspec/specs/catalogo-consultorio/` y `configuracion-margen/` (nuevos); change archivado en `openspec/changes/archive/2026-11-22-configuracion-consultorio/`. Migraciones V2 y V2_1 (seed solo en `dev`)
- **Responsable**: Emanuel
- **Scope**:
  - CRUD `/api/v1/profesionales`, `/sillones`, `/prestaciones` (US-001, US-002, US-003). Desactivar en lugar de borrar (RN-AG-11).
  - Especialidades: `PUT /profesionales/{id}/prestaciones` (RN-AG-15) y sillón habitual `PUT /profesionales/{id}/sillon-habitual` (US-018).
  - Configuración del margen: `PUT /configuracion/margen`, que rechaza valores menores de 10 (RN-AG-04).
  - **Migración V2**: `profesional_prestacion`, `configuracion`, columna `sillon_habitual_id`. Seed ficticio (Dra. Ana Ejemplo, Dr. Bruno Prueba, Sillón 1 y 2, 4 prestaciones).
  - Tests: CRUD, matrícula única, margen < 10 rechazado, profesional no habilitado.
- **Dependencias**: `C-03`
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §Profesional, §ProfesionalPrestacion, §Configuracion, §Seed data
  - `knowledge-base/05_reglas_de_negocio.md` §RN-AG-04, §RN-AG-11, §RN-AG-15
  - `knowledge-base/06_funcionalidades.md` §Épica 1

### [C-05] `auth-sesion-roles`
- **Estado**: `[x]` completo (2026-11-21, PR #14). Specs: `openspec/specs/seguridad-autenticacion/`, `seguridad-autorizacion/`, `cuentas-pacientes/`; change archivado en `openspec/changes/archive/2026-11-21-auth-sesion-roles/`. Evidencia: `docs/evidencia/etapa-c05-auth.txt`
- **Responsable**: Juan Sebastián
- **Scope**:
  - Spring Security con **sesión y cookie** `HttpOnly`/`SameSite=Lax`, CSRF habilitado (DD-10). Contraseñas con BCrypt.
  - Entidad `Usuario` con roles `ADMIN`, `RECEPCIONISTA`, `ODONTOLOGO`, `PACIENTE`. **Migración V3**.
  - `POST /api/v1/public/login`, `POST /logout`, `POST /public/pacientes/registro` (registro único del paciente, US-024).
  - Reglas de acceso: `/public/**` abierto; `/paciente/**` solo `PACIENTE` y solo sus datos; el resto según la matriz RBAC.
  - Tests: login correcto o incorrecto, `403` por rol, un paciente no accede a datos de otro paciente.
- **Dependencias**: `C-03`
- **Governance**: CRITICO (autenticación)
- **Leer antes**:
  - `knowledge-base/03_actores_y_roles.md`
  - `knowledge-base/08_arquitectura_propuesta.md` §Seguridad
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-10
  - `knowledge-base/05_reglas_de_negocio.md` §RN-RO-08

---

## FASE 3: Agenda completa

### [C-06] `api-turnos`
- **Estado**: `[x]` completo (2026-11-21, PR #17). Specs: `openspec/specs/agenda-api/`, `auditoria-turnos/` (nuevos) y `agenda-turnos/` (actualizado); change archivado en `openspec/changes/archive/2026-11-21-api-turnos/`. Migración V4
- **Responsable**: Sofia Indovino
- **Scope**:
  - Adaptador JPA del puerto `TurnoRepository` (de C-01) en `agenda/infrastructure`.
  - `POST /api/v1/turnos`, `GET /turnos?desde&hasta&profesionalId&sillonId`, `POST /turnos/{id}/cancelar`, `POST /turnos/{id}/reprogramar` (US-011, US-012). Cancelación por el consultorio con validación de DNI y número de reserva (RN-AG-07).
  - `@RestControllerAdvice`: excepciones de dominio y violación de `EXCLUDE` → `409` con códigos estables.
  - **Migración V4**: `turno_auditoria`, `codigo_reserva`. Auditoría de crear, cancelar y reprogramar (RN-GL-03).
  - Tests: integración API (creación, conflicto `409`, cancelar libera el horario, reprogramar atómico).
- **Dependencias**: `C-01, C-03, C-05`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/07_flujos_principales.md` §Flujo 1, §Flujo 3
  - `knowledge-base/02_descripcion_general.md` §API REST
  - `knowledge-base/05_reglas_de_negocio.md` §RN-AG-07, §RN-AG-08, §RN-AG-12
  - `knowledge-base/04_modelo_de_datos.md` §Turno, §TurnoAuditoria

### [C-07] `horarios-ausencias-bloqueos`
- **Estado**: `[x]` completo (2026-11-22, PR #31). Specs: `openspec/specs/disponibilidad-profesional/` (nuevo) y `agenda-turnos/` (actualizado); change archivado en `openspec/changes/archive/2026-11-22-horarios-ausencias-bloqueos/`. Migración V5 y seed V5_1
- **Responsable**: Emanuel
- **Scope**:
  - Entidades `HorarioTrabajo`, `AusenciaProfesional`, `BloqueoAgenda`. **Migración V5**.
  - `GET/PUT /profesionales/{id}/horarios` (US-016), `POST /profesionales/{id}/ausencias` (US-017), `POST /profesionales/{id}/bloqueos` (US-015).
  - Nuevas reglas de agenda como validadores aparte en `agenda/domain/reglas/` (archivos nuevos; **la líder revisa cualquier cambio en `CrearTurno`**): `FUERA_DE_HORARIO` (RN-AG-13), bloqueo y ausencia (RN-AG-10, RN-AG-16).
  - Al registrar una ausencia: listar los turnos afectados para reasignarlos (el aviso al paciente se conecta en C-11).
  - Tests: turno fuera de horario rechazado, turno durante una licencia rechazado, varias franjas por día.
- **Dependencias**: `C-01, C-04`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §RN-AG-10, §RN-AG-13, §RN-AG-16
  - `knowledge-base/04_modelo_de_datos.md` §HorarioTrabajo, §AusenciaProfesional, §BloqueoAgenda
  - `knowledge-base/06_funcionalidades.md` §US-015, §US-016, §US-017
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-08, §DD-11

### [C-08] `sobreturno-y-extension`
- **Estado**: `[x]` completo (2026-11-22, PR #21; archive en PR #28). Specs: `openspec/specs/agenda-api/`, `agenda-turnos/` y `auditoria-turnos/` (actualizados); change archivado en `openspec/changes/archive/2026-11-22-sobreturno-y-extension/`. Migración V6
- **Responsable**: Juan Cruz
- **Scope**:
  - `POST /api/v1/turnos/sobreturno` con motivo obligatorio. Puede pisar la agenda del profesional, **nunca la limpieza del sillón**; si no hay sillón libre, `409 SIN_SILLON_DISPONIBLE` (RN-AG-06; US-013).
  - `POST /turnos/{id}/extender` con motivo (RN-AG-14; US-019).
  - **Migración V6**: columnas `extendido` y `motivo_extension`.
  - Tests: sobreturno sin motivo rechazado, sobreturno con sillón libre aceptado, sobreturno sin sillón libre rechazado, paciente con `403`.
- **Dependencias**: `C-06`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §RN-AG-06, §RN-AG-14
  - `knowledge-base/06_funcionalidades.md` §US-013, §US-019
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-03, §DD-06

### [C-09] `frontend-agenda-personal`
- **Estado**: `[ ]` pendiente
- **Responsable**: Enzo
- **Scope**:
  - Login del personal, agenda diaria y semanal filtrable por profesional o sillón (US-014), formulario para crear y cancelar turnos con mensajes por código de error.
  - Indicadores visuales de turno confirmado y de sobreturno. Responsivo.
- **Dependencias**: `C-02, C-06`
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §US-014
  - `knowledge-base/02_descripcion_general.md` §API REST
  - `knowledge-base/03_actores_y_roles.md` §RBAC

---

## FASE 4: Paciente y notificaciones

### [C-10] `reserva-online-paciente`
- **Estado**: `[ ]` pendiente
- **Responsable**: Juan Sebastián
- **Scope**:
  - `GET /api/v1/public/disponibilidad?prestacionId&fecha[&profesionalId]`: unión de los huecos de los profesionales habilitados que trabajan ese día, sin datos de terceros (RN-RO-01, 04, 07).
  - `POST /paciente/reservas`: asigna el profesional (habilitado, con menos turnos y, si hay empate, al azar; RN-RO-05) y el sillón (habitual primero; RN-RO-06). Devuelve el número de reserva.
  - `GET /paciente/turnos`, cancelar y reprogramar con número de reserva hasta 24 h antes (RN-AG-07; US-020 a US-024).
  - Tests: "sin preferencia" con dos profesionales libres, empate, profesional no habilitado excluido, cancelación a 23 h rechazada.
- **Dependencias**: `C-05, C-06, C-07`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §Reserva online (RN-RO-01 a 08), §RN-AG-07
  - `knowledge-base/07_flujos_principales.md` §Flujo 2, §Flujo 3
  - `knowledge-base/06_funcionalidades.md` §Épica 3

### [C-11] `notificaciones-recordatorios`
- **Estado**: `[ ]` pendiente
- **Responsable**: Juan Cruz
- **Scope**:
  - `NotificacionPort` con `WhatsAppAdapter` y `EmailAdapter` **simulados** (log o servidor SMTP de desarrollo; DD-04).
  - Tarea `@Scheduled`: recordatorio **24 h antes** para los turnos activos (RN-NO-04). Canal WhatsApp o email (RN-NO-02), solo con consentimiento (RN-NO-03), sin datos de salud (RN-NO-01).
  - Confirmación por token de un solo uso (US-031). Avisos de reasignación por ausencia del profesional (RN-AG-16).
  - **Migración V7**: `notificacion`.
  - Tests: el mensaje no contiene datos de salud, se usa email si no tiene WhatsApp, no se envía sin consentimiento, no hay recordatorio si se reservó con menos de 24 h.
- **Dependencias**: `C-06`
- **Governance**: ALTO (notificaciones con datos personales)
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §Notificaciones
  - `knowledge-base/07_flujos_principales.md` §Flujo 4
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-04, §DD-07
  - `knowledge-base/02_descripcion_general.md` §Integraciones externas

### [C-12] `frontend-reserva-paciente`
- **Estado**: `[ ]` pendiente
- **Responsable**: Enzo
- **Scope**:
  - Registro e inicio de sesión del paciente; elección de prestación, fecha y profesional o "sin preferencia"; listado de horarios; confirmación con número de reserva.
  - "Mis turnos" con cancelar o reprogramar (aviso si faltan menos de 24 h). Responsivo, pensado para el celular.
- **Dependencias**: `C-02, C-10`
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §Épica 3
  - `knowledge-base/07_flujos_principales.md` §Flujo 2
  - `knowledge-base/03_actores_y_roles.md` §Rutas públicas

---

## FASE 5: Clínica

### [C-13] `ficha-clinica-odontograma`
- **Estado**: `[ ]` pendiente
- **Responsable**: Emanuel
- **Scope**:
  - `FichaClinica` y `OdontogramaRegistro` (FDI, permanentes y temporales, historial). **Migración V8**.
  - `GET/PUT /pacientes/{id}/ficha`, `PUT /pacientes/{id}/odontograma`: solo `ODONTOLOGO` edita, y `ADMIN` puede leer (RN-CL-01).
  - Marcar la ausencia del paciente desde la ficha: el turno pasa a `AUSENTE` y el tiempo restante queda libre (RN-AG-17; US-042).
  - Tests: la recepción recibe `403` en la ficha, el historial no se sobrescribe, la ausencia libera el horario.
- **Dependencias**: `C-05, C-06`
- **Governance**: ALTO (datos de salud, Ley 26.529)
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §Clínica, §RN-AG-17
  - `knowledge-base/04_modelo_de_datos.md` §FichaClinica, §OdontogramaRegistro
  - `knowledge-base/06_funcionalidades.md` §Épica 5

---

## FASE 6: Entrega

### [C-14] `entrega-readme-y-evidencia`
- **Estado**: `[ ]` pendiente
- **Responsable**: Sofia Indovino
- **Scope**:
  - `README.md` reproducible: requisitos (Java 21, opcional Docker o PostgreSQL), cómo correr `./gradlew test` (tests de dominio sin base) y los tests de integración.
  - Verificación final: suite en verde, ningún secreto en el repo, `.env.example` completo.
  - Evidencias: informe de Discovery en PDF, estado del orquestador, consulta a Engram para el video.
- **Dependencias**: `C-01` (mínimo); los demás changes que se hayan completado
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/README.md`
  - `knowledge-base/08_arquitectura_propuesta.md` §Variables de entorno
  - `knowledge-base/09_decisiones_y_supuestos.md` §SU-04

---

## FASE 7: Post-MVP v1.x (diferenciadores)

> Arranca con el MVP completo (C-01 a C-14). Los tres changes pueden ir en paralelo.

### [C-15] `lista-de-espera-automatica`
- **Estado**: `[ ]` pendiente
- **Responsable**: Sofia Indovino
- **Scope**:
  - Entidad `ListaEspera` (paciente, prestación, profesional opcional, rango de fechas preferido). **Migración V9**.
  - Al cancelarse un turno (RN-AG-08), ofrecer el horario liberado al primer paciente compatible de la lista, por su canal (RN-NO-02), con un plazo para aceptar.
  - Tests: el horario cancelado se ofrece en orden de llegada; si nadie acepta en el plazo, vuelve a quedar libre.
  - **Antes de empezar**: ampliar la KB con las reglas de la lista de espera (US-100). Hoy es el vacío del mercado: 0 de 16 sistemas la tienen.
- **Dependencias**: `C-06, C-11`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §Backlog (US-100)
  - `knowledge-base/05_reglas_de_negocio.md` §RN-AG-08, §Notificaciones
  - `docs/discovery/informe-discovery.md` §C.3, §C.4

### [C-16] `politica-cancelacion-configurable`
- **Estado**: `[ ]` pendiente
- **Responsable**: Sofia Indovino
- **Scope**:
  - Reemplazar las 24 h fijas de RN-AG-07 por un valor configurable por consultorio (`configuracion.cancelacion_horas`). **Migración V10**.
  - `PUT /configuracion/cancelacion` (ADMIN). Aplica igual en la cuenta del paciente y en los links del recordatorio.
  - Tests: cancelación dentro y fuera del plazo configurado.
- **Dependencias**: `C-04, C-10`
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §RN-AG-07
  - `knowledge-base/06_funcionalidades.md` §Backlog (US-101)
  - `knowledge-base/04_modelo_de_datos.md` §Configuracion

### [C-17] `consentimiento-comunicaciones`
- **Estado**: `[ ]` pendiente
- **Responsable**: Juan Cruz
- **Scope**:
  - Pantalla y endpoint para que el paciente otorgue o revoque el consentimiento de comunicaciones, con fecha y canal (Ley 25.326). Historial de cambios. **Migración V11**.
  - Las notificaciones respetan el estado vigente (RN-NO-03).
  - Tests: revocar el consentimiento corta los recordatorios; el historial no se pierde.
- **Dependencias**: `C-11`
- **Governance**: ALTO (datos personales)
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §RN-NO-03
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-07
  - `knowledge-base/06_funcionalidades.md` §Backlog (US-102)

---

## FASE 8: Post-MVP: obras sociales, cobros y facturación

> **Antes de empezar esta fase, ampliar la KB**: reglas de obras sociales y prepagas (nomencladores, coberturas, copagos), caja y facturación. Hoy solo están en el backlog. Referencias de mercado: DentalTec, DentalCore y el módulo de Dentalink para Argentina (informe, sección A).

### [C-18] `obras-sociales-y-planes`
- **Estado**: `[ ]` pendiente
- **Responsable**: Juan Sebastián
- **Scope**:
  - Entidades `ObraSocial`, `Plan`, `Nomenclador`, `AfiliacionPaciente` (número de afiliado, plan). **Migración V12**.
  - CRUD (ADMIN) y asociación del paciente con su obra social.
  - Tests: CRUD; un paciente con afiliación vigente y otro sin cobertura.
- **Dependencias**: `C-04, C-05`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §Backlog (US-200)
  - `knowledge-base/01_vision_y_objetivos.md` §Fuera de alcance
  - `docs/discovery/sources/relevamiento-fuentes.md` §DentalTec, §DentalCore, §Dentalink

### [C-19] `presupuestos-y-cobertura`
- **Estado**: `[ ]` pendiente
- **Responsable**: Juan Sebastián
- **Scope**:
  - Presupuesto por paciente a partir de las prestaciones del odontograma, separando la parte que cubre la obra social y el copago. **Migración V13**.
  - Tests: cálculo de cobertura y copago; presupuesto sin obra social.
- **Dependencias**: `C-13, C-18`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §OdontogramaRegistro
  - `docs/discovery/informe-discovery.md` §A.3 (presupuestos)

### [C-20] `liquidaciones-obras-sociales`
- **Estado**: `[ ]` pendiente
- **Responsable**: Juan Sebastián
- **Scope**:
  - Liquidación mensual por obra social y plan, con exportación a PDF o CSV y conciliación de lo cobrado. **Migración V14**.
  - Tests: agrupación por obra social y período; conciliación parcial.
- **Dependencias**: `C-19`
- **Governance**: ALTO (dinero)
- **Leer antes**:
  - `docs/discovery/sources/relevamiento-fuentes.md` §Dentalink (conciliaciones), §DentiClick
  - `knowledge-base/06_funcionalidades.md` §Backlog (US-200)

### [C-21] `validacion-online-obras-sociales`
- **Estado**: `[ ]` pendiente
- **Responsable**: Juan Sebastián
- **Scope**:
  - Puerto `ValidadorCobertura` con un adaptador **simulado** y un esqueleto de adaptador SOAP o REST por obra social: afiliado, prestación, topes y autorizaciones.
  - Tests: respuesta válida, afiliado inexistente, prestación no cubierta, timeout del servicio.
- **Dependencias**: `C-18`
- **Governance**: ALTO (integración externa)
- **Leer antes**:
  - `knowledge-base/02_descripcion_general.md` §Integraciones externas
  - `docs/discovery/sources/relevamiento-fuentes.md` §DentalTec

### [C-22] `caja-y-cobros`
- **Estado**: `[ ]` pendiente
- **Responsable**: Enzo
- **Scope**:
  - Caja diaria (apertura, cierre, arqueo), cobros por turno y cuenta corriente del paciente. **Migración V15**.
  - Pantallas de caja en `frontend/`.
  - Tests: arqueo cuadrado y descuadrado; cobro parcial.
- **Dependencias**: `C-06`
- **Governance**: CRITICO (dinero)
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §Backlog (US-207)
  - `knowledge-base/03_actores_y_roles.md` §RBAC

### [C-23] `senas-mercado-pago`
- **Estado**: `[ ]` pendiente
- **Responsable**: Enzo
- **Scope**:
  - Puerto `PasarelaPago` con adaptador Mercado Pago (modo sandbox, **sin credenciales reales en el repo**): link de pago para la seña al reservar y webhook de confirmación con validación de firma.
  - Tests: seña pagada confirma el turno; seña vencida lo libera; webhook con firma inválida rechazado.
- **Dependencias**: `C-10, C-22`
- **Governance**: CRITICO (pagos)
- **Leer antes**:
  - `knowledge-base/02_descripcion_general.md` §Integraciones externas
  - `knowledge-base/08_arquitectura_propuesta.md` §Seguridad, §Variables de entorno

### [C-24] `facturacion-electronica-arca`
- **Estado**: `[ ]` pendiente
- **Responsable**: Enzo
- **Scope**:
  - Puerto `Facturador` con adaptador ARCA (homologación, sin certificados reales en el repo): Factura B/C con CAE y QR.
  - Tests: comprobante emitido en homologación; error de ARCA manejado.
- **Dependencias**: `C-22`
- **Governance**: CRITICO (fiscal)
- **Leer antes**:
  - `knowledge-base/02_descripcion_general.md` §Integraciones externas
  - `docs/discovery/sources/relevamiento-fuentes.md` §DentalCore, §DentalTec

---

## FASE 9: Post-MVP: clínica avanzada

> Los cuatro changes pueden ir en paralelo con el MVP completo. Todos tocan `clinica/` (dueño: Emanuel).

### [C-25] `periodontograma`
- **Estado**: `[ ]` pendiente
- **Responsable**: Emanuel
- **Scope**:
  - Sondaje de 6 puntos por pieza (MV, V, DV, ML, L, DL), recesión, sangrado, placa, movilidad y furcación, con versiones por visita. **Migración V16**.
  - Tests: carga completa por pieza; cálculo del estado (sano, leve, moderado, severo).
- **Dependencias**: `C-13`
- **Governance**: ALTO (datos de salud)
- **Leer antes**:
  - `docs/discovery/verificacion/sofia.md` (periodontograma verificado en VecDent)
  - `knowledge-base/05_reglas_de_negocio.md` §Clínica

### [C-26] `imagenes-y-radiografias`
- **Estado**: `[ ]` pendiente
- **Responsable**: Emanuel
- **Scope**:
  - Carga de imágenes y radiografías por paciente, pieza y fecha, con almacenamiento detrás de un puerto (local o S3 compatible). **Migración V17**.
  - Tests: tipos y tamaños permitidos; acceso solo para el odontólogo.
- **Dependencias**: `C-13`
- **Governance**: ALTO (datos de salud)
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §RN-CL-01
  - `knowledge-base/08_arquitectura_propuesta.md` §Seguridad

### [C-27] `consentimientos-firma-electronica`
- **Estado**: `[ ]` pendiente
- **Responsable**: Emanuel
- **Scope**:
  - Plantillas de consentimiento informado y firma con hash, fecha, IP y dispositivo; documento bloqueado una vez firmado (Ley 25.506). **Migración V18**.
  - Tests: no se puede modificar un documento firmado; verificación del hash.
- **Dependencias**: `C-13`
- **Governance**: CRITICO (validez legal)
- **Leer antes**:
  - `docs/discovery/sources/relevamiento-fuentes.md` §Bilog
  - `knowledge-base/05_reglas_de_negocio.md` §Clínica

### [C-28] `exportacion-hl7-fhir`
- **Estado**: `[ ]` pendiente
- **Responsable**: Emanuel
- **Scope**:
  - Exportación de la historia clínica del paciente en PDF y en HL7 FHIR (recursos `Patient`, `Condition`, `Procedure`).
  - Tests: el documento exportado es válido contra el esquema FHIR; el paciente solo exporta lo suyo.
- **Dependencias**: `C-13`
- **Governance**: ALTO (portabilidad de datos de salud)
- **Leer antes**:
  - `docs/discovery/informe-discovery.md` §C.2 (interoperabilidad)
  - `knowledge-base/04_modelo_de_datos.md` §FichaClinica

---

## FASE 10: Post-MVP: escala, integraciones e inteligencia

### [C-29] `multisucursal`
- **Estado**: `[ ]` pendiente
- **Responsable**: Sofia Indovino
- **Scope**:
  - Entidad `Sucursal`; profesionales, sillones, horarios, turnos y caja asociados a una sucursal. Filtro por sucursal en toda la API. **Migración V19**.
  - Revisar DD-05 (hoy single-tenant) y las restricciones de exclusión por sucursal.
  - Tests: aislamiento de agendas entre sucursales; profesional que atiende en dos sucursales en días distintos.
- **Dependencias**: `C-04, C-06, C-07`
- **Governance**: ALTO (cambio transversal del modelo)
- **Leer antes**:
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-05
  - `knowledge-base/04_modelo_de_datos.md`

### [C-30] `sincronizacion-google-calendar`
- **Estado**: `[ ]` pendiente
- **Responsable**: Juan Cruz
- **Scope**:
  - Puerto `CalendarioExterno` con adaptador Google Calendar (OAuth del profesional, sin credenciales en el repo): publicar los turnos del profesional en su calendario.
  - Tests: alta, cancelación y reprogramación reflejadas; token revocado manejado.
- **Dependencias**: `C-06`
- **Governance**: ALTO (OAuth e integración externa)
- **Leer antes**:
  - `knowledge-base/02_descripcion_general.md` §Integraciones externas

### [C-31] `asistente-ia-whatsapp`
- **Estado**: `[ ]` pendiente
- **Responsable**: Juan Cruz
- **Scope**:
  - Asistente conversacional por WhatsApp que consulta disponibilidad, reserva, confirma y reprograma usando los mismos casos de uso de C-10 (sin saltear reglas). Derivación a una persona ante urgencias.
  - Sin datos de salud en la conversación (RN-NO-01).
  - Tests: reserva por conversación respeta RN-AG; la derivación funciona.
- **Dependencias**: `C-10, C-11`
- **Governance**: ALTO
- **Leer antes**:
  - `docs/discovery/informe-discovery.md` §C.2 (asistentes por WhatsApp)
  - `knowledge-base/05_reglas_de_negocio.md` §Reserva online, §Notificaciones

### [C-32] `reportes-y-metricas`
- **Estado**: `[ ]` pendiente
- **Responsable**: Enzo
- **Scope**:
  - Panel con ocupación por profesional y sillón, turnos por origen (online o del personal), ausencias de pacientes y de profesionales, y cancelaciones. Solo para el personal.
  - Tests: los cálculos coinciden con datos de prueba conocidos; el paciente recibe `403`.
- **Dependencias**: `C-06, C-13`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/01_vision_y_objetivos.md` §Métricas de éxito
  - `knowledge-base/03_actores_y_roles.md` §RBAC
