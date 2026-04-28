package Server;

import Concurrency.SynchronizationManager;
import Concurrency.TTLQueue;
import Core.AuditoriumManager;
import Core.ReservationCalendar;
import Logging.AuditoriumLog;
import UI.FrmServer;
import java.util.*;

public class ServerApp {

    // SynchronizationManager — base de todos los locks
    // Parámetros: capacidad=200, proyectores=3, micrófonos=5, sonido=2
    public static final SynchronizationManager gestor
            = new SynchronizationManager(200, 3, 5, 2);

    // Recursos compartidos R1 → R5
    public static final ReservationCalendar calendario = 
            new ReservationCalendar(gestor);
    public static final AuditoriumManager recursos = 
            new AuditoriumManager(gestor);
    public static final TTLQueue colaTTL = new TTLQueue(gestor);
    public static final AuditoriumLog bitacora = new AuditoriumLog(gestor);

    // Clientes conectados
    public static final List<ClientHandler> clientesConectados
            = Collections.synchronizedList(new ArrayList<>());

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
