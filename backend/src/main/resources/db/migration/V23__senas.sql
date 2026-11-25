-- C-23 senas-mercado-pago (RN-SE-01 a RN-SE-12, DD-21): señas de la reserva online pagadas con Mercado Pago.
-- Nada se borra: los cambios de estado y los movimientos quedan registrados. Sin ON DELETE CASCADE a propósito.

CREATE TABLE sena (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- El turno vigente: cambia si el turno se reprograma (RN-SE-07).
    turno_id             BIGINT        NOT NULL REFERENCES turno (id),
    paciente_id          BIGINT        NOT NULL REFERENCES paciente (id),
    porcentaje           SMALLINT      NOT NULL CONSTRAINT sena_porcentaje_valido CHECK (porcentaje BETWEEN 1 AND 100),
    copago_base          NUMERIC(12,2) NOT NULL CONSTRAINT sena_copago_valido CHECK (copago_base > 0),
    importe              NUMERIC(12,2) NOT NULL CONSTRAINT sena_importe_valido CHECK (importe > 0),
    estado               VARCHAR(12)   NOT NULL CONSTRAINT sena_estado_valido
        CHECK (estado IN ('PENDIENTE', 'PAGADA', 'VENCIDA', 'IMPUTADA', 'DEVUELTA', 'RETENIDA')),
    vence_en             TIMESTAMPTZ   NOT NULL,
    pagada_en            TIMESTAMPTZ,
    -- Referencia opaca que recibe Mercado Pago (RN-SE-11): no contiene datos del paciente ni del turno.
    referencia           VARCHAR(40)   NOT NULL CONSTRAINT sena_referencia_unica UNIQUE,
    pago_externo_id      VARCHAR(40)   CONSTRAINT sena_pago_unico UNIQUE,
    link_pago            VARCHAR(500),
    cobro_id             BIGINT        REFERENCES cobro (id),
    -- Lo que hay que devolver y lo ya devuelto (RN-SE-09, design D8).
    importe_a_reintegrar NUMERIC(12,2) NOT NULL DEFAULT 0,
    importe_reintegrado  NUMERIC(12,2) NOT NULL DEFAULT 0,
    causa_cancelacion    VARCHAR(12)   CONSTRAINT sena_causa_valida CHECK (causa_cancelacion IN ('PACIENTE', 'CONSULTORIO')),
    creada_en            TIMESTAMPTZ   NOT NULL,
    CONSTRAINT sena_reintegro_valido
        CHECK (importe_reintegrado >= 0 AND importe_reintegrado <= importe_a_reintegrar AND importe_a_reintegrar <= importe),
    CONSTRAINT sena_pago_registrado
        CHECK (estado NOT IN ('PAGADA', 'IMPUTADA') OR (pago_externo_id IS NOT NULL AND pagada_en IS NOT NULL)),
    CONSTRAINT sena_imputada_con_cobro CHECK (estado <> 'IMPUTADA' OR cobro_id IS NOT NULL)
);

-- Una sola seña no terminal por turno (RN-SE-04).
CREATE UNIQUE INDEX sena_vigente_por_turno ON sena (turno_id) WHERE estado IN ('PENDIENTE', 'PAGADA');
CREATE INDEX sena_estado ON sena (estado);
CREATE INDEX sena_paciente ON sena (paciente_id);

-- Todo lo que pasó con una seña. Una notificación de una referencia desconocida no tiene seña (sena_id nulo).
CREATE TABLE sena_movimiento (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sena_id       BIGINT        REFERENCES sena (id),
    tipo          VARCHAR(20)   NOT NULL CONSTRAINT sena_movimiento_tipo_valido
        CHECK (tipo IN ('NOTIFICACION', 'PAGO', 'REINTEGRO', 'REINTEGRO_FALLIDO', 'VENCIMIENTO', 'REPROGRAMACION')),
    id_externo    VARCHAR(60),
    importe       NUMERIC(12,2),
    registrado_en TIMESTAMPTZ   NOT NULL,
    detalle       VARCHAR(255)
);

-- Una notificación o un reintegro repetidos no se aplican dos veces (RN-SE-05, RN-SE-09).
CREATE UNIQUE INDEX sena_movimiento_externo_unico ON sena_movimiento (tipo, id_externo) WHERE id_externo IS NOT NULL;
CREATE INDEX sena_movimiento_sena ON sena_movimiento (sena_id);

-- RN-SE-08: la seña imputada es un cobro MERCADO_PAGO sin caja. No se edita V15.
ALTER TABLE cobro DROP CONSTRAINT cobro_medio_valido;
ALTER TABLE cobro ADD CONSTRAINT cobro_medio_valido
    CHECK (medio IN ('EFECTIVO', 'DEBITO', 'CREDITO', 'TRANSFERENCIA', 'MERCADO_PAGO'));
ALTER TABLE cobro ALTER COLUMN caja_id DROP NOT NULL;
ALTER TABLE cobro ADD CONSTRAINT cobro_caja_segun_medio CHECK ((caja_id IS NULL) = (medio = 'MERCADO_PAGO'));

-- RN-SE-06: la causa que indica el personal al cancelar un turno con seña pagada (agenda/, de Sofia).
ALTER TABLE turno_auditoria ADD COLUMN causa VARCHAR(12)
    CONSTRAINT turno_auditoria_causa_valida CHECK (causa IN ('PACIENTE', 'CONSULTORIO'));

-- RN-SE-11: aviso al paciente de que se reintegró su seña, sin el importe.
ALTER TABLE notificacion DROP CONSTRAINT notificacion_tipo_valido;
ALTER TABLE notificacion ADD CONSTRAINT notificacion_tipo_valido
    CHECK (tipo IN ('RECORDATORIO', 'CONFIRMACION', 'CANCELACION', 'REASIGNACION', 'REINTEGRO_SENA'));
