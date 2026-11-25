# Design

## Context

Ver `proposal.md` (Why) y las specs `aranceles-y-cobertura-planes` y `presupuestos`. Estado actual que condiciona el diseño:

- El módulo `coberturas/` (C-18, dueño Juan Sebastián) ya tiene: `domain` sin Spring ni JPA (`CodigoCobertura` con su `Estado`, `CoberturaException`), servicios `@Service` en `application`, entidades JPA y adaptadores JDBC de lectura en `infrastructure` (`PacientesExistentesJdbc`, `PrestacionesExistentesJdbc`, `LecturaCoberturasJdbc`), y `CoberturasExceptionHandler` (`@RestControllerAdvice(basePackages = "ar.turnos.odontologia.coberturas")`) que arma el `ProblemDetail` con `title` y `codigo` y traduce `DataIntegrityViolationException` por nombre de restricción.
- `ConsultaCobertura` ya resuelve la afiliación vigente de un paciente en una fecha (o particular), con los beans `Clock` y `ZoneId` (`America/Argentina/Buenos_Aires`) de `AgendaConfig`. Los tests usan un reloj ajustable (`ConRelojAjustable`, `IntegracionCoberturasBase`).
- V12: `plan (id, obra_social_id)`, `nomenclador (id, codigo, descripcion, prestacion_id NULL, activo)` con índice único `upper(codigo)`, `afiliacion_paciente (id, paciente_id, plan_id, obra_social_id, numero_afiliado, vigente_desde, vigente_hasta)`. Una prestación puede tener varios códigos (decisión 5 de C-18). El `PUT` del nomenclador puede cambiar su `prestacion_id`, y el `PUT` de afiliación puede corregir el plan.
- V1: `prestacion (id, nombre VARCHAR(100) UNIQUE, duracion_minutos, activo)` (de `configuracion/`, Emanuel) y `profesional (id, nombre, apellido, matricula, activo)`. V3: un `ODONTOLOGO` siempre tiene `profesional_id` (`usuario_odontologo_con_profesional`), que `UsuarioAutenticado.profesionalId()` expone.
- V8 (`clinica/`, Emanuel): `odontograma_registro (paciente_id, pieza, cara NULL, estado, registrado_en)` de solo agregado, con los `CHECK` FDI (`odontograma_pieza_fdi`, `odontograma_cara_valida`, `_lingual`, `_palatina`) y el índice `(paciente_id, pieza, cara, registrado_en DESC)`. El estado vigente se lee con `DISTINCT ON (pieza, cara)` (`RegistroClinicoJpa`).
- `application.yml` ya apaga `org.hibernate.engine.jdbc.spi.SqlExceptionHelper` (hallazgo de C-18) para que el `Detail` de PostgreSQL no vuelque valores de filas en los logs.
- El seed dev crea las cuatro prestaciones (V2_1) y V12_1 crea coberturas, "Paciente Uno" con "Plan Base" y "Paciente Dos" particular; `N-0301` quedó **sin** prestación vinculada.

## Goals / Non-Goals

**Goals:**
- Un cálculo de cobertura y copago único, en el dominio, con `BigDecimal` exacto y probado con tests unitarios.
- La foto del presupuesto (DD-15) garantizada en el dominio **y** en la base (`CHECK` de cálculo y triggers de inmutabilidad), igual que las garantías dobles del resto del proyecto.
- Leer el odontograma, las prestaciones y los profesionales sin modificar módulos ajenos.
- Una sola vista de presupuesto en `application`; la API decide por rol qué campos expone.

**Non-Goals:**
- Pantallas (ficha de frontend aparte). Paginación (pocos presupuestos por paciente). Auditoría de quién aceptó o rechazó (la KB no la pide; ver Open Questions). Recalcular, renovar o duplicar un presupuesto. Cobros (C-22) y liquidación (C-20).

## Decisions

### D1. Módulo y capas

