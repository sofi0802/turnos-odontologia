# Design

## Context

Ver `proposal.md` (Why) y las specs `catalogo-coberturas` y `afiliaciones-pacientes`. Estado actual que condiciona el diseño:

- No existe el módulo `coberturas/`. Los catálogos de C-04 (`configuracion/`) siguen este patrón: controller por recurso con `GET`, `POST`, `PUT /{id}`, `POST /{id}/activar` y `POST /{id}/desactivar`, sin `DELETE`; `@PreAuthorize("hasRole('ADMIN')")` en cada escritura; DTOs record con Bean Validation; un `@RestControllerAdvice(basePackages = ...)` por módulo que arma el `ProblemDetail` con `title` y la propiedad `codigo`, y traduce `DataIntegrityViolationException` por **nombre de restricción** a un código estable.
- `SecurityConfig` ya deja `/api/v1/paciente/**` solo a `PACIENTE` y el resto de `/api/v1/**` solo al personal. Los endpoints del paciente (`PerfilPacienteController`) usan `@AuthenticationPrincipal UsuarioAutenticado` y su `pacienteId()`, sin ids en el pedido.
- `AgendaConfig` publica los beans `ZoneId` (`America/Argentina/Buenos_Aires`) y `Clock`; los tests de integración usan un reloj ajustable (`IntegracionClinicaBase.RelojAjustable`).
- `paciente` (V1) tiene `id BIGINT`, `nombre`, `apellido` y `dni`; `prestacion` (V1) tiene `id BIGINT`. `btree_gist` ya está instalada en V1 y se usa en las `EXCLUDE` de `turno`.
- Flyway corre con `out-of-order: true`; el seed de desarrollo vive en `db/seed` (`V2_1__seed_dev.sql`, `V5_1__seed_horarios_dev.sql`) y solo lo carga el perfil `dev`. **El seed dev no crea pacientes**.
- Varios tests de integración hacen `TRUNCATE ... paciente, prestacion ... CASCADE`; con las FKs nuevas, ese `CASCADE` también vacía `afiliacion_paciente` y `nomenclador`, lo que es correcto y no rompe esos tests.

## Goals / Non-Goals

**Goals:**
- Módulo `coberturas/` con puertos y adaptadores: `domain` sin imports de Spring ni JPA.
- Las dos garantías contra vigencias superpuestas (dominio y `EXCLUDE USING gist`), igual que DD-03 para los turnos.
- Número de afiliado único por obra social garantizado también en la base, sin triggers.
- Una consulta de "cobertura en una fecha" reutilizable por C-19 y C-20.

**Non-Goals:**
- Pantallas (ficha de frontend aparte). Auditoría de cambios de afiliación (RN-GL-03 es solo de turnos). Cobertura por prestación, copago, liquidación o validación online (C-19 a C-21). Paginación del historial (un paciente tiene pocas afiliaciones).

## Decisions

### D1. Módulo y capas

```
coberturas/
  domain/          TipoCobertura, NumeroAfiliado, Vigencia, Afiliacion, CodigoCobertura, CoberturaException
  application/     ServicioObrasSociales, ServicioPlanes, ServicioNomenclador, ServicioAfiliaciones,
                   ConsultaCobertura; puertos CatalogoCoberturas, Afiliaciones, PacientesExistentes,
                   PrestacionesExistentes; vistas (records)
  infrastructure/  entidades JPA + repositorios Spring Data (escritura), adaptadores JDBC de lectura
                   (historial y cobertura con join a plan y obra_social), CoberturasConfig
  api/             ObrasSocialesController, PlanesController, NomencladorController,
                   AfiliacionesController, CoberturaPacienteController, DTOs, CoberturasExceptionHandler
```

- `Vigencia(desde, hasta?)` valida `hasta ≥ desde` (`VIGENCIA_INVALIDA`), sabe `contiene(fecha)` y `seSuperponeCon(otra)` con días inclusivos; `hasta` nula es abierta.
- `NumeroAfiliado` recorta los extremos y valida `^[A-Za-z0-9./-]{1,30}$` (RN-OS-03).
- Los servicios de aplicación son `@Transactional` y se registran en `CoberturasConfig` (o con `@Service`, según el patrón de `configuracion/`).
- **Alternativa descartada**: poner las afiliaciones en `usuarios/` (que tiene `Paciente`). Se descarta: `coberturas/` es el módulo reservado para C-18 a C-20 y su dueño es el mismo; `paciente` solo se referencia por FK y por un puerto de lectura propio (`PacientesExistentes`, consulta `SELECT 1 FROM paciente WHERE id = ?`), sin tocar `usuarios/` ni `shared/persistence`.

