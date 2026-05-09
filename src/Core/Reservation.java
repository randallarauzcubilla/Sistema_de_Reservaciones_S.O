package Core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a reservation in the system, including client data, schedule,
 * equipment requirements, priority, and lifecycle status.
 */
public class Reservation {

    /**
     * Current state of a reservation.
     */
    public enum Status {
        LIBRE, RESERVADO_TEMPORAL, CONFIRMADO, CANCELADO, EXPIRADO, FINALIZADO
    }

    /**
     * Type of equipment associated with a reservation.
     */
    public enum Equipment {
        NINGUNO, PROYECTOR, MICROFONO, SONIDO, COMPLETO
    }

    /**
     * Priority level of a reservation.
     */
    public enum Priority {
        ESTUDIANTE, DOCENTE, DECANATURA
    }

    private final String reservationId;
    private final String clientId;
    private String date;               
    private String startTime;         
    private String endTime;           
    private int attendeeCount;         
    private Equipment equipment;       
    private final Priority priority;
    private volatile Status status;
    private final long expirationTtl;
    private Map<Equipment, Integer> equipmentQuantities; 

    /**
     * Creates a temporary reservation with default equipment quantity.
     *
     * @param clientId client identifier
     * @param date reservation date
     * @param startTime start time
     * @param endTime end time
     * @param attendees number of attendees
     * @param equipment required equipment type
     * @param priority reservation priority
     */
    public Reservation(String clientId, String date, String startTime,
            String endTime, int attendees, Equipment equipment,
            Priority priority) {
        this(clientId, date, startTime, endTime, attendees,
                equipment, 1, new LinkedHashMap<>(), priority);
    }

    /**
     * Creates a reservation with custom equipment quantities.
     *
     * @param clientId client identifier
     * @param date reservation date
     * @param startTime start time
     * @param endTime end time
     * @param attendees number of attendees
     * @param equipment primary equipment type
     * @param equipmentQty quantity of primary equipment
     * @param extraQty additional equipment quantities
     * @param priority reservation priority
     */
    public Reservation(String clientId, String date, String startTime,
            String endTime, int attendees,
            Equipment equipment, int equipmentQty,
            Map<Equipment, Integer> extraQty,
            Priority priority) {
        this.reservationId = java.util.UUID.randomUUID()
                .toString().substring(0, 8);
        this.clientId = clientId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.attendeeCount = attendees;
        this.equipment = equipment;
        this.priority = priority;
        this.status = Status.RESERVADO_TEMPORAL;
        this.expirationTtl = System.currentTimeMillis() + 30_000;
        this.equipmentQuantities = buildQuantityMap(
                equipment, equipmentQty, extraQty);
    }

    /**
     * Creates a confirmed reservation (non-expiring).
     *
     * @param clientId client identifier
     * @param date reservation date
     * @param startTime start time
     * @param endTime end time
     * @param attendees number of attendees
     * @param equipment primary equipment type
     * @param priority reservation priority
     * @param restored indicates restored/confirmed state
     */
    public Reservation(String clientId, String date, String startTime,
            String endTime, int attendees, Equipment equipment,
            Priority priority, boolean restored) {
        this(clientId, date, startTime, endTime, attendees,
                equipment, 1, new LinkedHashMap<>(), priority, restored);
    }

    /**
     * Creates a confirmed reservation with custom equipment quantities.
     *
     * @param clientId client identifier
     * @param date reservation date
     * @param startTime start time
     * @param endTime end time
     * @param attendees number of attendees
     * @param equipment primary equipment type
     * @param equipmentQty quantity of primary equipment
     * @param extraQty additional equipment quantities
     * @param priority reservation priority
     * @param restored indicates restored/confirmed state
     */
    public Reservation(String clientId, String date, String startTime,
            String endTime, int attendees,
            Equipment equipment, int equipmentQty,
            Map<Equipment, Integer> extraQty,
            Priority priority, boolean restored) {
        this.reservationId = java.util.UUID.randomUUID()
                .toString().substring(0, 8);
        this.clientId = clientId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.attendeeCount = attendees;
        this.equipment = equipment;
        this.priority = priority;
        this.status = Status.CONFIRMADO;
        this.expirationTtl = Long.MAX_VALUE;
        this.equipmentQuantities = buildQuantityMap(
                equipment, equipmentQty, extraQty);
    }

