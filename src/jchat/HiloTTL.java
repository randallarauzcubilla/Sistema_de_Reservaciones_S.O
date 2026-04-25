package jchat;

public class HiloTTL extends Thread {
    private final Calendario       calendario;
    private final RecursoAuditorio recursos;
    private final ColaTTL          colaTTL;
    private final Bitacora         bitacora;
    private volatile boolean       activo = true;

    public HiloTTL(Calendario calendario, RecursoAuditorio recursos,
                   ColaTTL colaTTL, Bitacora bitacora) {
        this.calendario = calendario;
        this.recursos   = recursos;
        this.colaTTL    = colaTTL;
        this.bitacora   = bitacora;
        setName("HiloTTL");
    }

    @Override
    public void run() {
        bitacora.log("SISTEMA", "HiloTTL iniciado");
        while (activo) {
            try {
                long espera = colaTTL.msHastaProxima();
                colaTTL.esperarConTimeout(espera);
                java.util.List<Reserva> expiradas = calendario.expirarVencidas();

                for (Reserva r : expiradas) {
                    colaTTL.remover(r.getIdReserva());
                    bitacora.logExpiracion(r);
                    System.out.println("[TTL] Expirada: " + r.getIdReserva());

                    // Notificar al cliente dueño de la reserva
                    notificarClienteExpiracion(r);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                activo = false;
            }
        }
        bitacora.log("SISTEMA", "HiloTTL detenido");
    }

    // Busca el hilo del cliente y le avisa que su reserva expiró 
    private void notificarClienteExpiracion(Reserva r) {
        for (HiloReserva hilo : Servidor.clientesConectados) {
            if (hilo.getIdCliente().equals(r.getIdCliente())) {
                try {
                    synchronized (hilo.flujoEscritura) {
                        hilo.flujoEscritura.writeUTF("EXPIRACION|" + r.getIdReserva());
                        hilo.flujoEscritura.flush();
                    }
                } catch (java.io.IOException ignored) {
                    System.out.println("[TTL-DEBUG] Error al enviar: " + ignored.getMessage());
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