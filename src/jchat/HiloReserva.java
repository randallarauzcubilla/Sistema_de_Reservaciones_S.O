package jchat;

import java.io.*;
import java.net.*;

public class HiloReserva extends Thread {

    private final Socket socket;
    private final String idCliente;
    private final Calendario calendario;
    private final RecursoAuditorio recursos;
    private final ColaTTL colaTTL;
    private final Bitacora bitacora;

    DataInputStream flujoLectura;
    DataOutputStream flujoEscritura;

    public HiloReserva(Socket socket, String idCliente,
                       Calendario calendario, RecursoAuditorio recursos,
                       ColaTTL colaTTL, Bitacora bitacora) {
        this.socket = socket;
        this.idCliente = idCliente;
        this.calendario = calendario;
        this.recursos = recursos;
        this.colaTTL = colaTTL;
        this.bitacora = bitacora;
        try {
            flujoLectura  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            flujoEscritura = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        } catch (IOException e) {
            System.out.println("[ERROR] HiloReserva constructor: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        bitacora.log("CONEXION", idCliente + " conectado");
        responder("OK|CONECTADO");

        while (true) {
            try {
                String mensaje = flujoLectura.readUTF().trim();
                if (mensaje.isEmpty()) continue;

                String[] partes = mensaje.split("\\|");
                String comando = partes[0];

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
                bitacora.log("DESCONEXION", idCliente + " desconectado");
                System.out.println("[HILO] " + idCliente + " desconectado.");
                break;
            }
        }
    }

    // SC-01: Consultar disponibilidad — READ lock en Calendario
    private void procesarConsulta(String[] p) {
        if (p.length < 4) { responder("ERROR|PARAMETROS_INSUFICIENTES"); return; }
        // p[1]=fecha, p[2]=horaInicio, p[3]=horaFin
        boolean disponible = calendario.estaDisponible(p[1], p[2], p[3]);
        bitacora.log("CONSULTA", idCliente + " consultó " + p[1] + " " + p[2] + "-" + p[3]);
        responder(disponible ? "OK|DISPONIBLE" : "ERROR|FRANJA_OCUPADA");
    }

    // SC-02: Reservar temporal — WRITE lock en Calendario + semáforos
    // Protocolo actualizado (FIX 1):
    // RESERVAR|fecha|horaInicio|horaFin|asistentes|equipo|prioridad
    //   p[1]    p[2]    p[3]      p[4]    p[5]      p[6]
    private void procesarReserva(String[] p) {
        if (p.length < 6) { responder("ERROR|PARAMETROS_INSUFICIENTES"); return; }

        try {
            String fecha      = p[1];
            String horaInicio = p[2];
            String horaFin    = p[3];                        // FIX 1 — viene del cliente
            int asistentes    = Integer.parseInt(p[4]);      // FIX 1 — índice subió
            Reserva.Equipo equipo = Reserva.Equipo.valueOf(p[5]); // FIX 1 — índice subió
            Reserva.Prioridad prioridad = Reserva.Prioridad.ESTUDIANTE;

            if (p.length >= 7) {                             // FIX 1 — índice subió
                try {
                    prioridad = Reserva.Prioridad.valueOf(p[6]);
                } catch (IllegalArgumentException ignored) {}
            }

            // Verificar capacidad antes de intentar reservar
            if (!recursos.hayCapacidad(asistentes)) {
                bitacora.log("ERROR", idCliente + " sin capacidad para " + asistentes + " asistentes");
                responder("ERROR|SIN_CAPACIDAD");
                return;
            }

            // Verificar equipamiento disponible
            if (!recursos.hayEquipo(equipo)) {
                bitacora.log("ERROR", idCliente + " equipo no disponible: " + equipo);
                responder("ERROR|EQUIPO_NO_DISPONIBLE");
                return;
            }

            // SC-02: Reservar en calendario (WRITE lock interno)
            Reserva reserva = calendario.reservarTemporal(
                idCliente, fecha, horaInicio, horaFin,
                asistentes, equipo, prioridad
            );

            if (reserva == null) {
                bitacora.log("ERROR", idCliente + " franja no disponible: " + fecha + " " + horaInicio);
                responder("ERROR|FRANJA_OCUPADA");
                return;
            }

            // R4: Agregar a cola TTL
            colaTTL.agregar(reserva);

            // R5: Registrar en bitácora
            bitacora.logReserva(reserva);

            responder("OK|TEMPORAL|" + reserva.getIdReserva() + "|TTL:" + reserva.segundosRestantes());
            System.out.println("[HILO] Reserva temporal creada: " + reserva.getIdReserva());

        } catch (NumberFormatException e) {
            responder("ERROR|ASISTENTES_INVALIDOS");
        } catch (IllegalArgumentException e) {
            responder("ERROR|EQUIPO_INVALIDO");
        }
    }

    // SC-03: Confirmar reserva — WRITE lock en Calendario
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

    // SC-04: Cancelar reserva — WRITE lock en Calendario + liberar semáforos
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

    // SC-09: Consultar estado de una reserva — READ lock
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

    public String getIdCliente() { return idCliente; }
}