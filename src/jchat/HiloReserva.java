package jchat;

import java.io.*;
import java.net.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class HiloReserva extends Thread {

    final Socket socket;
    private final String idCliente;
    private final String nombreCliente;
    private final String rolCliente;
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

        String[] partes = datosCliente.split("\\|", 3);
        this.nombreCliente = partes[0].trim();
        this.idCliente     = partes.length >= 2 ? partes[1].trim() : partes[0].trim();
        this.rolCliente    = partes.length >= 3 ? partes[2].trim() : "ESTUDIANTE";

        try {
            flujoLectura   = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            flujoEscritura = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        } catch (IOException e) {
            System.out.println("[ERROR] HiloReserva constructor: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        // Verificar si el rol es válido para esta cédula
        if (!VerificadorRoles.puedeUsarRol(idCliente, rolCliente)) {
            responder("ERROR|ROL_NO_AUTORIZADO");
            bitacora.log("SEGURIDAD", nombreCliente + " intentó acceder como "
                + rolCliente + " sin autorización");
            try { socket.close(); } catch (IOException ignored) {}
            return;
        }

        bitacora.log("CONEXION", nombreCliente + " conectado como " + rolCliente);
        responder("OK|CONECTADO");
        enviarHistorial();

        while (true) {
            try {
                String mensaje = flujoLectura.readUTF().trim();
                if (mensaje.isEmpty()) continue;

                String[] partes = mensaje.split("\\|");
                String comando  = partes[0];

                switch (comando) {
                    case "CONSULTAR":      procesarConsulta(partes);      break;
                    case "RESERVAR":       procesarReserva(partes);       break;
                    case "CONFIRMAR":      procesarConfirmacion(partes);  break;
                    case "CANCELAR":       procesarCancelacion(partes);   break;
                    case "ESTADO":         procesarEstado(partes);        break;
                    case "EDITAR_RESERVA": procesarEdicion(partes);       break; // NUEVO
                    default:               responder("ERROR|COMANDO_DESCONOCIDO"); break;
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
                try { prioridad = Reserva.Prioridad.valueOf(p[6]); }
                catch (IllegalArgumentException ignored) {}
            }

            LocalDate fechaReserva;
            try {
                fechaReserva = LocalDate.parse(fecha);
            } catch (Exception e) {
                responder("ERROR|FECHA_INVALIDA");
                return;
            }

            LocalDate hoy = LocalDate.now();
            if (fechaReserva.isBefore(hoy)) {
                bitacora.log("ERROR", idCliente + " intentó reservar en fecha pasada: " + fecha);
                responder("ERROR|FECHA_EN_EL_PASADO");
                return;
            }

            if (!esHoraValida(horaInicio) || !esHoraValida(horaFin)) {
                responder("ERROR|HORA_INVALIDA");
                return;
            }

            if (fechaReserva.isEqual(hoy)) {
                LocalTime ahora      = LocalTime.now();
                LocalTime inicioTime = LocalTime.parse(horaInicio);
                if (inicioTime.isBefore(ahora)) {
                    bitacora.log("ERROR", idCliente + " intentó reservar hora pasada hoy: " + horaInicio);
                    responder("ERROR|HORA_EN_EL_PASADO");
                    return;
                }
            }

            LocalTime tInicio = LocalTime.parse(horaInicio);
            LocalTime tFin    = LocalTime.parse(horaFin);
            if (!tFin.isAfter(tInicio)) {
                responder("ERROR|HORA_FIN_INVALIDA");
                return;
            }

            int ocupada = calendario.capacidadOcupadaEnRango(fecha, horaInicio, horaFin);
            if (ocupada + asistentes > Servidor.gestor.getCapacidadMaxima()) {
                bitacora.log("ERROR", idCliente + " sin capacidad en franja: " + fecha);
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
                bitacora.log("ERROR", idCliente + " franja ocupada: " + fecha + " " + horaInicio);
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

    private boolean esHoraValida(String hora) {
        if (hora == null || !hora.matches("\\d{2}:\\d{2}")) return false;
        try {
            LocalTime.parse(hora);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void procesarConfirmacion(String[] p) {
        if (p.length < 2) { responder("ERROR|PARAMETROS_INSUFICIENTES"); return; }
        String idReserva = p[1];

        Reserva reserva = calendario.getReservaPorId(idReserva);
        if (reserva == null) { responder("ERROR|RESERVA_NO_ENCONTRADA"); return; }
        if (!reserva.getIdCliente().equals(idCliente)) { responder("ERROR|NO_AUTORIZADO"); return; }
        if (reserva.estaVencida()) {
            responder("ERROR|RESERVA_EXPIRADA");
            bitacora.log("ERROR", idCliente + " confirmó reserva expirada: " + idReserva);
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
        if (reserva == null) { responder("ERROR|RESERVA_NO_ENCONTRADA"); return; }
        if (!reserva.getIdCliente().equals(idCliente)) { responder("ERROR|NO_AUTORIZADO"); return; }

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
        if (reserva == null) { responder("ERROR|RESERVA_NO_ENCONTRADA"); return; }
        responder("OK|ESTADO|" + reserva.getEstado() + "|TTL:" + reserva.segundosRestantes());
    }

    private void procesarEdicion(String[] p) {
        if (p.length < 7) { responder("ERROR|PARAMETROS_INSUFICIENTES"); return; }

        String idReserva       = p[1];
        String nuevaFecha      = p[2];
        String nuevaHoraInicio = p[3];
        String nuevaHoraFin    = p[4];
        int nuevosAsistentes;
        Reserva.Equipo nuevoEquipo;

        try {
            nuevosAsistentes = Integer.parseInt(p[5]);
            nuevoEquipo      = Reserva.Equipo.valueOf(p[6]);
        } catch (Exception e) {
            responder("ERROR|PARAMETROS_INVALIDOS");
            return;
        }

        Reserva original = calendario.getReservaPorId(idReserva);
        if (original == null) {
            responder("ERROR|RESERVA_NO_ENCONTRADA");
            return;
        }

        // Validar fecha
        LocalDate fechaParsed;
        try {
            fechaParsed = LocalDate.parse(nuevaFecha);
        } catch (Exception e) {
            responder("ERROR|FECHA_INVALIDA");
            return;
        }
        if (fechaParsed.isBefore(LocalDate.now())) {
            responder("ERROR|FECHA_EN_EL_PASADO");
            return;
        }

        // Validar horas
        if (!esHoraValida(nuevaHoraInicio) || !esHoraValida(nuevaHoraFin)) {
            responder("ERROR|HORA_INVALIDA");
            return;
        }
        LocalTime tInicio = LocalTime.parse(nuevaHoraInicio);
        LocalTime tFin    = LocalTime.parse(nuevaHoraFin);
        if (!tFin.isAfter(tInicio)) {
            responder("ERROR|HORA_FIN_INVALIDA");
            return;
        }

        // Cancelar la vieja y crear una nueva
        calendario.cancelarReserva(idReserva);
        colaTTL.remover(idReserva);

        Reserva nueva = calendario.reservarTemporal(
            original.getIdCliente(),
            nuevaFecha, nuevaHoraInicio, nuevaHoraFin,
            nuevosAsistentes, nuevoEquipo,
            original.getPrioridad()
        );

        if (nueva == null) {
            // Franja ocupada — restaurar la original
            Reserva rest = calendario.reservarTemporal(
                original.getIdCliente(),
                original.getFecha(), original.getHoraInicio(), original.getHoraFin(),
                original.getCantAsistentes(), original.getEquipo(), original.getPrioridad()
            );
            if (rest != null) calendario.confirmarReserva(rest.getIdReserva());
            responder("ERROR|FRANJA_OCUPADA");
            return;
        }

        calendario.confirmarReserva(nueva.getIdReserva());
        PersistenciaReservas.guardar(calendario);
        bitacora.log("EDICION", "Reserva " + idReserva + " editada → "
            + nueva.getIdReserva() + " | " + nuevaFecha
            + " " + nuevaHoraInicio + "-" + nuevaHoraFin);

        responder("OK|EDITADO|" + nueva.getIdReserva());
        System.out.println("[HILO] Reserva editada: " + idReserva + " → " + nueva.getIdReserva());
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

    public String getIdCliente()     { return idCliente; }
    public String getNombreCliente() { return nombreCliente; }
}