### D2. Migración V12 (`V12__coberturas.sql`)

```sql
CREATE TABLE obra_social (
    id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL CHECK (nombre = btrim(nombre) AND nombre <> ''),
    sigla  VARCHAR(20),
    tipo   VARCHAR(20)  NOT NULL CONSTRAINT obra_social_tipo_valido CHECK (tipo IN ('OBRA_SOCIAL', 'PREPAGA')),
    activa BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX obra_social_nombre_unico ON obra_social (lower(nombre));

CREATE TABLE plan (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    obra_social_id BIGINT      NOT NULL REFERENCES obra_social (id),
    nombre         VARCHAR(80) NOT NULL CHECK (nombre = btrim(nombre) AND nombre <> ''),
    activo         BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT plan_id_obra_social_unico UNIQUE (id, obra_social_id)   -- destino de la FK compuesta (D3)
);
CREATE UNIQUE INDEX plan_nombre_unico ON plan (obra_social_id, lower(nombre));

CREATE TABLE nomenclador (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo        VARCHAR(20)  NOT NULL CHECK (codigo = btrim(codigo) AND codigo <> ''),
    descripcion   VARCHAR(200) NOT NULL,
    prestacion_id BIGINT REFERENCES prestacion (id),
    activo        BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX nomenclador_codigo_unico ON nomenclador (upper(codigo));
CREATE INDEX nomenclador_prestacion_idx ON nomenclador (prestacion_id);

CREATE TABLE afiliacion_paciente (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paciente_id     BIGINT      NOT NULL REFERENCES paciente (id),
    plan_id         BIGINT      NOT NULL,
    obra_social_id  BIGINT      NOT NULL,
    numero_afiliado VARCHAR(30) NOT NULL,
    vigente_desde   DATE        NOT NULL,
    vigente_hasta   DATE,
    CONSTRAINT afiliacion_plan_fk FOREIGN KEY (plan_id, obra_social_id) REFERENCES plan (id, obra_social_id),
    CONSTRAINT afiliacion_numero_valido CHECK (numero_afiliado ~ '^[A-Za-z0-9./-]{1,30}$'),
    CONSTRAINT afiliacion_vigencia_valida CHECK (vigente_hasta IS NULL OR vigente_hasta >= vigente_desde),
    CONSTRAINT afiliacion_sin_superposicion EXCLUDE USING gist (
        paciente_id WITH =,
        daterange(vigente_desde, vigente_hasta, '[]') WITH &&),
    CONSTRAINT afiliacion_numero_unico_por_obra_social EXCLUDE USING gist (
        obra_social_id WITH =,
        upper(numero_afiliado) WITH =,
        paciente_id WITH <>)
);
CREATE INDEX afiliacion_plan_idx ON afiliacion_paciente (plan_id, obra_social_id);
```

- Sin `ON DELETE`: nada de esto se borra (RN-OS-01, RN-OS-02); el `RESTRICT` por defecto protege el historial.
- `daterange(desde, NULL, '[]')` es un rango abierto hacia arriba, así que una afiliación sin fin choca con cualquier otra posterior. El índice GiST de la `EXCLUDE` de superposición también sirve para buscar la cobertura vigente por paciente.
- `VARCHAR(n)` y no `TEXT + CHECK`: sigue la convención de V1 a V22 y los largos de la KB.
- **Constraint de nombres**: los nombres de arriba son los que `CoberturasExceptionHandler` traduce (D6). Un índice único se informa con su nombre como `constraintName`, igual que las `EXCLUDE`.

### D3. Número de afiliado único por obra social: columna desnormalizada + FK compuesta + `EXCLUDE`

RN-OS-03 pide unicidad por **obra social**, pero la afiliación apunta al **plan**. Se agrega `obra_social_id` a `afiliacion_paciente` y una FK compuesta `(plan_id, obra_social_id) → plan (id, obra_social_id)`, que obliga a que la columna copiada sea siempre la obra social real del plan. La KB dice que un plan no cambia de obra social, así que la copia nunca queda desactualizada (y la API no permite cambiarla, D5).

