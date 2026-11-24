<!-- active-stack:agent-instruction -->
# turnos-odontologia: instrucciones para agentes

> Este archivo (y su copia `CLAUDE.md`) es lo PRIMERO que todo agente lee al entrar al repo.
> Generado a partir de `knowledge-base/` y `CHANGES.md` el 2026-11-21. No editar a mano sin re-sincronizar ambos archivos.

Sistema de agenda para consultorios odontológicos chicos de Argentina. Su diferencial es **impedir que se superpongan los turnos por profesional y por sillón, con un margen configurable (mínimo 10 minutos)**. Equipo de 5: Sofia Indovino (líder, dueña de `master` y revisora de los PR), Emanuel, Juan Sebastián, Juan Cruz y Enzo.

---

## Stack Tecnológico

| Capa | Tecnologías | Versión mínima |
|---|---|---|
| Backend | Java + **Spring Boot** (Web, Validation, Data JPA, Security) | Java 21, Spring Boot 3.x |
| Build | **Gradle** (wrapper en el repo) | 8.x |
| Base de datos | **PostgreSQL** con la extensión `btree_gist` | 15+ |
| Migraciones | Flyway | — |
| Tests backend | JUnit 5, AssertJ, Spring Boot Test | — |
| Frontend | **TypeScript vanilla**, responsivo, sin framework, con Vite | TypeScript 5.x |

Arquitectura: monolito modular con API REST (`/api/v1`) y el dominio de agenda separado de la infraestructura (puertos y adaptadores). WhatsApp y email van detrás de puertos y se simulan en el TP.

Detalle completo: [knowledge-base/02_descripcion_general.md](knowledge-base/02_descripcion_general.md)

---

## Base de Conocimiento

La fuente de verdad del dominio vive en `knowledge-base/`. **Leé el archivo relevante ANTES de implementar.**

| Archivo | Cuándo leerlo |
|---------|---------------|
| [01_vision_y_objetivos.md](knowledge-base/01_vision_y_objetivos.md) | Propósito, alcance del MVP y lo que queda fuera |
| [03_actores_y_roles.md](knowledge-base/03_actores_y_roles.md) | Roles ADMIN, RECEPCIONISTA, ODONTOLOGO, PACIENTE; matriz RBAC |
| [04_modelo_de_datos.md](knowledge-base/04_modelo_de_datos.md) | Entidades, restricciones, seed ficticio |
| [05_reglas_de_negocio.md](knowledge-base/05_reglas_de_negocio.md) | Reglas RN-AG, RN-RO, RN-NO, RN-CL, RN-GL (**RN-AG-01 a RN-AG-05 son el corazón**) |
| [06_funcionalidades.md](knowledge-base/06_funcionalidades.md) | Historias de usuario y backlog post-MVP |
| [07_flujos_principales.md](knowledge-base/07_flujos_principales.md) | Flujos de punta a punta |
| [08_arquitectura_propuesta.md](knowledge-base/08_arquitectura_propuesta.md) | Estructura de directorios, seguridad, variables de entorno |
| [09_decisiones_y_supuestos.md](knowledge-base/09_decisiones_y_supuestos.md) | Decisiones DD-01 a DD-11 y supuestos |
| [10_preguntas_abiertas.md](knowledge-base/10_preguntas_abiertas.md) | Preguntas pendientes: hoy solo PA-11 (Docker), de prioridad media |

---

## Skills Disponibles

| Agente | Rol | Skills que carga |
|--------|-----|------------------|
| **Backend core** | Agenda, turnos, persistencia, migraciones | `java-springboot`, `java-junit`, `postgresql-table-design` |
| **Backend aux** | Seguridad, configuración, notificaciones, clínica | `java-springboot`, `springboot-security`, `java-junit` |
| **Frontend** | `frontend/` en TypeScript vanilla con Vite | `vite` |
| **Ciclo OPSX** | Explorar, proponer, implementar y archivar changes | `openspec-explore`, `openspec-propose`, `openspec-update-change`, `openspec-apply-change`, `openspec-sync-specs`, `openspec-archive-change` |

Las skills están instaladas en el repo (`.claude/skills/` y `.agents/skills/`, versiones fijadas en `skills-lock.json`). Cargá la que corresponde ANTES de escribir código.

> Los compact rules de cada skill los resuelve el orquestador desde [.atl/skill-registry.md](.atl/skill-registry.md) (generado por `skill-registry` y versionado en este repo). Esta tabla solo mapea skill→rol.

---

## Roadmap de Changes