```
coberturas/
  domain/          Precio, Porcentaje, PiezaCara, CalculoItem (cálculo puro), Presupuesto (agregado),
                   EstadoPresupuesto; CodigoCobertura ampliado
  application/     ServicioAranceles, ServicioCoberturaPlan, ServicioPresupuestos, SugerenciasPresupuesto;
                   puertos Aranceles, CoberturasPlan, Presupuestos, LecturaPresupuestos, OdontogramaVigente,
                   ProfesionalesExistentes, PrestacionesPresupuestables; vistas (records)
  infrastructure/  ArancelesJdbc y CoberturasPlanJdbc (upsert), PresupuestoEntity + PresupuestoItemEntity
                   (JPA, @Immutable), PresupuestosJdbc (cambio de estado y lecturas), OdontogramaVigenteJdbc,
                   ProfesionalesExistentesJdbc, PrestacionesPresupuestablesJdbc
  api/             ArancelesController, CoberturaPlanController, PresupuestosController,
                   PresupuestosPacienteController, DTOs; CoberturasExceptionHandler ampliado
```

- Se amplía `coberturas/` y no se crea un módulo `presupuestos/`: CHANGES.md reserva `coberturas/` para C-18 a C-20, con el mismo dueño, y los presupuestos dependen de la afiliación vigente y del nomenclador.
- `PiezaCara` copia las reglas FDI de V8 en el dominio de `coberturas`. **Alternativa descartada**: importar `clinica.domain.PiezaDental` y `CaraDental`, que acopla a un módulo ajeno (Emanuel) y lo obligaría a mantener la compatibilidad. Un test de migración inserta las mismas piezas y caras en `odontograma_registro` y en `presupuesto_item` para detectar si las reglas se separan.

### D2. Migración V13 (`V13__presupuestos.sql`)

