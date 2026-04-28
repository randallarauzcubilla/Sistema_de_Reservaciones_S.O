package Persistence;

import Core.Reservation;
import Core.ReservationCalendar;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Guarda y carga reservas confirmadas en disco (reservas.dat).
 * Solo persiste CONFIRMADAS y vigentes (fecha futura o de hoy).
 */
public class ReservationPersistence {

    private static final String ARCHIVO = "reservas.dat";

    /**
     * Guarda en disco todas las reservas CONFIRMADAS del calendario.
     * Formato por línea: 
     * idCliente|fecha|horaInicio|horaFin|asistentes|equipo|prioridad
     */
    public static void guardar(ReservationCalendar calendario) {
        List<Reservation> todas = calendario.getTodasLasReservas();
        try (PrintWriter pw = new PrintWriter(new FileWriter(ARCHIVO, false))) {
            for (Reservation r : todas) {
                // Solo guardamos las confirmadas; 
                //las temporales se descartan al apagar
                if (r.getEstado() != Reservation.Estado.CONFIRMADO) continue;
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
            System.out.println("[PERSISTENCIA] Reservas guardadas en " 
                    + ARCHIVO);
        } catch (IOException e) {
            System.out.println("[PERSISTENCIA] Error al guardar: " 
                    + e.getMessage());
        }
    }

    /**
     * Lee el archivo y devuelve la lista de reservas a restaurar.
     * Descarta automáticamente las que ya tienen fecha pasada.
     */
    public static List<Reservation> cargar() {
        List<Reservation> lista = new ArrayList<>();
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
                    Reservation.Equipo eq  = Reservation.Equipo.valueOf(p[5]);
                    Reservation.Prioridad pr = 
                            Reservation.Prioridad.valueOf(p[6]);

                    // Descartar reservas de fechas que ya pasaron
                    java.time.LocalDate fechaReserva = 
                            java.time.LocalDate.parse(fecha);
                    if (fechaReserva.isBefore(hoy)) continue;

                    Reservation r = new Reservation(idCliente, fecha,horaInicio,
                                            horaFin, asistentes, eq, pr);
                    r.setEstado(Reservation.Estado.CONFIRMADO); 
                    lista.add(r);

                } catch (Exception e) {
                    System.out.println("[PERSISTENCIA] Línea invalida"
                            + " ignorada: " + linea);
                }
            }
            System.out.println("[PERSISTENCIA] " + lista.size()
                    + " reservas restauradas.");
        } catch (IOException e) {
            System.out.println("[PERSISTENCIA] Error al cargar: " 
                    + e.getMessage());
        }
        return lista;
    }
}