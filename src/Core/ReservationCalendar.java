package Core;

import Concurrency.SynchronizationManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core class responsible for managing the auditorium's schedule.
 * It handles the creation, confirmation, and cancellation of reservations 
 * while ensuring thread-safety through a SynchronizationManager.
 */
public class ReservationCalendar {

    /** Thread-safe storage for reservations, indexed by a time-based key. */
    private final Map<String, Reservation> timeSlots = 
            new ConcurrentHashMap<>();
    
    /** Coordinator for read/write locks and resource semaphores. */
    private final SynchronizationManager manager;

    /**
     * Constructs a new ReservationCalendar.
     * @param manager the manager handling concurrency and resources.
     */
    public ReservationCalendar(SynchronizationManager manager) {
        this.manager = manager;
    }

    /**
     * Generates a unique key based on the date and time range.
     * @return a formatted string "date-startTime-endTime".
     */
    private String generateKey(String date, String startTime, String endTime) {
        return date + "-" + startTime + "-" + endTime;
    }

    /**
     * Checks if a specific time slot is available for booking.
     *
     * @param date      the date to check
     * @param startTime the start of the time range
     * @param endTime   the end of the time range
     * @return true if the slot is empty or marked as LIBRE or CANCELADO
     */
    public boolean isAvailable(String date, String startTime, String endTime) {
        manager.lockReadCalendar().lock();
        try {
            String key = generateKey(date, startTime, endTime);
            Reservation r = timeSlots.get(key);

            return r == null
                    || r.getStatus() == Reservation.Status.CANCELADO
                    || r.getStatus() == Reservation.Status.EXPIRADO
                    || r.getStatus() == Reservation.Status.LIBRE;

        } finally {
            manager.lockReadCalendar().unlock();
        }
    }

    /**
     * Attempts to create a temporary reservation.
     * Validates overlaps and acquires necessary synchronization resources.
     *
     * @param clientId   identification of the requesting client
     * @param date       reservation date
     * @param startTime  start time of the event
     * @param endTime    end time of the event
     * @param attendees  number of people attending
     * @param equipment  requested equipment type
     * @param priority   priority level based on role
     * @return the created Reservation object, or null if overlap occurs
     * @throws InterruptedException if the thread is interrupted while waiting 
     * for resources
     */
    public Reservation reserveTemporarily(String clientId, String date,
                                          String startTime, String endTime,
                                          int attendees, 
                                          Reservation.Equipment equipment,
                                          Reservation.Priority priority) 
            throws InterruptedException {
        manager.lockWriteCalendar().lock();
        try {
            // Clean up any timed-out reservations before checking availability
            expireOverdue();

            // Overlap validation
            for (Reservation r : timeSlots.values()) {
                if (r.getStatus() == Reservation.Status.CANCELADO ||
                         r.getStatus() == Reservation.Status.EXPIRADO
                        || r.isExpired()) continue;
                if (!r.getDate().equals(date)) continue;

                if (doOverlap(startTime, endTime, r.getStartTime(), 
                        r.getEndTime())) {
                    return null;
                }
            }

            // Acquire resources from the manager
            manager.acquireForReservation(attendees, equipment);

            Reservation reservation = new Reservation(clientId, date, startTime,
                    endTime, attendees, equipment, priority);

            timeSlots.put(generateKey(date, startTime, endTime), reservation);

            return reservation;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            manager.lockWriteCalendar().unlock();
        }
    }

    /**
     * Confirms a temporary reservation, making it permanent and removing 
     * its TTL.
     *
     * @param reservationId the unique 8-character ID of the reservation 
     * to confirm
     * @return true if the reservation was successfully found and confirmed
     */
    public boolean confirmReservation(String reservationId) {
        manager.lockWriteCalendar().lock();
        try {
            Reservation r = findById(reservationId);
            if (r == null) return false;
            if (r.getStatus() != Reservation.Status.RESERVADO_TEMPORAL) return false;
            if (r.isExpired()) return false;

            r.setStatus(Reservation.Status.CONFIRMADO);
            return true;
        } finally {
            manager.lockWriteCalendar().unlock();
        }
    }

