package Core;

import Concurrency.SynchronizationManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calendar that manages reservation scheduling with thread-safe access. Handles
 * creation, validation, and storage of reservations while coordinating
 * concurrent operations through a synchronization manager.
 */
public class ReservationCalendar {

    /**
     * Stores all reservations indexed by time slot key.
     */
    private final Map<String, Reservation> timeSlots
            = new ConcurrentHashMap<>();

    /**
     * Synchronization manager used for thread safety control.
     */
    private final SynchronizationManager manager;

    /**
     * Creates a new ReservationCalendar instance.
     *
     * @param manager synchronization manager used for concurrency control
     */
    public ReservationCalendar(SynchronizationManager manager) {
        this.manager = manager;
    }

    /**
     * Generates a unique key based on date and time range.
     *
     * @param date reservation date
     * @param start start time
     * @param end end time
     * @return unique key representing the time slot
     */
    private String generateKey(String date, String start, String end) {
        return date + "-" + start + "-" + end;
    }

    /**
     * Checks if a time slot is available for reservation.
     *
     * @param date reservation date
     * @param start start time
     * @param end end time
     * @return true if slot is available, false otherwise
     */
    public boolean isAvailable(String date, String start, String end) {
        manager.lockReadCalendar().lock();
        try {
            Reservation r = timeSlots.get(generateKey(date, start, end));
            return r == null || isFreeStatus(r.getStatus());
        } finally {
            manager.lockReadCalendar().unlock();
        }
    }

