package jchat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Calendario {

    private final Map<String, Reserva> franjas = new HashMap<>();
    private final GestorSincronizacion gestor;

    public Calendario(GestorSincronizacion gestor) {
        this.gestor = gestor;
    }

    private String clave(String fecha, String horaInicio, String horaFin) {
        return fecha + "-" + horaInicio + "-" + horaFin;
    }

    // SC-01: Consultar disponibilidad (READ lock)
    public boolean estaDisponible(String fecha, String horaInicio, String horaFin) {
        gestor.lockLecturaCalendario().lock();
        try {
            String key = clave(fecha, horaInicio, horaFin);
            Reserva r = franjas.get(key);
            return r == null
                || r.getEstado() == Reserva.Estado.CANCELADO
                || r.getEstado() == Reserva.Estado.LIBRE;
        } finally {
            gestor.lockLecturaCalendario().unlock();
        }
    }

    // SC-02: Reservar temporal (WRITE lock)
    public Reserva reservarTemporal(String idCliente, String fecha,
                                     String horaInicio, String horaFin,
                                     int asistentes, Reserva.Equipo equipo,
                                     Reserva.Prioridad prioridad) {
        gestor.lockEscrituraCalendario().lock();
        try {
            if (!estaDisponibleSinLock(fecha, horaInicio, horaFin)) {
                return null;
            }
            gestor.adquirirParaReserva(asistentes, equipo);
            Reserva reserva = new Reserva(idCliente, fecha, horaInicio,
                                           horaFin, asistentes, equipo, prioridad);
            franjas.put(clave(fecha, horaInicio, horaFin), reserva);
            return reserva;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            gestor.lockEscrituraCalendario().unlock();
        }
    }

    // SC-03: Confirmar reserva (WRITE lock)
    public boolean confirmarReserva(String idReserva) {
        gestor.lockEscrituraCalendario().lock();
        try {
            Reserva r = buscarPorId(idReserva);
            if (r == null) return false;
            if (r.getEstado() != Reserva.Estado.RESERVADO_TEMPORAL) return false;
            if (r.estaVencida()) return false;
            r.setEstado(Reserva.Estado.CONFIRMADO);
            return true;
        } finally {
            gestor.lockEscrituraCalendario().unlock();
        }
    }

    // SC-04: Cancelar reserva (WRITE lock)
    public boolean cancelarReserva(String idReserva) {
        gestor.lockEscrituraCalendario().lock();
        try {
            Reserva r = buscarPorId(idReserva);
            if (r == null) return false;
            if (r.getEstado() == Reserva.Estado.CANCELADO) return false;
            r.setEstado(Reserva.Estado.CANCELADO);
            gestor.liberarDeReserva(r.getCantAsistentes(), r.getEquipo());
            return true;
        } finally {
            gestor.lockEscrituraCalendario().unlock();
        }
    }

    // SC-08: Expirar reservas vencidas (WRITE lock)
    public List<Reserva> expirarVencidas() {
        List<Reserva> expiradas = new ArrayList<>();
        gestor.lockEscrituraCalendario().lock();
        try {
            for (Reserva r : franjas.values()) {
                if (r.estaVencida()) {
                    r.setEstado(Reserva.Estado.CANCELADO);
                    gestor.liberarDeReserva(r.getCantAsistentes(), r.getEquipo());
                    expiradas.add(r);
                }
            }
        } finally {
            gestor.lockEscrituraCalendario().unlock();
        }
        return expiradas;
    }

    // SC-09: Estado general (READ lock)
    public List<Reserva> getReservasActivas() {
        gestor.lockLecturaCalendario().lock();
        try {
            List<Reserva> activas = new ArrayList<>();
            for (Reserva r : franjas.values()) {
                if (r.getEstado() != Reserva.Estado.CANCELADO) {
                    activas.add(r);
                }
            }
            return activas;
        } finally {
            gestor.lockLecturaCalendario().unlock();
        }
    }

    public Reserva getReservaPorId(String idReserva) {
        gestor.lockLecturaCalendario().lock();
        try {
            return buscarPorId(idReserva);
        } finally {
            gestor.lockLecturaCalendario().unlock();
        }
    }

    private Reserva buscarPorId(String idReserva) {
        for (Reserva r : franjas.values()) {
            if (r.getIdReserva().equals(idReserva)) return r;
        }
        return null;
    }

    private boolean estaDisponibleSinLock(String fecha,
                                           String horaInicio, String horaFin) {
        String key = clave(fecha, horaInicio, horaFin);
        Reserva r = franjas.get(key);
        return r == null
            || r.getEstado() == Reserva.Estado.CANCELADO
            || r.getEstado() == Reserva.Estado.LIBRE;
    }

    public int totalReservasActivas() {
        gestor.lockLecturaCalendario().lock();
        try {
            return (int) franjas.values().stream()
                    .filter(r -> r.getEstado() != Reserva.Estado.CANCELADO)
                    .count();
        } finally {
            gestor.lockLecturaCalendario().unlock();
        }
    }

    public List<Reserva> getTodasLasReservas() {
        gestor.lockLecturaCalendario().lock();
        try {
            return new ArrayList<>(franjas.values());
        } finally {
            gestor.lockLecturaCalendario().unlock();
        }
    }

    public int totalReservas() {
        return totalReservasActivas(); 
    }
}