```sql
CREATE TABLE arancel (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    prestacion_id  BIGINT        NOT NULL REFERENCES prestacion (id),
    precio         NUMERIC(12,2) NOT NULL CONSTRAINT arancel_precio_positivo CHECK (precio > 0),
    actualizado_en TIMESTAMPTZ   NOT NULL,
    CONSTRAINT arancel_prestacion_unica UNIQUE (prestacion_id)
);

CREATE TABLE cobertura_plan (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id        BIGINT   NOT NULL REFERENCES plan (id),
    nomenclador_id BIGINT   NOT NULL REFERENCES nomenclador (id),
    porcentaje     SMALLINT NOT NULL CONSTRAINT cobertura_plan_porcentaje_valido CHECK (porcentaje BETWEEN 0 AND 100),
    CONSTRAINT cobertura_plan_unica UNIQUE (plan_id, nomenclador_id)
);
CREATE INDEX cobertura_plan_nomenclador_idx ON cobertura_plan (nomenclador_id);

-- Destino de la FK compuesta: la afiliación del presupuesto es siempre del mismo paciente (D4).
ALTER TABLE afiliacion_paciente ADD CONSTRAINT afiliacion_id_paciente_unico UNIQUE (id, paciente_id);

CREATE TABLE presupuesto (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paciente_id        BIGINT        NOT NULL REFERENCES paciente (id),
    profesional_id     BIGINT        NOT NULL REFERENCES profesional (id),
    fecha              DATE          NOT NULL,
    vence_el           DATE          NOT NULL,
    afiliacion_id      BIGINT,
    obra_social_nombre VARCHAR(120),            -- foto (D4)
    plan_nombre        VARCHAR(80),
    numero_afiliado    VARCHAR(30),
    estado             VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
    total_precio       NUMERIC(12,2) NOT NULL,
    total_cobertura    NUMERIC(12,2) NOT NULL,
    total_copago       NUMERIC(12,2) NOT NULL,
    creado_en          TIMESTAMPTZ   NOT NULL,
    CONSTRAINT presupuesto_afiliacion_fk FOREIGN KEY (afiliacion_id, paciente_id)
        REFERENCES afiliacion_paciente (id, paciente_id),
    CONSTRAINT presupuesto_estado_valido CHECK (estado IN ('PENDIENTE', 'ACEPTADO', 'RECHAZADO')),
    CONSTRAINT presupuesto_vencimiento CHECK (vence_el = fecha + 30),
    CONSTRAINT presupuesto_totales CHECK (total_precio > 0 AND total_cobertura >= 0 AND total_copago >= 0
        AND total_precio = total_cobertura + total_copago),
    CONSTRAINT presupuesto_foto_cobertura CHECK (
        (afiliacion_id IS NULL AND obra_social_nombre IS NULL AND plan_nombre IS NULL AND numero_afiliado IS NULL)
        OR (afiliacion_id IS NOT NULL AND obra_social_nombre IS NOT NULL AND plan_nombre IS NOT NULL
            AND numero_afiliado IS NOT NULL))
);
CREATE INDEX presupuesto_paciente_idx ON presupuesto (paciente_id, fecha DESC, id DESC);
CREATE INDEX presupuesto_profesional_idx ON presupuesto (profesional_id);
CREATE INDEX presupuesto_afiliacion_idx ON presupuesto (afiliacion_id, paciente_id);

CREATE TABLE presupuesto_item (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    presupuesto_id       BIGINT        NOT NULL REFERENCES presupuesto (id),
    prestacion_id        BIGINT        NOT NULL REFERENCES prestacion (id),
    prestacion_nombre    VARCHAR(100)  NOT NULL,   -- foto (D4)
    nomenclador_id       BIGINT        REFERENCES nomenclador (id),
    codigo_nomenclador   VARCHAR(20),              -- foto (D4)
    pieza                SMALLINT,
    cara                 VARCHAR(1),
    cantidad             INTEGER       NOT NULL CONSTRAINT presupuesto_item_cantidad CHECK (cantidad BETWEEN 1 AND 99),
    precio_unitario      NUMERIC(12,2) NOT NULL CHECK (precio_unitario > 0),
    porcentaje_cobertura SMALLINT      NOT NULL CHECK (porcentaje_cobertura BETWEEN 0 AND 100),
    importe              NUMERIC(12,2) NOT NULL,
    cobertura            NUMERIC(12,2) NOT NULL,
    copago               NUMERIC(12,2) NOT NULL,
    CONSTRAINT presupuesto_item_importe CHECK (importe = precio_unitario * cantidad),
    -- round() de numeric redondea 0,5 alejándose de cero: con importes positivos es HALF_UP (RN-PR-03).
    CONSTRAINT presupuesto_item_cobertura CHECK (cobertura = round(importe * porcentaje_cobertura / 100.0, 2)),
    CONSTRAINT presupuesto_item_copago CHECK (copago >= 0 AND importe = cobertura + copago),
    CONSTRAINT presupuesto_item_codigo CHECK ((nomenclador_id IS NULL) = (codigo_nomenclador IS NULL)
        AND (nomenclador_id IS NOT NULL OR porcentaje_cobertura = 0)),
    -- Mismas reglas FDI que odontograma_registro (V8, RN-CL-03); la cara exige pieza.
    CONSTRAINT presupuesto_item_pieza_fdi CHECK (pieza IS NULL OR
        pieza BETWEEN 11 AND 18 OR pieza BETWEEN 21 AND 28 OR pieza BETWEEN 31 AND 38 OR pieza BETWEEN 41 AND 48
        OR pieza BETWEEN 51 AND 55 OR pieza BETWEEN 61 AND 65 OR pieza BETWEEN 71 AND 75 OR pieza BETWEEN 81 AND 85),
    CONSTRAINT presupuesto_item_cara_valida CHECK (cara IN ('O', 'M', 'D', 'V', 'L', 'P')),
    CONSTRAINT presupuesto_item_cara_con_pieza CHECK (cara IS NULL OR pieza IS NOT NULL),
    CONSTRAINT presupuesto_item_cara_lingual CHECK (cara <> 'L' OR pieza / 10 IN (3, 4, 7, 8)),
    CONSTRAINT presupuesto_item_cara_palatina CHECK (cara <> 'P' OR pieza / 10 IN (1, 2, 5, 6))
);
CREATE INDEX presupuesto_item_presupuesto_idx ON presupuesto_item (presupuesto_id);
CREATE INDEX presupuesto_item_prestacion_idx ON presupuesto_item (prestacion_id);
CREATE INDEX presupuesto_item_nomenclador_idx ON presupuesto_item (nomenclador_id);
```

