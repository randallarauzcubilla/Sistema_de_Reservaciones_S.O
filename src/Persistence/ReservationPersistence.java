package Persistence;

import Core.Reservation;
import Core.ReservationCalendar;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles persistence of confirmed reservations to disk (reservas.dat).
 * Only stores CONFIRMED reservations that are still valid
 * (today or future dates).
 * Provides functionality to save and load reservations from a plain text file.
 */
public class ReservationPersistence {

    private static final String FILE =
     System.getProperty("user.dir") + File.separator + "reservas.dat";

    /**
     * Saves all CONFIRMED reservations from the given calendar to disk.
     * Each reservation is stored in a pipe-separated format:
     * clientId|date|startTime|endTime|attendees|equipment|priority
     *
     * @param calendar the reservation calendar containing all reservations
     */
    public static void save(ReservationCalendar calendar) {
        System.out.println("[PERSISTENCIA] Guardando en: " + FILE);
        List<Reservation> allReservations  = calendar.getAllReservations();
        try (PrintWriter pw = new PrintWriter(new FileWriter(FILE, false))) {
            for (Reservation r : allReservations ) {
                if (r.getStatus() == Reservation.Status.RESERVADO_TEMPORAL)
                    continue;

                pw.println(
                    r.getClientId()       + "|" +
                    r.getDate()           + "|" +
                    r.getStartTime()      + "|" +
                    r.getEndTime()         + "|" +
                    r.getAttendeeCount()  + "|" +
                    r.getEquipment().name()   + "|" +
                    r.getPriority().name()   + "|" +
                    r.getStatus().name()
                );
            }
            System.out.println("[PERSISTENCIA] Reservas guardadas en " 
                    + FILE);
        } catch (IOException e) {
            System.out.println("[PERSISTENCIA] Error al guardar: " 
                    + e.getMessage());
        }
    }

    /**
     * Loads reservations from disk and reconstructs them into a list.
     * Automatically filters out reservations with past dates.
     *
     * @return list of valid reservations restored from file
     */
    public static List<Reservation> load() {
        System.out.println("[PERSISTENCIA] Buscando en: " + FILE);
        System.out.println("[PERSISTENCIA] Existe: " + new File(FILE).exists());
        List<Reservation> list = new ArrayList<>();
        File f = new File(FILE);
        if (!f.exists()) return list;

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            java.time.LocalDate today = java.time.LocalDate.now();

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] p = line.split("\\|");
                if (p.length < 7) continue;

                try {
                    String clientId   = p[0];
                    String date       = p[1];
                    String startTime  = p[2];
                    String endTime     = p[3];
                    int attendees     = Integer.parseInt(p[4]);

                    Reservation.Equipment eq =
                            Reservation.Equipment.valueOf(p[5]);

                    Reservation.Priority pr =
                            Reservation.Priority.valueOf(p[6]);
                    
                    Reservation.Status status = p.length >= 8
                            ? Reservation.Status.valueOf(p[7])
                            : Reservation.Status.CONFIRMADO;

                    java.time.LocalDate reservationDate =
                            java.time.LocalDate.parse(date);

                    if (reservationDate.isBefore(today)  
                        && status == Reservation.Status.CONFIRMADO) continue;

                    Reservation r = new Reservation(
                            clientId,
                            date,
                            startTime,
                            endTime,
                            attendees,
                            eq,
                            pr,
                            true
                    );

                    r.setStatus(status);
                    list.add(r);

                } catch (Exception e) {
                    System.out.println(
                            "[PERSISTENCIA] Línea invalida ignorada: " + line);
                }
            }

            System.out.println("[PERSISTENCIA] " + list.size()
                    + " reservas restauradas.");

        } catch (IOException e) {
            System.out.println("[PERSISTENCIA] Error al cargar: " 
                    + e.getMessage());
        }
        return list;
    }
}