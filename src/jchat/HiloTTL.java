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
                // Dormir hasta la próxima expiración
                long espera = colaTTL.msHastaProxima();
                colaTTL.esperarConTimeout(espera);

                // Expirar vencidas en el calendario
                java.util.List<Reserva> expiradas = calendario.expirarVencidas();

                // Limpiar de la cola y registrar en bitácora
                for (Reserva r : expiradas) {
                    colaTTL.remover(r.getIdReserva());
                    bitacora.logExpiracion(r);
                    System.out.println("[TTL] Expirada: " + r.getIdReserva()
                            + " | " + r.getIdCliente());
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                activo = false;
            }
        }
        bitacora.log("SISTEMA", "HiloTTL detenido");
    }

    public void detener() {
        activo = false;
        interrupt();
    }
}