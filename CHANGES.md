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
| **Ajustes del MVP (C-33 a C-40)** | | |
| `frontend/` agenda: reprogramación (C-33) | Juan Cruz (avisa a Enzo) | — |
| `frontend/` página Configuración (C-34) | Emanuel (avisa a Enzo) | — |
| `backend/.../agenda/api` y `frontend/` agenda propia del odontólogo (C-35) | Sofia (avisa a Juan Sebastián y a Enzo) | — |
| `backend/.../usuarios/` búsqueda de pacientes y `frontend/` buscador (C-36) | Juan Sebastián (avisa a Enzo) | V20 |
| `frontend/` portada y acceso (C-37) | Juan Cruz (avisa a Enzo) | — |
| `frontend/` explorador de prestaciones en la reserva (C-38) | Juan Cruz (avisa a Enzo) | — |
| `frontend/` barra lateral, colores y estilo uniforme (C-39) | Juan Cruz (avisa a Enzo) | — |
| `frontend/` agenda: nuevo turno guiado por prestación (C-40) | Enzo (si la disponibilidad necesita filtrar por sillón, avisa a Juan Sebastián) | — |
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
  │                 └── C-12 frontend-reserva-paciente [Emanuel]  ← + C-02
  │
  │  Ajustes del MVP (después del archive de C-09)
  │     C-09 + C-10 + C-12 ── C-33 frontend-reprogramacion-agenda [Juan Cruz]
  │     C-04 + C-07 + C-09 ── C-34 frontend-configuracion-consultorio [Emanuel]
  │     C-05 + C-06 + C-09 ── C-35 agenda-propia-odontologo [Sofia]   ← antes: cambio de regla en la KB
  │     C-05 + C-09 ──────── C-36 buscador-pacientes [Juan Sebastián]
  │     C-33 + C-35 + C-36 ─ C-40 frontend-nuevo-turno-guiado [Enzo]  ← reusa la disponibilidad de C-33 y el buscador de C-36
  │
  │  Ajustes del MVP, revisión de la aplicación del 2026-11-23 (en orden: los tres tocan el mismo frontend/)
  │     C-12 + ingreso-unico-mi-cuenta ── C-37 frontend-portada-y-acceso [Juan Cruz]
  │     C-12 + C-37 ──────── C-38 frontend-reserva-explorador-prestaciones [Juan Cruz]
  │     C-33 a C-38 ──────── C-39 frontend-uniformidad-visual [Juan Cruz]
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
  → C-12 frontend-reserva-paciente           [Agente C · Emanuel]

GATE 5b: C-09 archivado ✓ y C-12 ✓ (2026-11-23)                ← FORK (los 4 en paralelo)
  → C-33 frontend-reprogramacion-agenda      [Agente C · Juan Cruz]
  → C-34 frontend-configuracion-consultorio  [Agente C · Emanuel]
  → C-35 agenda-propia-odontologo            [Agente A · Sofia — después de actualizar la KB]
  → C-36 buscador-pacientes                  [Agente B · Juan Sebastián]
  (C-33, C-35 y C-36 tocan la agenda del personal en frontend/: coordinar el orden de los PR para evitar conflictos)

GATE 5c: C-12 archivado ✓ e `ingreso-unico-mi-cuenta` (fix de C-12, Emanuel) mergeado        ← SECUENCIA
  → C-37 frontend-portada-y-acceso           [Agente C · Juan Cruz]
  → C-38 frontend-reserva-explorador-prestaciones  [Agente C · Juan Cruz — después de C-37]
  → C-39 frontend-uniformidad-visual         [Agente C · Juan Cruz — después de C-37 y C-38, con C-33 a C-36 ya mergeados]
  (los tres tocan frontend/ y styles.css: un PR por vez, en este orden)

