package jchat;

public class RecursoAuditorio {

    private final GestorSincronizacion gestor;

    public RecursoAuditorio(GestorSincronizacion gestor) {
        this.gestor = gestor;
    }

    public boolean hayCapacidad(int asistentes) {
        return gestor.capacidadDisponible() >= asistentes;
    }

    public void liberarCapacidad(int asistentes) {
        gestor.getSemCapacidad().release(asistentes);
    }

    public boolean hayEquipo(Reserva.Equipo equipo) {
        switch (equipo) {
            case PROYECTOR: return gestor.proyectoresDisponibles() > 0;
            case MICROFONO: return gestor.microfonosDisponibles() > 0;
            case SONIDO:    return gestor.sonidosDisponibles() > 0;
            case COMPLETO:  return gestor.proyectoresDisponibles() > 0
                                && gestor.microfonosDisponibles() > 0
                                && gestor.sonidosDisponibles() > 0;
            case NINGUNO:
            default:        return true;
        }
    }

    public void liberarEquipo(Reserva.Equipo equipo) {
        gestor.liberarDeReserva(0, equipo);
    }

    public int capacidadDisponible()    { return gestor.capacidadDisponible();    }
    public int proyectoresDisponibles() { return gestor.proyectoresDisponibles(); }
    public int microfonosDisponibles()  { return gestor.microfonosDisponibles();  }
    public int sonidosDisponibles()     { return gestor.sonidosDisponibles();     }

    @Override
    public String toString() {
        return String.format(
            "Capacidad: %d | Proyectores: %d | Micrófonos: %d | Sonido: %d",
            capacidadDisponible(),
            proyectoresDisponibles(),
            microfonosDisponibles(),
            sonidosDisponibles()
        );
    }
}