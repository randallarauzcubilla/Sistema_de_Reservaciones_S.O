package Core;

import Concurrency.SynchronizationManager;

public class AuditoriumManager {

    private final SynchronizationManager gestor;

    public AuditoriumManager(SynchronizationManager gestor) {
        this.gestor = gestor;
    }

    public void liberarCapacidad(int asistentes) {
        gestor.liberarDeReserva(asistentes, Reservation.Equipo.NINGUNO);
    }

    public boolean hayEquipo(Reservation.Equipo equipo) {
        switch (equipo) {
            case PROYECTOR:
                return gestor.proyectoresDisponibles() > 0;
            case MICROFONO:
                return gestor.microfonosDisponibles() > 0;
            case SONIDO:
                return gestor.sonidosDisponibles() > 0;
            case COMPLETO:
                return gestor.proyectoresDisponibles() > 0
                        && gestor.microfonosDisponibles() > 0
                        && gestor.sonidosDisponibles() > 0;
            case NINGUNO:
            default:
                return true;
        }
    }

    public void liberarEquipo(Reservation.Equipo equipo) {
        gestor.liberarDeReserva(0, equipo);
    }

    public int capacidadDisponible() {
        return gestor.capacidadDisponible();
    }

    public int proyectoresDisponibles() {
        return gestor.proyectoresDisponibles();
    }

    public int microfonosDisponibles() {
        return gestor.microfonosDisponibles();
    }

    public int sonidosDisponibles() {
        return gestor.sonidosDisponibles();
    }

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
