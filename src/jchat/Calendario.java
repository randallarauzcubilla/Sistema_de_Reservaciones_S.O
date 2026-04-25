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
        // Verificar solapamiento y capacidad por rango (sin semáforo global)
        int ocupada = capacidadOcupadaEnRango(fecha, horaInicio, horaFin);
        int capacidadTotal = gestor.getCapacidadMaxima();

        if (ocupada + asistentes > capacidadTotal) {
            return null; // No hay capacidad en ESA franja
        }
        // Solo adquirir equipo (no capacidad — se controla por rango)
        gestor.adquirirSoloEquipo(equipo);

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

    // SC-03: Confirmar reserva
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

    // SC-04: Cancelar reserva
    public boolean cancelarReserva(String idReserva) {
        gestor.lockEscrituraCalendario().lock();
        try {
            Reserva r = buscarPorId(idReserva);
            if (r == null) return false;
            if (r.getEstado() == Reserva.Estado.CANCELADO) return false;
            r.setEstado(Reserva.Estado.CANCELADO);
            gestor.liberarSoloEquipo(r.getEquipo()); // Solo equipo, no capacidad
            return true;
        } finally {
            gestor.lockEscrituraCalendario().unlock();
        }
    }

    // SC-08: Expirar reservas
    public List<Reserva> expirarVencidas() {
        List<Reserva> expiradas = new ArrayList<>();
        gestor.lockEscrituraCalendario().lock();
        try {
            for (Reserva r : franjas.values()) {
                if (r.estaVencida()) {
                    r.setEstado(Reserva.Estado.CANCELADO);
                    gestor.liberarSoloEquipo(r.getEquipo()); // Solo equipo
                    expiradas.add(r);
                }
            }
            return expiradas;
        } finally {
            gestor.lockEscrituraCalendario().unlock();
        }
    }

    // SC-09: Activas
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
            if (r.getIdReserva().equals(idReserva)) {
                return r;
            }
        }
        return null;
    }

    // lógica correcta de solapamiento
    private boolean solapan(String ini1, String fin1, String ini2, String fin2) {
        return ini1.compareTo(fin2) < 0 && fin1.compareTo(ini2) > 0;
    }

    // capacidad por rango (CORRECTO)
    public int capacidadOcupadaEnRango(String fecha, String inicio, String fin) {
        gestor.lockLecturaCalendario().lock();
        try {
            int ocupada = 0;

            for (Reserva r : franjas.values()) {

                if (r.getEstado() == Reserva.Estado.CANCELADO) continue;
                if (!r.getFecha().equals(fecha)) continue;

                if (solapan(inicio, fin, r.getHoraInicio(), r.getHoraFin())) {
                    ocupada += r.getCantAsistentes();
                }
            }

            return ocupada;

        } finally {
            gestor.lockLecturaCalendario().unlock();
        }
    }

    // carga persistencia
    public void cargarReservaRestaurada(Reserva r) {
        gestor.lockEscrituraCalendario().lock();
        try {
            String key = clave(r.getFecha(), r.getHoraInicio(), r.getHoraFin());

            if (!franjas.containsKey(key)) {
                franjas.put(key, r);

                try {
                    gestor.adquirirParaReserva(r.getCantAsistentes(), r.getEquipo());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

        } finally {
            gestor.lockEscrituraCalendario().unlock();
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

    public int totalReservasActivas() {
        return getReservasActivas().size();
    }

    public int totalReservas() {
        return totalReservasActivas();
    }
}