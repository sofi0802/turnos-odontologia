# Design

## Context

Ver `proposal.md` (Why). Estado observado en `master` el 2026-11-24:

- `backend/.../coberturas/` (C-18 y C-19, Juan Sebastián) tiene capas `domain/application/infrastructure/api`, `CodigoCobertura` (estado `CONFLICTO`/`NO_ENCONTRADO`/`INVALIDO`), `CoberturaException`, `CoberturasExceptionHandler` (traduce restricciones de la base por nombre), `CalculoItem` (`BigDecimal`, `HALF_UP`), el patrón `conReintento` ante `PessimisticLockingFailureException` (`ServicioAfiliaciones`) y puertos JDBC de solo lectura (`LecturaCoberturasJdbc`, `OdontogramaVigenteJdbc`).
- `Clock` y `ZoneId` (`America/Argentina/Buenos_Aires`) son beans de `AgendaConfig`. Flyway corre con `out-of-order: true` (V22 ya está aplicada; V14 está reservada para C-20).
- `turno` (V1/V4/V22, agenda de Sofia): `id`, `paciente_id`, `prestacion_id`, `inicio timestamptz`, `estado` en (`RESERVADO`, `CONFIRMADO`, `CANCELADO`, `AUSENTE`, `ATENDIDO`). En el código no hay transición que saque un turno de `ATENDIDO`. `paciente` (V1): `nombre`, `apellido`, `dni` único.
- C-18/C-19: `obra_social`, `plan` (con `UNIQUE (id, obra_social_id)`), `nomenclador` (`prestacion_id`, `activo`), `afiliacion_paciente` (una vigente por fecha, `EXCLUDE`), `arancel` (uno por prestación), `cobertura_plan` (`UNIQUE (plan_id, nomenclador_id)`, sin fila = 0 %).
- **C-22 (`caja-y-cobros`, Enzo)**: en `master` solo está la KB (`Cargo` en 04, RN-CA-03, "en revisión"). `grep -ri cargo` en `db/migration` y en `backend/src/main/java` no encuentra nada: **no existen la tabla `cargo` ni su código**. V15 está reservada para C-22.
- Las specs de C-19 están en `openspec/changes/presupuestos-y-cobertura/` (mergeado, sin archivar); C-20 no las modifica.

## Goals / Non-Goals

**Goals:**
- Las garantías de dinero viven en dos lugares, como las de la agenda: el dominio (con códigos de error claros) y la base (`UNIQUE`, `CHECK` del cálculo y de la conciliación, triggers de inmutabilidad por estado).
- C-20 funciona completo sin C-22, y queda listo para usar el cargo cuando C-22 se mergee, sin depender de tablas inexistentes.
- No tocar `agenda/`, `usuarios/`/`paciente`, `caja/` ni `shared/`.

**Non-Goals:**
- PDF, ARCA, cobro de diferencias al paciente, validación online (ver proposal).
- Índices nuevos sobre `turno` (es tabla de la agenda; el volumen de un consultorio chico no lo justifica, ver Riesgos).
- Seed de desarrollo de liquidaciones: se generan desde la API con los turnos del seed.

## Decisions

### D1. Módulo y capas

Todo en `coberturas/`, con las convenciones de C-19:

- `domain/`: `Periodo` (año-mes; `primerDia()`, `ultimoDia()`, `estaCerrado(hoy)`), `EstadoLiquidacion { BORRADOR, PRESENTADA, CERRADA }`, `ResultadoConciliacion { COBRADA, PARCIAL, RECHAZADA }` con `de(montoCobrado, montoSolicitado)`, `MontoCobrado` (escala ≤ 2, `0 ≤ m ≤ solicitado`), `Liquidacion` (agregado con sus transiciones y reglas: presentar, conciliar, cerrar) y `LineaLiquidacion`. El monto solicitado reusa `CalculoItem.de(precio, 1, porcentaje).cobertura()`. Sin Spring ni JPA.
- `application/`: `ServicioLiquidaciones` (casos de uso), `OperacionesLiquidaciones` (cada operación en su `@Transactional`), puertos `TurnosLiquidables`, `CargosDeTurno`, `Liquidaciones` (persistencia), `ExportacionCsvLiquidacion`.
- `infrastructure/`: `TurnosLiquidablesJdbc` (solo lectura de `turno`, `paciente`, `afiliacion_paciente`, `plan`, `prestacion`, `nomenclador`, `cobertura_plan`, `arancel`), `LiquidacionesJdbc`, `SinCargosDeCaja` (D5).
- `api/`: `LiquidacionesController` con `@PreAuthorize("hasRole('ADMIN')")` a nivel de clase; DTOs/records en `LiquidacionesDtos`.

