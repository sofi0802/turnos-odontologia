package ar.turnos.odontologia.shared.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "turno")
public class TurnoEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "profesional_id")
    private ProfesionalEntity profesional;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sillon_id")
    private SillonEntity sillon;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "prestacion_id")
    private PrestacionEntity prestacion;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id")
    private PacienteEntity paciente;

    private Instant inicio;
    private Instant fin;
    private Instant finConMargen;
    // C-06 (V4): los turnos nuevos arrancan en RESERVADO, como en el dominio de C-01
    private String estado = "RESERVADO";
    private boolean esSobreturno;
    @Column(columnDefinition = "text")
    private String motivoSobreturno;
    // C-08 (V6): la extensión se guarda en la misma fila y recalcula fin_con_margen con el margen vigente.
    private boolean extendido;
    @Column(columnDefinition = "text")
    private String motivoExtension;
    private String codigoReserva;
    // C-43 (V22): hora de llegada del paciente; nula hasta que la recepción la registra.
    private Instant llegadaEn;

    @Version
    private long version;

    protected TurnoEntity() {}

    public TurnoEntity(ProfesionalEntity profesional, SillonEntity sillon, PrestacionEntity prestacion,
                       PacienteEntity paciente, Instant inicio, Instant fin, Instant finConMargen,
                       String codigoReserva) {
        this.profesional = profesional;
        this.sillon = sillon;
        this.prestacion = prestacion;
        this.paciente = paciente;
        this.inicio = inicio;
        this.fin = fin;
        this.finConMargen = finConMargen;
        this.codigoReserva = codigoReserva;
    }

    /** C-06: cancelar o reprogramar cambia el estado del turno. */
    public void cambiarEstado(String nuevoEstado) { this.estado = nuevoEstado; }

    /** C-08: marca el turno como sobreturno y guarda su motivo administrativo (nunca clínico). */
    public void marcarSobreturno(String motivo) {
        this.esSobreturno = true;
        this.motivoSobreturno = motivo;
    }

    /**
     * C-08: extiende el fin de la fila, recalcula el fin con margen y marca la extensión.
     * El aumento de {@code version} lo hace JPA por el {@code @Version} existente.
     */
    public void actualizarExtension(Instant nuevoFin, Instant nuevoFinConMargen, String motivo) {
        this.fin = nuevoFin;
        this.finConMargen = nuevoFinConMargen;
        this.extendido = true;
        this.motivoExtension = motivo;
    }

    /** C-43: registra la hora de llegada del paciente (el dominio ya verificó que se puede). */
    public void cambiarLlegada(Instant llegada) { this.llegadaEn = llegada; }

    public ProfesionalEntity getProfesional() { return profesional; }
    public SillonEntity getSillon() { return sillon; }
    public PrestacionEntity getPrestacion() { return prestacion; }
    public PacienteEntity getPaciente() { return paciente; }
    public String getCodigoReserva() { return codigoReserva; }
    public Long getId() { return id; }
    public Instant getInicio() { return inicio; }
    public Instant getFin() { return fin; }
    public Instant getFinConMargen() { return finConMargen; }
    public String getEstado() { return estado; }
    public boolean isEsSobreturno() { return esSobreturno; }
    public String getMotivoSobreturno() { return motivoSobreturno; }
    public boolean isExtendido() { return extendido; }
    public String getMotivoExtension() { return motivoExtension; }
    public long getVersion() { return version; }
    public Instant getLlegadaEn() { return llegadaEn; }
}