Más los triggers de D5. Notas:
- `NUMERIC(12,2)` y `VARCHAR(n)` siguen la KB y la convención de V1 a V22. Sin `ON DELETE`: nada se borra y el `RESTRICT` por defecto protege la foto.
- `porcentaje` como `SMALLINT`, como dice la KB. `vence_el` se guarda (no es columna generada) porque la KB lo modela así y el `CHECK` impide que difiera.
- **No** hay FK compuesta `(nomenclador_id, prestacion_id) → nomenclador`: el `PUT` de C-18 puede cambiar la prestación de un código y esa FK lo bloquearía para siempre. El vínculo se valida en el dominio al emitir y la foto guarda el código mostrado.
- La suma de los ítems contra los totales del presupuesto no es un `CHECK` (cruza tablas): la garantiza el agregado `Presupuesto` y un test de integración lee ambos.

### D3. Dinero y cálculo (RN-PR-03)

- Todo importe es `BigDecimal` con escala 2. `Precio` valida `> 0`, escala ≤ 2 y ≤ 9999999999.99 (`DATOS_INVALIDOS`, campo `precio`); nunca `double`.
- `CalculoItem.de(precioUnitario, cantidad, porcentaje)`: `importe = precio × cantidad` (escala 2 exacta); `cobertura = importe × porcentaje / 100` con `divide(CIEN, 2, RoundingMode.HALF_UP)`; `copago = importe − cobertura`. Tests unitarios: 100 %, 0 %, 50 % de 25000.15 → 12500.08 / 12500.07, 80 % con cantidad 2, y que `cobertura + copago = importe` siempre.
- Los totales son la suma de los ítems; si algún importe o total supera `NUMERIC(12,2)`, `DATOS_INVALIDOS` (campo `items`). Con cantidad ≤ 99 solo pasa con precios irreales.
- JSON: los importes salen como números con dos decimales (`15000.00`). El porcentaje de entrada se recibe como `BigDecimal` y se valida con `@Digits(fraction = 0)`, `@Min(0)`, `@Max(100)`: con `Integer`, Jackson convierte `50.5` en `50` en silencio (`ACCEPT_FLOAT_AS_INT` está activo por defecto) y no queremos cambiar la configuración global de Jackson.

### D4. Emitir un presupuesto (`ServicioPresupuestos.emitir`, una transacción)

1. Valida la forma del pedido (Bean Validation: 1 a 50 ítems, cantidad 1 a 99) y `PiezaCara` de cada ítem (`DATOS_INVALIDOS` con `items[i].pieza` o `items[i].cara`, sin el valor).
2. Paciente existente (`404`). Profesional = `UsuarioAutenticado.profesionalId()`; el body no tiene ese campo y Jackson ignora uno que llegue.
3. `hoy = LocalDate.now(reloj.withZone(zona))`; `vence_el = hoy + 30`. La cobertura sale de `LecturaCoberturas.vigenteEn(paciente, hoy)` (la consulta de C-18).
4. Por ítem, con un puerto `PrestacionesPresupuestables` que trae en una consulta la prestación (nombre, activa), su arancel y sus códigos activos: inexistente → `422 PRESTACION_INEXISTENTE`; inactiva → `409 PRESTACION_INACTIVA`; sin arancel → `409 ARANCEL_INEXISTENTE`. Código: uno solo activo → ese (si el ítem indica otro, `CODIGO_NOMENCLADOR_INVALIDO`); varios → el ítem debe indicar uno de ellos (`CODIGO_NOMENCLADOR_REQUERIDO` / `_INVALIDO`); ninguno → particular, y si el ítem indica uno, `_INVALIDO`.
5. Porcentaje: particular → 0; si no, `cobertura_plan` del plan de la afiliación y el código, o 0 sin fila. La vigencia de la afiliación no depende de que el plan siga activo (igual que `ConsultaCobertura`).
6. Arma el agregado y lo guarda con JPA (`saveAndFlush`, ítems en cascada). Errores evaluados en orden de ítem: el primero que falla responde y no se guarda nada.

**Foto (DD-15, RN-PR-04)**: además de lo que pide la KB (precio, porcentaje, importes y `afiliacion_id`), se guardan `obra_social_nombre`, `plan_nombre`, `numero_afiliado`, `prestacion_nombre` y `codigo_nomenclador`. Motivo: C-18 permite corregir el plan de una afiliación, renombrar un plan o una prestación y cambiar un código; leerlos por join haría que el presupuesto emitido "cambie". La FK compuesta `(afiliacion_id, paciente_id)` impide asociar la afiliación de otro paciente. (decisión 1, aceptada).

