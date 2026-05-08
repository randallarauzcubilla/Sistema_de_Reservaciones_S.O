package Core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class Reservation {

    public enum Status {
        LIBRE, RESERVADO_TEMPORAL, CONFIRMADO, CANCELADO, EXPIRADO, FINALIZADO
    }

    public enum Equipment {
        NINGUNO, PROYECTOR, MICROFONO, SONIDO, COMPLETO
    }

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
     * Mapa {tipo -> cantidad} de todos los equipos reservados.
     * Es la fuente de verdad para adquirir y liberar semáforos.
     */
    private final Map<Equipment, Integer> equipmentQuantities;

    // =========================================================
    // CONSTRUCTORES TEMPORALES
    // =========================================================

    /**
     * Constructor temporal sin equipo (NINGUNO).
     */
    public Reservation(String clientId, String date, String startTime,
            String endTime, int attendees, Equipment equipment,
            Priority priority) {
        this(clientId, date, startTime, endTime, attendees,
                equipment, 1, new LinkedHashMap<>(), priority);
    }

    /**
     * Constructor temporal principal con mapa de cantidades.
     *
     * @param equipment    equipo primario
     * @param equipmentQty cantidad del equipo primario
     * @param extraQty     mapa de equipos adicionales {tipo -> cantidad}
     */
    public Reservation(String clientId, String date, String startTime,
            String endTime, int attendees,
            Equipment equipment, int equipmentQty,
            Map<Equipment, Integer> extraQty,
            Priority priority) {
        this.reservationId =
                java.util.UUID.randomUUID().toString().substring(0, 8);
        this.clientId      = clientId;
        this.date          = date;
        this.startTime     = startTime;
        this.endTime       = endTime;
        this.attendeeCount = attendees;
        this.equipment     = equipment;
        this.priority      = priority;
        this.status        = Status.RESERVADO_TEMPORAL;
        this.expirationTtl = System.currentTimeMillis() + 30_000;
        this.equipmentQuantities = buildQuantityMap(
                equipment, equipmentQty, extraQty);
    }

    // =========================================================
    // CONSTRUCTORES RESTORED (persistencia)
    // =========================================================

    /**
     * Constructor restored sin equipo extra.
     */
    public Reservation(String clientId, String date, String startTime,
            String endTime, int attendees, Equipment equipment,
            Priority priority, boolean restored) {
        this(clientId, date, startTime, endTime, attendees,
                equipment, 1, new LinkedHashMap<>(), priority, restored);
    }

    /**
     * Constructor restored principal con mapa de cantidades.
     */
    public Reservation(String clientId, String date, String startTime,
            String endTime, int attendees,
            Equipment equipment, int equipmentQty,
            Map<Equipment, Integer> extraQty,
            Priority priority, boolean restored) {
        this.reservationId =
                java.util.UUID.randomUUID().toString().substring(0, 8);
        this.clientId      = clientId;
        this.date          = date;
        this.startTime     = startTime;
        this.endTime       = endTime;
        this.attendeeCount = attendees;
        this.equipment     = equipment;
        this.priority      = priority;
        this.status        = Status.CONFIRMADO;
        this.expirationTtl = Long.MAX_VALUE;
        this.equipmentQuantities = buildQuantityMap(
                equipment, equipmentQty, extraQty);
    }

    // =========================================================
    // LÓGICA DE ESTADO
    // =========================================================

    public boolean isExpired() {
        return status == Status.RESERVADO_TEMPORAL
                && System.currentTimeMillis() > expirationTtl;
    }

    public boolean isFinished() {
        if (status != Status.CONFIRMADO) return false;
        try {
            java.time.LocalDateTime end = java.time.LocalDateTime.of(
                    java.time.LocalDate.parse(date),
                    java.time.LocalTime.parse(endTime));
            return java.time.LocalDateTime.now().isAfter(end);
        } catch (Exception ex) {
            return false;
        }
    }

    public long getRemainingSeconds() {
        if (status != Status.RESERVADO_TEMPORAL) return -1;
        return Math.max(0,
                (expirationTtl - System.currentTimeMillis()) / 1000);
    }

    // =========================================================
    // GETTERS
    // =========================================================

    public String getReservationId()  { return reservationId; }
    public String getClientId()       { return clientId; }
    public String getDate()           { return date; }
    public String getStartTime()      { return startTime; }
    public String getEndTime()        { return endTime; }
    public int getAttendeeCount()     { return attendeeCount; }
    public Equipment getEquipment()   { return equipment; }
    public Priority getPriority()     { return priority; }
    public Status getStatus()         { return status; }
    public long getTTL()              { return expirationTtl; }

    public Map<Equipment, Integer> getEquipmentQuantities() {
        return Collections.unmodifiableMap(equipmentQuantities);
    }

    public void setStatus(Status status) { this.status = status; }

    @Override
    public String toString() {
        return String.format(
                "[%s] %s | %s %s-%s | %d personas | %s | %s | TTL:%ds",
                reservationId, clientId, date, startTime, endTime,
                attendeeCount, equipmentQuantities, status,
                getRemainingSeconds());
    }

    // =========================================================
    // PRIVADO
    // =========================================================

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