**Persistencia con JDBC, no JPA.** A diferencia de los presupuestos (que solo se insertan), las líneas en borrador se actualizan, se quitan y se concilian con reglas por estado. Con `JdbcTemplate` cada operación es un SQL explícito que coincide con lo que permiten los triggers (D3); con JPA, el *dirty checking* podría emitir `UPDATE` que los triggers rechazan y que habría que traducir. *Alternativa descartada*: entidades JPA `@Immutable` + `UPDATE` JDBC como en C-19: dos modelos para la misma tabla.

### D2. Migración V14 (`V14__liquidaciones.sql`)

```sql
CREATE TABLE liquidacion (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    obra_social_id   BIGINT        NOT NULL REFERENCES obra_social (id),
    periodo          DATE          NOT NULL,
    estado           VARCHAR(20)   NOT NULL DEFAULT 'BORRADOR',
    total_solicitado NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_cobrado    NUMERIC(12,2) NOT NULL DEFAULT 0,
    generada_en      TIMESTAMPTZ   NOT NULL,           -- última generación o regeneración
    generada_por     VARCHAR(120)  NOT NULL,           -- (decisión 6, aprobada)
    presentada_en    TIMESTAMPTZ,
    presentada_por   VARCHAR(120),
    cerrada_en       TIMESTAMPTZ,
    cerrada_por      VARCHAR(120),
    CONSTRAINT liquidacion_periodo_unico UNIQUE (obra_social_id, periodo),
    CONSTRAINT liquidacion_id_obra_social_unico UNIQUE (id, obra_social_id),   -- destino de la FK compuesta
    CONSTRAINT liquidacion_periodo_dia_1 CHECK (periodo = date_trunc('month', periodo)::date),
    CONSTRAINT liquidacion_estado_valido CHECK (estado IN ('BORRADOR', 'PRESENTADA', 'CERRADA')),
    CONSTRAINT liquidacion_totales CHECK (total_solicitado >= 0 AND total_cobrado >= 0
        AND total_cobrado <= total_solicitado),
    CONSTRAINT liquidacion_fechas_estado CHECK (
        (estado = 'BORRADOR'   AND presentada_en IS NULL AND presentada_por IS NULL AND cerrada_en IS NULL AND cerrada_por IS NULL)
     OR (estado = 'PRESENTADA' AND presentada_en IS NOT NULL AND presentada_por IS NOT NULL AND cerrada_en IS NULL AND cerrada_por IS NULL)
     OR (estado = 'CERRADA'    AND presentada_en IS NOT NULL AND presentada_por IS NOT NULL AND cerrada_en IS NOT NULL AND cerrada_por IS NOT NULL))
);
CREATE INDEX liquidacion_estado_idx ON liquidacion (estado, periodo DESC);

CREATE TABLE liquidacion_linea (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    liquidacion_id     BIGINT        NOT NULL,
    obra_social_id     BIGINT        NOT NULL,          -- copia para las dos FK compuestas
    turno_id           BIGINT        NOT NULL REFERENCES turno (id),
    paciente_id        BIGINT        NOT NULL REFERENCES paciente (id),
    plan_id            BIGINT        NOT NULL,
    prestacion_id      BIGINT        NOT NULL REFERENCES prestacion (id),
    -- Foto (DD-15): no cambia al presentar aunque cambien el catálogo, la afiliación o los nombres.
    fecha              DATE          NOT NULL,          -- inicio del turno en Buenos Aires
    paciente_nombre    VARCHAR(201)  NOT NULL,          -- "nombre apellido"
    paciente_dni       VARCHAR(15)   NOT NULL,
    numero_afiliado    VARCHAR(30)   NOT NULL,
    plan_nombre        VARCHAR(80)   NOT NULL,
    prestacion_nombre  VARCHAR(100)  NOT NULL,
    precio             NUMERIC(12,2) NOT NULL CHECK (precio > 0),
    origen_precio      VARCHAR(10)   NOT NULL,          -- 'ARANCEL' o 'CARGO' (D5)
    codigo_elegido     BOOLEAN       NOT NULL DEFAULT FALSE,  -- lo eligió el ADMIN (D6)
    nomenclador_id     BIGINT        REFERENCES nomenclador (id),
    codigo_nomenclador VARCHAR(20),
    porcentaje         SMALLINT,
    monto_solicitado   NUMERIC(12,2),
    -- Conciliación (RN-LQ-06)
    monto_cobrado      NUMERIC(12,2),
    resultado          VARCHAR(10),
    motivo_rechazo     VARCHAR(200),
    conciliada_en      TIMESTAMPTZ,
    conciliada_por     VARCHAR(120),
    CONSTRAINT liquidacion_linea_turno_unico UNIQUE (turno_id),
    CONSTRAINT liquidacion_linea_liquidacion_fk FOREIGN KEY (liquidacion_id, obra_social_id)
        REFERENCES liquidacion (id, obra_social_id),
    CONSTRAINT liquidacion_linea_plan_fk FOREIGN KEY (plan_id, obra_social_id) REFERENCES plan (id, obra_social_id),
    CONSTRAINT liquidacion_linea_origen CHECK (origen_precio IN ('ARANCEL', 'CARGO')),
    -- Código, su foto, porcentaje y monto van juntos: todos o ninguno (línea sin código, PA-38).
    CONSTRAINT liquidacion_linea_codigo CHECK (
        (nomenclador_id IS NULL AND codigo_nomenclador IS NULL AND porcentaje IS NULL AND monto_solicitado IS NULL
            AND NOT codigo_elegido)
     OR (nomenclador_id IS NOT NULL AND codigo_nomenclador IS NOT NULL AND porcentaje IS NOT NULL
            AND monto_solicitado IS NOT NULL)),
    CONSTRAINT liquidacion_linea_porcentaje CHECK (porcentaje BETWEEN 1 AND 100),
    -- RN-LQ-03, igual que presupuesto_item_cobertura de V13: round() de numeric es HALF_UP con positivos.
    CONSTRAINT liquidacion_linea_monto CHECK (monto_solicitado = round(precio * porcentaje / 100.0, 2)
        AND monto_solicitado > 0),
    CONSTRAINT liquidacion_linea_cobrado CHECK (monto_cobrado >= 0 AND monto_cobrado <= monto_solicitado),
    -- Con "resultado IS NOT NULL AND monto_cobrado IS NOT NULL" a propósito: un monto cobrado sin resultado daría NULL en
    -- todas las ramas y una CHECK con NULL se acepta (defecto de la primera versión de este diseño, hallado por la
    -- prueba de V14 en el apply).
    CONSTRAINT liquidacion_linea_resultado CHECK (
        (resultado IS NULL AND monto_cobrado IS NULL AND conciliada_en IS NULL AND conciliada_por IS NULL)
     OR (resultado IS NOT NULL AND monto_cobrado IS NOT NULL AND (
            (resultado = 'COBRADA'   AND monto_cobrado = monto_solicitado)
         OR (resultado = 'PARCIAL'   AND monto_cobrado > 0 AND monto_cobrado < monto_solicitado)
         OR (resultado = 'RECHAZADA' AND monto_cobrado = 0)))),
    CONSTRAINT liquidacion_linea_conciliada CHECK (resultado IS NULL
        OR (monto_solicitado IS NOT NULL AND conciliada_en IS NOT NULL AND conciliada_por IS NOT NULL)),
    CONSTRAINT liquidacion_linea_motivo CHECK (
        (resultado = 'RECHAZADA' AND motivo_rechazo IS NOT NULL AND motivo_rechazo = btrim(motivo_rechazo)
            AND motivo_rechazo <> '')
     OR (resultado IS DISTINCT FROM 'RECHAZADA' AND motivo_rechazo IS NULL))
);
CREATE INDEX liquidacion_linea_liquidacion_idx ON liquidacion_linea (liquidacion_id, plan_nombre, fecha, turno_id);
CREATE INDEX liquidacion_linea_paciente_idx ON liquidacion_linea (paciente_id);
CREATE INDEX liquidacion_linea_plan_idx ON liquidacion_linea (plan_id, obra_social_id);
CREATE INDEX liquidacion_linea_prestacion_idx ON liquidacion_linea (prestacion_id);
CREATE INDEX liquidacion_linea_nomenclador_idx ON liquidacion_linea (nomenclador_id);

-- Turnos quitados a mano de un borrador (D7, decisión 2, aprobada). Solo filas de liquidaciones en BORRADOR.
CREATE TABLE liquidacion_turno_quitado (
    liquidacion_id BIGINT NOT NULL REFERENCES liquidacion (id),
    turno_id       BIGINT NOT NULL REFERENCES turno (id),
    PRIMARY KEY (liquidacion_id, turno_id)
);
CREATE INDEX liquidacion_turno_quitado_turno_idx ON liquidacion_turno_quitado (turno_id);
```

