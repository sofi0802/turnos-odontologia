-- C-20 liquidaciones-obras-sociales: liquidación mensual de los turnos atendidos que se le pide a cada obra social o
-- prepaga, con su conciliación (RN-LQ-01 a RN-LQ-08, DD-15). Los importes son pesos con dos decimales. Una liquidación
-- contiene nombres, DNI y números de afiliado de pacientes: no van a logs ni a mensajes (RN-LQ-08).
-- Requiere V12 (obra_social, plan, nomenclador, afiliacion_paciente), V13 (arancel, cobertura_plan) y V1 (turno,
-- paciente, prestacion). Nada se borra: sin ON DELETE, el RESTRICT por defecto protege la historia. La migración no
-- modifica ninguna tabla existente; V14 está reservada para C-20 y se aplica fuera de orden (out-of-order).

CREATE TABLE liquidacion (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    obra_social_id   BIGINT        NOT NULL REFERENCES obra_social (id),
    periodo          DATE          NOT NULL,                -- siempre el día 1 del mes
    estado           VARCHAR(20)   NOT NULL DEFAULT 'BORRADOR',
    total_solicitado NUMERIC(12,2) NOT NULL DEFAULT 0,      -- suma de lo solicitado de las líneas (lo mantiene el servicio)
    total_cobrado    NUMERIC(12,2) NOT NULL DEFAULT 0,      -- suma de lo cobrado de las líneas conciliadas
    generada_en      TIMESTAMPTZ   NOT NULL,                -- última generación o regeneración
    generada_por     VARCHAR(120)  NOT NULL,
    presentada_en    TIMESTAMPTZ,
    presentada_por   VARCHAR(120),
    cerrada_en       TIMESTAMPTZ,
    cerrada_por      VARCHAR(120),
    CONSTRAINT liquidacion_periodo_unico UNIQUE (obra_social_id, periodo),
    -- Destino de la FK compuesta de las líneas: una línea nunca cuelga de la liquidación de otra obra social.
    CONSTRAINT liquidacion_id_obra_social_unico UNIQUE (id, obra_social_id),
    CONSTRAINT liquidacion_periodo_dia_1 CHECK (periodo = date_trunc('month', periodo)::date),
    CONSTRAINT liquidacion_estado_valido CHECK (estado IN ('BORRADOR', 'PRESENTADA', 'CERRADA')),
    CONSTRAINT liquidacion_totales CHECK (total_solicitado >= 0 AND total_cobrado >= 0
        AND total_cobrado <= total_solicitado),
    CONSTRAINT liquidacion_fechas_estado CHECK (
        (estado = 'BORRADOR' AND presentada_en IS NULL AND presentada_por IS NULL AND cerrada_en IS NULL
            AND cerrada_por IS NULL)
     OR (estado = 'PRESENTADA' AND presentada_en IS NOT NULL AND presentada_por IS NOT NULL AND cerrada_en IS NULL
            AND cerrada_por IS NULL)
     OR (estado = 'CERRADA' AND presentada_en IS NOT NULL AND presentada_por IS NOT NULL AND cerrada_en IS NOT NULL
            AND cerrada_por IS NOT NULL))
);
CREATE INDEX liquidacion_estado_idx ON liquidacion (estado, periodo DESC);

