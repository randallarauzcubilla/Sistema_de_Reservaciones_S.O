package Concurrency;

import Core.AuditoriumManager;
import Logging.AuditoriumLog;
import Server.ClientHandler;
import Core.Reservation;
import Server.ServerApp;
import Core.ReservationCalendar;

/**
 * Thread responsible for monitoring reservation time-to-live (TTL).
 * It periodically checks for expired reservations and handles their
 * removal and client notification.
 */
public class ReservationTTLThread extends Thread {

    private final ReservationCalendar calendar;
    private final AuditoriumManager resources;
    private final TTLQueue ttlQueue;
    private final AuditoriumLog log;
    private volatile boolean active = true;

    /**
     * Creates a new TTL monitoring thread.
     *
     * @param calendar the reservation calendar used to manage reservations
     * @param resources the auditorium resource manager
     * @param ttlQueue the queue that manages TTL timing
     * @param log the system log used for auditing events
     */
    public ReservationTTLThread(ReservationCalendar calendar,
            AuditoriumManager resources, TTLQueue ttlQueue,
            AuditoriumLog log) {
        this.calendar = calendar;
        this.resources = resources;
        this.ttlQueue = ttlQueue;
        this.log = log;
        setName("TTLThread");
    }

    /**
     * Main execution loop of the TTL thread.
     * Continuously checks for expired reservations and processes them.
     */
    @Override
    public void run() {
        log.log("SISTEMA", "HiloTTL iniciado");

        while (active) {
            try {
                long waitTime = ttlQueue.millisUntilNext();
                ttlQueue.awaitWithTimeout(waitTime);

                java.util.List<Reservation> expiredReservations =
                        calendar.expireOverdue();

                for (Reservation r : expiredReservations) {
                    ttlQueue.remove(r.getReservationId());
                    log.logExpiration(r);
                    System.out.println("[TTL] Expirada: " + 
                            r.getReservationId());
                    notifyClientExpiration(r);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                active = false;
            }
        }

        log.log("SISTEMA", "HiloTTL detenido");
    }

    /**
     * Notifies the client that a reservation has expired.
     *
     * @param r the expired reservation
     */
    private void notifyClientExpiration(Reservation r) {
        for (ClientHandler hilo : ServerApp.connectedClients) {
            if (hilo.getClientId().equals(r.getClientId())) {
                try {
                    hilo.send("EXPIRACION|" + r.getReservationId());
                } catch (Exception ignored) {
                    System.out.println("[TTL-DEBUG] Error al enviar: "
                            + ignored.getMessage());
                }
                break;
            }
        }
    }

    /**
     * Stops the TTL thread safely.
     */
    public void stopThread() {
        active = false;
        interrupt();
    }
}