- Sin `ON DELETE`: el `RESTRICT` por defecto protege la historia, como V12/V13. Las FK a `turno` y `paciente` son de solo referencia (avisos a Sofia y Juan Cruz).
- Las FK compuestas impiden una línea con un plan de otra obra social o colgada de una liquidación de otra obra social (patrón de V12/V13). Que `paciente_id` sea el del turno lo garantiza el servicio: una FK compuesta exigiría `UNIQUE (id, paciente_id)` en `turno`, que es de la agenda.
- `total_solicitado`/`total_cobrado` los mantiene el servicio en la misma transacción (una `CHECK` no ve otras filas); un test de integración compara la suma de las líneas con los totales después de cada operación.

### D3. Inmutabilidad por estado (triggers)

Mensajes fijos y sin valores, como V13. `TRUNCATE` (tests) no dispara triggers de fila.

- `liquidacion_reglas` (BEFORE UPDATE OR DELETE ON `liquidacion`): `DELETE` → error siempre. `UPDATE`: `obra_social_id` y `periodo` nunca cambian; transiciones válidas solo `BORRADOR→BORRADOR`, `BORRADOR→PRESENTADA`, `PRESENTADA→PRESENTADA` y `PRESENTADA→CERRADA`; con `OLD.estado = 'PRESENTADA'` solo pueden cambiar `estado`, `total_cobrado`, `cerrada_en` y `cerrada_por` (`to_jsonb(NEW) - ... IS DISTINCT FROM to_jsonb(OLD) - ...`, como `presupuesto_solo_estado`); con `OLD.estado = 'CERRADA'` → error.
- `liquidacion_linea_reglas` (BEFORE INSERT OR UPDATE OR DELETE ON `liquidacion_linea`), lee el estado de la liquidación (`SELECT estado ... FOR SHARE`): `INSERT` o `DELETE` solo si está en `BORRADOR`; `UPDATE` en `BORRADOR` solo si las columnas de conciliación quedan nulas y `liquidacion_id`/`turno_id` no cambian; en `PRESENTADA` solo pueden cambiar `monto_cobrado`, `resultado`, `motivo_rechazo`, `conciliada_en` y `conciliada_por`; en `CERRADA` → error.
- `liquidacion_turno_quitado_reglas` (BEFORE INSERT OR DELETE): solo si la liquidación está en `BORRADOR`.

