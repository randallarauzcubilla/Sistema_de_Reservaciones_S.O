package Concurrency;

import Core.AuditoriumManager;
import Logging.AuditoriumLog;
import Server.ClientHandler;
import Core.Reservation;
import Server.ServerApp;
import Core.ReservationCalendar;

public class ReservationTTLThread extends Thread {

    private final ReservationCalendar calendario;
    private final AuditoriumManager recursos;
    private final TTLQueue colaTTL;
    private final AuditoriumLog bitacora;
    private volatile boolean activo = true;

    public ReservationTTLThread(ReservationCalendar calendario, 
            AuditoriumManager recursos, TTLQueue colaTTL, 
            AuditoriumLog bitacora) {
        this.calendario = calendario;
        this.recursos = recursos;
        this.colaTTL = colaTTL;
        this.bitacora = bitacora;
        setName("HiloTTL");
    }

    @Override
    public void run() {
        bitacora.log("SISTEMA", "HiloTTL iniciado");
        while (activo) {
            try {
                long espera = colaTTL.msHastaProxima();
                colaTTL.esperarConTimeout(espera);
                java.util.List<Reservation> expiradas = 
                        calendario.expirarVencidas();

                for (Reservation r : expiradas) {
                    colaTTL.remover(r.getIdReserva());
                    bitacora.logExpiracion(r);
                    System.out.println("[TTL] Expirada: " + r.getIdReserva());
                    notificarClienteExpiracion(r);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                activo = false;
            }
        }
        bitacora.log("SISTEMA", "HiloTTL detenido");
    }
    
    private void notificarClienteExpiracion(Reservation r) {
    for (ClientHandler hilo : ServerApp.clientesConectados) {
        if (hilo.getIdCliente().equals(r.getIdCliente())) {
            try {
                hilo.enviar("EXPIRACION|" + r.getIdReserva());
            } catch (Exception ignored) {
                System.out.println("[TTL-DEBUG] Error al enviar: " 
                        + ignored.getMessage());
            }
            break;
        }
    }
}

    public void detener() {
        activo = false;
        interrupt();
    }
}
