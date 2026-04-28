package Core;

import Concurrency.SynchronizationManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ReservationCalendar {

    private final Map<String, Reservation> franjas = new ConcurrentHashMap<>();
    private final SynchronizationManager gestor;

    public ReservationCalendar(SynchronizationManager gestor) {
        this.gestor = gestor;
    }

    private String clave(String fecha, String horaInicio, String horaFin) {
        return fecha + "-" + horaInicio + "-" + horaFin;
    }

    // SC-01: Consultar disponibilidad (READ lock)
    public boolean estaDisponible(String fecha, String horaInicio,
            String horaFin) {
        gestor.lockLecturaCalendario().lock();
        try {
            String key = clave(fecha, horaInicio, horaFin);
            Reservation r = franjas.get(key);

            return r == null
                    || r.getEstado() == Reservation.Estado.CANCELADO
                    || r.getEstado() == Reservation.Estado.LIBRE;

        } finally {
            gestor.lockLecturaCalendario().unlock();
        }
    }

    // SC-02: Reservar temporal (WRITE lock)
    public Reservation reservarTemporal(String idCliente, String fecha,
                                String horaInicio, String horaFin,
                                int asistentes, Reservation.Equipo equipo,
        Reservation.Prioridad prioridad) throws InterruptedException {
        gestor.lockEscrituraCalendario().lock();
        try {

            expirarVencidas();

        // validar solapamiento
        for (Reservation r : franjas.values()) {

            if (r.getEstado() == Reservation.Estado.CANCELADO || 
                    r.estaVencida()) continue;
            if (!r.getFecha().equals(fecha)) continue;

            if (solapan(horaInicio, horaFin, r.getHoraInicio(),
                    r.getHoraFin())) {
                return null;
            }
        }

            gestor.adquirirParaReserva(asistentes, equipo);

            Reservation reserva = new Reservation(idCliente, fecha, horaInicio,
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
            Reservation r = buscarPorId(idReserva);
            if (r == null) return false;
            if (r.getEstado() != Reservation.Estado.RESERVADO_TEMPORAL)
                return false;
            if (r.estaVencida()) return false;

            r.setEstado(Reservation.Estado.CONFIRMADO);
            return true;

        } finally {
            gestor.lockEscrituraCalendario().unlock();
        }
    }

    // SC-04: Cancelar reserva
    public boolean cancelarReserva(String idReserva) {
        gestor.lockEscrituraCalendario().lock();
        try {
            Reservation r = buscarPorId(idReserva);
            if (r == null) return false;
            if (r.getEstado() == Reservation.Estado.CANCELADO) return false;

            r.setEstado(Reservation.Estado.CANCELADO);

            gestor.liberarDeReserva(r.getCantAsistentes(), r.getEquipo());

            return true;

        } finally {
            gestor.lockEscrituraCalendario().unlock();
        }
    }

    // SC-08: Expirar reservas
    public List<Reservation> expirarVencidas() {
        List<Reservation> expiradas = new ArrayList<>();
        gestor.lockEscrituraCalendario().lock();
        try {
            for (Reservation r : franjas.values()) {
                if (r.estaVencida()) {
                   r.setEstado(Reservation.Estado.CANCELADO);
                   gestor.liberarDeReserva(r.getCantAsistentes(),r.getEquipo());
                   expiradas.add(r);
                }
            }
            return expiradas;
        } finally {
            gestor.lockEscrituraCalendario().unlock();
        }
    }

    // SC-09: Activas
    public List<Reservation> getReservasActivas() {
        gestor.lockLecturaCalendario().lock();
        try {
            List<Reservation> activas = new ArrayList<>();
            for (Reservation r : franjas.values()) {
                if (r.getEstado() != Reservation.Estado.CANCELADO) {
                    activas.add(r);
                }
            }
            return activas;
        } finally {
            gestor.lockLecturaCalendario().unlock();
        }
    }

    public Reservation getReservaPorId(String idReserva) {
        gestor.lockLecturaCalendario().lock();
        try {
            return buscarPorId(idReserva);
        } finally {
            gestor.lockLecturaCalendario().unlock();
        }
    }

    private Reservation buscarPorId(String idReserva) {
        for (Reservation r : franjas.values()) {
            if (r.getIdReserva().equals(idReserva)) {
                return r;
            }
        }
        return null;
    }

    private boolean solapan(String ini1, String fin1, String ini2, String fin2){
        return ini1.compareTo(fin2) < 0 && fin1.compareTo(ini2) > 0;
    }

    public int capacidadOcupadaEnRango(String fecha, String inicio, String fin){
        gestor.lockLecturaCalendario().lock();
        try {
            int ocupada = 0;

            for (Reservation r : franjas.values()) {

                if (r.getEstado() == Reservation.Estado.CANCELADO) continue;
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
    public void cargarReservaRestaurada(Reservation r) {
        gestor.lockEscrituraCalendario().lock();
        try {
            String key = clave(r.getFecha(), r.getHoraInicio(), r.getHoraFin());

            if (!franjas.containsKey(key)) {
                franjas.put(key, r);
            }

        } finally {
            gestor.lockEscrituraCalendario().unlock();
        }
    }

    public List<Reservation> getTodasLasReservas() {
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