### D4. Generar y regenerar (`OperacionesLiquidaciones`, una transacción)

1. `hoy = LocalDate.now(reloj.withZone(zona))`. `Periodo.estaCerrado(hoy)` ⇔ `hoy > ultimoDia()`; si no, `409 PERIODO_NO_CERRADO`. Obra social inexistente → `404`. Generar: si ya existe `(obra_social_id, periodo)` → `409 LIQUIDACION_EXISTENTE`; si no, inserta la cabecera. Regenerar: `SELECT ... FOR UPDATE` de la liquidación; si no está en `BORRADOR` → `409 LIQUIDACION_PRESENTADA` / `LIQUIDACION_CERRADA`.
2. Candidatos (`TurnosLiquidables.candidatos(obraSocialId, finExclusivo, liquidacionId)`), un solo `SELECT`:
   - `t.estado = 'ATENDIDO'` y `t.inicio < finExclusivo`, con `finExclusivo = (periodo + 1 mes) a las 00:00 en Buenos Aires` como `timestamptz` (sin límite inferior: entran los atrasados, RN-LQ-01);
   - afiliación de `t.paciente_id` vigente en `(t.inicio AT TIME ZONE 'America/Argentina/Buenos_Aires')::date` con `a.obra_social_id = ?`;
   - `NOT EXISTS` línea de **otra** liquidación con ese turno y `NOT EXISTS` fila en `liquidacion_turno_quitado` de **esta** liquidación;
   - trae paciente (nombre, apellido, DNI), plan, prestación, arancel (puede ser nulo) y, en un `array_agg`, los códigos activos de la prestación con el porcentaje del plan (`LEFT JOIN cobertura_plan`, nulo = 0).
3. Por candidato, en el dominio (`LineaLiquidacion.desde(...)`): cargo de `CargosDeTurno` (D5) → precio y porcentaje del cargo (si el porcentaje es 0, se descarta); si no, arancel (sin arancel → se cuenta en `turnosSinArancel` y se descarta). Códigos: uno solo activo → ese, y se descarta si el plan lo cubre con 0 %; varios → se descarta si el plan no cubre ninguno con más de 0 %, y si cubre alguno la línea queda **sin código** (PA-38), salvo que la línea existente tenga `codigo_elegido` y ese código siga cubierto (se conserva, D6); ninguno → se descarta; si el monto redondea a cero (por ejemplo 1 % de $0,04) la línea también se descarta, porque la `CHECK` pide `monto_solicitado > 0` (agregado en el apply).
4. Regenerar sincroniza: `UPDATE` de las líneas existentes que siguen siendo candidatas (foto completa recalculada, incluido el precio para `origen_precio = 'ARANCEL'`), `INSERT` de las nuevas y `DELETE` de las que ya no son candidatas. Recalcula `total_solicitado` y `generada_en/por`.
5. Respuesta: el detalle (D9) más `turnosSinArancel`. Nada de esto se loguea.