La unicidad es entre **pacientes distintos**: `EXCLUDE USING gist (obra_social_id WITH =, upper(numero_afiliado) WITH =, paciente_id WITH <>)`. Un `UNIQUE (obra_social_id, numero_afiliado)` simple sería más corto, pero impediría que el mismo paciente conserve su número al cambiar de plan dentro de la misma obra social (por ejemplo, de "Plan Base" a "Plan Integral" con `OSE-000001`), que es lo normal y está en la spec.

**Alternativas descartadas**:
1. *Trigger* que busque el número en otras afiliaciones de la misma obra social: no es declarativo, tiene carrera entre transacciones concurrentes salvo con bloqueos explícitos, y su error no trae un nombre de restricción para traducir a `AFILIADO_DUPLICADO`.
2. `UNIQUE (obra_social_id, numero_afiliado)`: ver arriba.
3. Guardar la obra social en lugar del plan: pierde el plan, que C-19 necesita.

### D4. Garantías contra superposición y duplicados (dominio + base)

`ServicioAfiliaciones.afiliar` / `corregir`:
1. Valida `NumeroAfiliado` y `Vigencia` (422).
2. Carga paciente (404) y plan con su obra social (404); si el plan cambia o es alta, exige plan activo (`PLAN_INACTIVO`) y obra social activa (`OBRA_SOCIAL_INACTIVA`).
3. Lee las afiliaciones del paciente y verifica en el dominio que la nueva vigencia no se superpone con ninguna otra (excluyendo la propia al corregir) → `VIGENCIA_SUPERPUESTA`.
4. Verifica en el puerto que no exista el número en la obra social para otro paciente → `AFILIADO_DUPLICADO`.
5. Guarda con `saveAndFlush`, para que la violación de la `EXCLUDE` salte dentro del servicio. Dos pedidos concurrentes que pasen el paso 3 los frena la base: el handler traduce `afiliacion_sin_superposicion` a `409 VIGENCIA_SUPERPUESTA` y `afiliacion_numero_unico_por_obra_social` a `409 AFILIADO_DUPLICADO`.

Lo mismo para el catálogo: verificación previa (`OBRA_SOCIAL_EXISTENTE`, `PLAN_EXISTENTE`, `CODIGO_NOMENCLADOR_EXISTENTE`) y los índices únicos como red (`obra_social_nombre_unico`, `plan_nombre_unico`, `nomenclador_codigo_unico`).

### D5. Endpoints

| Método y ruta | Roles | Respuesta |
|---|---|---|
| `GET /api/v1/obras-sociales` | personal | lista por nombre, con planes |
| `GET /api/v1/obras-sociales/{id}` | personal | obra social con planes |
| `POST /api/v1/obras-sociales` · `PUT /{id}` | ADMIN | `201` / `200` |
| `POST /api/v1/obras-sociales/{id}/activar` · `/desactivar` | ADMIN | `200` |
| `GET /api/v1/obras-sociales/{id}/planes` · `GET .../planes/{planId}` | personal | planes de esa obra social |
| `POST /api/v1/obras-sociales/{id}/planes` · `PUT .../planes/{planId}` | ADMIN | `201` / `200` |
| `POST /api/v1/obras-sociales/{id}/planes/{planId}/activar` · `/desactivar` | ADMIN | `200` |
| `GET /api/v1/nomenclador` · `GET /{id}` | personal | lista por código |
| `POST /api/v1/nomenclador` · `PUT /{id}` · `POST /{id}/activar` · `/desactivar` | ADMIN | `201` / `200` |
| `GET /api/v1/pacientes/{id}/afiliaciones` | personal | historial, más reciente primero, con `vigente` |
| `POST /api/v1/pacientes/{id}/afiliaciones` | ADMIN, RECEPCIONISTA | `201` + `Location` |
| `PUT /api/v1/pacientes/{id}/afiliaciones/{afiliacionId}` | ADMIN, RECEPCIONISTA | corrige plan, número y `vigenteDesde` |
| `POST /api/v1/pacientes/{id}/afiliaciones/{afiliacionId}/cerrar` | ADMIN, RECEPCIONISTA | body `{ "vigenteHasta": "2026-11-30" }` |
| `GET /api/v1/pacientes/{id}/cobertura?fecha=AAAA-MM-DD` | personal | vigente en la fecha (por defecto hoy) o particular |
| `GET /api/v1/paciente/cobertura` | PACIENTE (sesión) | vigente hoy o particular |

