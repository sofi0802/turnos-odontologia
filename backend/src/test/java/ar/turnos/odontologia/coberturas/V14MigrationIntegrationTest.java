package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Esquema de V14 contra PostgreSQL real (C-20, tarea 1.1): la base repite el cálculo del monto solicitado (RN-LQ-03),
 * las reglas de la conciliación (RN-LQ-06) y la inmutabilidad por estado de la liquidación (RN-LQ-04 y RN-LQ-05, design
 * D2 y D3), así una escritura directa tampoco puede dejar una liquidación inconsistente. Solo datos ficticios.
 */
@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest
class V14MigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long pacienteUno;
    private long profesional;
    private long sillon;
    private long limpieza;
    private long consulta;
    private long ose;
    private long prepaga;
    private long planBase;
    private long planOro;
    private long codigo0201;
    private long codigo0101;
    private int minuto;

    @BeforeEach
    void datosFicticios() {
        jdbc.execute("TRUNCATE afiliacion_paciente, nomenclador, plan, obra_social, usuario, turno, paciente, "
                + "prestacion, sillon, profesional RESTART IDENTITY CASCADE");
        pacienteUno = id("INSERT INTO paciente(nombre, apellido, dni) VALUES ('Paciente','Uno','99000001') RETURNING id");
        profesional = id("INSERT INTO profesional(nombre, apellido, matricula) VALUES ('Ana','Ejemplo','MP-0001') RETURNING id");
        sillon = id("INSERT INTO sillon(nombre) VALUES ('Sillón 1') RETURNING id");
        consulta = id("INSERT INTO prestacion(nombre, duracion_minutos) VALUES ('Consulta o control', 30) RETURNING id");
        limpieza = id("INSERT INTO prestacion(nombre, duracion_minutos) VALUES ('Limpieza (profilaxis)', 45) RETURNING id");
        ose = id("INSERT INTO obra_social(nombre, sigla, tipo) VALUES ('Obra Social Ejemplo','OSE','OBRA_SOCIAL') RETURNING id");
        planBase = id("INSERT INTO plan(obra_social_id, nombre) VALUES (" + ose + ", 'Plan Base') RETURNING id");
        prepaga = id("INSERT INTO obra_social(nombre, tipo) VALUES ('Prepaga Prueba','PREPAGA') RETURNING id");
        planOro = id("INSERT INTO plan(obra_social_id, nombre) VALUES (" + prepaga + ", 'Plan Oro') RETURNING id");
        codigo0101 = id("INSERT INTO nomenclador(codigo, descripcion, prestacion_id) VALUES ('N-0101','Consulta',"
                + consulta + ") RETURNING id");
        codigo0201 = id("INSERT INTO nomenclador(codigo, descripcion, prestacion_id) VALUES ('N-0201','Limpieza',"
                + limpieza + ") RETURNING id");
        minuto = 0;
    }

    // --- Tablas, índices y triggers ---

    @Test
    @DisplayName("V14 crea las tres tablas, los índices y los triggers")
    void creaLasTablasLosIndicesYLosTriggers() {
        List<String> tablas = jdbc.queryForList("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name IN "
                + "('liquidacion','liquidacion_linea','liquidacion_turno_quitado')", String.class);
        assertThat(tablas).containsExactlyInAnyOrder("liquidacion", "liquidacion_linea", "liquidacion_turno_quitado");
        List<String> indices = jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname = 'public' "
                + "AND indexname LIKE 'liquidacion%'", String.class);
        assertThat(indices).contains("liquidacion_estado_idx", "liquidacion_linea_liquidacion_idx",
                "liquidacion_linea_paciente_idx", "liquidacion_linea_plan_idx", "liquidacion_linea_prestacion_idx",
                "liquidacion_linea_nomenclador_idx", "liquidacion_turno_quitado_turno_idx");
        List<String> triggers = jdbc.queryForList("SELECT tgname FROM pg_trigger WHERE NOT tgisinternal "
                + "AND tgname LIKE 'liquidacion%'", String.class);
        assertThat(triggers).containsExactlyInAnyOrder("liquidacion_reglas", "liquidacion_linea_reglas",
                "liquidacion_turno_quitado_reglas");
    }

    // --- Liquidación ---

    @Test
    @DisplayName("La base rechaza una liquidación repetida por obra social y período")
    void liquidacionRepetida() {
        liquidacion(ose, "2026-10-01");
        assertRechaza(() -> liquidacion(ose, "2026-10-01"), "liquidacion_periodo_unico");
        assertThatCode(() -> liquidacion(prepaga, "2026-10-01")).doesNotThrowAnyException();
        assertThatCode(() -> liquidacion(ose, "2026-09-01")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("La base rechaza un período que no es el día 1 y un estado desconocido")
    void periodoYEstadoInvalidos() {
        assertRechaza(() -> liquidacion(ose, "2026-10-15"), "liquidacion_periodo_dia_1");
        assertRechaza(() -> jdbc.update("INSERT INTO liquidacion(obra_social_id, periodo, estado, generada_en, "
                + "generada_por) VALUES (?, DATE '2026-08-01', 'ANULADA', now(), 'admin')", ose),
                "liquidacion_estado_valido");
    }

    @Test
    @DisplayName("La base rechaza un total cobrado mayor que el solicitado y totales negativos")
    void totalesInvalidos() {
        long liquidacion = liquidacion(ose, "2026-10-01");
        assertRechaza(() -> jdbc.update("UPDATE liquidacion SET total_solicitado = 100.00, total_cobrado = 100.01 "
                + "WHERE id = ?", liquidacion), "liquidacion_totales");
        assertRechaza(() -> jdbc.update("UPDATE liquidacion SET total_solicitado = -1.00 WHERE id = ?", liquidacion),
                "liquidacion_totales");
    }

    @Test
    @DisplayName("La base rechaza presentar sin fecha de presentación y fechas que no corresponden al estado")
    void fechasSegunElEstado() {
        long liquidacion = liquidacion(ose, "2026-10-01");
        assertRechaza(() -> jdbc.update("UPDATE liquidacion SET estado = 'PRESENTADA' WHERE id = ?", liquidacion),
                "liquidacion_fechas_estado");
        assertRechaza(() -> jdbc.update("UPDATE liquidacion SET presentada_en = now(), presentada_por = 'admin' "
                + "WHERE id = ?", liquidacion), "liquidacion_fechas_estado");
        assertThat(jdbc.update("UPDATE liquidacion SET estado = 'PRESENTADA', presentada_en = now(), "
                + "presentada_por = 'admin' WHERE id = ?", liquidacion)).isEqualTo(1);
    }

    // --- Línea ---

    @Test
    @DisplayName("La base rechaza el mismo turno en dos liquidaciones")
    void elMismoTurnoDosVeces() {
        long turno = turno();
        long octubre = liquidacion(ose, "2026-10-01");
        long noviembre = liquidacion(ose, "2026-11-01");
        linea(octubre, turno, "25000.00", 50, "12500.00");
        assertRechaza(() -> linea(noviembre, turno, "25000.00", 50, "12500.00"), "liquidacion_linea_turno_unico");
    }

    @Test
    @DisplayName("La base rechaza una línea con un plan de otra obra social")
    void planDeOtraObraSocial() {
        long liquidacion = liquidacion(ose, "2026-10-01");
        // la liquidación y la línea son de OSE, pero el plan es de la prepaga
        assertRechaza(() -> jdbc.update(lineaSql(liquidacion, ose, turno(), planOro, "25000.00", 50, "12500.00")),
                "liquidacion_linea_plan_fk");
    }

    @Test
    @DisplayName("La base rechaza una línea colgada de la liquidación de otra obra social")
    void liquidacionDeOtraObraSocial() {
        long deLaPrepaga = liquidacion(prepaga, "2026-10-01");
        // la línea declara la obra social OSE (por su plan) pero la liquidación es de la prepaga
        assertRechaza(() -> jdbc.update(lineaSql(deLaPrepaga, ose, turno(), planBase, "25000.00", 50, "12500.00")),
                "liquidacion_linea_liquidacion_fk");
    }

    @Test
    @DisplayName("La base rechaza un código sin porcentaje ni monto y un porcentaje sin código")
    void codigoPorcentajeYMontoVanJuntos() {
        long liquidacion = liquidacion(ose, "2026-10-01");
        assertRechaza(() -> jdbc.update("INSERT INTO liquidacion_linea(liquidacion_id, obra_social_id, turno_id, "
                + "paciente_id, plan_id, prestacion_id, fecha, paciente_nombre, paciente_dni, numero_afiliado, "
                + "plan_nombre, prestacion_nombre, precio, origen_precio, nomenclador_id, codigo_nomenclador) "
                + "VALUES (?,?,?,?,?,?, DATE '2026-10-20','Paciente Uno','99000001','OSE-000001','Plan Base',"
                + "'Limpieza (profilaxis)',25000.00,'ARANCEL',?,'N-0201')", liquidacion, ose, turno(), pacienteUno,
                planBase, limpieza, codigo0201), "liquidacion_linea_codigo");
        assertRechaza(() -> jdbc.update("INSERT INTO liquidacion_linea(liquidacion_id, obra_social_id, turno_id, "
                + "paciente_id, plan_id, prestacion_id, fecha, paciente_nombre, paciente_dni, numero_afiliado, "
                + "plan_nombre, prestacion_nombre, precio, origen_precio, porcentaje) "
                + "VALUES (?,?,?,?,?,?, DATE '2026-10-20','Paciente Uno','99000001','OSE-000001','Plan Base',"
                + "'Limpieza (profilaxis)',25000.00,'ARANCEL',50)", liquidacion, ose, turno(), pacienteUno, planBase,
                limpieza), "liquidacion_linea_codigo");
        assertThatCode(() -> sinCodigo(liquidacion, turno(), "25000.00")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("La base rechaza un monto solicitado que no es el precio por el porcentaje redondeado half up")
    void montoSolicitadoHalfUp() {
        long liquidacion = liquidacion(ose, "2026-10-01");
        assertRechaza(() -> linea(liquidacion, turno(), "25000.15", 50, "12500.07"), "liquidacion_linea_monto");
        assertRechaza(() -> linea(liquidacion, turno(), "25000.15", 50, "12500.09"), "liquidacion_linea_monto");
        assertThatCode(() -> linea(liquidacion, turno(), "25000.15", 50, "12500.08")).doesNotThrowAnyException();
        assertRechaza(() -> linea(liquidacion, turno(), "25000.00", 50, "12500.01"), "liquidacion_linea_monto");
        // con 0 % el monto es 0 y la base nombra primero la restricción del monto (orden alfabético de las CHECK)
        assertRechaza(() -> linea(liquidacion, turno(), "25000.00", 0, "0.00"), "liquidacion_linea_monto");
        assertRechaza(() -> linea(liquidacion, turno(), "25000.00", 101, "25250.00"), "liquidacion_linea_porcentaje");
    }

    @Test
    @DisplayName("La base rechaza un monto cobrado mayor que el solicitado o negativo")
    void montoCobradoFueraDeRango() {
        long liquidacion = liquidacion(ose, "2026-10-01");
        long linea = linea(liquidacion, turno(), "25000.00", 50, "12500.00");
        presentar(liquidacion);
        assertRechaza(() -> conciliar(linea, "12500.01", "COBRADA", null), "liquidacion_linea_cobrado");
        assertRechaza(() -> conciliar(linea, "-1.00", "RECHAZADA", "Motivo ficticio"), "liquidacion_linea_cobrado");
    }

    @Test
    @DisplayName("La base rechaza un resultado que no coincide con el monto cobrado")
    void resultadoCoherenteConElMonto() {
        long liquidacion = liquidacion(ose, "2026-10-01");
        long linea = linea(liquidacion, turno(), "25000.00", 50, "12500.00");
        presentar(liquidacion);
        assertRechaza(() -> conciliar(linea, "10000.00", "COBRADA", null), "liquidacion_linea_resultado");
        assertRechaza(() -> conciliar(linea, "0.00", "PARCIAL", null), "liquidacion_linea_resultado");
        assertRechaza(() -> conciliar(linea, "12500.00", "PARCIAL", null), "liquidacion_linea_resultado");
        assertRechaza(() -> conciliar(linea, "10000.00", "RECHAZADA", "Motivo ficticio"), "liquidacion_linea_resultado");
        assertRechaza(() -> conciliar(linea, "10000.00", "OTRO", null), "liquidacion_linea_resultado");
        assertThatCode(() -> conciliar(linea, "12500.00", "COBRADA", null)).doesNotThrowAnyException();
        assertThatCode(() -> conciliar(linea, "10000.00", "PARCIAL", null)).doesNotThrowAnyException();
        assertThatCode(() -> conciliar(linea, "0.00", "RECHAZADA", "Falta la orden de la práctica"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("La base rechaza un rechazo sin motivo, con motivo en blanco o un motivo en una línea cobrada")
    void motivoDelRechazo() {
        long liquidacion = liquidacion(ose, "2026-10-01");
        long linea = linea(liquidacion, turno(), "25000.00", 50, "12500.00");
        presentar(liquidacion);
        assertRechaza(() -> conciliar(linea, "0.00", "RECHAZADA", null), "liquidacion_linea_motivo");
        assertRechaza(() -> conciliar(linea, "0.00", "RECHAZADA", ""), "liquidacion_linea_motivo");
        assertRechaza(() -> conciliar(linea, "0.00", "RECHAZADA", "   "), "liquidacion_linea_motivo");
        assertRechaza(() -> conciliar(linea, "0.00", "RECHAZADA", " con espacios "), "liquidacion_linea_motivo");
        assertRechaza(() -> conciliar(linea, "12500.00", "COBRADA", "Motivo ficticio"), "liquidacion_linea_motivo");
        assertRechaza(() -> conciliar(linea, "10000.00", "PARCIAL", "Motivo ficticio"), "liquidacion_linea_motivo");
    }

    @Test
    @DisplayName("La base rechaza conciliar una línea sin código y un resultado sin quién ni cuándo")
    void conciliacionSinCodigoOSinActor() {
        long liquidacion = liquidacion(ose, "2026-10-01");
        long sinCodigo = sinCodigo(liquidacion, turno(), "15000.00");
        long conCodigo = linea(liquidacion, turno(), "25000.00", 50, "12500.00");
        presentar(liquidacion);
        assertRechaza(() -> conciliar(sinCodigo, "0.00", "RECHAZADA", "Motivo ficticio"),
                "liquidacion_linea_conciliada");
        assertRechaza(() -> jdbc.update("UPDATE liquidacion_linea SET monto_cobrado = 12500.00, resultado = 'COBRADA' "
                + "WHERE id = ?", conCodigo), "liquidacion_linea_conciliada");
        assertRechaza(() -> jdbc.update("UPDATE liquidacion_linea SET monto_cobrado = 12500.00 WHERE id = ?", conCodigo),
                "liquidacion_linea_resultado");
    }

    // --- Triggers ---

    @Test
    @DisplayName("La base rechaza borrar una liquidación en cualquier estado")
    void noSeBorraUnaLiquidacion() {
        long borrador = liquidacion(ose, "2026-10-01");
        long presentada = liquidacion(ose, "2026-09-01");
        presentar(presentada);
        long cerrada = liquidacion(ose, "2026-08-01");
        presentar(cerrada);
        cerrar(cerrada);
        for (long liquidacion : new long[] {borrador, presentada, cerrada}) {
            assertRechaza(() -> jdbc.update("DELETE FROM liquidacion WHERE id = ?", liquidacion), "no se borra");
        }
        assertThat(cantidad("liquidacion")).isEqualTo(3);
    }

    @Test
    @DisplayName("La base rechaza las transiciones de estado que no son BORRADOR, PRESENTADA y CERRADA en ese orden")
    void transicionesDeEstado() {
        long borrador = liquidacion(ose, "2026-10-01");
        assertRechaza(() -> jdbc.update("UPDATE liquidacion SET estado = 'CERRADA', presentada_en = now(), "
                + "presentada_por = 'admin', cerrada_en = now(), cerrada_por = 'admin' WHERE id = ?", borrador),
                "transición");
        long presentada = liquidacion(ose, "2026-09-01");
        presentar(presentada);
        assertRechaza(() -> jdbc.update("UPDATE liquidacion SET estado = 'BORRADOR', presentada_en = NULL, "
                + "presentada_por = NULL WHERE id = ?", presentada), "transición");
        assertThat(estado(presentada)).isEqualTo("PRESENTADA");
        assertThat(estado(borrador)).isEqualTo("BORRADOR");
        assertThatCode(() -> cerrar(presentada)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("La base rechaza cambiar el período, la obra social o el total solicitado de una presentada")
    void presentadaNoCambiaSusDatos() {
        long liquidacion = liquidacion(ose, "2026-10-01");
        linea(liquidacion, turno(), "25000.00", 50, "12500.00");
        jdbc.update("UPDATE liquidacion SET total_solicitado = 12500.00 WHERE id = ?", liquidacion);
        presentar(liquidacion);
        assertRechaza(() -> jdbc.update("UPDATE liquidacion SET total_solicitado = 1.00 WHERE id = ?", liquidacion),
                "presentada");
        assertRechaza(() -> jdbc.update("UPDATE liquidacion SET periodo = DATE '2026-09-01' WHERE id = ?", liquidacion),
                "período");
        assertRechaza(() -> jdbc.update("UPDATE liquidacion SET obra_social_id = ? WHERE id = ?", prepaga, liquidacion),
                "período");
        assertThat(jdbc.queryForObject("SELECT total_solicitado FROM liquidacion WHERE id = ?", BigDecimal.class,
                liquidacion)).isEqualByComparingTo("12500.00");
    }

    @Test
    @DisplayName("La base rechaza cualquier cambio en una liquidación cerrada")
    void cerradaNoCambia() {
        long liquidacion = liquidacion(ose, "2026-10-01");
        presentar(liquidacion);
        cerrar(liquidacion);
        assertRechaza(() -> jdbc.update("UPDATE liquidacion SET estado = 'PRESENTADA', cerrada_en = NULL, "
                + "cerrada_por = NULL WHERE id = ?", liquidacion), "cerrada");
        assertRechaza(() -> jdbc.update("UPDATE liquidacion SET generada_por = 'otro' WHERE id = ?", liquidacion),
                "cerrada");
        assertRechaza(() -> jdbc.update("UPDATE liquidacion SET total_cobrado = 0 WHERE id = ?", liquidacion), "cerrada");
        assertThat(estado(liquidacion)).isEqualTo("CERRADA");
    }

    @Test
    @DisplayName("La base rechaza insertar o borrar líneas y turnos quitados fuera de BORRADOR")
    void lineasYQuitadosSoloEnBorrador() {
        long liquidacion = liquidacion(ose, "2026-10-01");
        long linea = linea(liquidacion, turno(), "25000.00", 50, "12500.00");
        long turnoQuitado = turno();
        jdbc.update("INSERT INTO liquidacion_turno_quitado(liquidacion_id, turno_id) VALUES (?,?)", liquidacion,
                turnoQuitado);
        presentar(liquidacion);
        assertRechaza(() -> linea(liquidacion, turno(), "25000.00", 50, "12500.00"), "BORRADOR");
        assertRechaza(() -> jdbc.update("DELETE FROM liquidacion_linea WHERE id = ?", linea), "BORRADOR");
        assertRechaza(() -> jdbc.update("INSERT INTO liquidacion_turno_quitado(liquidacion_id, turno_id) VALUES (?,?)",
                liquidacion, turno()), "BORRADOR");
        assertRechaza(() -> jdbc.update("DELETE FROM liquidacion_turno_quitado WHERE liquidacion_id = ?", liquidacion),
                "BORRADOR");
        assertThat(cantidad("liquidacion_linea")).isEqualTo(1);
        assertThat(cantidad("liquidacion_turno_quitado")).isEqualTo(1);
    }

    @Test
    @DisplayName("La base acepta cambiar y borrar líneas de un borrador, pero no conciliarlas")
    void borradorAdmiteCambiosPeroNoConciliacion() {
        long liquidacion = liquidacion(ose, "2026-10-01");
        long linea = linea(liquidacion, turno(), "25000.00", 50, "12500.00");
        assertThat(jdbc.update("UPDATE liquidacion_linea SET precio = 26000.00, monto_solicitado = 13000.00 "
                + "WHERE id = ?", linea)).isEqualTo(1);
        assertRechaza(() -> conciliar(linea, "13000.00", "COBRADA", null), "BORRADOR");
        assertRechaza(() -> jdbc.update("UPDATE liquidacion_linea SET turno_id = ? WHERE id = ?", turno(), linea),
                "BORRADOR");
        assertThat(jdbc.update("DELETE FROM liquidacion_linea WHERE id = ?", linea)).isEqualTo(1);
    }

    @Test
    @DisplayName("La base rechaza cambiar el monto solicitado o la foto de una línea presentada")
    void lineaPresentadaNoCambiaSuFoto() {
        long liquidacion = liquidacion(ose, "2026-10-01");
        long linea = linea(liquidacion, turno(), "25000.00", 50, "12500.00");
        presentar(liquidacion);
        assertRechaza(() -> jdbc.update("UPDATE liquidacion_linea SET precio = 26000.00, monto_solicitado = 13000.00 "
                + "WHERE id = ?", linea), "presentada");
        assertRechaza(() -> jdbc.update("UPDATE liquidacion_linea SET plan_nombre = 'Plan Base 2027' WHERE id = ?",
                linea), "presentada");
        assertRechaza(() -> jdbc.update("UPDATE liquidacion_linea SET paciente_dni = '99000002' WHERE id = ?", linea),
                "presentada");
        assertThat(jdbc.queryForObject("SELECT monto_solicitado FROM liquidacion_linea WHERE id = ?", BigDecimal.class,
                linea)).isEqualByComparingTo("12500.00");
    }

    @Test
    @DisplayName("La base acepta conciliar una línea presentada y corregir la conciliación")
    void presentadaAceptaConciliar() {
        long liquidacion = liquidacion(ose, "2026-10-01");
        long linea = linea(liquidacion, turno(), "25000.00", 50, "12500.00");
        presentar(liquidacion);
        conciliar(linea, "10000.00", "PARCIAL", null);
        conciliar(linea, "12500.00", "COBRADA", null);
        jdbc.update("UPDATE liquidacion SET total_cobrado = 0 WHERE id = ?", liquidacion);
        assertThat(jdbc.queryForObject("SELECT resultado FROM liquidacion_linea WHERE id = ?", String.class, linea))
                .isEqualTo("COBRADA");
    }

    @Test
    @DisplayName("La base rechaza cambiar el monto cobrado de una línea cerrada")
    void lineaCerradaNoCambia() {
        long liquidacion = liquidacion(ose, "2026-10-01");
        long linea = linea(liquidacion, turno(), "25000.00", 50, "12500.00");
        presentar(liquidacion);
        conciliar(linea, "10000.00", "PARCIAL", null);
        cerrar(liquidacion);
        assertRechaza(() -> conciliar(linea, "12500.00", "COBRADA", null), "cerrada");
        assertRechaza(() -> jdbc.update("DELETE FROM liquidacion_linea WHERE id = ?", linea), "BORRADOR");
        assertThat(jdbc.queryForObject("SELECT monto_cobrado FROM liquidacion_linea WHERE id = ?", BigDecimal.class,
                linea)).isEqualByComparingTo("10000.00");
    }

    // --- Ayudas ---

    private long liquidacion(long obraSocial, String periodo) {
        return id("INSERT INTO liquidacion(obra_social_id, periodo, generada_en, generada_por) VALUES (" + obraSocial
                + ", DATE '" + periodo + "', now(), 'admin') RETURNING id");
    }

    private void presentar(long liquidacion) {
        jdbc.update("UPDATE liquidacion SET estado = 'PRESENTADA', presentada_en = now(), presentada_por = 'admin' "
                + "WHERE id = ?", liquidacion);
    }

    private void cerrar(long liquidacion) {
        jdbc.update("UPDATE liquidacion SET estado = 'CERRADA', cerrada_en = now(), cerrada_por = 'admin' WHERE id = ?",
                liquidacion);
    }

    private String estado(long liquidacion) {
        return jdbc.queryForObject("SELECT estado FROM liquidacion WHERE id = ?", String.class, liquidacion);
    }

    /** Un turno ATENDIDO ficticio de "Limpieza (profilaxis)" de "Paciente Uno", cada uno en su propio minuto. */
    private long turno() {
        minuto += 60;
        return id("INSERT INTO turno(profesional_id, sillon_id, prestacion_id, paciente_id, inicio, fin, "
                + "fin_con_margen, estado) VALUES (" + profesional + "," + sillon + "," + limpieza + "," + pacienteUno
                + ", TIMESTAMPTZ '2026-10-01 08:00:00-03' + interval '" + minuto + " minutes', TIMESTAMPTZ "
                + "'2026-10-01 08:30:00-03' + interval '" + minuto + " minutes', TIMESTAMPTZ '2026-10-01 08:40:00-03' "
                + "+ interval '" + minuto + " minutes', 'ATENDIDO') RETURNING id");
    }

    private long linea(long liquidacion, long turno, String precio, int porcentaje, String monto) {
        return id(lineaSql(liquidacion, ose, turno, planBase, precio, porcentaje, monto) + " RETURNING id");
    }

    private String lineaSql(long liquidacion, long obraSocial, long turno, long plan, String precio, int porcentaje,
                            String monto) {
        return "INSERT INTO liquidacion_linea(liquidacion_id, obra_social_id, turno_id, paciente_id, plan_id, "
                + "prestacion_id, fecha, paciente_nombre, paciente_dni, numero_afiliado, plan_nombre, "
                + "prestacion_nombre, precio, origen_precio, nomenclador_id, codigo_nomenclador, porcentaje, "
                + "monto_solicitado) SELECT " + liquidacion + ", " + obraSocial + ", " + turno + ", " + pacienteUno + ", "
                + plan + ", " + limpieza + ", DATE '2026-10-20', 'Paciente Uno', '99000001', 'OSE-000001', p.nombre, "
                + "'Limpieza (profilaxis)', " + precio + ", 'ARANCEL', " + codigo0201 + ", 'N-0201', " + porcentaje
                + ", " + monto + " FROM plan p WHERE p.id = " + plan;
    }

    private long sinCodigo(long liquidacion, long turno, String precio) {
        return id("INSERT INTO liquidacion_linea(liquidacion_id, obra_social_id, turno_id, paciente_id, plan_id, "
                + "prestacion_id, fecha, paciente_nombre, paciente_dni, numero_afiliado, plan_nombre, "
                + "prestacion_nombre, precio, origen_precio) VALUES (" + liquidacion + "," + ose + "," + turno + ","
                + pacienteUno + "," + planBase + "," + consulta + ", DATE '2026-10-06', 'Paciente Uno', '99000001', "
                + "'OSE-000001', 'Plan Base', 'Consulta o control', " + precio + ", 'ARANCEL') RETURNING id");
    }

    private void conciliar(long linea, String cobrado, String resultado, String motivo) {
        jdbc.update("UPDATE liquidacion_linea SET monto_cobrado = CAST(? AS numeric), resultado = ?, "
                + "motivo_rechazo = ?, conciliada_en = now(), conciliada_por = 'admin' WHERE id = ?", cobrado, resultado,
                motivo, linea);
    }

    private int cantidad(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private static void assertRechaza(Runnable accion, String fragmento) {
        assertThatThrownBy(accion::run).hasMessageContaining(fragmento);
    }
}