**Concurrencia.** Dos generaciones de la misma obra social y período: la segunda choca con `liquidacion_periodo_unico` → `409 LIQUIDACION_EXISTENTE` (verificación previa más traducción en `CoberturasExceptionHandler.RESTRICCIONES`). Dos generaciones distintas que se disputan un turno (solo posible si se corrigió una afiliación entre medio) chocan en `liquidacion_linea_turno_unico`: `ServicioLiquidaciones` reintenta la operación entera (hasta 3 veces, como `conReintento`) ante `PessimisticLockingFailureException` **y** ante esa violación puntual; en el reintento el `NOT EXISTS` ya excluye el turno. Toda operación sobre una liquidación existente (regenerar, quitar, elegir código, presentar, conciliar, cerrar) empieza con `SELECT ... FOR UPDATE` de la cabecera: se aplican de a una (escenario "Cerrar mientras se concilia la última línea").

### D5. Cargo de caja de C-22: puerto ahora, adaptador cuando exista (decisión 1, aprobada)

**Hallazgo**: C-22 no tiene tabla ni código en `master`. Por eso:

- Puerto `CargosDeTurno` en `coberturas/application`: `Map<Long, CargoTurno> de(Collection<Long> turnoIds)`, con `record CargoTurno(BigDecimal precio, int porcentaje, BigDecimal cobertura)`.
- Adaptador de C-20: `SinCargosDeCaja` (devuelve un mapa vacío), una `@Configuration` con un método `@Bean` y `@ConditionalOnMissingBean(CargosDeTurno.class)` (en una clase `@Component` escaneada la condición no se evalúa bien y deja el bean sin registrar; hallado en el apply). Una prueba aporta su propio adaptador con `@Primary`. Hoy todas las líneas salen con `origen_precio = 'ARANCEL'`.
- Cuando C-22 se mergee, se agrega `CargosDeTurnoJdbc` (lectura de `cargo` por `turno_id`), que reemplaza al nulo sin tocar el resto. Los escenarios de "Precio del cargo de caja del turno" se prueban ahora con un adaptador de prueba (`@TestConfiguration`) que devuelve el cargo ficticio; cuando exista la tabla, se agrega un test de integración con la fila real.
- Coherencia: con cargo, `monto_solicitado = round(precio × porcentaje / 100, 2)` es la `cobertura` del cargo (misma fórmula en RN-CA-03), así que la `CHECK liquidacion_linea_monto` vale para los dos orígenes; `CargosDeTurnoJdbc` verificará además `cobertura` = monto calculado y, si no, usará el arancel (no inventa montos).

*Recomendación*: el adaptador real lo escribe **Juan Sebastián en un change de seguimiento** (o una tarea al final del apply de C-22 coordinada con Enzo), porque vive en `coberturas/` y solo lee `cargo`. *Alternativas descartadas*: esperar a C-22 (bloquea C-20 sin necesidad); leer una tabla `cargo` que todavía no existe (rompe la migración y los tests); que C-22 escriba en las liquidaciones (invierte la dependencia).

### D6. Código de la línea (PA-38)

- `PUT /lineas/{lineaId}/codigo` con `{ nomencladorId }`, solo en `BORRADOR`. Válido si el código está activo, pertenece a la prestación de la línea y el plan lo cubre con más de 0 %; si no, `422 CODIGO_NOMENCLADOR_INVALIDO` (código de C-19, reusado). Guarda código, foto, porcentaje (o el del cargo si `origen_precio = 'CARGO'`), monto, `codigo_elegido = true`, y recalcula el total.
- También se puede cambiar el código de una línea que ya tenía uno elegido. Una línea con código único automático no se cambia (mismo `422`): no hay alternativa válida.
- El detalle de una línea sin código lista `codigosDisponibles` (id, código, porcentaje).

### D7. Quitar líneas (decisión 2, aprobada)

`DELETE /lineas/{lineaId}` en `BORRADOR`: borra la línea, inserta `(liquidacion_id, turno_id)` en `liquidacion_turno_quitado` y recalcula el total. La regeneración excluye esos turnos **solo para esa liquidación**; cualquier otra puede tomarlos (RN-LQ-04). *Recomendación*: conservar las quitas con esa tabla. *Alternativa*: sin tabla, regenerar vuelve a agregar los turnos quitados (más simple y más literal con "se agregan los turnos nuevos"), pero el `ADMIN` perdería en silencio una corrección sobre dinero. No hay "deshacer quitar" en C-20 (fuera del alcance de C-20; si hace falta, un `POST /turnos-quitados/{turnoId}/restaurar` es trivial).