- "personal" = `hasAnyRole('ADMIN','RECEPCIONISTA','ODONTOLOGO')`; ya lo impone `SecurityConfig` para `/api/v1/**`, pero las escrituras llevan su `@PreAuthorize` explícito, como en C-04. **`SecurityConfig` no cambia.**
- Un plan o una afiliación se buscan siempre **junto con su padre** de la ruta (`plan.id = ? AND obra_social_id = ?`, `afiliacion.id = ? AND paciente_id = ?`); si no coinciden, `404`. Así no se puede mover un plan de obra social ni tocar la afiliación de otro paciente por la ruta.
- Respuesta de cobertura: `{ "particular": false, "afiliacion": { "id", "obraSocial": { "id", "nombre", "sigla", "tipo" }, "plan": { "id", "nombre" }, "numeroAfiliado", "vigenteDesde", "vigenteHasta" } }` o `{ "particular": true, "afiliacion": null }`. Fechas como `LocalDate` ISO; no hay instantes, así que no hace falta convertir zonas en la respuesta.
- `cerrar` es una acción y no un `PUT` de `vigenteHasta`, para que "cambiar de cobertura = cerrar + crear" (PA-20) quede explícito y `AFILIACION_CERRADA` proteja el historial.

### D6. Errores

Enum `CodigoCobertura` y `CoberturasExceptionHandler` con `basePackages = "ar.turnos.odontologia.coberturas"`, mismo formato que `ConfiguracionExceptionHandler` (`title` y `codigo`):

| Código | Estado |
|---|---|
| `OBRA_SOCIAL_EXISTENTE`, `PLAN_EXISTENTE`, `CODIGO_NOMENCLADOR_EXISTENTE`, `VIGENCIA_SUPERPUESTA`, `AFILIADO_DUPLICADO`, `PLAN_INACTIVO`, `OBRA_SOCIAL_INACTIVA`, `AFILIACION_CERRADA` | `409` |
| `RECURSO_NO_ENCONTRADO` | `404` |
| `DATOS_INVALIDOS` (con `errores[].campo`, sin el valor), `VIGENCIA_INVALIDA`, `PRESTACION_INEXISTENTE` | `422` |

- `PLAN_INACTIVO` y `OBRA_SOCIAL_INACTIVA` van con `409` (el pedido es válido, choca con el estado del catálogo), como pidió la definición del change; `configuracion/` usa `422` para `PRESTACION_INACTIVA`. Ver Open Questions.
- Restricciones traducidas por nombre: `obra_social_nombre_unico`, `plan_nombre_unico`, `nomenclador_codigo_unico`, `afiliacion_sin_superposicion`, `afiliacion_numero_unico_por_obra_social`, `afiliacion_vigencia_valida` (→ `VIGENCIA_INVALIDA`), `afiliacion_numero_valido` (→ `DATOS_INVALIDOS`). Una restricción desconocida se relanza (termina en `500 ERROR_INTERNO`).
- El `detail` de los errores **nunca** incluye el número de afiliado, el nombre del paciente ni el texto de la restricción violada con valores (RN-OS-07): mensajes fijos por código.

### D7. "Hoy" y privacidad

- `ConsultaCobertura` recibe `Clock` y `ZoneId` (beans de `AgendaConfig`) y calcula `LocalDate.now(reloj.withZone(zona))`. Los tests usan el reloj ajustable para el borde de medianoche (spec: "Hoy se calcula en la zona de Buenos Aires").
- El módulo no escribe logs con datos de cobertura; `notificaciones/` no lee las tablas nuevas, así que los recordatorios no cambian (regla dura 6). Un test lo comprueba armando un recordatorio de un paciente afiliado.

### D8. Seed de desarrollo `db/seed/V12_1__seed_coberturas_dev.sql`

- Obras sociales, planes y nomenclador del seed de la KB. `N-0101` se vincula a "Consulta o control" y `N-0201` a "Limpieza (profilaxis)" buscando la prestación por nombre (`INSERT ... SELECT`); `N-0301` queda sin vínculo, como lo enumera la KB.
- Como el seed dev no crea pacientes, se insertan "Paciente Uno" (`99000001`) y "Paciente Dos" (`99000002`) con `ON CONFLICT (dni) DO NOTHING`, y una afiliación de `99000001` a "Plan Base" con `OSE-000001` desde 2026-01-01; `99000002` queda particular. **(a confirmar)** — ver Open Questions.
- Se aplica con out-of-order después de V22 en las bases que ya existen.

