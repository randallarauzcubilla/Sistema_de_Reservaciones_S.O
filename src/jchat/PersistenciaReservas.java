package jchat;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Guarda y carga reservas confirmadas en disco (reservas.dat).
 * Solo persiste CONFIRMADAS y vigentes (fecha futura o de hoy).
 */
public class PersistenciaReservas {

    private static final String ARCHIVO = "reservas.dat";

    /**
     * Guarda en disco todas las reservas CONFIRMADAS del calendario.
     * Formato por línea: idCliente|fecha|horaInicio|horaFin|asistentes|equipo|prioridad
     */
    public static void guardar(Calendario calendario) {
        List<Reserva> todas = calendario.getTodasLasReservas();
        try (PrintWriter pw = new PrintWriter(new FileWriter(ARCHIVO, false))) {
            for (Reserva r : todas) {
                // Solo guardamos las confirmadas; las temporales se descartan al apagar
                if (r.getEstado() != Reserva.Estado.CONFIRMADO) continue;
                pw.println(
                    r.getIdCliente()       + "|" +
                    r.getFecha()           + "|" +
                    r.getHoraInicio()      + "|" +
                    r.getHoraFin()         + "|" +
                    r.getCantAsistentes()  + "|" +
                    r.getEquipo().name()   + "|" +
                    r.getPrioridad().name()
                );
            }
            System.out.println("[PERSISTENCIA] Reservas guardadas en " + ARCHIVO);
        } catch (IOException e) {
            System.out.println("[PERSISTENCIA] Error al guardar: " + e.getMessage());
        }
    }

    /**
     * Lee el archivo y devuelve la lista de reservas a restaurar.
     * Descarta automáticamente las que ya tienen fecha pasada.
     */
    public static List<Reserva> cargar() {
        List<Reserva> lista = new ArrayList<>();
        File f = new File(ARCHIVO);
        if (!f.exists()) return lista;

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String linea;
            java.time.LocalDate hoy = java.time.LocalDate.now();

            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty()) continue;

                String[] p = linea.split("\\|");
                if (p.length < 7) continue;

                try {
                    String idCliente   = p[0];
                    String fecha       = p[1];
                    String horaInicio  = p[2];
                    String horaFin     = p[3];
                    int asistentes     = Integer.parseInt(p[4]);
                    Reserva.Equipo eq  = Reserva.Equipo.valueOf(p[5]);
                    Reserva.Prioridad pr = Reserva.Prioridad.valueOf(p[6]);

                    // Descartar reservas de fechas que ya pasaron
                    java.time.LocalDate fechaReserva = java.time.LocalDate.parse(fecha);
                    if (fechaReserva.isBefore(hoy)) continue;

                    Reserva r = new Reserva(idCliente, fecha, horaInicio,
                                            horaFin, asistentes, eq, pr);
                    r.setEstado(Reserva.Estado.CONFIRMADO); // restaurar como confirmada
                    lista.add(r);

                } catch (Exception e) {
                    System.out.println("[PERSISTENCIA] Línea inválida ignorada: " + linea);
                }
            }
            System.out.println("[PERSISTENCIA] " + lista.size() + " reservas restauradas.");
        } catch (IOException e) {
            System.out.println("[PERSISTENCIA] Error al cargar: " + e.getMessage());
        }
        return lista;
    }
}