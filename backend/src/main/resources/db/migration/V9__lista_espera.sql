-- C-15 lista-de-espera-automatica (RN-LE-01 a RN-LE-09, DD-21). Número reservado en CHANGES.md.
-- La oferta no reserva el horario: el turno se crea recién al aceptar, con las restricciones EXCLUDE de siempre.

-- 1. Inscripciones. El orden de llegada es inscripta_en (y el id, para desempatar). inscripta_por es el actor,
--    sin datos personales: 'paciente-online:<usuarioId>' o el usuario del personal.
CREATE TABLE inscripcion_lista_espera (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paciente_id    BIGINT       NOT NULL REFERENCES paciente (id),
    prestacion_id  BIGINT       NOT NULL REFERENCES prestacion (id),
    profesional_id BIGINT       REFERENCES profesional (id),
    desde          DATE         NOT NULL,
    hasta          DATE         NOT NULL,
    estado         VARCHAR(10)  NOT NULL DEFAULT 'ACTIVA',
    inscripta_en   TIMESTAMPTZ  NOT NULL,
    inscripta_por  VARCHAR(80)  NOT NULL,
    cerrada_en     TIMESTAMPTZ,
    CONSTRAINT inscripcion_lista_espera_estado_valido
        CHECK (estado IN ('ACTIVA', 'ASIGNADA', 'BAJA', 'VENCIDA')),
    CONSTRAINT inscripcion_lista_espera_rango_valido CHECK (hasta >= desde AND hasta <= desde + 60)
);

-- Una sola inscripción activa por paciente y prestación (RN-LE-01).
CREATE UNIQUE INDEX inscripcion_lista_espera_activa_unica
    ON inscripcion_lista_espera (paciente_id, prestacion_id) WHERE estado = 'ACTIVA';
CREATE INDEX inscripcion_lista_espera_orden ON inscripcion_lista_espera (estado, inscripta_en, id);

-- 2. Ofertas. El token viaja solo en el link del mensaje: se guarda su huella SHA-256 (hexadecimal). El aviso no
--    pasa por la tabla notificacion (le avisa al paciente inscripto, no al dueño del turno liberado): su estado y sus
--    reintentos viven acá.
CREATE TABLE oferta_lista_espera (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inscripcion_id    BIGINT       NOT NULL REFERENCES inscripcion_lista_espera (id),
    turno_liberado_id BIGINT       NOT NULL REFERENCES turno (id),
    profesional_id    BIGINT       NOT NULL REFERENCES profesional (id),
    inicio            TIMESTAMPTZ  NOT NULL,
    estado            VARCHAR(15)  NOT NULL DEFAULT 'PENDIENTE',
    ofrecida_en       TIMESTAMPTZ  NOT NULL,
    vence_en          TIMESTAMPTZ  NOT NULL,
    respondida_en     TIMESTAMPTZ,
    token_hash        VARCHAR(64)  NOT NULL,
    aviso_enviado     BOOLEAN      NOT NULL DEFAULT FALSE,
    aviso_intentos    SMALLINT     NOT NULL DEFAULT 0,
    turno_creado_id   BIGINT       REFERENCES turno (id),
    CONSTRAINT oferta_lista_espera_estado_valido
        CHECK (estado IN ('PENDIENTE', 'ACEPTADA', 'RECHAZADA', 'VENCIDA', 'NO_DISPONIBLE')),
    CONSTRAINT oferta_lista_espera_vencimiento_valido CHECK (vence_en > ofrecida_en),
    CONSTRAINT oferta_lista_espera_token_unico UNIQUE (token_hash)
);

-- Como máximo una oferta pendiente por horario liberado y por inscripción (RN-LE-04, design D3).
CREATE UNIQUE INDEX oferta_lista_espera_pendiente_por_horario
    ON oferta_lista_espera (turno_liberado_id) WHERE estado = 'PENDIENTE';
CREATE UNIQUE INDEX oferta_lista_espera_pendiente_por_inscripcion
    ON oferta_lista_espera (inscripcion_id) WHERE estado = 'PENDIENTE';
CREATE INDEX oferta_lista_espera_vencimiento ON oferta_lista_espera (estado, vence_en);

-- 3. Plazo para aceptar una oferta, en horas (RN-LE-05): de 1 a 24, por defecto 2.
INSERT INTO configuracion (clave, valor) VALUES ('lista_espera_plazo_horas', '2');