## Risks / Trade-offs

- [`obra_social_id` duplicado en `afiliacion_paciente`] → la FK compuesta impide que difiera del plan y la API no permite mover un plan de obra social; si algún día se permitiera, la FK lo rechazaría en lugar de corromper datos.
- [`EXCLUDE` con `<>` sobre `paciente_id` es menos conocida que un `UNIQUE`] → comentario en la migración y un test de migración que la ejercita (mismo paciente sí, otro paciente no).
- [V12 se aplica fuera de orden en bases dev con V16 a V22] → ya pasó con V16; `out-of-order: true` está en dev y test. En producción no hay datos todavía.
- [Los `TRUNCATE ... CASCADE` de otros módulos vacían las tablas nuevas] → es lo esperado; los tests de coberturas preparan sus datos en su propia base (`IntegracionCoberturasBase`).
- [Insertar pacientes en el seed de coberturas] → solo perfil dev, datos ficticios, `ON CONFLICT DO NOTHING`; si la líder prefiere no hacerlo, la afiliación del seed se condiciona a que exista el paciente y se pierde el ejemplo "con cobertura" en una base nueva.
- [`nomenclador.prestacion_id` referencia una tabla de `configuracion/` (Emanuel)] → solo FK, sin cambios en su módulo; se le avisa en el PR.
- [`PUT` de afiliación permite cambiar el plan] → corrige errores de carga sin dejar un día de historial falso; el cambio real de cobertura se documenta como "cerrar + crear".

## Migration Plan

1. Mergear el PR con `V12__coberturas.sql` y `V12_1__seed_coberturas_dev.sql`; Flyway aplica V12 out-of-order al arrancar.
2. Rollback: es un change aditivo (tablas nuevas, ningún endpoint existente cambia). Si hay que revertir antes de producción, se revierte el PR y se limpia la base dev (`DROP TABLE afiliacion_paciente, nomenclador, plan, obra_social` y borrar las filas 12 y 12.1 de `flyway_schema_history`); nunca se edita V12 una vez mergeada (regla dura 3).

## Open Questions

Las decisiones marcadas **(a confirmar)** no cambian las specs ni las tareas; tienen recomendación y se confirman con la líder en la revisión del propose:

1. **(a confirmar)** Seed: insertar "Paciente Uno" y "Paciente Dos" con `ON CONFLICT DO NOTHING` en `V12_1`. *Recomendación*: sí, para tener en dev un paciente con cobertura y otro particular como pide el seed de la KB.
2. **(a confirmar)** `PLAN_INACTIVO` y `OBRA_SOCIAL_INACTIVA` con `409` (como pide el change) o `422` (como `PRESTACION_INACTIVA` de C-04). *Recomendación*: `409`, que es un conflicto con el estado del catálogo.
3. **(a confirmar)** `GET /api/v1/paciente/cobertura` devuelve solo la vigente hoy, sin historial. *Recomendación*: solo la vigente, que es lo que pide US-046 CA-6; el historial queda para el personal.
4. **(a confirmar)** Unicidad sin distinguir mayúsculas para el número de afiliado y el código del nomenclador (`upper(...)`), guardándolos como se escribieron. *Recomendación*: sí, evita `OSE-000001` y `ose-000001` como afiliados distintos.
5. **(a confirmar)** Una misma prestación puede tener varios códigos del nomenclador (la KB lo permite: `Nomenclador *—0..1 Prestacion`). *Recomendación*: dejarlo así en C-18 y que C-19 decida si necesita uno solo activo por prestación (sería una migración V13).
6. **(a confirmar)** `PUT` de afiliación permite corregir el plan (no solo el número). *Recomendación*: sí, solo como corrección de carga y con las mismas validaciones.

## UI Mockups

- Design system revision: no aplica (sin UI).
- Decision: no (no UI) — 2026-11-24. C-18 es solo backend por decisión del usuario; las pantallas (pestaña de Configuración, cobertura en el registro del paciente y "Mi cuenta") van en una ficha de frontend posterior, que sí pasa por el gate de mockups (`system-and-mockups`).
- Mockups: —
- Approval: no aplica.
- Preview: no aplica.
- Visual verify: no aplica.
