package Concurrency;

import Core.AuditoriumManager;
import Logging.AuditoriumLog;
import Server.ClientHandler;
import Core.Reservation;
import Server.ServerApp;
import Core.ReservationCalendar;

/**
 * Thread responsible for monitoring reservation time-to-live (TTL). It
 * periodically checks for expired reservations and handles their removal and
 * client notification.
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
     * Main execution loop of the TTL thread. Continuously checks for expired
     * reservations and processes them. Also releases resources for reservations
     * whose time slot has already passed.
     */
    @Override
    public void run() {
        log.log("SISTEMA", "HiloTTL iniciado");
        while (active) {
            try {
                long waitTime = ttlQueue.millisUntilNext();
                ttlQueue.awaitWithTimeout(waitTime);

                java.util.List<Reservation> finished = 
                        calendar.markFinishedReservations();
                for (Reservation r : finished) {
                    log.log("FINALIZADO", "Reserva " + r.getReservationId()
                            + " finalizada. Cliente: " + r.getClientId());
                    notifyClientFinished(r);
                }

                java.util.List<Reservation> expired = calendar.expireOverdue();
                for (Reservation r : expired) {
                    ttlQueue.remove(r.getReservationId());
                    log.logExpiration(r);
                    System.out.println("[TTL] Expirada: "
                            + r.getReservationId());
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
     * Notifies the client that a reservation has finished.
     *
     * @param r the finished reservation
     */
    private void notifyClientFinished(Reservation r) {
        for (ClientHandler handler : ServerApp.connectedClients) {
            try {
                if (handler.getClientId().equals(r.getClientId())) {
                    handler.send("FINALIZADO|" + r.getReservationId());
                } else {
                    handler.send("SLOT_LIBRE|" + r.getDate()
                            + "|" + r.getStartTime()
                            + "|" + r.getEndTime());
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Notifies the client that a reservation has expired.
     *
     * @param r the expired reservation
     */
    private void notifyClientExpiration(Reservation r) {
        for (ClientHandler handler : ServerApp.connectedClients) {
            try {
                if (handler.getClientId().equals(r.getClientId())) {
                    handler.send("EXPIRACION|" + r.getReservationId());
                } else {
                    handler.send("SLOT_LIBRE|" + r.getDate()
                            + "|" + r.getStartTime()
                            + "|" + r.getEndTime());
                }
            } catch (Exception ignored) {
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
