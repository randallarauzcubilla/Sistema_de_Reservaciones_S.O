package Core;
 
import Concurrency.SynchronizationManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
 
public class ReservationCalendar {
 
    private final Map<String, Reservation> timeSlots
            = new ConcurrentHashMap<>();
    private final SynchronizationManager manager;
 
    public ReservationCalendar(SynchronizationManager manager) {
        this.manager = manager;
    }
 
    private String generateKey(String date, String start, String end) {
        return date + "-" + start + "-" + end;
    }
 
    // =========================================================
    // DISPONIBILIDAD
    // =========================================================
 
    public boolean isAvailable(String date, String start, String end) {
        manager.lockReadCalendar().lock();
        try {
            Reservation r = timeSlots.get(generateKey(date, start, end));
            return r == null || isFreeStatus(r.getStatus());
        } finally {
            manager.lockReadCalendar().unlock();
        }
    }
 
    // =========================================================
    // RESERVA TEMPORAL
    // =========================================================
 
    public Reservation reserveTemporarily(String clientId, String date,
            String startTime, String endTime, int attendees,
            Map<Reservation.Equipment, Integer> equipmentQuantities,
            Reservation.Priority priority)
            throws InterruptedException {
 
        Map<Reservation.Equipment, Integer> toAcquire =
                equipmentQuantities != null && !equipmentQuantities.isEmpty()
                ? new LinkedHashMap<>(equipmentQuantities)
                : Collections.emptyMap();
 
        manager.lockWriteCalendar().lock();
        try {
            // Limpiar reservas vencidas y finalizadas sin llamadas bloqueantes
            markFinishedInternal();
            expireOverdueInternal();
 
            // Verificar solapamiento de franja horaria
            for (Reservation r : timeSlots.values()) {
                if (isFreeStatus(r.getStatus())) continue;
                if (!r.getDate().equals(date)) continue;
                if (doOverlap(startTime, endTime,
                        r.getStartTime(), r.getEndTime())) {
                    return null;
                }
            }
 
            // Verificar disponibilidad de equipos por franja (calendario, no semáforo)
            for (Map.Entry<Reservation.Equipment, Integer> entry
                    : toAcquire.entrySet()) {
                int inUse = getEquipmentInUseForSlot(
                        date, startTime, endTime, entry.getKey());
                int total = getTotalForType(entry.getKey());
                if (inUse + entry.getValue() > total) {
                    return null;
                }
            }
 
            // Extraer primario y extra para el constructor
            Reservation.Equipment primary = Reservation.Equipment.NINGUNO;
            int primaryQty = 1;
            Map<Reservation.Equipment, Integer> extra = new LinkedHashMap<>();
 
            if (!toAcquire.isEmpty()) {
                var it = toAcquire.entrySet().iterator();
                var first = it.next();
                primary    = first.getKey();
                primaryQty = first.getValue();
                while (it.hasNext()) {
                    var e = it.next();
                    extra.put(e.getKey(), e.getValue());
                }
            }
 
            Reservation reservation = new Reservation(
                    clientId, date, startTime, endTime,
                    attendees, primary, primaryQty, extra, priority);
 
            timeSlots.put(generateKey(date, startTime, endTime), reservation);
            return reservation;
 
        } finally {
            manager.lockWriteCalendar().unlock();
        }
    }
 
    /**
     * Backward-compat: un solo tipo de equipo, cantidad 1.
     */
    public Reservation reserveTemporarily(String clientId, String date,
            String startTime, String endTime, int attendees,
            Reservation.Equipment equipment,
            Reservation.Priority priority)
            throws InterruptedException {
        Map<Reservation.Equipment, Integer> map = new LinkedHashMap<>();
        if (equipment != null && equipment != Reservation.Equipment.NINGUNO) {
            map.put(equipment, 1);
        }
        return reserveTemporarily(clientId, date, startTime, endTime,
                attendees, map, priority);
    }
 
    // =========================================================
    // CONFIRMAR
    // =========================================================
 
    public boolean confirmReservation(String reservationId) {
        manager.lockWriteCalendar().lock();
        try {
            Reservation r = findById(reservationId);
            if (r == null) return false;
            if (r.getStatus() != Reservation.Status.RESERVADO_TEMPORAL)
                return false;
            if (r.isExpired()) return false;
            r.setStatus(Reservation.Status.CONFIRMADO);
            return true;
        } finally {
            manager.lockWriteCalendar().unlock();
        }
    }
 
    // =========================================================
    // CANCELAR
    // =========================================================
 
    public boolean cancelReservation(String reservationId) {
        manager.lockWriteCalendar().lock();
        try {
            Reservation r = findById(reservationId);
            if (r == null) return false;
            Reservation.Status st = r.getStatus();
            if (st == Reservation.Status.CANCELADO
                    || st == Reservation.Status.EXPIRADO
                    || st == Reservation.Status.FINALIZADO) {
                return false;
            }
            r.setStatus(Reservation.Status.CANCELADO);
            // Sin releaseEquipmentMap — el calendario es la fuente de verdad
            return true;
        } finally {
            manager.lockWriteCalendar().unlock();
        }
    }
 
    // =========================================================
    // EXPIRAR / FINALIZAR (públicos, con su propio lock)
    // =========================================================
 
    public List<Reservation> expireOverdue() {
        List<Reservation> expired = new ArrayList<>();
        manager.lockWriteCalendar().lock();
        try {
            for (Reservation r : timeSlots.values()) {
                if (r.isExpired()) {
                    r.setStatus(Reservation.Status.EXPIRADO);
                    expired.add(r);
                }
            }
            return expired;
        } finally {
            manager.lockWriteCalendar().unlock();
        }
    }
 