### D8. Presentar, conciliar y cerrar

- **Presentar** (`POST /{id}/presentar`): `FOR UPDATE`; `BORRADOR` con ≥ 1 línea y todas con código. Orden de verificación: estado (`LIQUIDACION_PRESENTADA`/`LIQUIDACION_CERRADA`), vacía (`LIQUIDACION_VACIA`), sin código (`LINEA_SIN_CODIGO`). Guarda `presentada_en/por`. No revalida los montos contra el catálogo actual: presenta lo que el `ADMIN` revisó.
- **Conciliar** (`PUT /{id}/lineas/{lineaId}/conciliacion` con `{ montoCobrado, motivo }`): estado `PRESENTADA` (si no, `LIQUIDACION_NO_PRESENTADA`/`LIQUIDACION_CERRADA`); línea de esa liquidación (`404`); `MontoCobrado` (`422 MONTO_COBRADO_INVALIDO`: nulo, negativo, más de dos decimales o mayor que el solicitado; se recibe como `BigDecimal`, nunca `double`); `ResultadoConciliacion.de(...)`; motivo `btrim` obligatorio si `RECHAZADA` (`MOTIVO_REQUERIDO`), prohibido si no (`DATOS_INVALIDOS`, campo `motivo`), largo 1..200 (`DATOS_INVALIDOS`). Sobrescribe una conciliación previa mientras siga `PRESENTADA` (decisión 4, aprobada). Recalcula `total_cobrado`.
- **Cerrar** (`POST /{id}/cerrar`): `PRESENTADA` y ninguna línea con `resultado IS NULL` (`CONCILIACION_INCOMPLETA`); guarda `cerrada_en/por`.
- **Totales del detalle**: `totalSolicitado` (suma de las líneas con código), `totalCobrado`, `solicitadoConciliado` (suma de lo solicitado de las líneas con resultado), `diferencia = solicitadoConciliado − totalCobrado`, `lineasPendientes`.

### D9. Endpoints (`/api/v1/liquidaciones`, todos `ADMIN`)

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| `POST /api/v1/liquidaciones` | `{ obraSocialId, periodo: "2026-10" }` | `201` + `Location`, detalle y `turnosSinArancel` |
| `GET /api/v1/liquidaciones?obraSocialId=&estado=` | — | `200` resúmenes: id, obra social, período, estado, cantidad de líneas, totales (sin datos de pacientes) |
| `GET /api/v1/liquidaciones/{id}` | — | `200` detalle: cabecera, grupos por plan (subtotal) con líneas, totales de D8 |
| `POST /api/v1/liquidaciones/{id}/regenerar` | — | `200` detalle y `turnosSinArancel` |
| `PUT /api/v1/liquidaciones/{id}/lineas/{lineaId}/codigo` | `{ nomencladorId }` | `200` detalle |
| `DELETE /api/v1/liquidaciones/{id}/lineas/{lineaId}` | — | `204` |
| `POST /api/v1/liquidaciones/{id}/presentar` | — | `200` detalle |
| `PUT /api/v1/liquidaciones/{id}/lineas/{lineaId}/conciliacion` | `{ montoCobrado, motivo? }` | `200` detalle |
| `POST /api/v1/liquidaciones/{id}/cerrar` | — | `200` detalle |
| `GET /api/v1/liquidaciones/{id}/csv` | — | `200 text/csv` (D10) |

- `periodo` como `YearMonth` (`"yyyy-MM"`); inválido → `422 DATOS_INVALIDOS` campo `periodo`. Importes como números con dos decimales. Los instantes (`generadaEn`, `presentadaEn`, `cerradaEn`, `conciliadaEn`) se devuelven siempre en UTC, con el mismo formato al generar y al consultar (agregado en el apply). El actor (`generada_por`, etc.) sale de la sesión (`Authentication.getName()`), nunca del cuerpo.
- Sin `DELETE /{id}` ni `PUT /{id}`: Spring responde `405`. `SecurityConfig` no cambia; CSRF activo para `POST`/`PUT`/`DELETE` (regla dura 8).
- Los `@PathVariable` se cruzan siempre: una línea se busca por `id` **y** `liquidacion_id`.

### D10. Exportación CSV (RN-LQ-05; decisión 3, aprobada)