El plan completo está en [CHANGES.md](CHANGES.md). Resumen:

- **Total**: 44 changes en 10 fases. **MVP**: C-01 a C-14 (fases 1 a 6) más los ajustes C-33 a C-44 (fase 5b, de la prueba y la revisión del 2026-11-23 y 2026-11-24). **Post-MVP**: C-15 a C-32, que arrancan cuando el MVP esté completo y la KB ampliada.
- **Camino crítico del MVP** (7): `C-01 → C-03 → C-05 → C-06 → C-10 → C-12 → C-14`.
- **Primer change**: `C-01` (`crear-turno-sin-solapamientos`). Es el change del ciclo OPSX y lo hace solo la líder. Su tarea 1 (scaffolding del backend) se mergea temprano para desbloquear C-03.

**Antes de iniciar un item**: leé [CHANGES.md](CHANGES.md), verificá que sus dependencias estén mergeadas y leé los archivos de "Leer antes". Se puede empezar antes en una rama propia, pero el PR se mergea en el orden de los gates.

---

## Reglas Duras (específicas del proyecto)

> Reglas globales ya definidas en el archivo global de instrucciones del cliente activo (orquestador OPSX, governance por dominio, Engram, editar en lugar de reescribir, validar antes de declarar hecho): el proyecto las hereda. Acá viven solo las reglas **específicas de este proyecto** y las universales que el global no cubre. Son contrato; romperlas es un defecto.

Confirmadas por la líder el 2026-11-21:

**Equipo y datos**

1. **NUNCA usar datos reales de personas** en código, tests, seeds, capturas ni ejemplos → usar solo datos ficticios, como el seed de [04_modelo_de_datos.md](knowledge-base/04_modelo_de_datos.md).
2. **NUNCA commitear directo a `master`** → trabajar en la rama `change/C-NN-<nombre>` y abrir un PR a `master`. Sofia lo revisa y lo mergea.
3. **NUNCA tocar un módulo ajeno sin avisar a su dueño, ni crear una migración con un número no reservado, ni editar una migración ya mergeada** → respetar la tabla de dueños y migraciones de [CHANGES.md](CHANGES.md); para corregir el esquema, crear una migración nueva.
4. **NUNCA subir claves, contraseñas ni credenciales al repo** → usar variables de entorno; `.env.example` solo con nombres y valores de ejemplo.

**Dominio**

5. **NUNCA quitar ni saltear una de las dos garantías contra solapamientos** → la validación en el dominio (con códigos de error claros) **y** las restricciones `EXCLUDE USING gist` de PostgreSQL por sillón y por profesional (DD-03) van siempre juntas.
6. **NUNCA incluir datos de salud en recordatorios, mensajes de WhatsApp, emails ni logs** → solo fecha, hora, consultorio y links de confirmar o cancelar, y solo con consentimiento (RN-NO-01, Ley 25.326).

**Stack**

7. **Cada escenario GIVEN/WHEN/THEN del spec tiene su test**. **NUNCA deshabilitar, borrar ni debilitar un test que falla** → encontrar la causa. Los tests de integración usan PostgreSQL real (no H2, que no soporta `btree_gist`) y llevan `@Tag("integration")`.
8. **NUNCA usar JWT ni desactivar CSRF** → sesión de Spring Security con cookie `HttpOnly`, CSRF activo y acceso denegado por defecto según el rol (DD-10).
9. **NUNCA devolver entidades JPA en la API ni errores de negocio genéricos** → DTOs o records; errores de negocio como `409` con código estable (`SOLAPAMIENTO_PROFESIONAL`, `SOLAPAMIENTO_SILLON`, `MARGEN_INSUFICIENTE`…) en `ProblemDetail`; `422` para validaciones de entrada.
10. **NUNCA usar `any` ni frameworks en `frontend/`** → TypeScript con `strict`, vanilla con Vite y diseño responsivo.

---

## Flujo de Trabajo

```
1. Leer la KB relevante (knowledge-base/)              → entender el dominio
2. Identificar el change en CHANGES.md                 → respetar dependencias y dueño del módulo
3. Acordar alcance y criterios de aceptación           → proposal + specs del change OpenSpec, revisados por la líder
4. Implementar en la rama change/C-NN-<nombre>         → un test por escenario, ./gradlew build en verde
5. Abrir el PR a master y marcar [x] en CHANGES.md     → la líder revisa y mergea
```

Aplicar TODAS las reglas duras en cada paso. Ante conflicto entre la KB y este archivo, las reglas duras prevalecen.
<!-- /active-stack:agent-instruction -->