    public void markFinishedReservations() {
        manager.lockWriteCalendar().lock();
        try {
            markFinishedInternal();
        } finally {
            manager.lockWriteCalendar().unlock();
        }
    }
 
    // =========================================================
    // INTERNOS SIN LOCK (llamar solo desde dentro del writeLock)
    // =========================================================
 
    private void expireOverdueInternal() {
        for (Reservation r : timeSlots.values()) {
            if (r.isExpired()) {
                r.setStatus(Reservation.Status.EXPIRADO);
            }
        }
    }
 
    private void markFinishedInternal() {
        for (Reservation r : timeSlots.values()) {
            if (r.isFinished()) {
                r.setStatus(Reservation.Status.FINALIZADO);
            }
        }
    }
 
    // =========================================================
    // CONSULTAS
    // =========================================================
 
    public List<Reservation> getActiveReservations() {
        manager.lockReadCalendar().lock();
        try {
            List<Reservation> active = new ArrayList<>();
            for (Reservation r : timeSlots.values()) {
                if (!isFreeStatus(r.getStatus())) active.add(r);
            }
            return active;
        } finally {
            manager.lockReadCalendar().unlock();
        }
    }
 
    public List<Reservation> getAllReservations() {
        manager.lockReadCalendar().lock();
        try {
            return new ArrayList<>(timeSlots.values());
        } finally {
            manager.lockReadCalendar().unlock();
        }
    }
 
    public Reservation getReservationById(String reservationId) {
        manager.lockReadCalendar().lock();
        try {
            return findById(reservationId);
        } finally {
            manager.lockReadCalendar().unlock();
        }
    }
 
    public void loadRestoredReservation(Reservation reservation) {
        manager.lockWriteCalendar().lock();
        try {
            String key = generateKey(reservation.getDate(),
                    reservation.getStartTime(), reservation.getEndTime());
            if (!timeSlots.containsKey(key)) {
                timeSlots.put(key, reservation);
            }
        } finally {
            manager.lockWriteCalendar().unlock();
        }
    }
 
    public int getOccupiedCapacityInRange(String date,
            String startTime, String endTime) {
        manager.lockReadCalendar().lock();
        try {
            int total = 0;
            for (Reservation r : timeSlots.values()) {
                if (isFreeStatus(r.getStatus())) continue;
                if (!r.getDate().equals(date)) continue;
                if (doOverlap(startTime, endTime,
                        r.getStartTime(), r.getEndTime())) {
                    total += r.getAttendeeCount();
                }
            }
            return total;
        } finally {
            manager.lockReadCalendar().unlock();
        }
    }
 
    public int getTotalActiveReservations() {
        return getActiveReservations().size();
    }
 
    public int getTotalReservations() {
        return getTotalActiveReservations();
    }
 
    /**
     * Cuántas unidades de un tipo de equipo están en uso AHORA MISMO.
     * Usado por el panel del admin para mostrar disponibilidad real.
     */
    public int getEquipmentInUseNow(Reservation.Equipment type) {
        manager.lockReadCalendar().lock();
        try {
            String nowDate = java.time.LocalDate.now().toString();
            String nowTime = java.time.LocalTime.now()
                    .format(java.time.format.DateTimeFormatter
                            .ofPattern("HH:mm"));
            int inUse = 0;
            for (Reservation r : timeSlots.values()) {
                if (isFreeStatus(r.getStatus())) continue;
                if (!r.getDate().equals(nowDate)) continue;
                if (r.getStartTime().compareTo(nowTime) <= 0
                        && r.getEndTime().compareTo(nowTime) > 0) {
                    Integer qty = r.getEquipmentQuantities().get(type);
                    if (qty != null) inUse += qty;
                }
            }
            return inUse;
        } finally {
            manager.lockReadCalendar().unlock();
        }
    }
 
    // =========================================================
    // PRIVADOS
    // =========================================================
 
    private Reservation findById(String reservationId) {
        for (Reservation r : timeSlots.values()) {
            if (r.getReservationId().equals(reservationId)) return r;
        }
        return null;
    }
 
    private boolean doOverlap(String s1, String e1, String s2, String e2) {
        return s1.compareTo(e2) < 0 && e1.compareTo(s2) > 0;
    }
 
    private boolean isFreeStatus(Reservation.Status st) {
        return st == Reservation.Status.LIBRE
            || st == Reservation.Status.CANCELADO
            || st == Reservation.Status.EXPIRADO
            || st == Reservation.Status.FINALIZADO;
    }
 
    /**
     * Equipos en uso en una franja horaria específica.
     * Fuente de verdad — no usa semáforos.
     */
    private int getEquipmentInUseForSlot(String date, String startTime,
            String endTime, Reservation.Equipment type) {
        int inUse = 0;
        for (Reservation r : timeSlots.values()) {
            if (isFreeStatus(r.getStatus())) continue;
            if (!r.getDate().equals(date)) continue;
            if (doOverlap(startTime, endTime,
                    r.getStartTime(), r.getEndTime())) {
                Integer qty = r.getEquipmentQuantities().get(type);
                if (qty != null) inUse += qty;
            }
        }
        return inUse;
    }
 
    /**
     * Total de un tipo de equipo según SynchronizationManager.
     */
    private int getTotalForType(Reservation.Equipment type) {
        switch (type) {
            case PROYECTOR: return manager.getTotalProjectors();
            case MICROFONO: return manager.getTotalMicrophones();
            case SONIDO:    return manager.getTotalSound();
            default:        return 0;
        }
    }
}