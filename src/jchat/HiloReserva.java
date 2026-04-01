package jchat;

import java.io.*;
import java.net.*;

public class HiloReserva extends Thread {

    private final Socket socket;
    private final String idCliente;

    // Referencias a los recursos compartidos (inyectados desde Servidor)
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

                // Protocolo: COMANDO|param1|param2|...
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

    private void procesarConsulta(String[] p) {
        // p[1]=fecha, p[2]=horaInicio, p[3]=horaFin
        // Solo lectura — R1 con readLock
        boolean disponible = calendario.estaDisponible(p[1], p[2], p[3]);
        responder(disponible ? "OK|DISPONIBLE" : "ERROR|FRANJA_OCUPADA");
    }

    private void procesarReserva(String[] p) {
        // p[1]=fecha, p[2]=horaInicio, p[3]=horaFin, p[4]=asistentes, p[5]=equipo
        // ORDEN JERÁRQUICO: R1 → R2 → R3 → R4 → R5
        // (implementación completa en siguiente etapa)
        responder("OK|RESERVA_PENDIENTE");
    }

    private void procesarConfirmacion(String[] p) {
        // p[1]=idReserva
        responder("OK|CONFIRMADO");
    }

    private void procesarCancelacion(String[] p) {
        // p[1]=idReserva
        responder("OK|CANCELADO");
    }

    private void procesarEstado(String[] p) {
        // p[1]=idReserva
        responder("OK|ESTADO_PENDIENTE");
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