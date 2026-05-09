package Server;

import Concurrency.TTLQueue;
import Core.AuditoriumManager;
import Core.Reservation;
import Core.ReservationCalendar;
import Logging.AuditoriumLog;
import Persistence.ReservationPersistence;
import Security.RoleValidator;
import java.io.*;
import java.net.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles communication and request processing for a connected client. Manages
 * reservations, validations, and client interaction with the server.
 */
public class ClientHandler extends Thread {

    final Socket socket;
    private final String clientId;
    private final String clientName;
    private final String clientRole;
    private final ReservationCalendar calendar;
    private final AuditoriumManager resources;
    private final TTLQueue ttlQueue;
    private final AuditoriumLog log;

    DataInputStream inputStream;
    DataOutputStream outputStream;

    /**
     * Creates a new client handler for a connected user.
     *
     * @param socket the client socket connection
     * @param clientData client information string
     * @param calendar the reservation calendar instance
     * @param resources the auditorium resource manager
     * @param ttlQueue the TTL reservation queue
     * @param log the system log manager
     */
    public ClientHandler(Socket socket, String clientData,
            ReservationCalendar calendar, AuditoriumManager resources,
            TTLQueue ttlQueue, AuditoriumLog log) {
        this.socket = socket;
        this.calendar = calendar;
        this.resources = resources;
        this.ttlQueue = ttlQueue;
        this.log = log;

        String[] parts = clientData.split("\\|", 3);
        this.clientName = parts[0].trim();
        this.clientId = parts.length >= 2 ? parts[1].trim() : parts[0].trim();
        this.clientRole = parts.length >= 3 ? parts[2].trim() : "ESTUDIANTE";

        try {
            inputStream = new DataInputStream(
                    new BufferedInputStream(socket.getInputStream()));
            outputStream = new DataOutputStream(
                    new BufferedOutputStream(socket.getOutputStream()));
        } catch (IOException e) {
            System.out.println("[ERROR] ClientHandler constructor: "
                    + e.getMessage());
        }
    }

    /**
     * Executes the client session lifecycle. Validates the client role,
     * processes incoming commands, and handles client disconnection events.
     */
    @Override
    public void run() {
        if (!RoleValidator.canUseRole(clientId, clientRole)) {
            sendResponse("ERROR|ROL_NO_AUTORIZADO");
            log.log("SEGURIDAD", clientName + " intentó acceder como "
                    + clientRole + " sin autorización");
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            return;
        }

        log.log("CONEXION", clientName + " conectado como " + clientRole);
        sendResponse("OK|CONECTADO");
        sendHistory();
        sendOccupiedSlots();

        while (true) {
            try {
                String message = inputStream.readUTF().trim();
                if (message.isEmpty()) {
                    continue;
                }

                String[] parts = message.split("\\|");
                String command = parts[0];

                switch (command) {
                    case "CONSULTAR":
                        processQuery(parts);
                        break;
                    case "RESERVAR":
                        try {
                            processReservation(parts);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(ClientHandler.class.getName())
                                    .log(Level.SEVERE, null, ex);
                        }
                        break;
                    case "CONFIRMAR":
                        processConfirmation(parts);
                        break;
                    case "CANCELAR":
                        processCancellation(parts);
                        break;
                    case "ESTADO":
                        processStatus(parts);
                        break;
                    case "EDITAR_RESERVA":
                        try {
                            processEdition(parts);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(ClientHandler.class.getName())
                                    .log(Level.SEVERE, null, ex);
                        }
                        break;
                    default:
                        sendResponse("ERROR|COMANDO_DESCONOCIDO");
                        break;
                }
            } catch (IOException e) {
                ServerApp.connectedClients.remove(this);
                log.log("DESCONEXION", clientName + " (DNI: "
                        + clientId + ") desconectado");
                break;
            }
        }
    }

    /**
     * Sends the reservation history associated with the current client.
     * Includes reservation details and current status information.
     */
    private void sendHistory() {
        List<Reservation> all = calendar.getAllReservations();
        StringBuilder sb = new StringBuilder("HISTORIAL");
        for (Reservation r : all) {
            if (!r.getClientId().equals(clientId)) {
                continue;
            }
            sb.append("|")
                    .append(r.getReservationId()).append(",")
                    .append(r.getDate()).append(",")
                    .append(r.getStartTime()).append(",")
                    .append(r.getEndTime()).append(",")
                    .append(r.getStatus().toString());
        }
        sendResponse(sb.toString());
    }