    /**
     * Checks if the reservation has expired (temporary reservations only).
     *
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        return status == Status.RESERVADO_TEMPORAL
                && System.currentTimeMillis() > expirationTtl;
    }

    /**
     * Checks if the reservation has already finished.
     *
     * @return true if current time is after end time
     */
    public boolean isFinished() {
        if (status != Status.CONFIRMADO) {
            return false;
        }
        try {
            java.time.LocalDateTime end = java.time.LocalDateTime.of(
                    java.time.LocalDate.parse(date),
                    java.time.LocalTime.parse(endTime));
            return java.time.LocalDateTime.now().isAfter(end);
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Gets remaining time in seconds for temporary reservations.
     *
     * @return seconds remaining, or -1 if not temporary
     */
    public long getRemainingSeconds() {
        if (status != Status.RESERVADO_TEMPORAL) {
            return -1;
        }
        return Math.max(0,
                (expirationTtl - System.currentTimeMillis()) / 1000);
    }

    // GETTERS

    public String getReservationId() {
        return reservationId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getDate() {
        return date;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public int getAttendeeCount() {
        return attendeeCount;
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public Priority getPriority() {
        return priority;
    }

    public Status getStatus() {
        return status;
    }

    public long getTTL() {
        return expirationTtl;
    }

    /**
     * Returns an immutable view of the equipment quantities map.
     *
     * @return equipment quantity map
     */
    public Map<Equipment, Integer> getEquipmentQuantities() {
        return Collections.unmodifiableMap(equipmentQuantities);
    }

    /**
     * Updates reservation status.
     *
     * @param status new reservation status
     */
    public void setStatus(Status status) {
        this.status = status;
    }

    /**
     * Updates the reservation date.
     *
     * @param date new date in YYYY-MM-DD format
     */
    public void setDate(String date) {
        this.date = date;
    }

    /**
     * Updates the reservation start time.
     *
     * @param startTime new start time in HH:mm format
     */
    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    /**
     * Updates the reservation end time.
     *
     * @param endTime new end time in HH:mm format
     */
    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    /**
     * Updates the number of attendees.
     *
     * @param attendeeCount new attendee count
     */
    public void setAttendeeCount(int attendeeCount) {
        this.attendeeCount = attendeeCount;
    }

    /**
     * Updates the primary equipment type.
     *
     * @param equipment new equipment type
     */
    public void setEquipment(Equipment equipment) {
        this.equipment = equipment;
    }

    /**
     * Replaces the equipment quantities map in-place (clears and refills).
     * The existing map instance is reused so external references remain valid.
     *
     * @param newQuantities new equipment quantities; null is treated as empty
     */
    public void setEquipmentQuantities(Map<Equipment, Integer> newQuantities) {
        this.equipmentQuantities.clear();
        if (newQuantities != null) {
            this.equipmentQuantities.putAll(newQuantities);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "[%s] %s | %s %s-%s | %d personas | %s | %s | TTL:%ds",
                reservationId, clientId, date, startTime, endTime,
                attendeeCount, equipmentQuantities, status,
                getRemainingSeconds());
    }

    /**
     * Builds a normalized equipment quantity map.
     */
    private Map<Equipment, Integer> buildQuantityMap(
            Equipment primary, int primaryQty,
            Map<Equipment, Integer> extra) {

        Map<Equipment, Integer> map = new LinkedHashMap<>();

        if (primary != null && primary != Equipment.NINGUNO
                && primary != Equipment.COMPLETO) {
            map.put(primary, Math.max(1, primaryQty));
        }

        if (extra != null) {
            for (Map.Entry<Equipment, Integer> e : extra.entrySet()) {
                if (e.getKey() != null
                        && e.getKey() != Equipment.NINGUNO
                        && e.getKey() != Equipment.COMPLETO) {
                    map.merge(e.getKey(),
                            Math.max(1, e.getValue()), Integer::sum);
                }
            }
        }
        return map;
    }
}