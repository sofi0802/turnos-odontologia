-- C-22 caja-y-cobros (RN-CA-01 a RN-CA-08, DD-18): caja diaria, cargos por turno atendido y cobros.
-- Nada se borra: las correcciones son anulaciones (RN-CA-07). Sin ON DELETE CASCADE a propósito.

CREATE TABLE caja (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fecha             DATE          NOT NULL,
    monto_inicial     NUMERIC(12,2) NOT NULL CONSTRAINT caja_monto_inicial_valido CHECK (monto_inicial >= 0),
    abierta_por       VARCHAR(120)  NOT NULL,
    abierta_en        TIMESTAMPTZ   NOT NULL,
    cerrada_por       VARCHAR(120),
    cerrada_en        TIMESTAMPTZ,
    efectivo_contado  NUMERIC(12,2) CONSTRAINT caja_contado_valido CHECK (efectivo_contado >= 0),
    diferencia        NUMERIC(12,2),
    motivo_diferencia VARCHAR(255),
    -- Abierta: sin datos de cierre. Cerrada: con todos los datos del arqueo (RN-CA-06).
    CONSTRAINT caja_cierre_completo CHECK (
        (cerrada_en IS NULL AND cerrada_por IS NULL AND efectivo_contado IS NULL AND diferencia IS NULL
            AND motivo_diferencia IS NULL)
        OR (cerrada_en IS NOT NULL AND cerrada_por IS NOT NULL AND efectivo_contado IS NOT NULL
            AND diferencia IS NOT NULL)),
    CONSTRAINT caja_motivo_con_diferencia CHECK (
        diferencia IS NULL OR diferencia = 0
        OR (motivo_diferencia IS NOT NULL AND length(btrim(motivo_diferencia)) BETWEEN 1 AND 255))
);

-- RN-CA-02: una sola caja abierta en el consultorio, también con aperturas simultáneas (design D4).
CREATE UNIQUE INDEX caja_una_abierta ON caja ((true)) WHERE cerrada_en IS NULL;
CREATE INDEX caja_fecha ON caja (fecha);

CREATE TABLE cargo (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paciente_id          BIGINT        NOT NULL REFERENCES paciente (id),
    turno_id             BIGINT        NOT NULL REFERENCES turno (id),
    prestacion_id        BIGINT        NOT NULL REFERENCES prestacion (id),
    fecha                DATE          NOT NULL,
    afiliacion_id        BIGINT        REFERENCES afiliacion_paciente (id),
    precio               NUMERIC(12,2) NOT NULL CONSTRAINT cargo_precio_valido CHECK (precio > 0),
    porcentaje_cobertura SMALLINT      NOT NULL
        CONSTRAINT cargo_porcentaje_valido CHECK (porcentaje_cobertura BETWEEN 0 AND 100),
    cobertura            NUMERIC(12,2) NOT NULL CONSTRAINT cargo_cobertura_valida CHECK (cobertura >= 0),
    copago               NUMERIC(12,2) NOT NULL CONSTRAINT cargo_copago_valido CHECK (copago >= 0),
    creado_en            TIMESTAMPTZ   NOT NULL,
    -- RN-CA-03: un cargo por turno; el barrido lo inserta con ON CONFLICT (design D2).
    CONSTRAINT cargo_turno_unico UNIQUE (turno_id),
    CONSTRAINT cargo_importes CHECK (precio = cobertura + copago)
);

CREATE INDEX cargo_paciente ON cargo (paciente_id);

CREATE TABLE cobro (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cargo_id         BIGINT        NOT NULL REFERENCES cargo (id),
    caja_id          BIGINT        NOT NULL REFERENCES caja (id),
    importe          NUMERIC(12,2) NOT NULL CONSTRAINT cobro_importe_positivo CHECK (importe > 0),
    medio            VARCHAR(20)   NOT NULL
        CONSTRAINT cobro_medio_valido CHECK (medio IN ('EFECTIVO', 'DEBITO', 'CREDITO', 'TRANSFERENCIA')),
    registrado_por   VARCHAR(120)  NOT NULL,
    registrado_en    TIMESTAMPTZ   NOT NULL,
    anulado          BOOLEAN       NOT NULL DEFAULT false,
    anulado_por      VARCHAR(120),
    anulado_en       TIMESTAMPTZ,
    motivo_anulacion VARCHAR(255),
    -- RN-CA-07: una anulación guarda quién, cuándo y el motivo.
    CONSTRAINT cobro_anulacion_completa CHECK (
        (anulado = false AND anulado_por IS NULL AND anulado_en IS NULL AND motivo_anulacion IS NULL)
        OR (anulado = true AND anulado_por IS NOT NULL AND anulado_en IS NOT NULL
            AND motivo_anulacion IS NOT NULL AND length(btrim(motivo_anulacion)) BETWEEN 1 AND 255))
);

CREATE INDEX cobro_cargo ON cobro (cargo_id);
CREATE INDEX cobro_caja ON cobro (caja_id);