    /**
     * Creates a temporary reservation if no conflicts exist. Also validates
     * equipment availability and time overlaps.
     *
     * @param clientId client identifier
     * @param date reservation date
     * @param startTime start time
     * @param endTime end time
     * @param attendees number of attendees
     * @param equipmentQuantities equipment required with quantities
     * @param priority reservation priority level
     * @return created reservation or null if not possible
     * @throws InterruptedException if thread is interrupted during locking
     */
    public Reservation reserveTemporarily(String clientId, String date,
            String startTime, String endTime, int attendees,
            Map<Reservation.Equipment, Integer> equipmentQuantities,
            Reservation.Priority priority)
            throws InterruptedException {

        Map<Reservation.Equipment, Integer> toAcquire
                = equipmentQuantities != null && !equipmentQuantities.isEmpty()
                ? new LinkedHashMap<>(equipmentQuantities)
                : Collections.emptyMap();

        manager.lockWriteCalendar().lock();
        try {
            markFinishedInternal();
            expireOverdueInternal();

            for (Reservation r : timeSlots.values()) {
                if (isFreeStatus(r.getStatus())) {
                    continue;
                }
                if (!r.getDate().equals(date)) {
                    continue;
                }
                if (doOverlap(startTime, endTime,
                        r.getStartTime(), r.getEndTime())) {
                    return null;
                }
            }

            for (Map.Entry<Reservation.Equipment, Integer> entry
                    : toAcquire.entrySet()) {
                int inUse = getEquipmentInUseForSlot(
                        date, startTime, endTime, entry.getKey());
                int total = getTotalForType(entry.getKey());
                if (inUse + entry.getValue() > total) {
                    return null;
                }
            }

            Reservation.Equipment primary = Reservation.Equipment.NINGUNO;
            int primaryQty = 1;
            Map<Reservation.Equipment, Integer> extra = new LinkedHashMap<>();

            if (!toAcquire.isEmpty()) {
                var it = toAcquire.entrySet().iterator();
                var first = it.next();
                primary = first.getKey();
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
     * Creates a temporary reservation for a single equipment type.
     *
     * @param clientId client identifier
     * @param date reservation date
     * @param startTime start time
     * @param endTime end time
     * @param attendees number of attendees
     * @param equipment equipment type required
     * @param priority reservation priority level
     * @return created reservation or null if not possible
     * @throws InterruptedException if thread is interrupted during processing
     */
    public Reservation reserveTemporarilySingleEquipment(String clientId,
            String date, String startTime, String endTime, int attendees,
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

    /**
     * Confirms a temporary reservation, making it permanent if valid.
     *
     * @param reservationId reservation identifier
     * @return true if reservation was confirmed successfully
     */
    public boolean confirmReservation(String reservationId) {
        manager.lockWriteCalendar().lock();
        try {
            Reservation r = findById(reservationId);
            if (r == null) {
                return false;
            }
            if (r.getStatus() != Reservation.Status.RESERVADO_TEMPORAL) {
                return false;
            }
            if (r.isExpired()) {
                return false;
            }
            r.setStatus(Reservation.Status.CONFIRMADO);
            return true;
        } finally {
            manager.lockWriteCalendar().unlock();
        }
    }

    /**
     * Cancels an active reservation if it is not already finalized.
     *
     * @param reservationId reservation identifier
     * @return true if cancellation was successful
     */
    public boolean cancelReservation(String reservationId) {
        manager.lockWriteCalendar().lock();
        try {
            Reservation r = findById(reservationId);
            if (r == null) {
                return false;
            }
            Reservation.Status st = r.getStatus();
            if (st == Reservation.Status.CANCELADO
                    || st == Reservation.Status.EXPIRADO
                    || st == Reservation.Status.FINALIZADO) {
                return false;
            }
            r.setStatus(Reservation.Status.CANCELADO);
            return true;
        } finally {
            manager.lockWriteCalendar().unlock();
        }
    }

    /**
     * Marks all expired reservations and returns them.
     *
     * @return list of reservations that were marked as expired
     */
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

    /**
     * Marks reservations as finished if their end time has passed.
     */
    public void markFinishedReservations() {
        manager.lockWriteCalendar().lock();
        try {
            markFinishedInternal();
        } finally {
            manager.lockWriteCalendar().unlock();
        }
    }

    /**
     * Internal method that updates expired reservations without locking.
     */
    private void expireOverdueInternal() {
        for (Reservation r : timeSlots.values()) {
            if (r.isExpired()) {
                r.setStatus(Reservation.Status.EXPIRADO);
            }
        }
    }

    /**
     * Internal method that marks finished reservations without locking.
     */
    private void markFinishedInternal() {
        for (Reservation r : timeSlots.values()) {
            if (r.isFinished()) {
                r.setStatus(Reservation.Status.FINALIZADO);
            }
        }
    }

    /**
     * Returns all active (non-free) reservations.
     *
     * @return list of active reservations
     */
    public List<Reservation> getActiveReservations() {
        manager.lockReadCalendar().lock();
        try {
            List<Reservation> active = new ArrayList<>();
            for (Reservation r : timeSlots.values()) {
                if (!isFreeStatus(r.getStatus())) {
                    active.add(r);
                }
            }
            return active;
        } finally {
            manager.lockReadCalendar().unlock();
        }
    }

    /**
     * Returns a copy of all reservations in the system.
     *
     * @return list of all reservations
     */
    public List<Reservation> getAllReservations() {
        manager.lockReadCalendar().lock();
        try {
            return new ArrayList<>(timeSlots.values());
        } finally {
            manager.lockReadCalendar().unlock();
        }
    }

    /**
     * Retrieves a reservation by its unique identifier.
     *
     * @param reservationId reservation identifier
     * @return reservation if found, null otherwise
     */
    public Reservation getReservationById(String reservationId) {
        manager.lockReadCalendar().lock();
        try {
            return findById(reservationId);
        } finally {
            manager.lockReadCalendar().unlock();
        }
    }

    /**
     * Loads a previously restored reservation into the calendar if the slot is
     * free.
     *
     * @param reservation reservation to restore
     */
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

    /**
     * Calculates total occupied capacity within a given time range.
     *
     * @param date reservation date
     * @param startTime start of range
     * @param endTime end of range
     * @return total number of attendees occupying the range
     */
    public int getOccupiedCapacityInRange(String date,
            String startTime, String endTime) {
        manager.lockReadCalendar().lock();
        try {
            int total = 0;
            for (Reservation r : timeSlots.values()) {
                if (isFreeStatus(r.getStatus())) {
                    continue;
                }
                if (!r.getDate().equals(date)) {
                    continue;
                }
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

    /**
     * Returns the number of active reservations in the system.
     *
     * @return number of active reservations
     */
    public int getTotalActiveReservations() {
        return getActiveReservations().size();
    }

    /**
     * Returns total number of reservations (currently based on active ones).
     *
     * @return total reservations count
     */
    public int getTotalReservations() {
        return getTotalActiveReservations();
    }

    /**
     * Returns amount of equipment currently in use for the current time.
     *
     * @param type type of equipment
     * @return quantity of equipment in use
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
                if (isFreeStatus(r.getStatus())) {
                    continue;
                }
                if (!r.getDate().equals(nowDate)) {
                    continue;
                }
                if (r.getStartTime().compareTo(nowTime) <= 0
                        && r.getEndTime().compareTo(nowTime) > 0) {
                    Integer qty = r.getEquipmentQuantities().get(type);
                    if (qty != null) {
                        inUse += qty;
                    }
                }
            }
            return inUse;
        } finally {
            manager.lockReadCalendar().unlock();
        }
    }

    /**
     * Finds a reservation by its ID (internal lookup).
     *
     * @param reservationId reservation identifier
     * @return reservation if found, null otherwise
     */
    private Reservation findById(String reservationId) {
        for (Reservation r : timeSlots.values()) {
            if (r.getReservationId().equals(reservationId)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Checks whether two time intervals overlap.
     *
     * @return true if intervals overlap, false otherwise
     */
    private boolean doOverlap(String s1, String e1, String s2, String e2) {
        return s1.compareTo(e2) < 0 && e1.compareTo(s2) > 0;
    }

    /**
     * Determines whether a reservation status is considered free.
     *
     * @param st reservation status
     * @return true if status is free (not blocking), false otherwise
     */
    private boolean isFreeStatus(Reservation.Status st) {
        return st == Reservation.Status.LIBRE
                || st == Reservation.Status.CANCELADO
                || st == Reservation.Status.EXPIRADO
                || st == Reservation.Status.FINALIZADO;
    }

    /**
     * Calculates equipment usage for a specific slot without using semaphores.
     * Acts as a logical source of truth for availability checks.
     *
     * @param date reservation date
     * @param startTime start time
     * @param endTime end time
     * @param type equipment type
     * @return total equipment in use
     */
    private int getEquipmentInUseForSlot(String date, String startTime,
            String endTime, Reservation.Equipment type) {
        int inUse = 0;
        for (Reservation r : timeSlots.values()) {
            if (isFreeStatus(r.getStatus())) {
                continue;
            }
            if (!r.getDate().equals(date)) {
                continue;
            }
            if (doOverlap(startTime, endTime,
                    r.getStartTime(), r.getEndTime())) {
                Integer qty = r.getEquipmentQuantities().get(type);
                if (qty != null) {
                    inUse += qty;
                }
            }
        }
        return inUse;
    }

    /**
     * Returns total available units for a specific equipment type.
     *
     * @param type equipment type
     * @return total units available in the system
     */
    private int getTotalForType(Reservation.Equipment type) {
        switch (type) {
            case PROYECTOR:
                return manager.getTotalProjectors();
            case MICROFONO:
                return manager.getTotalMicrophones();
            case SONIDO:
                return manager.getTotalSound();
            default:
                return 0;
        }
    }
}