    /**
     * Cancels an active reservation and releases its resources back to 
     * the manager.
     * @param reservationId the unique identifier of the reservation to cancel
     * @return true if the reservation was found and successfully cancelled
     */
    public boolean cancelReservation(String reservationId) {
        manager.lockWriteCalendar().lock();
        try {
            Reservation r = findById(reservationId);
            if (r == null) return false;
            if (r.getStatus() == Reservation.Status.CANCELADO) return false;

            r.setStatus(Reservation.Status.CANCELADO);
            manager.releaseFromReservation(r.getAttendeeCount(), 
                    r.getEquipment());

            return true;
        } finally {
            manager.lockWriteCalendar().unlock();
        }
    }

    /**
     * Scans for and removes temporary reservations that have exceeded 
     * their TTL.
     * @return a list of newly expired reservations.
     */
    public List<Reservation> expireOverdue() {
        List<Reservation> expiredReservations = new ArrayList<>();
        manager.lockWriteCalendar().lock();
        try {
            for (Reservation r : timeSlots.values()) {
                if (r.isExpired()) {
                    r.setStatus(Reservation.Status.EXPIRADO);
                    manager.releaseFromReservation(r.getAttendeeCount(), 
                            r.getEquipment());
                    expiredReservations.add(r);
                }
            }
            return expiredReservations;
        } finally {
            manager.lockWriteCalendar().unlock();
        }
    }

    /** @return a list of all current reservations that are not cancelled. */
    public List<Reservation> getActiveReservations() {
        manager.lockReadCalendar().lock();
        try {
            List<Reservation> activeReservations = new ArrayList<>();
            for (Reservation r : timeSlots.values()) {
                if (r.getStatus() != Reservation.Status.CANCELADO
                    && r.getStatus() != Reservation.Status.EXPIRADO) {
                    activeReservations.add(r);
                }
            }
            return activeReservations;
        } finally {
            manager.lockReadCalendar().unlock();
        }
    }

    /**Retrieves a specific reservation by its unique identifier.
     * @param reservationId the unique 8-character ID to search for
     * @return the matching Reservation object, or null if not found
     */
    public Reservation getReservationById(String reservationId) {
        manager.lockReadCalendar().lock();
        try {
            return findById(reservationId);
        } finally {
            manager.lockReadCalendar().unlock();
        }
    }

    /** Helper method to find a reservation in the values of the map. */
    private Reservation findById(String reservationId) {
        for (Reservation r : timeSlots.values()) {
            if (r.getReservationId().equals(reservationId)) {
                return r;
            }
        }
        return null;
    }

    /** Logic to determine if two time intervals overlap. */
    private boolean doOverlap(String start1, String end1, String start2, 
            String end2){
        return start1.compareTo(end2) < 0 && end1.compareTo(start2) > 0;
    }

   /**
     * Calculates the total number of attendees currently booked within a 
     * specific time range.
     * This is used to check if the auditorium's maximum capacity is being
     * exceeded.
     *
     * @param date      the date to check the capacity for
     * @param startTime the start of the time range
     * @param endTime   the end of the time range
     * @return the sum of attendee counts for all active reservations in the 
     * given range
     */
    public int getOccupiedCapacityInRange(String date, String startTime, 
            String endTime) {
        manager.lockReadCalendar().lock();
        try {
            int totalOccupied = 0;
            for (Reservation r : timeSlots.values()) {
                if (r.getStatus() == Reservation.Status.CANCELADO
                    || r.getStatus() == Reservation.Status.EXPIRADO) continue;
                if (!r.getDate().equals(date)) continue;

                if (doOverlap(startTime, endTime, r.getStartTime(), 
                        r.getEndTime())) {
                    totalOccupied += r.getAttendeeCount();
                }
            }
            return totalOccupied;
        } finally {
            manager.lockReadCalendar().unlock();
        }
    }

   /**
     * Loads a reservation from a persistent source into the calendar.
     * This is typically used during system startup to restore saved state.
     *
     * @param reservation the Reservation object to be restored into the
     * memory map
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

    /** @return a copy of all reservations in the system. */
    public List<Reservation> getAllReservations() {
        manager.lockReadCalendar().lock();
        try {
            return new ArrayList<>(timeSlots.values());
        } finally {
            manager.lockReadCalendar().unlock();
        }
    }

    /** @return the count of current non-cancelled reservations. */
    public int getTotalActiveReservations() {
        return getActiveReservations().size();
    }

    /** @return for getTotalActiveReservations. */
    public int getTotalReservations() {
        return getTotalActiveReservations();
    }
}