### D5. Estados, vencimiento e inmutabilidad

- `EstadoPresupuesto { PENDIENTE, ACEPTADO, RECHAZADO }`. "Vencido" se calcula: `estado == PENDIENTE && vence_el < hoy` (Buenos Aires). La respuesta lleva `estado` (el guardado) y `vencido` (boolean). El día de `vence_el` todavía se puede aceptar.
- Aceptar y rechazar sin carrera, con un `UPDATE` condicional por JDBC:
  `UPDATE presupuesto SET estado = ? WHERE id = ? AND paciente_id = ? AND estado = 'PENDIENTE' AND (? = 'RECHAZADO' OR vence_el >= ?)`.
  Si actualiza 0 filas, se relee para responder `404` (no existe o es de otro paciente), `409 PRESUPUESTO_CERRADO` (ya no está pendiente) o `409 PRESUPUESTO_VENCIDO`. Con dos pedidos a la vez, PostgreSQL serializa las dos actualizaciones de la fila y el segundo ve `estado <> 'PENDIENTE'`. No hace falta el reintento por deadlock de C-18 (no hay `EXCLUDE`).
  **Alternativa descartada**: leer la entidad, cambiarla y guardar con `@Version`: más código y un `409` por `OptimisticLockException` que habría que traducir aparte.
- Cualquier `ODONTOLOGO` acepta o rechaza, no solo el autor (como la ficha clínica, que escribe cualquier odontólogo). (decisión 4, aceptada).
- **Inmutabilidad en la base** (como `odontograma_solo_agregado` de V8): trigger `presupuesto_item_solo_agregado` (BEFORE UPDATE OR DELETE → error) y trigger `presupuesto_solo_estado` (BEFORE DELETE → error; BEFORE UPDATE → error si cambia otra columna que `estado`, si `OLD.estado <> 'PENDIENTE'` o si `NEW.estado` no es `ACEPTADO` ni `RECHAZADO`). Los mensajes de los triggers son fijos y no incluyen valores. `TRUNCATE` (tests) no dispara triggers de fila. (decisión 5, aceptada).
- Las entidades JPA son `@Immutable` (solo insertan); el estado cambia solo por el `UPDATE` de arriba.

### D6. Aranceles y cobertura por plan

- `PUT /api/v1/aranceles/{prestacionId}` y `PUT .../coberturas/{nomencladorId}` son **upsert** idempotentes por JDBC (`INSERT ... ON CONFLICT (prestacion_id) DO UPDATE` y `ON CONFLICT (plan_id, nomenclador_id) DO UPDATE`): dos pedidos simultáneos no chocan con la restricción única y no hace falta un código `COBERTURA_EXISTENTE`. `actualizado_en` sale del `Clock`. (decisión 6, aceptada).
- Se puede cargar el precio de una prestación inactiva y la cobertura de un plan o un código inactivos (es configuración); lo que se rechaza es **presupuestar** una prestación inactiva o con un código inactivo.
- El plan se busca junto con su obra social de la ruta (`404` si no coinciden), como en C-18.

### D7. Endpoints

| Método y ruta | Roles | Respuesta |
|---|---|---|
| `GET /api/v1/aranceles` | personal | todas las prestaciones por nombre, con `precio` o `null` y `actualizadoEn` |
| `PUT /api/v1/aranceles/{prestacionId}` body `{ "precio": 15000.00 }` | ADMIN | `200` |
| `GET /api/v1/obras-sociales/{id}/planes/{planId}/coberturas` | personal | cobertura cargada por código |
| `PUT /api/v1/obras-sociales/{id}/planes/{planId}/coberturas/{nomencladorId}` body `{ "porcentaje": 50 }` | ADMIN | `200` |
| `GET /api/v1/pacientes/{id}/presupuestos` | personal | lista, más reciente primero (recepción sin pieza ni cara) |
| `GET /api/v1/pacientes/{id}/presupuestos/{presupuestoId}` | personal | detalle (recepción sin pieza ni cara) |
| `POST /api/v1/pacientes/{id}/presupuestos` | ODONTOLOGO | `201` + `Location` |
| `POST /api/v1/pacientes/{id}/presupuestos/{presupuestoId}/aceptar` · `/rechazar` | ODONTOLOGO | `200` |
| `GET /api/v1/pacientes/{id}/presupuestos/sugerencias` | ODONTOLOGO | `[{ pieza, cara, estado }]` |
| `GET /api/v1/paciente/presupuestos` · `GET /api/v1/paciente/presupuestos/{presupuestoId}` | PACIENTE (sesión) | solo los propios, completos |

