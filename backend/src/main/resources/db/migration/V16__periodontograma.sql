-- C-25 periodontograma: sondaje periodontal por visita, de solo agregado (RN-CL-04, RN-CL-05). Son datos de salud:
-- no van a logs ni a errores. El estado de cada pieza no se guarda: se calcula al leer (RN-CL-06, design D4).
-- V16 se reservó en CHANGES.md y se aplica con out-of-order aunque V21 y V22 ya existan.

-- 1. Una fila por cada vez que se guarda el periodontograma. Cuelga del paciente, como el odontograma (design D2).
CREATE TABLE periodontograma_version (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paciente_id    BIGINT      NOT NULL REFERENCES paciente (id),
    profesional_id BIGINT      NOT NULL REFERENCES profesional (id),
    registrado_en  TIMESTAMPTZ NOT NULL
);
CREATE INDEX periodontograma_version_paciente_idx ON periodontograma_version (paciente_id, registrado_en DESC, id DESC);

-- 2. Piezas permanentes de la versión: ausente sin datos, o presente con movilidad y furcación opcional.
CREATE TABLE periodontograma_pieza (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version_id BIGINT   NOT NULL REFERENCES periodontograma_version (id),
    pieza      SMALLINT NOT NULL,
    ausente    BOOLEAN  NOT NULL,
    movilidad  SMALLINT,
    furcacion  SMALLINT,

    CONSTRAINT periodontograma_pieza_unica UNIQUE (version_id, pieza),
    -- Solo permanentes en notación FDI: 11-18, 21-28, 31-38, 41-48.
    CONSTRAINT periodontograma_pieza_permanente CHECK (
        pieza BETWEEN 11 AND 18 OR pieza BETWEEN 21 AND 28 OR pieza BETWEEN 31 AND 38 OR pieza BETWEEN 41 AND 48),
    CONSTRAINT periodontograma_movilidad_rango CHECK (movilidad BETWEEN 0 AND 3),
    CONSTRAINT periodontograma_furcacion_rango CHECK (furcacion BETWEEN 0 AND 3),
    -- Furcación solo en molares (posiciones 6 a 8) y en los primeros premolares superiores 14 y 24.
    CONSTRAINT periodontograma_furcacion_multirradicular CHECK (
        furcacion IS NULL OR pieza % 10 >= 6 OR pieza IN (14, 24)),
    -- Ausente: sin movilidad ni furcación. Presente: con movilidad.
    CONSTRAINT periodontograma_pieza_ausente CHECK (
        (ausente AND movilidad IS NULL AND furcacion IS NULL) OR (NOT ausente AND movilidad IS NOT NULL))
);

-- 3. Los 6 sitios de cada pieza presente. Que estén los 6 lo garantiza el dominio, en la misma transacción (design D3).
CREATE TABLE periodontograma_sitio (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pieza_id            BIGINT     NOT NULL REFERENCES periodontograma_pieza (id),
    sitio               VARCHAR(2) NOT NULL,
    profundidad_sondaje SMALLINT   NOT NULL,
    recesion            SMALLINT   NOT NULL,
    sangrado            BOOLEAN    NOT NULL,
    placa               BOOLEAN    NOT NULL,

    CONSTRAINT periodontograma_sitio_unico UNIQUE (pieza_id, sitio),
    CONSTRAINT periodontograma_sitio_valido CHECK (sitio IN ('MV', 'V', 'DV', 'ML', 'L', 'DL')),
    CONSTRAINT periodontograma_profundidad_rango CHECK (profundidad_sondaje BETWEEN 0 AND 15),
    CONSTRAINT periodontograma_recesion_rango CHECK (recesion BETWEEN -5 AND 15)
);

-- Solo agregado también en la base (RN-CL-04). El TRUNCATE de los tests no dispara triggers de fila.
CREATE FUNCTION periodontograma_solo_agregado() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'El periodontograma es de solo agregado: no se modifican ni se borran versiones';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER periodontograma_version_solo_agregado
    BEFORE UPDATE OR DELETE ON periodontograma_version
    FOR EACH ROW EXECUTE FUNCTION periodontograma_solo_agregado();

CREATE TRIGGER periodontograma_pieza_solo_agregado
    BEFORE UPDATE OR DELETE ON periodontograma_pieza
    FOR EACH ROW EXECUTE FUNCTION periodontograma_solo_agregado();

CREATE TRIGGER periodontograma_sitio_solo_agregado
    BEFORE UPDATE OR DELETE ON periodontograma_sitio
    FOR EACH ROW EXECUTE FUNCTION periodontograma_solo_agregado();
