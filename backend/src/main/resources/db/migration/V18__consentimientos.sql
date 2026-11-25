-- C-27 consentimientos informados con firma electrónica (RN-CL-09 a RN-CL-11, DD-20). Es firma electrónica (Ley 25.506,
-- art. 5), no firma digital. El texto firmado, el firmante, la IP, el dispositivo y el motivo de la revocación son datos
-- personales y de salud: no van a logs ni a errores. V18 se reservó en CHANGES.md y se aplica con out-of-order.

-- Lo único mutable de una plantilla es si está activa. El título y el texto viven en sus versiones.
CREATE TABLE plantilla_consentimiento (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    activa     BOOLEAN     NOT NULL DEFAULT true,
    creada_en  TIMESTAMPTZ NOT NULL,
    creada_por BIGINT      NOT NULL REFERENCES usuario (id)
);
CREATE INDEX plantilla_consentimiento_creada_por_idx ON plantilla_consentimiento (creada_por);

-- Cada edición es una versión nueva con el número siguiente; las anteriores no cambian (solo agregado).
CREATE TABLE plantilla_consentimiento_version (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plantilla_id BIGINT      NOT NULL REFERENCES plantilla_consentimiento (id),
    numero       INTEGER     NOT NULL,
    titulo       TEXT        NOT NULL,
    texto        TEXT        NOT NULL,
    creada_en    TIMESTAMPTZ NOT NULL,
    creada_por   BIGINT      NOT NULL REFERENCES usuario (id),

    -- El UNIQUE también indexa plantilla_id y frena dos ediciones simultáneas sobre la misma versión (design D6).
    CONSTRAINT plantilla_consentimiento_version_numero_unico UNIQUE (plantilla_id, numero),
    CONSTRAINT plantilla_consentimiento_version_numero CHECK (numero > 0),
    CONSTRAINT plantilla_consentimiento_version_titulo CHECK (char_length(titulo) BETWEEN 1 AND 120),
    CONSTRAINT plantilla_consentimiento_version_texto CHECK (char_length(texto) BETWEEN 1 AND 20000)
);
CREATE INDEX plantilla_consentimiento_version_creada_por_idx ON plantilla_consentimiento_version (creada_por);

-- El documento firmado guarda su propia copia del título y del texto, la evidencia y el hash del contenido canónico
-- (design D4). La IP es TEXT y no INET: se guarda exactamente el texto que entró al hash.
CREATE TABLE consentimiento_firmado (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paciente_id          BIGINT      NOT NULL REFERENCES paciente (id),
    plantilla_version_id BIGINT      NOT NULL REFERENCES plantilla_consentimiento_version (id),
    profesional_id       BIGINT      NOT NULL REFERENCES profesional (id),
    titulo               TEXT        NOT NULL,
    texto                TEXT        NOT NULL,
    firmante_nombre      TEXT        NOT NULL,
    firmante_dni         TEXT        NOT NULL,
    firmado_en           TIMESTAMPTZ NOT NULL,
    ip_origen            TEXT        NOT NULL,
    dispositivo          TEXT        NOT NULL,
    tipo_firma           TEXT        NOT NULL,
    formato_canonico     SMALLINT    NOT NULL,
    hash_algoritmo       TEXT        NOT NULL,
    hash                 CHAR(64)    NOT NULL,

    CONSTRAINT consentimiento_firmado_titulo CHECK (char_length(titulo) BETWEEN 1 AND 120),
    CONSTRAINT consentimiento_firmado_texto CHECK (char_length(texto) BETWEEN 1 AND 20000),
    CONSTRAINT consentimiento_firmado_firmante_nombre CHECK (char_length(firmante_nombre) BETWEEN 3 AND 200),
    CONSTRAINT consentimiento_firmado_firmante_dni CHECK (firmante_dni ~ '^[0-9]{7,8}$'),
    CONSTRAINT consentimiento_firmado_ip_origen CHECK (char_length(ip_origen) BETWEEN 1 AND 45),
    -- Vacío si el pedido no trajo User-Agent.
    CONSTRAINT consentimiento_firmado_dispositivo CHECK (char_length(dispositivo) <= 512),
    CONSTRAINT consentimiento_firmado_tipo_firma CHECK (tipo_firma = 'ELECTRONICA'),
    CONSTRAINT consentimiento_firmado_formato CHECK (formato_canonico = 1),
    CONSTRAINT consentimiento_firmado_algoritmo CHECK (hash_algoritmo = 'SHA-256'),
    CONSTRAINT consentimiento_firmado_hash CHECK (hash ~ '^[0-9a-f]{64}$')
);
CREATE INDEX consentimiento_firmado_paciente_idx ON consentimiento_firmado (paciente_id, firmado_en DESC, id DESC);
CREATE INDEX consentimiento_firmado_version_idx ON consentimiento_firmado (plantilla_version_id);
CREATE INDEX consentimiento_firmado_profesional_idx ON consentimiento_firmado (profesional_id);

-- La revocación va aparte para que el documento firmado no tenga ninguna excepción a su bloqueo. El UNIQUE deja una
-- sola revocación aunque lleguen dos pedidos a la vez (design D5).
CREATE TABLE consentimiento_revocacion (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    consentimiento_id BIGINT      NOT NULL REFERENCES consentimiento_firmado (id),
    revocado_en       TIMESTAMPTZ NOT NULL,
    revocado_por      BIGINT      NOT NULL REFERENCES profesional (id),
    motivo            TEXT        NOT NULL,

    CONSTRAINT consentimiento_revocacion_unica UNIQUE (consentimiento_id),
    CONSTRAINT consentimiento_revocacion_motivo CHECK (btrim(motivo) <> '' AND char_length(motivo) <= 200)
);
CREATE INDEX consentimiento_revocacion_revocado_por_idx ON consentimiento_revocacion (revocado_por);

-- RN-CL-09: una plantilla no se borra y de ella solo cambia activa. El TRUNCATE de los tests no dispara triggers de fila.
CREATE FUNCTION plantilla_consentimiento_solo_estado() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Las plantillas de consentimiento no se borran: se desactivan';
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id
        OR NEW.creada_en IS DISTINCT FROM OLD.creada_en
        OR NEW.creada_por IS DISTINCT FROM OLD.creada_por THEN
        RAISE EXCEPTION 'De una plantilla de consentimiento solo se puede cambiar si está activa';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER plantilla_consentimiento_solo_estado
    BEFORE UPDATE OR DELETE ON plantilla_consentimiento
    FOR EACH ROW EXECUTE FUNCTION plantilla_consentimiento_solo_estado();

-- RN-CL-09 a RN-CL-11: las versiones, los documentos firmados y las revocaciones son de solo agregado, sin excepciones.
CREATE FUNCTION consentimiento_solo_agregado() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'La tabla % es de solo agregado: no se modifica ni se borra', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER plantilla_consentimiento_version_solo_agregado
    BEFORE UPDATE OR DELETE ON plantilla_consentimiento_version
    FOR EACH ROW EXECUTE FUNCTION consentimiento_solo_agregado();

CREATE TRIGGER consentimiento_firmado_bloqueado
    BEFORE UPDATE OR DELETE ON consentimiento_firmado
    FOR EACH ROW EXECUTE FUNCTION consentimiento_solo_agregado();

CREATE TRIGGER consentimiento_revocacion_solo_agregado
    BEFORE UPDATE OR DELETE ON consentimiento_revocacion
    FOR EACH ROW EXECUTE FUNCTION consentimiento_solo_agregado();