- "personal" = `hasAnyRole('ADMIN','RECEPCIONISTA','ODONTOLOGO')` explícito en cada método. **`SecurityConfig` no cambia**: ya deja `/api/v1/paciente/**` a `PACIENTE` y el resto de `/api/v1/**` al personal.
- `sugerencias` es un literal y `{presupuestoId}` un `Long`; Spring MVC prefiere el patrón literal.
- Pedido de emisión: `{ "items": [ { "prestacionId", "nomencladorId"?, "pieza"?, "cara"?, "cantidad" } ] }`.
- Respuesta: `{ id, fecha, venceEl, estado, vencido, profesional { id, nombre }, particular, cobertura { afiliacionId, obraSocial, plan, numeroAfiliado } | null, items [ { id, prestacion { id, nombre }, codigoNomenclador, pieza, cara, cantidad, precioUnitario, porcentajeCobertura, importe, cobertura, copago } ], totales { precio, cobertura, copago }, creadoEn }`.
- **Vista de recepción**: records propios (`PresupuestoRecepcionRespuesta`, `ItemRecepcionRespuesta`) **sin** los componentes `pieza` y `cara`, no los mismos con `null`: así el campo no existe en el JSON y no depende de la configuración de Jackson. El controller elige el mapeo con `Authentication` (rol `RECEPCIONISTA`); el servicio devuelve siempre la vista completa.
- El paciente ve sus presupuestos completos, con pieza y cara (son sus datos, PA-27); `PresupuestosPacienteController` usa `@AuthenticationPrincipal UsuarioAutenticado` y busca `id = ? AND paciente_id = sesión`, sin `@PathVariable` de paciente. (decisión 7, aceptada).

### D8. Sugerencias desde el odontograma

Puerto `OdontogramaVigente` con adaptador `OdontogramaVigenteJdbc` en `coberturas/infrastructure`, de solo lectura:

```sql
SELECT pieza, cara, estado FROM (
    SELECT DISTINCT ON (pieza, cara) pieza, cara, estado
    FROM odontograma_registro WHERE paciente_id = ?
    ORDER BY pieza, cara, registrado_en DESC, id DESC) vigente
WHERE estado IN ('A_TRATAR', 'CARIES')
ORDER BY pieza, cara NULLS FIRST
```

Usa el índice `odontograma_registro_vigente_idx` de V8. No llama a `clinica/` ni la modifica; si Emanuel cambia la tabla, el test de sugerencias lo detecta. **Alternativa descartada**: exponer un servicio público en `clinica/` (obligaría a tocar un módulo ajeno).

### D9. Errores

`CodigoCobertura` suma:

| Código | Estado |
|---|---|
| `ARANCEL_INEXISTENTE`, `PRESTACION_INACTIVA`, `PRESUPUESTO_VENCIDO`, `PRESUPUESTO_CERRADO` | `409` |
| `CODIGO_NOMENCLADOR_REQUERIDO`, `CODIGO_NOMENCLADOR_INVALIDO` | `422` |

Se reutilizan `RECURSO_NO_ENCONTRADO` (`404`), `DATOS_INVALIDOS` y `PRESTACION_INEXISTENTE` (`422`). `ACCESO_DENEGADO` (`403`) lo escribe la seguridad.
- `ARANCEL_INEXISTENTE` y `PRESTACION_INACTIVA` son `409`: el pedido está bien formado y choca con el estado del catálogo, igual que `PLAN_INACTIVO` en C-18. (decisión 3, aceptada).
- `PRESTACION_INEXISTENTE` queda `422` porque el id viene en el body (como en el nomenclador de C-18); un id inexistente en la **ruta** es `404`.
- Restricciones traducidas por nombre (red de seguridad): `arancel_precio_positivo`, `cobertura_plan_porcentaje_valido`, `presupuesto_item_cantidad` y los `presupuesto_item_pieza_fdi`/`_cara_*` → `DATOS_INVALIDOS`. Las demás (`presupuesto_totales`, `presupuesto_item_cobertura`, triggers) indican un defecto del código: se relanzan y terminan en `500` sin valores.
- `detail` fijo por código; los `422` llevan solo el campo (`items[0].pieza`), nunca el valor recibido (RN-PR-07).

