package Persistence;

import Core.Reservation;
import Core.ReservationCalendar;
import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReservationPersistence {

    private static final String FILE =
            System.getProperty("user.dir") + File.separator + "reservas.dat";

    /**
     * Guarda CONFIRMADO y FINALIZADO al disco.
     * Formato por línea:
     * clientId|date|start|end|attendees|priority|status|EQUIPO:qty,EQUIPO:qty
     */
    public static void save(ReservationCalendar calendar) {
        System.out.println("[PERSISTENCIA] Guardando en: " + FILE);
        List<Reservation> all = calendar.getAllReservations();
        try (PrintWriter pw = new PrintWriter(new FileWriter(FILE, false))) {
            for (Reservation r : all) {
                if (r.getStatus() == Reservation.Status.RESERVADO_TEMPORAL
                        || r.getStatus() == Reservation.Status.CANCELADO
                        || r.getStatus() == Reservation.Status.EXPIRADO) {
                    continue;
                }
                StringBuilder equipSb = new StringBuilder();
                for (Map.Entry<Reservation.Equipment, Integer> e
                        : r.getEquipmentQuantities().entrySet()) {
                    if (equipSb.length() > 0) equipSb.append(",");
                    equipSb.append(e.getKey().name())
                           .append(":").append(e.getValue());
                }
                pw.println(
                    r.getClientId()        + "|" +
                    r.getDate()            + "|" +
                    r.getStartTime()       + "|" +
                    r.getEndTime()         + "|" +
                    r.getAttendeeCount()   + "|" +
                    r.getPriority().name() + "|" +
                    r.getStatus().name()   + "|" +
                    equipSb.toString()
                );
            }
            System.out.println("[PERSISTENCIA] Guardado OK en " + FILE);
        } catch (IOException e) {
            System.out.println("[PERSISTENCIA] Error al guardar: "
                    + e.getMessage());
        }
    }

    /**
     * Carga reservas del disco.
     */
    public static List<Reservation> load() {
        System.out.println("[PERSISTENCIA] Buscando en: " + FILE);
        List<Reservation> list = new ArrayList<>();
        File f = new File(FILE);
        if (!f.exists()) return list;

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            java.time.LocalDate today = java.time.LocalDate.now();

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] p = line.split("\\|", -1);
                if (p.length < 7) continue;

                try {
                    String clientId  = p[0];
                    String date      = p[1];
                    String startTime = p[2];
                    String endTime   = p[3];
                    int attendees    = Integer.parseInt(p[4]);
                    Reservation.Priority pr =
                            Reservation.Priority.valueOf(p[5]);
                    Reservation.Status status =
                            Reservation.Status.valueOf(p[6]);

                    // Parsear mapa de equipos (campo 7, opcional)
                    Map<Reservation.Equipment, Integer> equipMap =
                            new LinkedHashMap<>();
                    if (p.length >= 8 && !p[7].isEmpty()) {
                        for (String token : p[7].split(",")) {
                            String[] kv = token.trim().split(":");
                            if (kv.length < 2) continue;
                            try {
                                Reservation.Equipment eq =
                                        Reservation.Equipment.valueOf(
                                                kv[0].trim());
                                int qty = Integer.parseInt(kv[1].trim());
                                equipMap.put(eq, qty);
                            } catch (Exception ignored) {}
                        }
                    }

                    java.time.LocalDate resDate =
                            java.time.LocalDate.parse(date);
                    if (resDate.isBefore(today)
                            && status == Reservation.Status.CONFIRMADO) {
                        continue;
                    }

                    // Extraer primario y extra
                    Reservation.Equipment primary =
                            Reservation.Equipment.NINGUNO;
                    int primaryQty = 1;
                    Map<Reservation.Equipment, Integer> extra =
                            new LinkedHashMap<>();

                    if (!equipMap.isEmpty()) {
                        var it    = equipMap.entrySet().iterator();
                        var first = it.next();
                        primary    = first.getKey();
                        primaryQty = first.getValue();
                        while (it.hasNext()) {
                            var entry = it.next();
                            extra.put(entry.getKey(), entry.getValue());
                        }
                    }

                    Reservation r = new Reservation(
                            clientId, date, startTime, endTime,
                            attendees, primary, primaryQty,
                            extra, pr, true);
                    r.setStatus(status);
                    list.add(r);

                } catch (Exception e) {
                    System.out.println(
                            "[PERSISTENCIA] Línea inválida ignorada: " + line);
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