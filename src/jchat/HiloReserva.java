package jchat;

import java.io.*;
import java.net.*;
import java.util.List;

public class HiloReserva extends Thread {

    final Socket socket;                // package-private para que FrmServidor pueda cerrarlo
    private final String idCliente;
    private final String nombreCliente;
    private final Calendario calendario;
    private final RecursoAuditorio recursos;
    private final ColaTTL colaTTL;
    private final Bitacora bitacora;

    DataInputStream flujoLectura;
    DataOutputStream flujoEscritura;

    public HiloReserva(Socket socket, String datosCliente,
                       Calendario calendario, RecursoAuditorio recursos,
                       ColaTTL colaTTL, Bitacora bitacora) {
        this.socket     = socket;
        this.calendario = calendario;
        this.recursos   = recursos;
        this.colaTTL    = colaTTL;
        this.bitacora   = bitacora;

        String[] partes = datosCliente.split("\\|", 2);
        this.nombreCliente = partes[0].trim();
        this.idCliente     = partes.length >= 2 ? partes[1].trim() : partes[0].trim();

        try {
            flujoLectura   = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            flujoEscritura = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        } catch (IOException e) {
            System.out.println("[ERROR] HiloReserva constructor: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        bitacora.log("CONEXION", nombreCliente + " (DNI: " + idCliente + ") conectado");

        responder("OK|CONECTADO");
        enviarHistorial();

        while (true) {
            try {
                String mensaje = flujoLectura.readUTF().trim();
                if (mensaje.isEmpty()) continue;

                String[] partes = mensaje.split("\\|");
                String comando  = partes[0];

                switch (comando) {
                    case "CONSULTAR":
                        procesarConsulta(partes);
                        break;
                    case "RESERVAR":
                        procesarReserva(partes);
                        break;
                    case "CONFIRMAR":
                        procesarConfirmacion(partes);
                        break;
                    case "CANCELAR":
                        procesarCancelacion(partes);
                        break;
                    case "ESTADO":
                        procesarEstado(partes);
                        break;
                    default:
                        responder("ERROR|COMANDO_DESCONOCIDO");
                        break;
                }

            } catch (IOException e) {
                Servidor.clientesConectados.remove(this);
                bitacora.log("DESCONEXION", nombreCliente + " (DNI: " + idCliente + ") desconectado");
                System.out.println("[HILO] " + nombreCliente + " desconectado.");
                break;
            }
        }
    }

    private void enviarHistorial() {
        List<Reserva> todas = calendario.getTodasLasReservas();
        StringBuilder sb = new StringBuilder("HISTORIAL");

        for (Reserva r : todas) {
            if (!r.getIdCliente().equals(idCliente)) continue;
            if (r.getEstado() == Reserva.Estado.CANCELADO) continue;

            sb.append("|")
              .append(r.getIdReserva()).append(",")
              .append(r.getFecha()).append(",")
              .append(r.getHoraInicio()).append(",")
              .append(r.getHoraFin()).append(",")
              .append(r.getEstado().toString());
        }

        responder(sb.toString());
    }

    private void procesarConsulta(String[] p) {
        if (p.length < 4) { responder("ERROR|PARAMETROS_INSUFICIENTES"); return; }
        boolean disponible = calendario.estaDisponible(p[1], p[2], p[3]);
        bitacora.log("CONSULTA", idCliente + " consultó " + p[1] + " " + p[2] + "-" + p[3]);
        responder(disponible ? "OK|DISPONIBLE" : "ERROR|FRANJA_OCUPADA");
    }

    private void procesarReserva(String[] p) {
        if (p.length < 6) { responder("ERROR|PARAMETROS_INSUFICIENTES"); return; }

        try {
            String fecha      = p[1];
            String horaInicio = p[2];
            String horaFin    = p[3];
            int asistentes    = Integer.parseInt(p[4]);
            Reserva.Equipo equipo = Reserva.Equipo.valueOf(p[5]);
            Reserva.Prioridad prioridad = Reserva.Prioridad.ESTUDIANTE;

            if (p.length >= 7) {
                try {
                    prioridad = Reserva.Prioridad.valueOf(p[6]);
                } catch (IllegalArgumentException ignored) {}
            }

            if (!recursos.hayCapacidad(asistentes)) {
                bitacora.log("ERROR", idCliente + " sin capacidad para " + asistentes + " asistentes");
                responder("ERROR|SIN_CAPACIDAD");
                return;
            }

            if (!recursos.hayEquipo(equipo)) {
                bitacora.log("ERROR", idCliente + " equipo no disponible: " + equipo);
                responder("ERROR|EQUIPO_NO_DISPONIBLE");
                return;
            }

            Reserva reserva = calendario.reservarTemporal(
                idCliente, fecha, horaInicio, horaFin,
                asistentes, equipo, prioridad
            );

            if (reserva == null) {
                bitacora.log("ERROR", idCliente + " franja no disponible: " + fecha + " " + horaInicio);
                responder("ERROR|FRANJA_OCUPADA");
                return;
            }

            colaTTL.agregar(reserva);
            bitacora.logReserva(reserva);
            responder("OK|TEMPORAL|" + reserva.getIdReserva() + "|TTL:" + reserva.segundosRestantes());
            System.out.println("[HILO] Reserva temporal creada: " + reserva.getIdReserva());

        } catch (NumberFormatException e) {
            responder("ERROR|ASISTENTES_INVALIDOS");
        } catch (IllegalArgumentException e) {
            responder("ERROR|EQUIPO_INVALIDO");
        }
    }

    private void procesarConfirmacion(String[] p) {
        if (p.length < 2) { responder("ERROR|PARAMETROS_INSUFICIENTES"); return; }
        String idReserva = p[1];

        Reserva reserva = calendario.getReservaPorId(idReserva);
        if (reserva == null) {
            responder("ERROR|RESERVA_NO_ENCONTRADA");
            return;
        }
        if (!reserva.getIdCliente().equals(idCliente)) {
            responder("ERROR|NO_AUTORIZADO");
            return;
        }
        if (reserva.estaVencida()) {
            responder("ERROR|RESERVA_EXPIRADA");
            bitacora.log("ERROR", idCliente + " intentó confirmar reserva expirada: " + idReserva);
            return;
        }

        boolean confirmado = calendario.confirmarReserva(idReserva);
        if (confirmado) {
            colaTTL.remover(idReserva);
            bitacora.logConfirmacion(reserva);
            responder("OK|CONFIRMADO|" + idReserva);
            System.out.println("[HILO] Reserva confirmada: " + idReserva);
        } else {
            responder("ERROR|NO_SE_PUDO_CONFIRMAR");
        }
    }

    private void procesarCancelacion(String[] p) {
        if (p.length < 2) { responder("ERROR|PARAMETROS_INSUFICIENTES"); return; }
        String idReserva = p[1];

        Reserva reserva = calendario.getReservaPorId(idReserva);
        if (reserva == null) {
            responder("ERROR|RESERVA_NO_ENCONTRADA");
            return;
        }
        if (!reserva.getIdCliente().equals(idCliente)) {
            responder("ERROR|NO_AUTORIZADO");
            return;
        }

        boolean cancelado = calendario.cancelarReserva(idReserva);
        if (cancelado) {
            colaTTL.remover(idReserva);
            bitacora.logCancelacion(reserva, "Cancelado por cliente");
            responder("OK|CANCELADO|" + idReserva);
            System.out.println("[HILO] Reserva cancelada: " + idReserva);
        } else {
            responder("ERROR|NO_SE_PUDO_CANCELAR");
        }
    }

    private void procesarEstado(String[] p) {
        if (p.length < 2) { responder("ERROR|PARAMETROS_INSUFICIENTES"); return; }
        String idReserva = p[1];

        Reserva reserva = calendario.getReservaPorId(idReserva);
        if (reserva == null) {
            responder("ERROR|RESERVA_NO_ENCONTRADA");
            return;
        }
        responder("OK|ESTADO|" + reserva.getEstado() + "|TTL:" + reserva.segundosRestantes());
    }

    private void responder(String mensaje) {
        try {
            synchronized (flujoEscritura) {
                flujoEscritura.writeUTF(mensaje);
                flujoEscritura.flush();
            }
        } catch (IOException e) {
            System.out.println("[ERROR] Responder a " + idCliente + ": " + e.getMessage());
        }
    }

    public String getIdCliente()    { return idCliente; }
    public String getNombreCliente(){ return nombreCliente; }
}