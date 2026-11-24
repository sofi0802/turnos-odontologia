-- C-18 (obras-sociales-y-planes): catálogo de coberturas y afiliaciones de los pacientes.
-- btree_gist ya está instalada desde V1 (también la usan las EXCLUDE de turno).
-- Nada de esto se borra (RN-OS-01, RN-OS-02): sin ON DELETE, el RESTRICT por defecto protege el historial.

CREATE TABLE obra_social (
    id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL CHECK (nombre = btrim(nombre) AND nombre <> ''),
    sigla  VARCHAR(20),
    tipo   VARCHAR(20)  NOT NULL CONSTRAINT obra_social_tipo_valido CHECK (tipo IN ('OBRA_SOCIAL', 'PREPAGA')),
    activa BOOLEAN      NOT NULL DEFAULT TRUE
);
-- RN-OS-01: nombre único sin distinguir mayúsculas, activas o no.
CREATE UNIQUE INDEX obra_social_nombre_unico ON obra_social (lower(nombre));

CREATE TABLE plan (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    obra_social_id BIGINT      NOT NULL REFERENCES obra_social (id),
    nombre         VARCHAR(80) NOT NULL CHECK (nombre = btrim(nombre) AND nombre <> ''),
    activo         BOOLEAN     NOT NULL DEFAULT TRUE,
    -- Destino de la FK compuesta de afiliacion_paciente (design D3).
    CONSTRAINT plan_id_obra_social_unico UNIQUE (id, obra_social_id)
);
-- RN-OS-02: nombre de plan único dentro de su obra social.
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
    -- Copia de la obra social del plan para poder exigir unicidad por obra social. La FK compuesta de abajo
    -- obliga a que sea siempre la obra social real del plan (design D3).
    obra_social_id  BIGINT      NOT NULL,
    numero_afiliado VARCHAR(30) NOT NULL,
    vigente_desde   DATE        NOT NULL,
    vigente_hasta   DATE,
    CONSTRAINT afiliacion_plan_fk FOREIGN KEY (plan_id, obra_social_id) REFERENCES plan (id, obra_social_id),
    CONSTRAINT afiliacion_numero_valido CHECK (numero_afiliado ~ '^[A-Za-z0-9./-]{1,30}$'),
    CONSTRAINT afiliacion_vigencia_valida CHECK (vigente_hasta IS NULL OR vigente_hasta >= vigente_desde),
    -- RN-OS-05, igual que DD-03 para los turnos: una sola afiliación por paciente en cada fecha. El rango es
    -- cerrado en ambos extremos (días inclusivos) y abierto hacia arriba cuando vigente_hasta es nula.
    CONSTRAINT afiliacion_sin_superposicion EXCLUDE USING gist (
        paciente_id WITH =,
        daterange(vigente_desde, vigente_hasta, '[]') WITH &&),
    -- RN-OS-03: un número de afiliado pertenece a un solo paciente dentro de su obra social, sin distinguir
    -- mayúsculas. El operador <> sobre paciente_id permite que el mismo paciente conserve su número al cambiar de
    -- plan de la misma obra social (un UNIQUE simple lo impediría) y no necesita triggers.
    CONSTRAINT afiliacion_numero_unico_por_obra_social EXCLUDE USING gist (
        obra_social_id WITH =,
        upper(numero_afiliado) WITH =,
        paciente_id WITH <>)
);
CREATE INDEX afiliacion_plan_idx ON afiliacion_paciente (plan_id, obra_social_id);