    /**
     * Sends the list of currently occupied reservation time slots. Excludes
     * canceled, expired, and completed reservations.
     */
    private void sendOccupiedSlots() {
        List<Reservation> all = calendar.getAllReservations();
        StringBuilder sb = new StringBuilder("SLOTS_OCUPADOS");
        for (Reservation r : all) {
            Reservation.Status s = r.getStatus();
            if (s == Reservation.Status.CANCELADO
                    || s == Reservation.Status.EXPIRADO
                    || s == Reservation.Status.FINALIZADO) {
                continue;
            }
            sb.append("|")
                    .append(r.getDate()).append(",")
                    .append(r.getStartTime()).append(",")
                    .append(r.getEndTime());
        }
        sendResponse(sb.toString());
    }

    /**
     * Processes an availability query for a reservation time slot.
     *
     * @param p command parameters containing date and time range
     */
    private void processQuery(String[] p) {
        if (p.length < 4) {
            sendResponse("ERROR|PARAMETROS_INSUFICIENTES");
            return;
        }
        boolean available = calendar.isAvailable(p[1], p[2], p[3]);
        log.log("CONSULTA", clientId + " consultó "
                + p[1] + " " + p[2] + "-" + p[3]);
        sendResponse(available ? "OK|DISPONIBLE" : "ERROR|FRANJA_OCUPADA");
    }

    /**
     * Processes a temporary reservation request from the client. Validates
     * reservation data, checks availability and capacity, and creates a
     * temporary reservation with TTL control.
     *
     * @param p command parameters containing reservation information
     * @throws InterruptedException if the reservation process is interrupted
     */
    private void processReservation(String[] p) throws InterruptedException {
        if (p.length < 6) {
            sendResponse("ERROR|PARAMETROS_INSUFICIENTES");
            return;
        }

        try {
            String date = p[1];
            String startTime = p[2];
            String endTime = p[3];
            int attendees = Integer.parseInt(p[4]);

            Map<Reservation.Equipment, Integer> equipMap
                    = parseEquipmentField(p[5]);

            Reservation.Priority priority = Reservation.Priority.ESTUDIANTE;
            if (p.length >= 7) {
                try {
                    priority = Reservation.Priority.valueOf(p[6]);
                } catch (IllegalArgumentException ignored) {
                }
            }

            LocalDate reservationDate;
            try {
                reservationDate = LocalDate.parse(date);
            } catch (Exception e) {
                sendResponse("ERROR|FECHA_INVALIDA");
                return;
            }
            LocalDate today = LocalDate.now();
            if (reservationDate.isBefore(today)) {
                sendResponse("ERROR|FECHA_EN_EL_PASADO");
                return;
            }
            if (!isValidTime(startTime) || !isValidTime(endTime)) {
                sendResponse("ERROR|HORA_INVALIDA");
                return;
            }
            if (reservationDate.isEqual(today)) {
                if (LocalTime.parse(startTime).isBefore(LocalTime.now())) {
                    sendResponse("ERROR|HORA_EN_EL_PASADO");
                    return;
                }
            }
            if (!LocalTime.parse(endTime).isAfter(LocalTime.parse(startTime))) {
                sendResponse("ERROR|HORA_FIN_INVALIDA");
                return;
            }

            int occupied = calendar.getOccupiedCapacityInRange(
                    date, startTime, endTime);
            if (occupied + attendees > ServerApp.manager.getMaxCapacity()) {
                sendResponse("ERROR|SIN_CAPACIDAD");
                return;
            }

            Reservation reservation = calendar.reserveTemporarily(
                    clientId, date, startTime, endTime,
                    attendees, equipMap, priority);

            if (reservation == null) {
                sendResponse("ERROR|FRANJA_OCUPADA");
                return;
            }

            ttlQueue.add(reservation);
            log.logReservation(reservation);
            sendResponse("OK|TEMPORAL|" + reservation.getReservationId()
                    + "|TTL:" + reservation.getRemainingSeconds());
            broadcastToOthers("SLOT_NUEVO|" + date
                    + "|" + startTime + "|" + endTime);

        } catch (NumberFormatException e) {
            sendResponse("ERROR|ASISTENTES_INVALIDOS");
        } catch (IllegalArgumentException e) {
            sendResponse("ERROR|EQUIPO_INVALIDO");
        }
    }

