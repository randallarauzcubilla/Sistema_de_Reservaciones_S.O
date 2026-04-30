package Core;

/**
 * Represents a reservation in the auditorium system.
 * Handles temporary and confirmed states, including TTL (Time-To-Live) 
 * management for temporary holds.
 */
public class Reservation {

    /**
     * Internal status of the reservation.
     */
    public enum Status {
        LIBRE, RESERVADO_TEMPORAL, CONFIRMADO, CANCELADO, EXPIRADO
    }

    /**
     * Equipment requested for the reservation. 
     * Values match UI selection in Spanish.
     */
    public enum Equipment {
        NINGUNO, PROYECTOR, MICROFONO, SONIDO, COMPLETO
    }

    /**
     * Priority level based on the user's role.
     * Values match UI selection in Spanish.
     */
    public enum Priority {
        ESTUDIANTE, DOCENTE, DECANATURA
    }

    private final String reservationId;
    private final String clientId;
    private final String date;
    private final String startTime;
    private final String endTime;
    private final int attendeeCount;
    private final Equipment equipment;
    private final Priority priority;
    private volatile Status status;
    private final long expirationTtl;

    /**
     * Standard constructor for a temporary reservation.
     * Sets a 30-second TTL from the moment of creation.
     *
     * @param clientId      identification of the requesting client
     * @param date          reservation date
     * @param startTime     start time of the event
     * @param endTime       end time of the event
     * @param attendees      number of attendees
     * @param equipment     requested equipment type
     * @param priority      priority level based on role
     */
    public Reservation(String clientId, String date, String startTime,
            String endTime, int attendees, Equipment equipment,
            Priority priority) {
        this.reservationId = 
                java.util.UUID.randomUUID().toString().substring(0, 8);
        this.clientId = clientId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.attendeeCount = attendees;
        this.equipment = equipment;
        this.priority = priority;
        this.status = Status.RESERVADO_TEMPORAL;
        this.expirationTtl = System.currentTimeMillis() + 30_000;
    }

    /**
     * Restoration constructor used exclusively by persistence handlers.
     * Creates a confirmed reservation that does not expire.
     *
     * @param clientId      identification of the requesting client
     * @param date          reservation date
     * @param startTime     start time of the event
     * @param endTime       end time of the event
     * @param attendees      number of attendees
     * @param equipment     requested equipment type
     * @param priority      priority level based on role
     * @param restored      flag to distinguish from standard creation
     */
    public Reservation(String clientId, String date, String startTime,
            String endTime, int attendees, Equipment equipment,
            Priority priority, boolean restored) {
        this.reservationId = 
                java.util.UUID.randomUUID().toString().substring(0, 8);
        this.clientId = clientId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.attendeeCount = attendees;
        this.equipment = equipment;
        this.priority = priority;
        this.status = Status.CONFIRMADO;
        this.expirationTtl = Long.MAX_VALUE; // never expires
    }

    /**
     * Checks if a temporary reservation has exceeded its allowed time.
     * @return true if the current time is greater than the expiration timestamp
     */
    public boolean isExpired() {
        return status == Status.RESERVADO_TEMPORAL
                && System.currentTimeMillis() > expirationTtl;
    }

    /**
     * Calculates the remaining time for temporary holds.
     * @return seconds left before expiration, or -1 if the reservation is 
     * not temporary
     */
    public long getRemainingSeconds() {
        if (status != Status.RESERVADO_TEMPORAL) return -1;

        long remaining = (expirationTtl - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    // --- Getters & Setters ---

    /** @return unique 8-character reservation identifier */
    public String getReservationId() { return reservationId; }

    /** @return identification of the client owner */
    public String getClientId() { return clientId; }

    /** @return the date assigned to the reservation */
    public String getDate() { return date; }

    /** @return starting time string */
    public String getStartTime() { return startTime; }

    /** @return ending time string */
    public String getEndTime() { return endTime; }

    /** @return total number of people attending */
    public int getAttendeeCount() { return attendeeCount; }

    /** @return the selected equipment enum */
    public Equipment getEquipment() { return equipment; }

    /** @return the assigned priority enum */
    public Priority getPriority() { return priority; }

    /** @return current status of the reservation */
    public Status getStatus() { return status; }

    /** @return raw expiration timestamp in milliseconds */
    public long getTTL() { return expirationTtl; }

    /**
     * Updates the current status of the reservation.
     * @param status the new status to apply
     */
    public void setStatus(Status status) {
        this.status = status;
    }

    /**
     * Provides a formatted representation of the reservation.
     * Labels are kept in Spanish for consistent user display.
     * @return formatted status string
     */
    @Override
    public String toString() {
        return String.format(
                "[%s] %s | %s %s-%s | %d personas | %s | %s | TTL: %ds",
                reservationId, clientId, date, startTime, endTime,
                attendeeCount, equipment, status, getRemainingSeconds());
    }
}