GATE 5d: C-33 ✓ + C-35 ✓ + C-36 ✓ (2026-11-23)                ← en paralelo con GATE 5c (otras pantallas)
  → C-40 frontend-nuevo-turno-guiado         [Agente C · Enzo]
  (rehace el formulario "Nuevo turno" que tocaron C-35 y C-36: sus tests tienen que seguir pasando.
   Va antes de la etapa D de C-39, que reestiliza las pantallas del personal: coordinar el orden con Juan Cruz)

GATE 6: changes de la entrega ✓ (incluye C-33 a C-40)
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
| **Ajustes del MVP** (fase 5b) | C-33 a C-40 | Prueba y revisión de la aplicación del 2026-11-23 |
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
| **Sofia Indovino** (líder) | **C-01** (OPSX), C-06, C-35, C-14 + revisión de todos los PR | C-15, C-16, C-29 |
| **Juan Cruz** | C-03, C-08, C-11, C-33, C-37, C-38, C-39 | C-17, C-30, C-31 |
| **Emanuel** | C-04, C-07, C-13, C-12, C-34 | C-25, C-26, C-27, C-28 |
| **Juan Sebastián** | C-05, C-10, C-36 | C-18, C-19, C-20, C-21 |
| **Enzo** | C-02, C-09, C-40 | C-22, C-23, C-24, C-32 (backend y pantallas de administración) |

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
- **Seguimiento (RN-AG-15)**: `[x]` completo (2026-11-22, PR #43). Crear, reprogramar a otro profesional y sobreturno rechazan con `409 PROFESIONAL_NO_HABILITADO`. Specs `agenda-turnos` y `agenda-api` actualizadas; change archivado en `openspec/changes/archive/2026-11-22-validar-habilitacion-profesional/`. Sin migración
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
- **Estado**: `[x]` completo (2026-11-22; archive el 2026-11-23, antes que C-12). Specs: `openspec/specs/frontend-sesion-personal/` y `frontend-agenda-personal/` (nuevos), `frontend-cliente-api/` y `frontend-navegacion/` (actualizados); change archivado en `openspec/changes/archive/2026-11-23-frontend-agenda-personal/`. Evidencia: 99 tests y capturas en `verify/`
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
- **Estado**: `[x]` completo (2026-11-22, PR #37). Specs: `openspec/specs/disponibilidad-online/`, `reserva-online/`, `turnos-paciente/` (nuevos) y `auditoria-turnos/` (actualizado); change archivado en `openspec/changes/archive/2026-11-22-reserva-online-paciente/`. Sin migración nueva
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
- **Estado**: `[x]` completo (2026-11-23, PR #40). Specs: `openspec/specs/notificaciones-recordatorios/` y `confirmacion-asistencia/` (nuevos), `agenda-turnos/` y `auditoria-turnos/` (actualizados); change archivado en `openspec/changes/archive/2026-11-23-notificaciones-recordatorios/`. Migración V7. Incluye los avisos de la reserva online (puerto de C-10) y de reasignación (puerto de C-07); los links de cancelar y reprogramar quedan a cargo de C-12 (rutas `/mis-turnos/cancelar` y `/mis-turnos/reprogramar`, configurables)
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
- **Estado**: `[x]` completo (2026-11-23, PR #47 propuesta y PR #48 apply). Specs: `openspec/specs/frontend-cuenta-paciente/`, `frontend-reserva-paciente/`, `frontend-turnos-paciente/` y `frontend-confirmacion-asistencia/` (nuevos), `frontend-navegacion/` y `frontend-cliente-api/` (actualizados); change archivado en `openspec/changes/archive/2026-11-23-frontend-reserva-paciente/`. Evidencia: 180 tests, mockups aprobados (revisiones 1 y 2) y capturas en `mockups/verify/`
- **Responsable**: Emanuel (antes Enzo; `frontend/` sigue siendo de Enzo y se le avisa en el PR)
- **Scope**:
  - Registro e inicio de sesión del paciente; elección de prestación, fecha y profesional o "sin preferencia"; listado de horarios; confirmación con número de reserva.
  - "Mis turnos" con cancelar o reprogramar (aviso si faltan menos de 24 h). Responsivo, pensado para el celular.
  - Menú según la sesión (paciente o personal) y las pantallas de los links de C-11 (`/mis-turnos/cancelar`, `/mis-turnos/reprogramar` y `/confirmar/{token}`).
  - Ajuste de la prueba del 2026-11-23: sin sesión el menú muestra solo "Reservar turno" e "Ingresar"; "Mis turnos" aparece con sesión de paciente. Cambia un mockup aprobado: se actualiza y vuelve a aprobación de la líder.
  - Fix del 2026-11-23: el change `ingreso-unico-mi-cuenta` (rama `fix/C-12-ingreso-unico`) unifica el ingreso en Mi cuenta (`#/mi-cuenta`) para pacientes y personal: "Salir" del personal, la Agenda sin sesión y la sesión vencida llevan ahí, se elimina la página `#/ingreso` de C-09 y su dirección vieja lleva a `#/mi-cuenta`. Solo redirecciones, sin cambios visuales. Toca `frontend/` (dueño Enzo, avisar en el PR).
- **Dependencias**: `C-02, C-10` (y C-11 para los links). El archive de C-09 tiene que hacerse antes que el de C-12, porque los dos modifican `frontend-navegacion`
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §Épica 3
  - `knowledge-base/07_flujos_principales.md` §Flujo 2
  - `knowledge-base/03_actores_y_roles.md` §Rutas públicas

---

## FASE 5: Clínica

### [C-13] `ficha-clinica-odontograma`
- **Estado**: `[x]` completo (2026-11-22, PR #39). Specs: `openspec/specs/ficha-clinica/` (nuevo), `agenda-turnos/` y `auditoria-turnos/` (actualizados); change archivado en `openspec/changes/archive/2026-11-22-ficha-clinica-odontograma/`. Migración V8
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

## FASE 5b: Ajustes del MVP

> Surgen de la prueba de la aplicación del 2026-11-23. Son parte del MVP: se hacen antes de C-14. El punto "sin sesión no aparece Mis turnos" quedó dentro de C-12, que todavía estaba abierto.
> Los changes que tocan `frontend/` avisan en el PR a su dueño, y los de la agenda del personal (C-33, C-35, C-36 y C-40) arrancan después del archive de C-09, porque modifican sus specs.
> C-37, C-38 y C-39 salen de la revisión de la aplicación del 2026-11-23. Van **en ese orden y de a un PR por vez**, porque los tres modifican el encabezado, las rutas y `styles.css`. Las observaciones de otras pantallas que lleguen después se suman como una etapa de C-39, o como un change nuevo si piden comportamiento nuevo. Los tres usan el modo `system-and-mockups` del design system: los mockups se aprueban antes del apply.
> C-40 es un change nuevo por pedir comportamiento nuevo en "Nuevo turno" de la agenda del personal. Puede ir en paralelo con C-37 y C-38, que tocan otras pantallas, pero su PR se mergea antes de la etapa D de C-39 (pantallas del personal).

### [C-33] `frontend-reprogramacion-agenda`
- **Estado**: `[x]` mergeado (PR #55, 2026-11-23); falta archivarlo (`openspec archive frontend-reprogramacion-agenda`)
- **Responsable**: Juan Cruz
- **Scope**:
  - En "Reprogramar turno" de la agenda del personal, ofrecer solo los profesionales habilitados para la prestación del turno (RN-AG-15), con `GET /public/profesionales?prestacionId=`. Hoy se ofrecen todos.
  - Reemplazar la fecha y la hora libres por los horarios disponibles del profesional elegido (`GET /public/disponibilidad`), como botones, igual que en la reserva del paciente (reusar `paciente/horarios.ts` y `paciente/disponibilidad.ts` de C-12).
  - Reproducir y corregir por qué la reprogramación desde la agenda falla con un horario válido (revisar la identificación con DNI y número de reserva, y el mensaje de cada código de rechazo).
  - Riesgo conocido: la disponibilidad pública cuenta el propio turno como ocupado. Si se necesita ofrecer horarios que se superponen con él, el backend tiene que excluirlo (se avisa a Sofia, dueña de `agenda/api`).
  - Tests: un escenario por cada caso; los de C-09 siguen pasando.
- **Dependencias**: `C-09, C-10, C-12` (y el archive de C-09)
- **Governance**: MEDIO (reglas de la agenda en la pantalla del personal)
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §Dominio: Agenda (RN-AG-15)
  - `knowledge-base/06_funcionalidades.md` §US-012, §US-018
  - `openspec/changes/frontend-agenda-personal/` (reprogramación en la agenda)

### [C-34] `frontend-configuracion-consultorio`
- **Estado**: `[x]` completo (2026-11-23, PR #60; el archive va en un PR aparte). La página Configuración tiene siete pestañas con permisos por rol. Incluye los ajustes de la segunda prueba: sillón habitual exclusivo (RN-RO-09), alta completa, franjas para varios días, horas de 5 en 5, "Todos los profesionales" y reasignación desde bloqueos (RN-AG-19). Mockups: revisión 1 aprobada por la líder (PR #51) y revisión 2 aprobada por Emanuel. Tests: 367 del frontend, 477 unitarios y 487 de integración del backend.
- **Responsable**: Emanuel
- **Scope**:
  - Completar la página "Configuración" (hoy solo muestra el título) sobre los endpoints que ya existen de C-04 y C-07: profesionales, sillones y prestaciones (alta, edición, activar y desactivar), especialidades por profesional, sillón habitual, margen entre turnos, horarios de trabajo, ausencias y bloqueos.
  - Permisos según la matriz RBAC: `ADMIN` edita; `RECEPCIONISTA` y `ODONTOLOGO` solo leen, salvo las ausencias (recepción) y los bloqueos propios (odontólogo).
  - Cambio visual: mockups en `openspec/changes/<change>/mockups/` aprobados por la líder antes del apply (design system Revision 1).
  - Para el odontólogo usa el `profesionalId` de la sesión que agregó C-35.
  - **Ajustes de la segunda prueba (2026-11-23):**
    - sillón habitual exclusivo (RN-RO-09, backend en `configuracion/`);
    - alta del profesional con prestaciones y sillón;
    - franjas para varios días;
    - horas de 5 en 5;
    - "Todos los profesionales" en ausencias y bloqueos;
    - reasignación de turnos afectados por un bloqueo (RN-AG-19, backend en `agenda/disponibilidad/`, con lectura de bloqueos para la recepción).
  - Sin migraciones. Los módulos de backend que se tocan son de Emanuel (C-04 y C-07).
  - Tests: un escenario por cada sección y por cada permiso.
- **Dependencias**: `C-04, C-07, C-09, C-35` (y el archive de C-09)
- **Governance**: MEDIO (configuración que afecta la agenda)
- **Leer antes**:
  - `knowledge-base/03_actores_y_roles.md` §RBAC: matriz de permisos
  - `knowledge-base/04_modelo_de_datos.md` §Profesional, §Sillon, §Prestacion, §HorarioTrabajo, §BloqueoAgenda, §Configuracion
  - `knowledge-base/06_funcionalidades.md` §Épica 1, §US-015 a §US-018

### [C-35] `agenda-propia-odontologo`
- **Estado**: `[x]` completo (2026-11-23; el archive va en un PR aparte). Regla RN-AG-18 en la KB; change en `openspec/changes/agenda-propia-odontologo/` con deltas de `agenda-api`, `seguridad-autenticacion` y `frontend-agenda-personal`. Mockups aprobados (revisión 3) y verificación en `mockups/verify/`. Evidencia: `docs/evidencia/c35-backend.txt`. Sin migración. La sesión ya informa `profesionalId` del odontólogo (lo reusa C-34)
- **Responsable**: Sofia Indovino
- **Scope**:
  - **Cambio de regla de negocio**: hoy la KB dice que el `ODONTOLOGO` ve toda la agenda y "puede filtrar la propia". Antes del propose, actualizar `03_actores_y_roles.md` (y `05_reglas_de_negocio.md` si corresponde) con la regla nueva: cada odontólogo ve solo sus turnos del día y de la semana.
  - Backend: `GET /api/v1/turnos` con sesión de `ODONTOLOGO` filtra siempre por el profesional de su usuario, aunque pida otro `profesionalId` (que no se pueda saltear desde la API).
  - Frontend: con sesión de odontólogo, la agenda fija el filtro en su profesional y no ofrece los demás.
  - Tests: Ana no recibe los turnos de Bruno aunque los pida; recepción y admin siguen viendo todos.
- **Dependencias**: `C-05, C-06, C-09` (y el archive de C-09)
- **Governance**: ALTO (autorización: se propone y se revisa antes de escribir código)
- **Leer antes**:
  - `knowledge-base/03_actores_y_roles.md` §RBAC: matriz de permisos
  - `knowledge-base/06_funcionalidades.md` §US-014
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-10
  - `openspec/specs/seguridad-autorizacion/spec.md`, `openspec/specs/agenda-api/spec.md`

### [C-36] `buscador-pacientes`
- **Estado**: `[x]` completo (2026-11-23, PR #53 propuesta y PR #54 apply). Specs: `openspec/specs/busqueda-pacientes/` y `frontend-buscador-pacientes/` (nuevos) y `frontend-agenda-personal/` (actualizado); change archivado en `openspec/changes/archive/2026-11-23-buscador-pacientes/`. Sin migración V20 (D7); "Sobreturno" fuera de C-36 (D11). Pendiente: prueba manual con lector de pantalla (tarea 7.4)
- **Responsable**: Juan Sebastián
- **Scope**:
  - Backend: `GET /api/v1/pacientes?q=` para el personal (`ADMIN`, `RECEPCIONISTA` y `ODONTOLOGO`), que busca por DNI o por nombre y apellido y devuelve una lista corta: id, nombre, apellido y DNI. Sin datos de salud ni de contacto (Ley 25.326). Hoy no existe ningún endpoint para buscar pacientes.
  - Frontend: en "Nuevo turno" y "Sobreturno" de la agenda, reemplazar el campo "Id del paciente" por un buscador que muestra las coincidencias y guarda el id elegido.
  - **Migración V20** (reservada), solo si hace falta un índice para la búsqueda.
  - Tests: búsqueda por DNI exacto, por parte del apellido, sin resultados, el paciente no puede usar el endpoint (`403`) y la respuesta no trae otros datos.
- **Dependencias**: `C-05, C-09` (y el archive de C-09)
- **Governance**: ALTO (datos personales de pacientes)
- **Leer antes**:
  - `knowledge-base/03_actores_y_roles.md` §RBAC: matriz de permisos (Paciente, datos de contacto)
  - `knowledge-base/04_modelo_de_datos.md` §Paciente
  - `knowledge-base/05_reglas_de_negocio.md` §Dominio: Excepciones globales
  - `knowledge-base/06_funcionalidades.md` §US-010

### [C-37] `frontend-portada-y-acceso`
- **Estado**: `[x]` mergeado (PR #67, 2026-11-23; 345 tests, mockups aprobados); falta archivarlo, después del archive de `ingreso-unico-mi-cuenta`
- **Responsable**: Juan Cruz (avisa a Enzo, dueño de `frontend/`)
- **Scope**:
  - **Portada pública** en su propia dirección, `#/inicio`, que pasa a ser la página inicial: la dirección vacía (`http://localhost:5173/`) y las direcciones desconocidas la muestran en lugar de llevar a `#/reserva`, y el logo del encabezado lleva a ella. Pantalla descriptiva del consultorio y de la aplicación (qué hace y cómo se usa en tres pasos), atractiva, con ilustraciones SVG propias (sin recursos remotos ni fotos de personas). Dos botones principales: "Reservar turno" (`#/reserva`, sin registrarse antes) e "Ingresar a mi cuenta" (`#/mi-cuenta`). Arriba a la derecha, el botón "Acceso personal" para empleados (`#/mi-cuenta`).
  - **Un solo lugar para iniciar sesión**: `#/mi-cuenta`, para pacientes y personal, y crear cuenta solo para pacientes. Lo resuelve `ingreso-unico-mi-cuenta` (fix de C-12, Emanuel; mergeado en el PR #59): C-37 lo da por hecho y no rehace el login, siempre que la verificación de abajo confirme que funciona.
  - **Después de ingresar**, redirigir según el rol: PACIENTE a `#/mis-turnos`; ADMIN, RECEPCIONISTA y ODONTOLOGO a `#/agenda`. Con una sesión activa, la portada ofrece ir a su espacio en lugar de pedir ingresar.
  - **Primera tarea del propose: revisar qué pasó con `#/ingreso`.** `ingreso-unico-mi-cuenta` ya está en `master` (PR #59): en el código `frontend/src/pages/ingreso.ts` ya no existe y `#/ingreso` figura como una redirección a `#/mi-cuenta` (`REDIRECCIONES` en `frontend/src/app.ts`). Emanuel cree que **no funcionó**. **Verificado el 2026-11-23** (`openspec/changes/frontend-portada-y-acceso/verify/ingreso.md`): `#/ingreso` **sí redirige** a `#/mi-cuenta`. La única laguna es que el personal con sesión que abre Mi cuenta se queda ahí en lugar de ir a la Agenda, y C-37 la cierra.
  - **Dirección de la portada (decidido por la líder el 2026-11-23)**: como `ingreso-unico-mi-cuenta` eliminó `#/ingreso` y lo redirige a `#/mi-cuenta`, la portada no reutiliza esa dirección sino que vive en `#/inicio`. `#/ingreso` sigue redirigiendo a `#/mi-cuenta`, como lo dejó el PR #59, para no romper marcadores ni obligar a revertir el trabajo de Emanuel. El propose define qué cambia en el spec `frontend-navegacion` (página inicial y direcciones desconocidas) y a dónde lleva "Salir" de un paciente (hoy a `#/reserva`).
  - Modifica los specs `frontend-navegacion`, `frontend-sesion-personal` y `frontend-cuenta-paciente`. Modo `system-and-mockups`: mockups de la portada y de Mi cuenta aprobados antes del apply.
  - Tests: portada sin sesión con sus tres botones, "Acceso personal" va a `#/mi-cuenta`, redirección por rol, portada con sesión activa, los enlaces viejos y los de los avisos siguen funcionando; los tests de C-09 y C-12 siguen pasando.
- **Dependencias**: `C-09, C-12` e `ingreso-unico-mi-cuenta` (mergeado en el PR #59, a verificar)
- **Governance**: MEDIO (flujo de acceso y de sesión, solo en el frontend)
- **Leer antes**:
  - `knowledge-base/03_actores_y_roles.md` §RBAC
  - `knowledge-base/06_funcionalidades.md` §US-020
  - `openspec/specs/frontend-navegacion/`, `openspec/specs/frontend-sesion-personal/` y `openspec/specs/frontend-cuenta-paciente/`
  - `design-system/README.md` y `design-system/brand.md`

### [C-38] `frontend-reserva-explorador-prestaciones`
- **Estado**: `[x]` mergeado (PR #72, 2026-11-23; 447 tests, mockups aprobados); falta archivarlo
- **Responsable**: Juan Cruz (avisa a Enzo)
- **Scope**:
  - En el paso 1 de `#/reserva`, reemplazar la lista con todas las prestaciones por un **explorador**: un selector desplegable con buscador (combobox accesible, con teclado) que muestra solo las coincidencias, con el nombre y la duración de cada prestación.
  - Al elegir una prestación se sigue con el flujo actual. No cambian los pasos 2 y 3 ni lo que se envía a la API.
  - Sin categorías de prestaciones: necesitarían un cambio en el backend y quedan fuera.
  - **Formato decidido por la líder el 2026-11-23**: selector desplegable con buscador (no lista con panel de detalle).
  - Modifica el spec `frontend-reserva-paciente`. Modo `system-and-mockups`.
  - Tests: buscar por parte del nombre, sin coincidencias, elegir con el teclado y con el toque, el celular; los de C-12 siguen pasando.
- **Dependencias**: `C-12, C-37`
- **Governance**: BAJO (presentación)
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §US-020
  - `openspec/specs/frontend-reserva-paciente/`
  - `design-system/components.html` (secciones "Reserva online" y "Formularios y avisos")

### [C-39] `frontend-uniformidad-visual`
- **Estado**: `[ ]` propuesta lista (proposal, specs, design y tasks en `openspec/changes/frontend-uniformidad-visual/`, 2026-11-23); se hace en cuatro etapas (A tokens y CSS modular, B barra lateral y componentes, C pantallas del paciente, D pantallas del personal), cada una con su PR; la etapa A no lleva mockups
- **Responsable**: Juan Cruz (avisa a Enzo)
- **Scope**:
  - **Barra lateral desplegable** con los botones del menú ("Reservar turno", "Mis turnos", "Agenda", "Configuración"), que muestra solo las opciones del rol que ingresó. En el celular es un cajón con un botón de menú. Sin sesión se mantiene un encabezado simple con "Acceso personal" arriba a la derecha.
  - **Color**: el margen superior (encabezado) y el inferior (pie, con su texto si lo tiene) pasan al azul de los botones, con texto blanco y contraste suficiente (AA).
  - **Hover y foco** en todos los botones según su estilo (principal, secundario, peligro, fantasma, botones de horario y opciones), con transiciones suaves y respeto de `prefers-reduced-motion`.
  - **Más bonito y uniforme**: aire entre elementos, tarjetas con sombra suave, bordes redondeados consistentes, jerarquía tipográfica clara, íconos SVG propios, estados vacíos y de carga con ilustración, y los mismos botones, campos, avisos y diálogos en todas las pantallas (por ejemplo, el orden de los botones y el lugar del aviso en los diálogos).
  - **Base técnica**: dejar solo `--ds-*` (eliminar los alias `--color-*`, `--espacio-*` y `--radio`) y partir `styles.css` en módulos, sin cambiar el comportamiento.
  - Por etapas, cada una con mockups aprobados y capturas de antes y después a 390 y 1440 px: (A) tokens y CSS modular, (B) barra lateral y componentes comunes, (C) pantallas del paciente, (D) pantallas del personal. Si hace falta, design system revisión 2.
  - Las observaciones de otras pantallas (de la revisión del equipo) se suman como etapa.
  - Fuera de alcance: funcionalidades nuevas, backend, logo y nombre definitivos (TBD), modo oscuro y librerías de UI o de íconos.
  - Tests: los existentes siguen pasando, y se agregan los de la barra lateral por rol, los estados hover y foco, y las reglas de CSS.
- **Dependencias**: `C-33, C-34, C-35, C-36, C-37, C-38`
- **Governance**: BAJO (presentación), pero toca todas las pantallas: se hace por etapas
- **Leer antes**:
  - `design-system/README.md`, `design-system/brand.md` y `design-system/components.html`
  - `openspec/specs/frontend-navegacion/` y los specs `frontend-*` de cada pantalla

### [C-40] `frontend-nuevo-turno-guiado`
- **Estado**: `[x]` mergeado (2026-11-23, PR #69; propuesta y mockups aprobados en el PR #65). Change `openspec/changes/frontend-nuevo-turno-guiado/` (delta de `frontend-agenda-personal`). Evidencia: 381 tests y verificación contra el backend de desarrollo en `mockups/verify/`. Sin migración ni cambios de backend
- **Responsable**: Enzo
- **Scope**:
  - Rehacer "Nuevo turno" de la agenda del personal (`ADMIN`, `RECEPCIONISTA` y `ODONTOLOGO`) como un formulario guiado, donde cada paso se habilita cuando el anterior tiene un valor: **prestación → profesional → fecha → horario**, y el **sillón** del profesional se muestra sin poder cambiarlo. El paciente se sigue eligiendo con el buscador de C-36.
  - **Profesional**: solo los habilitados para la prestación elegida (RN-AG-15), con `GET /public/profesionales?prestacionId=` como en C-33.
  - **Fecha**: un calendario mensual en el que solo se pueden elegir días desde hoy en adelante; los días pasados se ven deshabilitados. Al elegir un día se cargan sus horarios; si el profesional no tiene horarios libres ese día (no trabaja, está ausente o la agenda está bloqueada, C-07), se avisa y se puede elegir otro día.
  - **Horario**: los horarios libres del profesional ese día como botones (`GET /public/disponibilidad`), reusando `paciente/horarios.ts` y `paciente/disponibilidad.ts` (C-12 y C-33).
  - **Sillón**: se muestra, sin poder cambiarlo, el sillón habitual del profesional elegido (campo `sillonHabitualId` de `GET /api/v1/profesionales`, C-04), que es el que se envía al crear el turno. Si el profesional no tiene sillón habitual, se avisa y no se puede crear el turno hasta que el administrador se lo asigne (US-018). Si el sillón está ocupado en ese horario, el backend responde `409 SOLAPAMIENTO_SILLON` y se muestra su mensaje.
  - Al cambiar un paso anterior, se vacían los que dependen de él.
  - Con sesión de `ODONTOLOGO` el formulario funciona igual: RN-AG-18 (C-35) limita lo que ve en la agenda, no los turnos que puede crear.
  - Cambio visual: mockups en `openspec/changes/<change>/mockups/` aprobados por la líder antes del apply (design system Revision 1).
  - Riesgo conocido: la disponibilidad pública ofrece un horario si hay algún sillón libre, no necesariamente el habitual. Si en las pruebas aparecen horarios ofrecidos con el sillón habitual ocupado, se avisa a Juan Sebastián (dueño de `reserva/`) para filtrar la disponibilidad por sillón.
  - Tests: un escenario por paso (qué se habilita y con qué opciones), por los días pasados deshabilitados en el calendario, por un día sin horarios, por el sillón habitual mostrado sin poder cambiarlo, por un profesional sin sillón habitual, por vaciar los pasos siguientes y por cada rechazo del backend; los de C-09, C-33, C-35 y C-36 siguen pasando.
- **Dependencias**: `C-04, C-07, C-09, C-33, C-35, C-36` (y el archive de C-09)
- **Governance**: MEDIO (reglas de la agenda en la pantalla del personal)
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §US-010, §US-018
  - `knowledge-base/05_reglas_de_negocio.md` §Dominio: Agenda (RN-AG-02 a RN-AG-05, RN-AG-15, RN-AG-18)
  - `knowledge-base/07_flujos_principales.md` §Flujo 1
  - `openspec/specs/frontend-agenda-personal/spec.md`, `openspec/specs/disponibilidad-online/spec.md`
  - `openspec/changes/frontend-reprogramacion-agenda/`, `openspec/changes/buscador-pacientes/` y `openspec/changes/agenda-propia-odontologo/` (lo que se reusa o ya cambió en la agenda)

---

## FASE 6: Entrega

### [C-14] `entrega-readme-y-evidencia`
- **Estado**: `[ ]` pendiente
- **Responsable**: Sofia Indovino
- **Scope**:
  - `README.md` reproducible: requisitos (Java 21, opcional Docker o PostgreSQL), cómo correr `./gradlew test` (tests de dominio sin base) y los tests de integración.
  - Verificación final: suite en verde, ningún secreto en el repo, `.env.example` completo.
  - Evidencias: informe de Discovery en PDF, estado del orquestador, consulta a Engram para el video.
- **Dependencias**: `C-01` (mínimo); los demás changes que se hayan completado, incluidos los ajustes C-33 a C-40
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
