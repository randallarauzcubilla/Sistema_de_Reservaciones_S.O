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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Individual worker thread responsible for managing a single client connection.
 * This class implements the communication protocol between the client and 
 * the server, handling requests such as making, confirming, and canceling 
 * reservations. 
 * It acts as a bridge between the network sockets and the core business 
 * logic (Calendar, Resources, and TTL Queue), ensuring thread-safe operations
 * via the {@code SynchronizationManager} provided by the calendar.
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
     * Constructs a ClientHandler to manage a specific client connection.
     * Parses client information (name, ID, and role) from a delimited string
     * and initializes the buffered communication streams.
     *
     * @param socket     the client's connection socket.
     * @param clientData formatted string containing client info separated 
     * by "|".
     * @param calendar   reference to the global reservation calendar.
     * @param resources  reference to the auditorium resource manager.
     * @param ttlQueue   queue for handling temporary reservation expirations.
     * @param log        system logger for recording operations.
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
        this.clientId = parts.length >= 
                2 ? parts[1].trim() : parts[0].trim();
        this.clientRole = parts.length >= 3 ? parts[2].trim() : "ESTUDIANTE";

        try {
            inputStream = new DataInputStream(new BufferedInputStream
        (socket.getInputStream()));
            outputStream = new DataOutputStream(new BufferedOutputStream
        (socket.getOutputStream()));
        } catch (IOException e) {
        System.out.println("[ERROR] HiloReserva constructor: " 
                + e.getMessage());
        }
    }

    /**
     * Main execution loop for the client handler.
     * Validates roles, manages the connection lifecycle, and dispatches 
     * commands.
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

        log.log("CONEXION",clientName+" conectado como " + clientRole);
        sendResponse("OK|CONECTADO");
        sendHistory();

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
                    case "RESERVAR": {
                        try {
                            processReservation(parts);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(ClientHandler.class.getName()
                            ).log(Level.SEVERE, null, ex);
                        }
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
                    case "EDITAR_RESERVA": {
                        try {
                            processEdition(parts);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(ClientHandler.class.getName()
                            ).log(Level.SEVERE, null, ex);
                        }
                    }
                    break;
                    default:
                        sendResponse("ERROR|COMANDO_DESCONOCIDO");
                        break;
                }
            } catch (IOException e) {
                ServerApp.connectedClients.remove(this);
                log.log("DESCONEXION", clientName + " (DNI: " + 
                        clientId + ") desconectado");
                System.out.println("[HILO] " + clientName+ " desconectado.");
                break;
            }
        }
    }

    /**
     * Sends the client's active reservation history.
     * Filters the global calendar for reservations belonging to this client 
     * that haven't been cancelled.
     */
    private void sendHistory() {
        List<Reservation> allReservations = calendar.getAllReservations();
        StringBuilder sb = new StringBuilder("HISTORIAL");

        for (Reservation r : allReservations) {
            if (!r.getClientId().equals(clientId)) {
                continue;
            }
            if (r.getStatus() == Reservation.Status.CANCELADO) {
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
     * Processes a availability query request.
     * Verifies if a specific date and time range is free for booking.
     *
     * @param parts the message parts containing [COMMAND, DATE, START_TIME, 
     * END_TIME]
     */
    private void processQuery(String[] p) {
        if (p.length < 4) {
            sendResponse("ERROR|PARAMETROS_INSUFICIENTES");
            return;
        }
        boolean isAvailable = calendar.isAvailable(p[1], p[2], p[3]);
        log.log("CONSULTA", clientId + " consultó " + 
                p[1] + " " + p[2] + "-" + p[3]);
        sendResponse(isAvailable ? "OK|DISPONIBLE" : "ERROR|FRANJA_OCUPADA");
    }

    /**
     * Processes a new reservation request.
     * Performs strict validation on dates, times, capacity, and equipment 
     * availability
     * before creating a temporary reservation entry.
     *
     * @param parts the message parts containing reservation details
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
            Reservation.Equipment equipment = 
                    Reservation.Equipment.valueOf(p[5]);
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
                log.log("ERROR", clientId + 
                        " intentó reservar en fecha pasada: " + date);
                sendResponse("ERROR|FECHA_EN_EL_PASADO");
                return;
            }

            if (!isValidTime(startTime) || !isValidTime(endTime)) {
                sendResponse("ERROR|HORA_INVALIDA");
                return;
            }

            if (reservationDate.isEqual(today)) {
                LocalTime nowTime = LocalTime.now();
                LocalTime startLocalTime = LocalTime.parse(startTime);
                if (startLocalTime.isBefore(nowTime)) {
                    log.log("ERROR", clientId + 
                            " intentó reservar hora pasada hoy: " + startTime);
                    sendResponse("ERROR|HORA_EN_EL_PASADO");
                    return;
                }
            }

            LocalTime tStart = LocalTime.parse(startTime);
            LocalTime tEnd = LocalTime.parse(endTime);
            if (!tEnd.isAfter(tStart)) {
                sendResponse("ERROR|HORA_FIN_INVALIDA");
                return;
            }

            int occupiedCapacity = calendar.getOccupiedCapacityInRange
        (date, startTime, endTime);
            if (occupiedCapacity + attendees > 
                    ServerApp.manager.getMaxCapacity()) {
                log.log("ERROR", clientId 
                        + " sin capacidad en franja: " + date);
                sendResponse("ERROR|SIN_CAPACIDAD");
                return;
            }

            if (!resources.hasEquipment(equipment)) {
                log.log("ERROR", clientId + " equipo no disponible: " 
                        + equipment);
                sendResponse("ERROR|EQUIPO_NO_DISPONIBLE");
                return;
            }

            Reservation reservation = calendar.reserveTemporarily(clientId,
                    date, startTime, endTime,
                    attendees, equipment, priority
            );

            if (reservation == null) {
                log.log("ERROR", clientId + " franja ocupada: " 
                        + date + " " + startTime);
                sendResponse("ERROR|FRANJA_OCUPADA");
                return;
            }

            ttlQueue.add(reservation);
            log.logReservation(reservation);
            sendResponse("OK|TEMPORAL|" + reservation.getReservationId()
                    + "|TTL:" 
                    + reservation.getRemainingSeconds());
            System.out.println("[HILO] Reserva temporal creada: " 
                    + reservation.getReservationId());

        } catch (NumberFormatException e) {
            sendResponse("ERROR|ASISTENTES_INVALIDOS");
        } catch (IllegalArgumentException e) {
            sendResponse("ERROR|EQUIPO_INVALIDO");
        }
    }

    /**
     * Validates if a string follows the "HH:mm" format and represents a 
     * real time.
     * @param time the string to validate
     * @return true if the format is correct and the time is valid
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
     * Confirms a temporary reservation, making it permanent and removing it 
     * from the TTL queue.
     * Validates that the reservation belongs to the requesting client and is 
     * not expired.
     *
     * @param parts the message parts containing [COMMAND, RESERVATION_ID]
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
            log.log("ERROR", clientId + " confirmó reserva expirada: " 
                    + reservationId);
            return;
        }

        boolean isConfirmed = calendar.confirmReservation(reservationId);
        if (isConfirmed) {
            ttlQueue.remove(reservationId);
            log.logConfirmation(reservation);
            sendResponse("OK|CONFIRMADO|" + reservationId);
            System.out.println("[HILO] Reserva confirmada: " + reservationId);
        } else {
            sendResponse("ERROR|NO_SE_PUDO_CONFIRMAR");
        }
    }

    /**
     * Cancels an existing reservation and releases its resources.
     * Verifies ownership and existence before proceeding with the cancellation.
     *
     * @param parts the message parts containing [COMMAND, RESERVATION_ID]
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

        boolean isCancelled = calendar.cancelReservation(reservationId);
        if (isCancelled) {
            ttlQueue.remove(reservationId);
            log.logCancellation(reservation, "Cancelado por cliente");
            sendResponse("OK|CANCELADO|" + reservationId);
            System.out.println("[HILO] Reserva cancelada: " + reservationId);
        } else {
            sendResponse("ERROR|NO_SE_PUDO_CANCELAR");
        }
    }

    /**
     * Retrieves the current status and remaining time-to-live (TTL) of a 
     * specific reservation.
     *
     * @param parts the message parts containing [COMMAND, RESERVATION_ID]
     */
    private void processStatus(String[] p) {
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
        sendResponse("OK|ESTADO|" + reservation.getStatus() + "|TTL:" 
                + reservation.getRemainingSeconds());
    }

    /**
     * Processes the modification of an existing reservation.
     * Cancels the original reservation and attempts to create a new one with 
     * updated details.
     * If the new slot is unavailable, it attempts to restore the original 
     * reservation.
     *
     * @param parts the message parts containing [COMMAND, ID, DATE, START,
     * END, ATTENDEES, EQUIP]
     * @throws InterruptedException if the process is interrupted
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
        } catch (Exception e) {
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
        LocalTime tStart = LocalTime.parse(newStartTime);
        LocalTime tEnd=  LocalTime.parse(newEndTime);
        if (!tEnd.isAfter(tStart)) {
            sendResponse("ERROR|HORA_FIN_INVALIDA");
            return;
        }

        calendar.cancelReservation(reservationId);
        ttlQueue.remove(reservationId);

        Reservation newRes = calendar.reserveTemporarily(
                original.getClientId(),
                newDate, newStartTime, newEndTime,
                newAttendees, newEquipment,
                original.getPriority()
        );

        if (newRes == null) {
            Reservation restored = calendar.reserveTemporarily(
                    original.getClientId(),
                    original.getDate(), original.getStartTime(), 
                    original.getEndTime(),
                    original.getAttendeeCount(), 
                    original.getEquipment(),
                    original.getPriority()
            );
            if (restored != null) {
                calendar.confirmReservation(restored.getReservationId());
            }
            sendResponse("ERROR|FRANJA_OCUPADA");
            return;
        }

        calendar.confirmReservation(newRes.getReservationId());
        ReservationPersistence.save(calendar);
        log.log("EDICION", "Reserva " + reservationId + " editada → "
                + newRes.getReservationId() + " | " + newDate
                + " " + newStartTime + "-" + newEndTime);

        sendResponse("OK|EDITADO|" + newRes.getReservationId());
        System.out.println("[HILO] Reserva editada: " + reservationId + " - " 
                + newRes.getReservationId());
    }

    /**
     * Sends a synchronized response to the client.
     * * @param message the formatted string to send
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

    // --- Getters ---
    public String getClientId() {
        return clientId;
    }

    public String getClientName() {
        return clientName;
    }

    /**
     * Public method to send a message to the client.
     * Useful for broadcasting or external notifications.
     * @param msg the message to send
     */
    public void send(String msg) {
        try {
            synchronized (outputStream) {
                outputStream.writeUTF(msg);
                outputStream.flush();
            }
        } catch (IOException ignored) {}
    }

    /**
     * Safely closes the client socket and terminates the connection.
     */
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {}
    }
}