    /**
     * Parses the equipment field from the reservation protocol. Supports empty,
     * full equipment, or specific equipment quantities.
     *
     * @param field equipment specification string from the client
     * @return map of equipment types and requested quantities
     */
    private Map<Reservation.Equipment, Integer> parseEquipmentField(
            String field) {
        Map<Reservation.Equipment, Integer> map = new LinkedHashMap<>();
        if (field == null || field.isBlank() || "NINGUNO".equals(field)) {
            return map;
        }
        if ("COMPLETO".equals(field)) {
            map.put(Reservation.Equipment.PROYECTOR,
                    ServerApp.manager.getTotalProjectors());
            map.put(Reservation.Equipment.MICROFONO,
                    ServerApp.manager.getTotalMicrophones());
            map.put(Reservation.Equipment.SONIDO,
                    ServerApp.manager.getTotalSound());
            return map;
        }
        for (String token : field.split(",")) {
            token = token.trim();
            if (token.isEmpty()) {
                continue;
            }
            String[] kv = token.split(":");
            try {
                Reservation.Equipment eq
                        = Reservation.Equipment.valueOf(kv[0].trim());
                int qty = kv.length >= 2
                        ? Math.max(1, Integer.parseInt(kv[1].trim()))
                        : 1;
                if (eq != Reservation.Equipment.NINGUNO
                        && eq != Reservation.Equipment.COMPLETO) {
                    map.merge(eq, qty, Integer::sum);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return map;
    }

    /**
     * Processes a reservation confirmation request. Validates ownership,
     * existence, and expiration status before confirming.
     *
     * @param p command parameters containing the reservation ID
     */
    private void processConfirmation(String[] p) {
        if (p.length < 2) {
            sendResponse("ERROR|PARAMETROS_INSUFICIENTES");
            return;
        }
        String reservationId = p[1];
        Reservation reservation = calendar.getReservationById(reservationId);
        if (reservation == null) {
            sendResponse("ERROR|RESERVA_NO_ENCONTRADA");
            return;
        }
        if (!reservation.getClientId().equals(clientId)) {
            sendResponse("ERROR|NO_AUTORIZADO");
            return;
        }
        if (reservation.isExpired()) {
            sendResponse("ERROR|RESERVA_EXPIRADA");
            return;
        }
        boolean confirmed = calendar.confirmReservation(reservationId);
        if (confirmed) {
            ttlQueue.remove(reservationId);
            log.logConfirmation(reservation);
            sendResponse("OK|CONFIRMADO|" + reservationId);
        } else {
            sendResponse("ERROR|NO_SE_PUDO_CONFIRMAR");
        }
    }

    /**
     * Processes a reservation cancellation request. Validates ownership and
     * existence before canceling and freeing the slot.
     *
     * @param p command parameters containing the reservation ID
     */
    private void processCancellation(String[] p) {
        if (p.length < 2) {
            sendResponse("ERROR|PARAMETROS_INSUFICIENTES");
            return;
        }
        String reservationId = p[1];
        Reservation reservation = calendar.getReservationById(reservationId);
        if (reservation == null) {
            sendResponse("ERROR|RESERVA_NO_ENCONTRADA");
            return;
        }
        if (!reservation.getClientId().equals(clientId)) {
            sendResponse("ERROR|NO_AUTORIZADO");
            return;
        }
        boolean cancelled = calendar.cancelReservation(reservationId);
        if (cancelled) {
            ttlQueue.remove(reservationId);
            log.logCancellation(reservation, "Cancelado por cliente");
            sendResponse("OK|CANCELADO|" + reservationId);
            broadcastToOthers("SLOT_LIBRE|" + reservation.getDate()
                    + "|" + reservation.getStartTime()
                    + "|" + reservation.getEndTime());
        } else {
            sendResponse("ERROR|NO_SE_PUDO_CANCELAR");
        }
    }

    /**
     * Processes a request to check the status of a reservation. Returns current
     * state and remaining TTL if applicable.
     *
     * @param p command parameters containing the reservation ID
     */
    private void processStatus(String[] p) {
        if (p.length < 2) {
            sendResponse("ERROR|PARAMETROS_INSUFICIENTES");
            return;
        }
        Reservation reservation = calendar.getReservationById(p[1]);
        if (reservation == null) {
            sendResponse("ERROR|RESERVA_NO_ENCONTRADA");
            return;
        }
        sendResponse("OK|ESTADO|" + reservation.getStatus()
                + "|TTL:" + reservation.getRemainingSeconds());
    }

    /**
     * Processes a reservation edition request. Validates new data, replaces the
     * existing reservation, and performs rollback if the new slot is
     * unavailable.
     *
     * @param p command parameters containing reservation ID and new data
     * @throws InterruptedException if the editing process is interrupted
     */
    private void processEdition(String[] p) throws InterruptedException {
        if (p.length < 7) {
            sendResponse("ERROR|PARAMETROS_INSUFICIENTES");
            return;
        }
        String reservationId = p[1];
        String newDate = p[2];
        String newStartTime = p[3];
        String newEndTime = p[4];
        int newAttendees;
        Reservation.Equipment newEquipment;
        try {
            newAttendees = Integer.parseInt(p[5]);
            newEquipment = Reservation.Equipment.valueOf(p[6]);
        } catch (NumberFormatException e) {
            sendResponse("ERROR|PARAMETROS_INVALIDOS");
            return;
        }

        Reservation original = calendar.getReservationById(reservationId);
        if (original == null) {
            sendResponse("ERROR|RESERVA_NO_ENCONTRADA");
            return;
        }

        LocalDate parsedDate;
        try {
            parsedDate = LocalDate.parse(newDate);
        } catch (Exception e) {
            sendResponse("ERROR|FECHA_INVALIDA");
            return;
        }
        if (parsedDate.isBefore(LocalDate.now())) {
            sendResponse("ERROR|FECHA_EN_EL_PASADO");
            return;
        }
        if (!isValidTime(newStartTime) || !isValidTime(newEndTime)) {
            sendResponse("ERROR|HORA_INVALIDA");
            return;
        }
        if (!LocalTime.parse(newEndTime).isAfter(LocalTime.parse(newStartTime))) {
            sendResponse("ERROR|HORA_FIN_INVALIDA");
            return;
        }

        calendar.cancelReservation(reservationId);
        ttlQueue.remove(reservationId);

        Reservation newRes = calendar.reserveTemporarily(
                original.getClientId(),
                newDate, newStartTime, newEndTime,
                newAttendees,
                original.getEquipmentQuantities(),
                original.getPriority());

        if (newRes == null) {
            Reservation restored = calendar.reserveTemporarily(
                    original.getClientId(),
                    original.getDate(), original.getStartTime(),
                    original.getEndTime(),
                    original.getAttendeeCount(),
                    original.getEquipmentQuantities(),
                    original.getPriority());
            if (restored != null) {
                calendar.confirmReservation(restored.getReservationId());
            }
            sendResponse("ERROR|FRANJA_OCUPADA");
            return;
        }

        calendar.confirmReservation(newRes.getReservationId());
        ReservationPersistence.save(calendar);
        log.log("EDICION", "Reserva " + reservationId + " editada → "
                + newRes.getReservationId() + " | "
                + newDate + " " + newStartTime + "-" + newEndTime);
        sendResponse("OK|EDITADO|" + newRes.getReservationId());
    }

    /**
     * Validates if a time string follows HH:mm format and represents a valid
     * time.
     *
     * @param time time string to validate
     * @return true if the time is valid, false otherwise
     */
    private boolean isValidTime(String time) {
        if (time == null || !time.matches("\\d{2}:\\d{2}")) {
            return false;
        }
        try {
            LocalTime.parse(time);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Sends a message to all connected clients except the current one. Used to
     * notify state changes in real time.
     *
     * @param msg message to broadcast to other clients
     */
    private void broadcastToOthers(String msg) {
        synchronized (ServerApp.connectedClients) {
            for (ClientHandler ch : ServerApp.connectedClients) {
                if (ch != this) {
                    ch.send(msg);
                }
            }
        }
    }

    /**
     * Sends a response message to the connected client in a thread-safe way.
     *
     * @param message message to be sent to the client
     */
    private void sendResponse(String message) {
        try {
            synchronized (outputStream) {
                outputStream.writeUTF(message);
                outputStream.flush();
            }
        } catch (IOException e) {
            System.out.println("[ERROR] Responder a " + clientId + ": "
                    + e.getMessage());
        }
    }

    /**
     * Returns the client unique identifier.
     *
     * @return client ID
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Returns the client display name.
     *
     * @return client name
     */
    public String getClientName() {
        return clientName;
    }

    /**
     * Sends a message to the client in a thread-safe manner. Used for
     * server-initiated notifications.
     *
     * @param msg message to send to the client
     */
    public void send(String msg) {
        try {
            synchronized (outputStream) {
                outputStream.writeUTF(msg);
                outputStream.flush();
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * Closes the client socket connection safely. Releases all associated
     * network resources.
     */
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}