- Solo `PRESENTADA` o `CERRADA` (`409 LIQUIDACION_NO_PRESENTADA` en borrador).
- `Content-Type: text/csv; charset=UTF-8`; `Content-Disposition: attachment; filename="liquidacion-<obraSocialId>-<yyyy-MM>.csv"` (ASCII, sin datos personales); `Cache-Control: no-store`; `X-Content-Type-Options: nosniff` (Spring Security ya lo agrega).
- **Separador `;`**, decimales con punto (`15000.00`), fechas ISO `yyyy-MM-dd`, fin de línea `\r\n` (RFC 4180), UTF-8 **con BOM** para que Excel en español muestre bien los acentos y separe columnas. *Recomendación*: esta combinación; *alternativa*: `,` sin BOM (más "estándar", pero Excel es-AR la abre en una sola columna).
- Encabezado `fecha;paciente;dni;numero_afiliado;plan;codigo;prestacion;monto_solicitado`; filas ordenadas por plan, fecha y turno; paciente como "nombre apellido"; fila final `Total;;;;;;;27500.00`. No incluye la conciliación (no la pide RN-LQ-05).
- Escapado: campos con `;`, `"` o saltos de línea entre comillas dobles (`"` duplicada); celdas que empiezan con `=`, `+`, `-`, `@`, tab o CR llevan un `'` delante (inyección de fórmulas, OWASP). Los importes no se escapan (son números positivos).
- Se arma en memoria con `StringBuilder` (unas pocas centenas de líneas por mes); no se escribe a disco ni a logs.

### D11. Errores

Se agregan a `CodigoCobertura` (`409`): `LIQUIDACION_EXISTENTE`, `PERIODO_NO_CERRADO`, `LIQUIDACION_PRESENTADA`, `LIQUIDACION_CERRADA`, `LIQUIDACION_NO_PRESENTADA`, `LIQUIDACION_VACIA`, `LINEA_SIN_CODIGO`, `CONCILIACION_INCOMPLETA`; (`422`): `MONTO_COBRADO_INVALIDO`, `MOTIVO_REQUERIDO`. Se reusan `RECURSO_NO_ENCONTRADO` (`404`), `DATOS_INVALIDOS` y `CODIGO_NOMENCLADOR_INVALIDO` (`422`). `PERIODO_NO_CERRADO` es `409` (el pedido está bien formado y choca con la fecha de hoy), coherente con `PRESUPUESTO_VENCIDO`. `RESTRICCIONES` del handler suma `liquidacion_periodo_unico` → `LIQUIDACION_EXISTENTE`, `liquidacion_linea_cobrado` → `MONTO_COBRADO_INVALIDO` y `liquidacion_linea_motivo` → `MOTIVO_REQUERIDO`. Los errores de los triggers no se traducen: si aparecen, es un defecto (500 sin datos).

### D12. Privacidad (RN-LQ-08)

Sin `log.*` con pacientes, DNI, afiliados, prestaciones ni importes en el módulo; mensajes de error fijos; `turnosSinArancel` es solo un número; el CSV no se cachea. Test con `OutputCaptureExtension` (como `PrivacidadPresupuestosIntegrationTest`).

### Tests

Unitarios del dominio (`Periodo`, `ResultadoConciliacion`, `MontoCobrado`, `LineaLiquidacion.desde`, transiciones de `Liquidacion`, escapado CSV). Integración con `@Tag("integration")` sobre PostgreSQL real, con una base `IntegracionLiquidacionesBase` que extiende `IntegracionPresupuestosBase`, inserta turnos por SQL con el estado necesario y fija el reloj (hoy 2026-11-24 en Buenos Aires). El `TRUNCATE` de la base agrega `liquidacion_turno_quitado, liquidacion_linea, liquidacion`; los `TRUNCATE ... CASCADE` de `turno`/`paciente` de otros módulos ya vacían estas tablas. `@DisplayName` = nombre del escenario.

## Risks / Trade-offs

- [C-22 cambia el modelo de `Cargo` antes de mergearse] → El puerto solo pide precio, porcentaje y cobertura por turno; cualquier modelo de cargo puede adaptarse. Aviso a Enzo en la tarea 0.2.
- [Antes de C-22, una liquidación usa el arancel vigente al generar y el cargo (después) puede usar otro precio] → Ya aceptado en la KB (RN-LQ-03, PA-40). Los turnos ya liquidados no se recalculan.
- [Generar un mes posterior antes que el anterior arrastra los turnos del anterior] → Es lo que pide RN-LQ-01 (sin límite inferior). El listado muestra las liquidaciones por período; la líder resolvió no exigir que exista la del mes anterior (decisión 5, aprobada).
- [La consulta de candidatos recorre `turno` sin índice por estado] → Para un consultorio chico (miles de turnos por año) es inmediata; si hiciera falta, se pide a Sofia un índice parcial en una migración propia.
- [Triggers con lógica por estado] → Cada rama tiene su test de SQL directo en `V14MigrationIntegrationTest`.
- [Una corrección de afiliación posterior no cambia una línea presentada] → Es la foto pedida; la corrección se hace en la liquidación del mes siguiente (PA-42) quitando o agregando turnos libres.