CREATE TABLE liquidacion_linea (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    liquidacion_id     BIGINT        NOT NULL,
    obra_social_id     BIGINT        NOT NULL,              -- copia para las dos FK compuestas
    turno_id           BIGINT        NOT NULL REFERENCES turno (id),
    paciente_id        BIGINT        NOT NULL REFERENCES paciente (id),
    plan_id            BIGINT        NOT NULL,
    prestacion_id      BIGINT        NOT NULL REFERENCES prestacion (id),
    -- Foto (DD-15): no cambia al presentar aunque cambien el catálogo, la afiliación o los nombres.
    fecha              DATE          NOT NULL,              -- inicio del turno en Buenos Aires
    paciente_nombre    VARCHAR(201)  NOT NULL,              -- "nombre apellido"
    paciente_dni       VARCHAR(15)   NOT NULL,
    numero_afiliado    VARCHAR(30)   NOT NULL,
    plan_nombre        VARCHAR(80)   NOT NULL,
    prestacion_nombre  VARCHAR(100)  NOT NULL,
    precio             NUMERIC(12,2) NOT NULL CHECK (precio > 0),
    origen_precio      VARCHAR(10)   NOT NULL,              -- 'ARANCEL' o 'CARGO' (cargo de caja de C-22)
    codigo_elegido     BOOLEAN       NOT NULL DEFAULT FALSE, -- lo eligió el ADMIN entre varios códigos (PA-38)
    nomenclador_id     BIGINT        REFERENCES nomenclador (id),
    codigo_nomenclador VARCHAR(20),                         -- foto
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
    -- El código, su foto, el porcentaje y el monto van juntos: todos o ninguno (línea sin código, PA-38).
    CONSTRAINT liquidacion_linea_codigo CHECK (
        (nomenclador_id IS NULL AND codigo_nomenclador IS NULL AND porcentaje IS NULL AND monto_solicitado IS NULL
            AND NOT codigo_elegido)
     OR (nomenclador_id IS NOT NULL AND codigo_nomenclador IS NOT NULL AND porcentaje IS NOT NULL
            AND monto_solicitado IS NOT NULL)),
    CONSTRAINT liquidacion_linea_porcentaje CHECK (porcentaje BETWEEN 1 AND 100),
    -- RN-LQ-03: monto = precio x porcentaje / 100 a centavos HALF_UP, igual que presupuesto_item_cobertura de V13.
    -- round() de numeric redondea el medio alejándose de cero: con importes positivos es HALF_UP, igual que el dominio
    -- (BigDecimal, RoundingMode.HALF_UP). 100.0 es numeric: con enteros la división sería entera.
    CONSTRAINT liquidacion_linea_monto CHECK (monto_solicitado = round(precio * porcentaje / 100.0, 2)
        AND monto_solicitado > 0),
    CONSTRAINT liquidacion_linea_cobrado CHECK (monto_cobrado >= 0 AND monto_cobrado <= monto_solicitado),
    -- Sin resultado no hay nada cobrado ni quién ni cuándo. Con resultado, el monto cobrado lo acompaña. Se pide
    -- "resultado IS NOT NULL" a propósito: un monto cobrado sin resultado daría NULL en todas las ramas y una CHECK
    -- con NULL se acepta.
    CONSTRAINT liquidacion_linea_resultado CHECK (
        (resultado IS NULL AND monto_cobrado IS NULL AND conciliada_en IS NULL AND conciliada_por IS NULL)
     OR (resultado IS NOT NULL AND monto_cobrado IS NOT NULL AND (
            (resultado = 'COBRADA' AND monto_cobrado = monto_solicitado)
         OR (resultado = 'PARCIAL' AND monto_cobrado > 0 AND monto_cobrado < monto_solicitado)
         OR (resultado = 'RECHAZADA' AND monto_cobrado = 0)))),
    -- Solo se concilia una línea con código (con monto solicitado), y siempre con quién y cuándo.
    CONSTRAINT liquidacion_linea_conciliada CHECK (resultado IS NULL
        OR (monto_solicitado IS NOT NULL AND conciliada_en IS NOT NULL AND conciliada_por IS NOT NULL)),
    -- Una línea rechazada lleva motivo (1 a 200 caracteres, sin espacios en los extremos); las demás no.
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

-- Turnos que el ADMIN quitó a mano de un borrador: la regeneración no los vuelve a agregar a esa liquidación, pero
-- quedan libres para cualquier otra (RN-LQ-04). Solo hay filas de liquidaciones en BORRADOR.
CREATE TABLE liquidacion_turno_quitado (
    liquidacion_id BIGINT NOT NULL REFERENCES liquidacion (id),
    turno_id       BIGINT NOT NULL REFERENCES turno (id),
    PRIMARY KEY (liquidacion_id, turno_id)
);
CREATE INDEX liquidacion_turno_quitado_turno_idx ON liquidacion_turno_quitado (turno_id);

-- Inmutabilidad por estado en la base (RN-LQ-04 y RN-LQ-05), como los presupuestos de V13. Los mensajes son fijos y no
-- incluyen valores. El TRUNCATE de los tests no dispara triggers de fila.

-- Una liquidación no se borra; BORRADOR -> PRESENTADA -> CERRADA, sin volver atrás; la obra social y el período no
-- cambian; una presentada solo cambia su estado y lo cobrado; una cerrada no cambia.
CREATE FUNCTION liquidacion_aplicar_reglas() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Una liquidación no se borra';
    END IF;
    IF NEW.obra_social_id <> OLD.obra_social_id OR NEW.periodo <> OLD.periodo THEN
        RAISE EXCEPTION 'La obra social y el período de una liquidación no cambian';
    END IF;
    IF OLD.estado = 'CERRADA' THEN
        RAISE EXCEPTION 'Una liquidación cerrada no cambia';
    END IF;
    IF NOT ((OLD.estado = 'BORRADOR' AND NEW.estado IN ('BORRADOR', 'PRESENTADA'))
            OR (OLD.estado = 'PRESENTADA' AND NEW.estado IN ('PRESENTADA', 'CERRADA'))) THEN
        RAISE EXCEPTION 'La liquidación no admite esa transición de estado';
    END IF;
    IF OLD.estado = 'PRESENTADA'
       AND (to_jsonb(NEW) - 'estado' - 'total_cobrado' - 'cerrada_en' - 'cerrada_por')
           IS DISTINCT FROM (to_jsonb(OLD) - 'estado' - 'total_cobrado' - 'cerrada_en' - 'cerrada_por') THEN
        RAISE EXCEPTION 'Una liquidación presentada solo cambia su estado y lo cobrado';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER liquidacion_reglas
    BEFORE UPDATE OR DELETE ON liquidacion
    FOR EACH ROW EXECUTE FUNCTION liquidacion_aplicar_reglas();

-- Las líneas se agregan y se quitan solo en BORRADOR; una presentada solo registra la conciliación; una cerrada no
-- cambia. Lee el estado de la liquidación con FOR SHARE para no competir con un cambio de estado en curso.
CREATE FUNCTION liquidacion_linea_aplicar_reglas() RETURNS trigger AS $$
DECLARE
    estado_actual VARCHAR(20);
BEGIN
    SELECT l.estado INTO estado_actual FROM liquidacion l
        WHERE l.id = CASE WHEN TG_OP = 'DELETE' THEN OLD.liquidacion_id ELSE NEW.liquidacion_id END
        FOR SHARE;
    IF estado_actual IS NULL THEN
        -- Sin liquidación: la FK rechaza el INSERT con su propio error.
        RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
    END IF;
    IF TG_OP = 'INSERT' OR TG_OP = 'DELETE' THEN
        IF estado_actual <> 'BORRADOR' THEN
            RAISE EXCEPTION 'Las líneas de una liquidación solo se agregan o se quitan en BORRADOR';
        END IF;
        RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
    END IF;
    -- UPDATE
    IF estado_actual = 'CERRADA' THEN
        RAISE EXCEPTION 'Una liquidación cerrada no cambia';
    END IF;
    IF NEW.liquidacion_id <> OLD.liquidacion_id OR NEW.turno_id <> OLD.turno_id THEN
        RAISE EXCEPTION 'En BORRADOR una línea no cambia de liquidación ni de turno';
    END IF;
    IF estado_actual = 'BORRADOR' THEN
        IF NEW.monto_cobrado IS NOT NULL OR NEW.resultado IS NOT NULL OR NEW.motivo_rechazo IS NOT NULL
           OR NEW.conciliada_en IS NOT NULL OR NEW.conciliada_por IS NOT NULL THEN
            RAISE EXCEPTION 'En BORRADOR las líneas no se concilian';
        END IF;
    ELSIF (to_jsonb(NEW) - 'monto_cobrado' - 'resultado' - 'motivo_rechazo' - 'conciliada_en' - 'conciliada_por')
          IS DISTINCT FROM
          (to_jsonb(OLD) - 'monto_cobrado' - 'resultado' - 'motivo_rechazo' - 'conciliada_en' - 'conciliada_por') THEN
        RAISE EXCEPTION 'Una liquidación presentada solo cambia lo cobrado de sus líneas';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER liquidacion_linea_reglas
    BEFORE INSERT OR UPDATE OR DELETE ON liquidacion_linea
    FOR EACH ROW EXECUTE FUNCTION liquidacion_linea_aplicar_reglas();

-- Los turnos quitados solo se agregan o se sacan en BORRADOR.
CREATE FUNCTION liquidacion_turno_quitado_aplicar_reglas() RETURNS trigger AS $$
DECLARE
    estado_actual VARCHAR(20);
BEGIN
    SELECT l.estado INTO estado_actual FROM liquidacion l
        WHERE l.id = CASE WHEN TG_OP = 'DELETE' THEN OLD.liquidacion_id ELSE NEW.liquidacion_id END
        FOR SHARE;
    IF estado_actual IS NOT NULL AND estado_actual <> 'BORRADOR' THEN
        RAISE EXCEPTION 'Los turnos quitados de una liquidación solo cambian en BORRADOR';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER liquidacion_turno_quitado_reglas
    BEFORE INSERT OR DELETE ON liquidacion_turno_quitado
    FOR EACH ROW EXECUTE FUNCTION liquidacion_turno_quitado_aplicar_reglas();
