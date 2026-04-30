package Logging;

import Concurrency.SynchronizationManager;
import Core.Reservation;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Handles logging operations for the auditorium system.
 * Stores log entries in memory and persists them to a file.
 * Provides thread-safe access using a synchronization manager.
 */
public class AuditoriumLog {

    private final SynchronizationManager manager;
    private final List<String> entries = new ArrayList<>();
    private final String logFile = "bitacora_auditorio.txt";
    private final SimpleDateFormat sdf  =
            new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    /**
     * Creates a new AuditoriumLog instance and initializes the system log.
     *
     * @param manager the synchronization manager used for thread-safe logging
     */
    public AuditoriumLog(SynchronizationManager manager) {
        this.manager = manager;
        log("SISTEMA", "Bitacora inicializada");
    }

    /**
     * Writes a log entry with type and description.
     * Also stores it in memory and persists it to disk.
     *
     * @param type the type/category of the log entry
     * @param description the detailed message of the log entry
     */
    public void log(String type, String description) {
        String entry = String.format("[%s] [%-16s] %s",
                sdf.format(new Date()), type, description);
        manager.getLogMutex().lock();
        try {
            entries.add(entry);
            writeToDisk(entry);
            System.out.println("[BITACORA] " + entry);
        } finally {
            manager.getLogMutex().unlock();
        }
    }

    /**
     * Logs a client connection event.
     *
     * @param client the client identifier
     */
    public void logConnection(String client) {
        log("CONEXION", client + " conectado");
    }

    /**
     * Logs a client disconnection event.
     *
     * @param client the client identifier
     */
    public void logDisconnection(String client) {
        log("DESCONEXION", client + " desconectado");
    }

    /**
     * Logs a temporary reservation event.
     *
     * @param r the reservation object
     */
    public void logReservation(Reservation r) {
        log("RESERVA_TEMPORAL", r.toString());
    }

    /**
     * Logs a reservation confirmation event.
     *
     * @param r the confirmed reservation
     */
    public void logConfirmation(Reservation r) {
        log("CONFIRMACION", "Reserva " + r.getReservationId()
                + " confirmada por " + r.getClientId());
    }

    /**
     * Logs a reservation cancellation event.
     *
     * @param r the canceled reservation
     * @param reason the reason for cancellation
     */
    public void logCancellation(Reservation r, String reason) {
        log("CANCELACION", "Reserva " + r.getReservationId()
                + " cancelada. Motivo: " + reason);
    }

    /**
     * Logs a reservation expiration event due to TTL.
     *
     * @param r the expired reservation
     */
    public void logExpiration(Reservation r) {
        log("EXPIRACION_TTL", "Reserva " + r.getReservationId()
                + " expirada. Cliente: " + r.getClientId());
    }

    /**
     * Logs an error message.
     *
     * @param description the error description
     */
    public void logError(String description) {
        log("ERROR", description);
    }

    /**
     * Returns a thread-safe copy of all log entries.
     *
     * @return list of all log entries
     */
    public List<String> getEntries() {
        manager.getLogMutex().lock();
        try { return new ArrayList<>(entries); }
        finally { manager.getLogMutex().unlock(); }
    }

    /**
     * Returns the last N log entries.
     *
     * @param n number of recent entries to retrieve
     * @return list of the last N log entries
     */
    public List<String> getLast(int n) {
        manager.getLogMutex().lock();
        try {
            int total = entries.size();
            int fromIndex = Math.max(0, total - n);
            return new ArrayList<>(entries.subList(fromIndex, total));
        } finally {
            manager.getLogMutex().unlock();
        }
    }

    /**
     * Writes a log entry to the persistent log file.
     *
     * @param entry the log entry to persist
     */
    private void writeToDisk(String entry) {
        try {
            FileWriter fw = new FileWriter(logFile, true);
            PrintWriter pw = new PrintWriter(fw);
            pw.println(entry);
            pw.close();
        } catch (IOException e) {
            System.out.println("[ERROR] Bitacora disco: " + e.getMessage());
        }
    }
}