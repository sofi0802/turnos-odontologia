-- C-26 imágenes y radiografías (RN-CL-07, RN-CL-08, DD-17). La base guarda solo los metadatos: el contenido va al
-- almacén de archivos con una clave generada por el servidor, sin datos del paciente. La descripción y el motivo son
-- datos de salud: no van a logs ni a errores. V17 se reservó en CHANGES.md y se aplica con out-of-order.

CREATE TABLE imagen_clinica (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paciente_id      BIGINT       NOT NULL REFERENCES paciente (id),
    profesional_id   BIGINT       NOT NULL REFERENCES profesional (id),
    categoria        VARCHAR(20)  NOT NULL,
    pieza            SMALLINT,
    tomada_el        DATE         NOT NULL,
    descripcion      VARCHAR(200),
    tipo_contenido   VARCHAR(30)  NOT NULL,
    tamano_bytes     INTEGER      NOT NULL,
    sha256           CHAR(64)     NOT NULL,
    clave_almacen    VARCHAR(80)  NOT NULL,
    registrada_en    TIMESTAMPTZ  NOT NULL,
    anulada_en       TIMESTAMPTZ,
    anulada_por      BIGINT REFERENCES profesional (id),
    motivo_anulacion VARCHAR(200),

    CONSTRAINT imagen_clinica_clave_unica UNIQUE (clave_almacen),
    CONSTRAINT imagen_clinica_categoria CHECK (categoria IN ('RADIOGRAFIA', 'FOTOGRAFIA', 'ESTUDIO')),
    -- Notación FDI, permanentes y temporales, igual que el odontograma (V8).
    CONSTRAINT imagen_clinica_pieza_fdi CHECK (
        pieza BETWEEN 11 AND 18 OR pieza BETWEEN 21 AND 28 OR pieza BETWEEN 31 AND 38 OR pieza BETWEEN 41 AND 48
        OR pieza BETWEEN 51 AND 55 OR pieza BETWEEN 61 AND 65 OR pieza BETWEEN 71 AND 75 OR pieza BETWEEN 81 AND 85),
    CONSTRAINT imagen_clinica_tipo CHECK (tipo_contenido IN ('image/jpeg', 'image/png', 'application/pdf')),
    -- De 1 byte a 10 MB.
    CONSTRAINT imagen_clinica_tamano CHECK (tamano_bytes BETWEEN 1 AND 10485760),
    CONSTRAINT imagen_clinica_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    -- La anulación va completa (momento, profesional y motivo no vacío) o no va.
    CONSTRAINT imagen_clinica_anulacion CHECK (
        (anulada_en IS NULL AND anulada_por IS NULL AND motivo_anulacion IS NULL)
        OR (anulada_en IS NOT NULL AND anulada_por IS NOT NULL AND btrim(motivo_anulacion) <> ''))
);
CREATE INDEX imagen_clinica_paciente_idx ON imagen_clinica (paciente_id, tomada_el DESC, registrada_en DESC, id DESC);

-- RN-CL-08: una imagen no se borra ni se modifica. Lo único que se registra después es su anulación, una sola vez.
-- El TRUNCATE de los tests no dispara triggers de fila.
CREATE FUNCTION imagen_clinica_solo_anulacion() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Las imágenes clínicas no se borran: se anulan con un motivo';
    END IF;
    IF OLD.anulada_en IS NOT NULL THEN
        RAISE EXCEPTION 'La imagen clínica ya está anulada';
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id
        OR NEW.paciente_id IS DISTINCT FROM OLD.paciente_id
        OR NEW.profesional_id IS DISTINCT FROM OLD.profesional_id
        OR NEW.categoria IS DISTINCT FROM OLD.categoria
        OR NEW.pieza IS DISTINCT FROM OLD.pieza
        OR NEW.tomada_el IS DISTINCT FROM OLD.tomada_el
        OR NEW.descripcion IS DISTINCT FROM OLD.descripcion
        OR NEW.tipo_contenido IS DISTINCT FROM OLD.tipo_contenido
        OR NEW.tamano_bytes IS DISTINCT FROM OLD.tamano_bytes
        OR NEW.sha256 IS DISTINCT FROM OLD.sha256
        OR NEW.clave_almacen IS DISTINCT FROM OLD.clave_almacen
        OR NEW.registrada_en IS DISTINCT FROM OLD.registrada_en THEN
        RAISE EXCEPTION 'De una imagen clínica solo se puede registrar la anulación';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER imagen_clinica_solo_anulacion
    BEFORE UPDATE OR DELETE ON imagen_clinica
    FOR EACH ROW EXECUTE FUNCTION imagen_clinica_solo_anulacion();
