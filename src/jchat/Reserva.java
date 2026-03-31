package jchat;

public class Reserva {

    public enum Estado { LIBRE, RESERVADO_TEMPORAL, CONFIRMADO, CANCELADO }
    
    public enum Equipo { NINGUNO, PROYECTOR, MICROFONO, SONIDO, COMPLETO }
    
    public enum Prioridad { ESTUDIANTE, DOCENTE, DECANATURA }

    private final String idReserva;
    private final String idCliente;
    private final String fecha;
    private final String horaInicio;  
    private final String horaFin;    
    private final int cantAsistentes;
    private final Equipo equipo;      
    private final Prioridad prioridad;
    private volatile Estado estado;
    private final long ttlExpiracion;

    public Reserva(String idCliente, String fecha, String horaInicio,
                   String horaFin, int asistentes, Equipo equipo,
                   Prioridad prioridad) {
        this.idReserva     = java.util.UUID.randomUUID().toString().substring(0, 8);
        this.idCliente     = idCliente;
        this.fecha         = fecha;
        this.horaInicio    = horaInicio;
        this.horaFin       = horaFin;
        this.cantAsistentes = asistentes;
        this.equipo        = equipo;
        this.prioridad     = prioridad;
        this.estado        = Estado.RESERVADO_TEMPORAL;
        this.ttlExpiracion = System.currentTimeMillis() + 30_000;
    }

    public boolean estaVencida() {
        return estado == Estado.RESERVADO_TEMPORAL &&
               System.currentTimeMillis() > ttlExpiracion;
    }

    public long segundosRestantes() {
        long restante = (ttlExpiracion - System.currentTimeMillis()) / 1000;
        return Math.max(0, restante);
    }

    // Getters
    public String getIdReserva()    { return idReserva; }
    public String getIdCliente()    { return idCliente; }
    public String getFecha()        { return fecha; }
    public String getHoraInicio()   { return horaInicio; }
    public String getHoraFin()      { return horaFin; }
    public int getCantAsistentes()  { return cantAsistentes; }
    public Equipo getEquipo()       { return equipo; }
    public Prioridad getPrioridad() { return prioridad; }
    public Estado getEstado()       { return estado; }
    public long getTTL()            { return ttlExpiracion; }

    public void setEstado(Estado e) { this.estado = e; }

    @Override
    public String toString() {
        return String.format("[%s] %s | %s %s-%s | %d personas | %s | %s | TTL: %ds",
                idReserva, idCliente, fecha, horaInicio, horaFin,
                cantAsistentes, equipo, estado, segundosRestantes());
    }
}