### D10. Privacidad (RN-PR-07)

El módulo no escribe logs con piezas, caras ni importes; `SqlExceptionHelper` ya está apagado; los triggers y el handler no incluyen valores. `notificaciones/` no lee las tablas nuevas. Tests con `OutputCaptureExtension` comprueban la salida de una emisión exitosa y de una rechazada, y que el recordatorio de un paciente con presupuesto no cambia.

### D11. Seed de desarrollo `db/seed/V13_1__seed_presupuestos_dev.sql`

- Aranceles y coberturas exactamente como el seed de la KB ("Aranceles y cobertura (C-19)"), con `ON CONFLICT DO NOTHING` y buscando por nombre o código.
- Vincula `N-0301` a "Extracción simple" (`UPDATE nomenclador SET prestacion_id = ... WHERE codigo = 'N-0301' AND prestacion_id IS NULL`); sin eso, el 70 % de "Plan Integral" y el 80 % de "Plan Oro" para `N-0301` nunca se aplicarían. (decisión 2, aceptada).
- Agrega "Paciente Tres" (`99000003`) con "Plan Oro" de "Prepaga Prueba" y el número `PP-000003` desde 2026-01-01, para tener en dev un paciente de cada tipo (la KB pide 3 pacientes ficticios). (decisión 2, aceptada).
- No crea presupuestos (dependen de la fecha y del odontólogo de la sesión).

### Tests

- Unitarios (`coberturas/domain`): `Precio`, `Porcentaje`, `PiezaCara`, `CalculoItem`, agregado `Presupuesto` (totales, vencido, transiciones).
- Integración (`@Tag("integration")`, PostgreSQL real): `IntegracionPresupuestosBase` extiende `IntegracionCoberturasBase` y agrega `bruno.prueba`, "Paciente Tres", las prestaciones, los aranceles, la cobertura y los registros de odontograma de cada escenario (insertados por SQL en la base de test, sin usar código de `clinica/`). Un test por escenario con `@DisplayName` igual al nombre del escenario.

## Risks / Trade-offs

- [Columnas de foto duplican nombres] → es lo que pide DD-15 llevado a los datos que C-18 permite corregir; son pocas columnas y el `CHECK presupuesto_foto_cobertura` mantiene la coherencia.
- [`CHECK` de cobertura depende de que `round()` de PostgreSQL sea HALF_UP para positivos] → un test de migración inserta el caso 12500.075 y verifica que solo acepta 12500.08; el dominio calcula lo mismo con `RoundingMode.HALF_UP`.
- [Reglas FDI copiadas de V8] → un test inserta el mismo conjunto de piezas y caras válidas e inválidas en ambas tablas; si `clinica/` cambia las reglas, falla y se avisa.
- [Leer `odontograma_registro` desde otro módulo] → solo lectura, sin FK ni triggers; aviso a Emanuel en el PR (grupo 0).
- [`ALTER TABLE afiliacion_paciente` en V13] → es una tabla del mismo módulo y dueño; agrega una restricción única redundante con la PK (no puede fallar con datos existentes) y no edita V12 (regla dura 3).
- [Triggers de inmutabilidad] → impiden corregir un presupuesto a mano en la base; es lo que pide RN-PR-05 (se emite otro).
- [`TRUNCATE ... CASCADE` de otros módulos vacía las tablas nuevas] → esperado, como en C-18.
- [El seed actualiza un código de C-18] → solo perfil dev, condicionado a `prestacion_id IS NULL`, mismo dueño.

## Migration Plan

1. Mergear el PR con `V13__presupuestos.sql` y `V13_1__seed_presupuestos_dev.sql`; Flyway aplica V13 con out-of-order después de V22 en las bases existentes (requiere V12).
2. Rollback antes de producción: revertir el PR y en la base dev `DROP TABLE presupuesto_item, presupuesto, cobertura_plan, arancel`, `DROP FUNCTION` de los triggers, `ALTER TABLE afiliacion_paciente DROP CONSTRAINT afiliacion_id_paciente_unico` y borrar las filas 13 y 13.1 de `flyway_schema_history`. Nunca se edita V13 una vez mergeada (regla dura 3).

