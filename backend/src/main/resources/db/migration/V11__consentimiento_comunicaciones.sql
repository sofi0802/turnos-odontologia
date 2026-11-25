-- C-17 consentimiento-comunicaciones. El estado vigente sigue en paciente.consentimiento_comunicaciones y
-- paciente.consentimiento_fecha (V7); acá va el historial de cada cambio (Ley 25.326, RN-NO-03, design D1).
-- Los pacientes existentes no reciben eventos: todos están sin consentimiento, que es el estado "sin historial".

CREATE TABLE consentimiento_comunicacion_evento (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paciente_id   BIGINT      NOT NULL REFERENCES paciente (id),
    otorgado      BOOLEAN     NOT NULL,
    registrado_en TIMESTAMPTZ NOT NULL,
    origen        VARCHAR(20) NOT NULL,
    medio         VARCHAR(20),
    usuario_id    BIGINT      REFERENCES usuario (id),

    CONSTRAINT consentimiento_evento_origen_valido CHECK (origen IN ('PACIENTE', 'REGISTRO', 'RECEPCION')),
    CONSTRAINT consentimiento_evento_medio_valido CHECK (medio IS NULL OR medio IN ('TELEFONO', 'PRESENCIAL')),
    -- El medio y el usuario del personal van solo y siempre con RECEPCION.
    CONSTRAINT consentimiento_evento_recepcion CHECK (
        (origen = 'RECEPCION' AND medio IS NOT NULL AND usuario_id IS NOT NULL)
        OR (origen <> 'RECEPCION' AND medio IS NULL AND usuario_id IS NULL))
);

CREATE INDEX consentimiento_evento_paciente_idx ON consentimiento_comunicacion_evento (paciente_id, id);

-- El historial no se modifica ni se borra (spec "Historial de cambios del consentimiento").
CREATE FUNCTION consentimiento_evento_inmutable() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'El historial del consentimiento no se modifica ni se borra';
END;
$$;

CREATE TRIGGER consentimiento_evento_sin_cambios
    BEFORE UPDATE OR DELETE ON consentimiento_comunicacion_evento
    FOR EACH ROW EXECUTE FUNCTION consentimiento_evento_inmutable();
