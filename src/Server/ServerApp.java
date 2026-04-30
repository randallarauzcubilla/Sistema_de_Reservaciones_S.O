package Server;

import Concurrency.SynchronizationManager;
import Concurrency.TTLQueue;
import Core.AuditoriumManager;
import Core.ReservationCalendar;
import Logging.AuditoriumLog;
import UI.FrmServer;
import java.util.*;

/**
 * Main application class for the server.
 * Manages shared resources and initializes the synchronization manager.
 */
public class ServerApp {

    /**
     * Base synchronization manager for all locks.
     * Parameters: capacity=200, projectors=3, microphones=5, sound=2.
     */
    public static final SynchronizationManager manager
            = new SynchronizationManager(200, 3, 5, 2);

    /** Shared auditorium calendar. */
    public static final ReservationCalendar calendar = 
            new ReservationCalendar(manager);

    /** Manager for physical resources (projectors, mics, etc). */
    public static final AuditoriumManager resources = 
            new AuditoriumManager(manager);

    /** Queue to handle reservation expiration (Time To Live). */
    public static final TTLQueue ttlQueue = new TTLQueue(manager);

    /** System log for recording events. */
    public static final AuditoriumLog log = new AuditoriumLog(manager);

    /** List of currently connected clients, thread-safe. */
    public static final List<ClientHandler> connectedClients
            = Collections.synchronizedList(new ArrayList<>());

    /**
     * Application entry point. Sets the UI Look and Feel and opens the 
     * server window.
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                javax.swing.UIManager.setLookAndFeel(
                        javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new FrmServer().setVisible(true);
        });
    }
}