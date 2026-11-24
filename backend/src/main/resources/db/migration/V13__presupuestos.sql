-- C-19 presupuestos-y-cobertura: precio particular de cada prestación, cobertura de cada plan por código del
-- nomenclador y presupuestos con la foto de su cálculo (RN-PR-01 a RN-PR-07, DD-15). Los importes son pesos con dos
-- decimales. Los presupuestos contienen piezas y caras (datos de salud): no van a logs ni a mensajes (RN-PR-07).
-- Requiere V12 (plan, nomenclador, afiliacion_paciente) y V1 (prestacion, profesional). Nada se borra: sin ON DELETE,
-- el RESTRICT por defecto protege la foto.

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

-- Destino de la FK compuesta del presupuesto: la afiliación usada es siempre del mismo paciente. Es redundante con la
-- PK (id ya es único), así que no puede fallar con datos existentes. No se edita V12 (regla dura 3).
ALTER TABLE afiliacion_paciente ADD CONSTRAINT afiliacion_id_paciente_unico UNIQUE (id, paciente_id);

CREATE TABLE presupuesto (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paciente_id        BIGINT        NOT NULL REFERENCES paciente (id),
    profesional_id     BIGINT        NOT NULL REFERENCES profesional (id),
    fecha              DATE          NOT NULL,
    vence_el           DATE          NOT NULL,
    afiliacion_id      BIGINT,
    -- Foto de la cobertura usada (DD-15, RN-PR-04): corregir o renombrar después no cambia el presupuesto.
    obra_social_nombre VARCHAR(120),
    plan_nombre        VARCHAR(80),
    numero_afiliado    VARCHAR(30),
    estado             VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
    total_precio       NUMERIC(12,2) NOT NULL,
    total_cobertura    NUMERIC(12,2) NOT NULL,
    total_copago       NUMERIC(12,2) NOT NULL,
    creado_en          TIMESTAMPTZ   NOT NULL,
    -- FK compuesta: impide asociar la afiliación de otro paciente (con afiliacion_id nulo no se verifica).
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
    prestacion_nombre    VARCHAR(100)  NOT NULL,   -- foto
    nomenclador_id       BIGINT        REFERENCES nomenclador (id),
    codigo_nomenclador   VARCHAR(20),              -- foto
    pieza                SMALLINT,
    cara                 VARCHAR(1),
    cantidad             INTEGER       NOT NULL CONSTRAINT presupuesto_item_cantidad CHECK (cantidad BETWEEN 1 AND 99),
    precio_unitario      NUMERIC(12,2) NOT NULL CHECK (precio_unitario > 0),
    porcentaje_cobertura SMALLINT      NOT NULL CHECK (porcentaje_cobertura BETWEEN 0 AND 100),
    importe              NUMERIC(12,2) NOT NULL,
    cobertura            NUMERIC(12,2) NOT NULL,
    copago               NUMERIC(12,2) NOT NULL,
    CONSTRAINT presupuesto_item_importe CHECK (importe = precio_unitario * cantidad),
    -- RN-PR-03: cobertura = importe x porcentaje / 100 a centavos HALF_UP. round() de numeric redondea el medio
    -- alejándose de cero: con importes positivos es HALF_UP, igual que el dominio (BigDecimal, RoundingMode.HALF_UP).
    -- 100.0 es numeric: con enteros la división sería entera.
    CONSTRAINT presupuesto_item_cobertura CHECK (cobertura = round(importe * porcentaje_cobertura / 100.0, 2)),
    CONSTRAINT presupuesto_item_copago CHECK (copago >= 0 AND importe = cobertura + copago),
    -- Sin código del nomenclador el ítem es particular (0 %), y el código y su foto van juntos.
    CONSTRAINT presupuesto_item_codigo CHECK ((nomenclador_id IS NULL) = (codigo_nomenclador IS NULL)
        AND (nomenclador_id IS NOT NULL OR porcentaje_cobertura = 0)),
    -- Mismas reglas FDI que odontograma_registro (V8, RN-CL-03); acá la pieza y la cara son opcionales, pero una cara
    -- exige pieza. Un test compara ambas tablas con las mismas piezas y caras.
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

-- Inmutabilidad en la base (RN-PR-04 y RN-PR-05), como el odontograma de V8. Los mensajes son fijos y no incluyen
-- valores. El TRUNCATE de los tests no dispara triggers de fila.
CREATE FUNCTION presupuesto_item_inmutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Los ítems de un presupuesto emitido son de solo agregado: no se modifican ni se borran';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER presupuesto_item_solo_agregado
    BEFORE UPDATE OR DELETE ON presupuesto_item
    FOR EACH ROW EXECUTE FUNCTION presupuesto_item_inmutable();

-- Un presupuesto solo cambia de PENDIENTE a ACEPTADO o RECHAZADO; ninguna otra columna cambia y no se borra.
CREATE FUNCTION presupuesto_solo_cambia_estado() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Un presupuesto emitido no se borra';
    END IF;
    IF OLD.estado <> 'PENDIENTE' OR NEW.estado NOT IN ('ACEPTADO', 'RECHAZADO') THEN
        RAISE EXCEPTION 'Un presupuesto solo pasa de PENDIENTE a ACEPTADO o RECHAZADO';
    END IF;
    IF (to_jsonb(NEW) - 'estado') IS DISTINCT FROM (to_jsonb(OLD) - 'estado') THEN
        RAISE EXCEPTION 'Un presupuesto emitido solo cambia de estado: el resto de sus datos no se modifica';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER presupuesto_solo_estado
    BEFORE UPDATE OR DELETE ON presupuesto
    FOR EACH ROW EXECUTE FUNCTION presupuesto_solo_cambia_estado();