## Migration Plan

1. `V14__liquidaciones.sql` se aplica fuera de orden en las bases que ya tienen V22 (`out-of-order: true`). No modifica tablas existentes: solo crea tablas, índices, funciones y triggers.
2. Rollback: la tabla no tiene datos al desplegar; revertir es `DROP` de las tres tablas y sus funciones en una migración nueva (nunca editar V14 una vez mergeada, regla dura 3).
3. Cuando C-22 esté en `master`: change de seguimiento con `CargosDeTurnoJdbc` y su test de integración.

## Open Questions

Decisiones que se le plantearon a la líder en la tarea 0.1, cada una con su recomendación (todas resueltas, ver "Resolución de decisiones"):

1. **Cargo de C-22** (D5): puerto `CargosDeTurno` con adaptador nulo ahora y `CargosDeTurnoJdbc` en un change de seguimiento de Juan Sebastián cuando C-22 se mergee. *Recomendación*: sí.
2. **Las quitas sobreviven a la regeneración** (D7) con la tabla `liquidacion_turno_quitado` (no está en la KB). *Recomendación*: sí; la alternativa sin tabla re-agrega en silencio lo que el `ADMIN` quitó.
3. **Formato CSV** (D10): `;`, punto decimal, UTF-8 con BOM, `\r\n`, fila final de total y sin subtotales por plan. *Recomendación*: sí.
4. **Corregir una conciliación** mientras la liquidación sigue `PRESENTADA`. *Recomendación*: sí; una vez `CERRADA`, nada cambia.
5. **Generar un mes sin la liquidación del anterior** (Riesgos). *Recomendación*: permitirlo, como dice RN-LQ-01.
6. **Quién y cuándo** (`generada_por`, `presentada_por`, `cerrada_por`, `conciliada_en/por`): no están en la KB. *Recomendación*: guardarlos (governance ALTO, dinero), como `abierta_por` de la Caja de C-22.
7. **Liquidación vacía**: se genera en `BORRADOR` sin líneas y no se puede presentar (`LIQUIDACION_VACIA`). *Recomendación*: sí; permite regenerar después sin un `409 LIQUIDACION_EXISTENTE` confuso.
8. **Motivo solo en rechazadas** (`DATOS_INVALIDOS` si viene con un monto mayor que 0). *Recomendación*: sí, como dice RN-LQ-06; si la líder quiere motivo en las parciales, se amplía la `CHECK`.
9. **Elegir código solo entre los que el plan cubre con más de 0 %** (D6). *Recomendación*: sí; un código al 0 % daría monto 0 y no se liquida (RN-LQ-01).

## Resolución de decisiones (2026-11-24)

Sofia (líder, dueña de `master`) mergeó el propose (PR #122) y, a través de Juan Sebastián (responsable de `coberturas/`), **aceptó las 9 decisiones tal como estaban recomendadas**. No hay cambios en proposal, specs ni tasks. Queda la aprobación como visto bueno para empezar el grupo 1.

1. **Cargo de C-22**: puerto `CargosDeTurno` con adaptador vacío (`SinCargosDeCaja`) ahora; el adaptador real va en un change de seguimiento cuando C-22 esté en `master` (verificado de nuevo: no hay `cargo` en migraciones ni en el código).
2. **Las quitas sobreviven a la regeneración**, con la tabla `liquidacion_turno_quitado`.
3. **CSV**: separador `;`, punto decimal, UTF-8 con BOM, `
`, fila final de total y sin subtotales por plan.
4. **Corregir una conciliación** es posible mientras la liquidación esté `PRESENTADA`.
5. **Generar un mes aunque no exista la liquidación del mes anterior**.
6. **Se registra quién y cuándo** generó, presentó, cerró y concilió.
7. **Liquidación vacía**: se genera en `BORRADOR` y no se puede presentar (`LIQUIDACION_VACIA`).
8. **Motivo solo en las líneas `RECHAZADA`**.
9. **El ADMIN elige el código solo entre los que el plan cubre con más de 0 %**.

El aviso a Sofia, Juan Cruz y Enzo (tarea 0.2) va en la descripción del PR.

## UI Mockups

- Design system revision: no aplica (sin UI).
- Decision: no (no UI) — 2026-11-24. C-20 es solo backend, como C-18 y C-19; las pantallas de liquidaciones van en una ficha de frontend posterior, que sí pasa por el gate de mockups del proyecto.
- Mockups: —
- Approval: no aplica.
- Preview: no aplica.
- Visual verify: no aplica.
