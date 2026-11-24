package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Esquema de V13 contra PostgreSQL real (C-19, tarea 1.1): la base repite el cálculo de RN-PR-03, la foto de RN-PR-04
 * y la inmutabilidad de RN-PR-05 (design D2 y D5), así una escritura directa tampoco puede dejar un presupuesto
 * inconsistente. Solo datos ficticios.
 */
@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest
class V13MigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long pacienteUno;
    private long pacienteDos;
    private long profesional;
    private long consulta;
    private long limpieza;
    private long conducto;
    private long planBase;
    private long planOro;
    private long codigo0101;
    private long codigo0201;
    private long afiliacionUno;
    private long afiliacionDos;

    @BeforeEach
    void datosFicticios() {
        jdbc.execute("TRUNCATE afiliacion_paciente, nomenclador, plan, obra_social, usuario, turno, paciente, "
                + "prestacion, sillon, profesional RESTART IDENTITY CASCADE");
        pacienteUno = id("INSERT INTO paciente(nombre, apellido, dni) VALUES ('Paciente','Uno','99000001') RETURNING id");
        pacienteDos = id("INSERT INTO paciente(nombre, apellido, dni) VALUES ('Paciente','Dos','99000002') RETURNING id");
        profesional = id("INSERT INTO profesional(nombre, apellido, matricula) VALUES ('Ana','Ejemplo','MP-0001') RETURNING id");
        consulta = id("INSERT INTO prestacion(nombre, duracion_minutos) VALUES ('Consulta o control', 30) RETURNING id");
        limpieza = id("INSERT INTO prestacion(nombre, duracion_minutos) VALUES ('Limpieza (profilaxis)', 45) RETURNING id");
        conducto = id("INSERT INTO prestacion(nombre, duracion_minutos) VALUES ('Tratamiento de conducto', 60) RETURNING id");
        long ose = id("INSERT INTO obra_social(nombre, sigla, tipo) VALUES ('Obra Social Ejemplo','OSE','OBRA_SOCIAL') RETURNING id");
        planBase = id("INSERT INTO plan(obra_social_id, nombre) VALUES (" + ose + ", 'Plan Base') RETURNING id");
        long prepaga = id("INSERT INTO obra_social(nombre, tipo) VALUES ('Prepaga Prueba','PREPAGA') RETURNING id");
        planOro = id("INSERT INTO plan(obra_social_id, nombre) VALUES (" + prepaga + ", 'Plan Oro') RETURNING id");
        codigo0101 = id("INSERT INTO nomenclador(codigo, descripcion, prestacion_id) VALUES ('N-0101','Consulta',"
                + consulta + ") RETURNING id");
        codigo0201 = id("INSERT INTO nomenclador(codigo, descripcion, prestacion_id) VALUES ('N-0201','Limpieza',"
                + limpieza + ") RETURNING id");
        afiliacionUno = afiliar(pacienteUno, planBase, "OSE-000001");
        afiliacionDos = afiliar(pacienteDos, planOro, "PP-000002");
    }

    // --- Tablas y restricciones de aranceles y coberturas ---

    @Test
    @DisplayName("V13 crea las cuatro tablas, los triggers y la restricción de la afiliación")
    void creaLasTablasYLosTriggers() {
        List<String> tablas = jdbc.queryForList("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name IN "
                + "('arancel','cobertura_plan','presupuesto','presupuesto_item')", String.class);
        assertThat(tablas).containsExactlyInAnyOrder("arancel", "cobertura_plan", "presupuesto", "presupuesto_item");
        List<String> triggers = jdbc.queryForList("SELECT tgname FROM pg_trigger WHERE NOT tgisinternal "
                + "AND tgname IN ('presupuesto_item_solo_agregado','presupuesto_solo_estado')", String.class);
        assertThat(triggers).containsExactlyInAnyOrder("presupuesto_item_solo_agregado", "presupuesto_solo_estado");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE conname = 'afiliacion_id_paciente_unico'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("La base rechaza un precio cero y un precio repetido para la misma prestación")
    void arancelInvalido() {
        assertRechaza(() -> arancel(consulta, "0.00"), "arancel_precio_positivo");
        assertRechaza(() -> arancel(consulta, "-1.00"), "arancel_precio_positivo");
        arancel(consulta, "15000.00");
        assertRechaza(() -> arancel(consulta, "16000.00"), "arancel_prestacion_unica");
    }

    @Test
    @DisplayName("La base rechaza un porcentaje de 101 y un par plan y código repetido")
    void coberturaInvalida() {
        assertRechaza(() -> cobertura(planBase, codigo0101, 101), "cobertura_plan_porcentaje_valido");
        assertRechaza(() -> cobertura(planBase, codigo0101, -1), "cobertura_plan_porcentaje_valido");
        assertThatCode(() -> cobertura(planBase, codigo0101, 0)).doesNotThrowAnyException();
        assertThatCode(() -> cobertura(planOro, codigo0101, 100)).doesNotThrowAnyException();
        assertRechaza(() -> cobertura(planBase, codigo0101, 50), "cobertura_plan_unica");
    }

    // --- Presupuesto ---

    @Test
    @DisplayName("La base rechaza un vencimiento que no es la fecha más 30 días")
    void vencimientoInvalido() {
        assertRechaza(() -> presupuesto(pacienteUno, null, "2026-11-24", "2026-12-23", "40000.00", "27500.00", "12500.00"),
                "presupuesto_vencimiento");
        assertThatCode(() -> presupuesto(pacienteUno, null, "2026-11-24", "2026-12-24", "40000.00", "27500.00", "12500.00"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("La base rechaza totales que no suman")
    void totalesInvalidos() {
        assertRechaza(() -> presupuesto(pacienteUno, null, "2026-11-24", "2026-12-24", "40000.00", "27500.00", "12500.01"),
                "presupuesto_totales");
        assertRechaza(() -> presupuesto(pacienteUno, null, "2026-11-24", "2026-12-24", "0.00", "0.00", "0.00"),
                "presupuesto_totales");
    }

    @Test
    @DisplayName("La base rechaza la afiliación de otro paciente")
    void afiliacionDeOtroPaciente() {
        assertRechaza(() -> presupuesto(pacienteUno, afiliacionDos, "2026-11-24", "2026-12-24", "40000.00", "27500.00",
                "12500.00"), "presupuesto_afiliacion_fk");
        assertThatCode(() -> presupuesto(pacienteUno, afiliacionUno, "2026-11-24", "2026-12-24", "40000.00", "27500.00",
                "12500.00")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("La base rechaza nombres de cobertura sin afiliación y afiliación sin nombres")
    void fotoDeCoberturaIncompleta() {
        assertRechaza(() -> jdbc.update("INSERT INTO presupuesto(paciente_id, profesional_id, fecha, vence_el, "
                + "obra_social_nombre, plan_nombre, numero_afiliado, total_precio, total_cobertura, total_copago, "
                + "creado_en) VALUES (?,?,DATE '2026-11-24',DATE '2026-12-24','Obra Social Ejemplo','Plan Base',"
                + "'OSE-000001',100,0,100,now())", pacienteUno, profesional), "presupuesto_foto_cobertura");
        assertRechaza(() -> jdbc.update("INSERT INTO presupuesto(paciente_id, profesional_id, afiliacion_id, fecha, "
                + "vence_el, total_precio, total_cobertura, total_copago, creado_en) "
                + "VALUES (?,?,?,DATE '2026-11-24',DATE '2026-12-24',100,0,100,now())",
                pacienteUno, profesional, afiliacionUno), "presupuesto_foto_cobertura");
    }

    @Test
    @DisplayName("La base rechaza un estado inválido")
    void estadoInvalido() {
        assertRechaza(() -> jdbc.update("INSERT INTO presupuesto(paciente_id, profesional_id, fecha, vence_el, estado, "
                + "total_precio, total_cobertura, total_copago, creado_en) VALUES (?,?,DATE '2026-11-24',"
                + "DATE '2026-12-24','VENCIDO',100,0,100,now())", pacienteUno, profesional),
                "presupuesto_estado_valido");
    }

    // --- Ítems ---

    @Test
    @DisplayName("La base rechaza un importe mal calculado y acepta uno bien calculado")
    void importeDelItem() {
        long p = presupuestoParticular();
        assertRechaza(() -> item(p, limpieza, null, null, null, 1, "25000.00", 0, "24999.99", "0.00", "24999.99"),
                "presupuesto_item_importe");
        assertRechaza(() -> item(p, limpieza, null, null, null, 2, "25000.00", 0, "25000.00", "0.00", "25000.00"),
                "presupuesto_item_importe");
        assertThatCode(() -> item(p, limpieza, null, null, null, 2, "25000.00", 0, "50000.00", "0.00", "50000.00"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("La base rechaza cobertura 12500.07 para el 50 % de 25000.15 y acepta 12500.08")
    void redondeoHalfUpDeLaCobertura() {
        long p = presupuestoParticular();
        assertRechaza(() -> item(p, limpieza, codigo0201, "N-0201", null, 1, "25000.15", 50, "25000.15", "12500.07",
                "12500.08"), "presupuesto_item_cobertura");
        assertThatCode(() -> item(p, limpieza, codigo0201, "N-0201", null, 1, "25000.15", 50, "25000.15", "12500.08",
                "12500.07")).doesNotThrowAnyException();
        // 12500.075 es el medio exacto: solo el redondeo hacia arriba (12500.08) lo acepta; hacia abajo no
        assertRechaza(() -> item(p, limpieza, codigo0201, "N-0201", null, 1, "25000.15", 50, "25000.15", "12500.06",
                "12500.09"), "presupuesto_item_cobertura");
    }

    @Test
    @DisplayName("El round de la base coincide con HALF_UP de Java en los bordes y en importes al azar")
    void roundDeLaBaseCoincideConHalfUp() {
        List<BigDecimal[]> casos = new ArrayList<>();
        casos.add(new BigDecimal[] {new BigDecimal("25000.15"), BigDecimal.valueOf(50)});
        casos.add(new BigDecimal[] {new BigDecimal("0.01"), BigDecimal.valueOf(50)});
        casos.add(new BigDecimal[] {new BigDecimal("0.05"), BigDecimal.valueOf(10)});
        casos.add(new BigDecimal[] {new BigDecimal("0.01"), BigDecimal.valueOf(49)});
        casos.add(new BigDecimal[] {new BigDecimal("0.03"), BigDecimal.valueOf(50)});
        casos.add(new BigDecimal[] {new BigDecimal("9999999999.99"), BigDecimal.valueOf(99)});
        Random azar = new Random(19);
        for (int i = 0; i < 300; i++) {
            casos.add(new BigDecimal[] {BigDecimal.valueOf(1 + azar.nextInt(99_999_999), 2),
                    BigDecimal.valueOf(azar.nextInt(101))});
        }
        for (BigDecimal[] caso : casos) {
            BigDecimal esperado = caso[0].multiply(caso[1]).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal enLaBase = jdbc.queryForObject(
                    "SELECT round(CAST(? AS numeric) * ? / 100.0, 2)", BigDecimal.class, caso[0], caso[1].intValue());
            assertThat(enLaBase).as("importe %s al %s %%", caso[0], caso[1]).isEqualByComparingTo(esperado);
        }
    }

    @Test
    @DisplayName("La base rechaza un porcentaje mayor que cero sin código y un código a medias")
    void porcentajeSinCodigo() {
        long p = presupuestoParticular();
        assertRechaza(() -> item(p, limpieza, null, null, null, 1, "1000.00", 50, "1000.00", "500.00", "500.00"),
                "presupuesto_item_codigo");
        assertRechaza(() -> item(p, limpieza, codigo0201, null, null, 1, "1000.00", 0, "1000.00", "0.00", "1000.00"),
                "presupuesto_item_codigo");
        assertThatCode(() -> item(p, limpieza, codigo0201, "N-0201", null, 1, "1000.00", 50, "1000.00", "500.00",
                "500.00")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("La base rechaza pieza 19, cara sin pieza, cara P en la 36 y cara L en la 16")
    void piezaYCara() {
        long p = presupuestoParticular();
        assertRechaza(() -> itemEn(p, 19, null), "presupuesto_item_pieza_fdi");
        assertRechaza(() -> itemEn(p, null, "O"), "presupuesto_item_cara_con_pieza");
        assertRechaza(() -> itemEn(p, 36, "P"), "presupuesto_item_cara_palatina");
        assertRechaza(() -> itemEn(p, 16, "L"), "presupuesto_item_cara_lingual");
        assertRechaza(() -> itemEn(p, 16, "X"), "presupuesto_item_cara_valida");
        assertThatCode(() -> itemEn(p, 36, "L")).doesNotThrowAnyException();
        assertThatCode(() -> itemEn(p, 16, "P")).doesNotThrowAnyException();
        assertThatCode(() -> itemEn(p, 16, "O")).doesNotThrowAnyException();
        assertThatCode(() -> itemEn(p, 85, null)).doesNotThrowAnyException();
        assertThatCode(() -> itemEn(p, null, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("La base rechaza una cantidad fuera de 1 a 99")
    void cantidadInvalida() {
        long p = presupuestoParticular();
        assertRechaza(() -> item(p, limpieza, null, null, null, 0, "1000.00", 0, "0.00", "0.00", "0.00"),
                "presupuesto_item_cantidad");
        assertRechaza(() -> item(p, limpieza, null, null, null, 100, "1000.00", 0, "100000.00", "0.00", "100000.00"),
                "presupuesto_item_cantidad");
    }

    @Test
    @DisplayName("Las reglas de pieza y cara del ítem coinciden con las del odontograma")
    void mismasReglasQueElOdontograma() {
        long p = presupuestoParticular();
        List<String> caras = new ArrayList<>();
        caras.add(null);
        caras.addAll(List.of("O", "M", "D", "V", "L", "P", "X"));
        int comparadas = 0;
        for (int pieza = 0; pieza <= 99; pieza++) {
            for (String cara : caras) {
                int piezaProbada = pieza;
                String caraProbada = cara;
                boolean enOdontograma = acepta(() -> jdbc.update("INSERT INTO odontograma_registro(paciente_id, pieza, "
                        + "cara, estado, profesional_id, registrado_en) VALUES (?,?,?,'SANO',?,now())",
                        pacienteUno, piezaProbada, caraProbada, profesional));
                boolean enItem = acepta(() -> itemEn(p, piezaProbada, caraProbada));
                assertThat(enItem).as("pieza %s cara %s", pieza, cara).isEqualTo(enOdontograma);
                comparadas++;
            }
        }
        assertThat(comparadas).isEqualTo(800);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM odontograma_registro", Integer.class)).isPositive();
    }

    // --- Inmutabilidad ---

    @Test
    @DisplayName("La base rechaza modificar un ítem emitido")
    void noSeModificaUnItem() {
        long p = presupuestoParticular();
        long item = id("INSERT INTO presupuesto_item(presupuesto_id, prestacion_id, prestacion_nombre, cantidad, "
                + "precio_unitario, porcentaje_cobertura, importe, cobertura, copago) VALUES (" + p + "," + limpieza
                + ",'Limpieza (profilaxis)',1,25000.00,0,25000.00,0.00,25000.00) RETURNING id");
        assertRechaza(() -> jdbc.update("UPDATE presupuesto_item SET precio_unitario = 1.00, importe = 1.00, "
                + "copago = 1.00 WHERE id = ?", item), "solo agregado");
        assertRechaza(() -> jdbc.update("DELETE FROM presupuesto_item WHERE id = ?", item), "solo agregado");
        assertThat(jdbc.queryForObject("SELECT precio_unitario FROM presupuesto_item WHERE id = ?", BigDecimal.class,
                item)).isEqualByComparingTo("25000.00");
    }

    @Test
    @DisplayName("La base rechaza borrar un presupuesto y cambiar algo más que el estado")
    void noSeBorraNiSeEditaUnPresupuesto() {
        long p = presupuestoParticular();
        assertRechaza(() -> jdbc.update("DELETE FROM presupuesto WHERE id = ?", p), "presupuesto");
        assertRechaza(() -> jdbc.update("UPDATE presupuesto SET total_precio = 50000, total_copago = 50000 WHERE id = ?",
                p), "presupuesto");
        assertRechaza(() -> jdbc.update("UPDATE presupuesto SET paciente_id = ? WHERE id = ?", pacienteDos, p),
                "presupuesto");
        assertThat(jdbc.queryForObject("SELECT total_precio FROM presupuesto WHERE id = ?", BigDecimal.class, p))
                .isEqualByComparingTo("40000.00");
    }

    @Test
    @DisplayName("La base acepta PENDIENTE a ACEPTADO y rechaza cambiar un estado cerrado")
    void soloPendienteCambiaDeEstado() {
        long aceptado = presupuestoParticular();
        assertThat(jdbc.update("UPDATE presupuesto SET estado = 'ACEPTADO' WHERE id = ?", aceptado)).isEqualTo(1);
        assertRechaza(() -> jdbc.update("UPDATE presupuesto SET estado = 'RECHAZADO' WHERE id = ?", aceptado),
                "presupuesto");
        assertRechaza(() -> jdbc.update("UPDATE presupuesto SET estado = 'PENDIENTE' WHERE id = ?", aceptado),
                "presupuesto");
        long rechazado = presupuestoParticular();
        assertThat(jdbc.update("UPDATE presupuesto SET estado = 'RECHAZADO' WHERE id = ?", rechazado)).isEqualTo(1);
        assertRechaza(() -> jdbc.update("UPDATE presupuesto SET estado = 'ACEPTADO' WHERE id = ?", rechazado),
                "presupuesto");
        assertThat(jdbc.queryForObject("SELECT estado FROM presupuesto WHERE id = ?", String.class, aceptado))
                .isEqualTo("ACEPTADO");
    }

    // --- Ayudas ---

    private long presupuestoParticular() {
        return presupuesto(pacienteUno, null, "2026-11-24", "2026-12-24", "40000.00", "0.00", "40000.00");
    }

    private long presupuesto(long paciente, Long afiliacion, String fecha, String vence, String precio,
                             String cobertura, String copago) {
        return id("INSERT INTO presupuesto(paciente_id, profesional_id, afiliacion_id, obra_social_nombre, plan_nombre, "
                + "numero_afiliado, fecha, vence_el, total_precio, total_cobertura, total_copago, creado_en) "
                + "SELECT " + paciente + "," + profesional + ", a.id, o.nombre, p.nombre, a.numero_afiliado, DATE '"
                + fecha + "', DATE '" + vence + "'," + precio + "," + cobertura + "," + copago + ", now() "
                + "FROM (SELECT 1) x LEFT JOIN afiliacion_paciente a ON a.id = " + (afiliacion == null ? "-1" : afiliacion)
                + " LEFT JOIN plan p ON p.id = a.plan_id LEFT JOIN obra_social o ON o.id = a.obra_social_id RETURNING id");
    }

    private void item(long presupuesto, long prestacion, Long nomenclador, String codigo, Integer pieza, int cantidad,
                      String precio, int porcentaje, String importe, String cobertura, String copago) {
        jdbc.update("INSERT INTO presupuesto_item(presupuesto_id, prestacion_id, prestacion_nombre, nomenclador_id, "
                + "codigo_nomenclador, pieza, cantidad, precio_unitario, porcentaje_cobertura, importe, cobertura, "
                + "copago) VALUES (?,?,'Prestación ficticia',?,?,?,?,CAST(? AS numeric),?,CAST(? AS numeric),"
                + "CAST(? AS numeric),CAST(? AS numeric))",
                presupuesto, prestacion, nomenclador, codigo, pieza, cantidad, precio, porcentaje, importe, cobertura,
                copago);
    }

    private void itemEn(long presupuesto, Integer pieza, String cara) {
        jdbc.update("INSERT INTO presupuesto_item(presupuesto_id, prestacion_id, prestacion_nombre, pieza, cara, "
                + "cantidad, precio_unitario, porcentaje_cobertura, importe, cobertura, copago) "
                + "VALUES (?,?,'Prestación ficticia',?,?,1,100.00,0,100.00,0.00,100.00)",
                presupuesto, consulta, pieza, cara);
    }

    private void arancel(long prestacion, String precio) {
        jdbc.update("INSERT INTO arancel(prestacion_id, precio, actualizado_en) VALUES (?, CAST(? AS numeric), now())",
                prestacion, precio);
    }

    private void cobertura(long plan, long codigo, int porcentaje) {
        jdbc.update("INSERT INTO cobertura_plan(plan_id, nomenclador_id, porcentaje) VALUES (?,?,?)", plan, codigo,
                porcentaje);
    }

    private long afiliar(long paciente, long plan, String numero) {
        return id("INSERT INTO afiliacion_paciente(paciente_id, plan_id, obra_social_id, numero_afiliado, vigente_desde) "
                + "SELECT " + paciente + ", p.id, p.obra_social_id, '" + numero + "', DATE '2026-01-01' FROM plan p "
                + "WHERE p.id = " + plan + " RETURNING id");
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private static boolean acepta(Runnable accion) {
        try {
            accion.run();
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    private static void assertRechaza(Runnable accion, String restriccion) {
        assertThatThrownBy(accion::run).hasMessageContaining(restriccion);
    }
}