## Open Questions

Decisiones que se consultaron con la líder en la tarea 0.1; las respuestas están en "Resolución de decisiones (2026-11-24)" al final de este documento.

1. **Foto con nombres** (D4): guardar además de lo que pide la KB `obra_social_nombre`, `plan_nombre`, `numero_afiliado`, `prestacion_nombre` y `codigo_nomenclador`. *Recomendación*: sí; sin eso, corregir la afiliación o renombrar un plan cambia lo que muestra un presupuesto emitido (escenario "Se corrige la afiliación después de emitir").
2. **Seed dev V13_1** (D11): vincular `N-0301` a "Extracción simple" y agregar "Paciente Tres" con "Plan Oro". *Recomendación*: sí, para que el seed de cobertura de la KB tenga efecto y haya en dev un paciente con obra social, uno con prepaga y uno particular.
3. **`409` para `ARANCEL_INEXISTENTE` y `PRESTACION_INACTIVA`** (D9) en lugar de `422`. *Recomendación*: `409`, coherente con `PLAN_INACTIVO` de C-18.
4. **Cualquier odontólogo acepta o rechaza** (D5), no solo el autor. *Recomendación*: cualquiera, como la escritura de la ficha clínica.
5. **Triggers de inmutabilidad** en `presupuesto` y `presupuesto_item` (D5). *Recomendación*: sí, como el odontograma de solo agregado.
6. **Upsert** con `PUT` para aranceles y coberturas (D6), sin `COBERTURA_EXISTENTE`. *Recomendación*: sí; un solo valor vigente por clave (PA-28) y sin carrera.
7. **El paciente ve pieza y cara** de sus presupuestos (D7). *Recomendación*: sí, son sus propios datos (PA-27); la restricción de PA-24 es solo para la recepción.
8. **Límites**: 1 a 50 ítems por presupuesto y cantidad de 1 a 99. *Recomendación*: sí; evita desbordar `NUMERIC(12,2)` y pedidos abusivos.
9. **Quién y cuándo aceptó o rechazó**: la KB no lo guarda. *Recomendación*: no agregarlo en C-19; si C-22 lo necesita, una migración propia agrega `resuelto_en` y `resuelto_por`.

## Resolución de decisiones (2026-11-24)

La líder (Sofia Indovino) aprobó el propose al mergear el PR #110 y, por medio de Juan Sebastián, aceptó **las 9 decisiones tal como se recomendaron**; no hay cambios en las specs ni en las tareas:

1. Foto con nombres: se guardan también `obra_social_nombre`, `plan_nombre`, `numero_afiliado`, `prestacion_nombre` y `codigo_nomenclador`. **Aceptada.**
2. Seed dev V13_1: vincula `N-0301` a "Extracción simple" y agrega "Paciente Tres" (`99000003`) con "Plan Oro" y `PP-000003` (ficticios, `ON CONFLICT DO NOTHING` como V12_1). **Aceptada.**
3. `ARANCEL_INEXISTENTE` y `PRESTACION_INACTIVA` responden `409`. **Aceptada.**
4. Cualquier `ODONTOLOGO` puede aceptar o rechazar. **Aceptada.**
5. Triggers de inmutabilidad en `presupuesto` y `presupuesto_item`. **Aceptada.**
6. `PUT` con upsert para aranceles y coberturas. **Aceptada.**
7. El paciente ve pieza y cara de sus presupuestos. **Aceptada.**
8. De 1 a 50 ítems por presupuesto y cantidad de 1 a 99. **Aceptada.**
9. No se registra quién ni cuándo aceptó o rechazó por ahora. **Aceptada.**

## UI Mockups

- Design system revision: no aplica (sin UI).
- Decision: no (no UI) — 2026-11-24. C-19 es solo backend por decisión del usuario; las pantallas (aranceles y cobertura en Configuración, presupuesto en la ficha del paciente y "Mi cuenta") van en una ficha de frontend posterior, que sí pasa por el gate de mockups del proyecto.
- Mockups: —
- Approval: no aplica.
- Preview: no aplica.
- Visual verify: no aplica.
