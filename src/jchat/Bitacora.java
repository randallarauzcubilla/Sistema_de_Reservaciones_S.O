/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package jchat;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 *
 * @author KeilorM
 */
public class Bitacora {

    private final GestorSincronizacion gestor;
    private final List<String> entradas = new ArrayList<>();
    private final String archivoLog     = "bitacora_auditorio.txt";
    private final SimpleDateFormat sdf  = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    public Bitacora(GestorSincronizacion gestor) {
        this.gestor = gestor;
        log("SISTEMA", "Bitácora inicializada");
    }

    // SC-07: Escritura con mutex_bitacora (Nivel 5)
    public void log(String tipo, String descripcion) {
        String entrada = String.format("[%s] [%-16s] %s",
                sdf.format(new Date()), tipo, descripcion);
        gestor.getMutexBitacora().lock();
        try {
            entradas.add(entrada);
            escribirDisco(entrada);
            System.out.println("[BITACORA] " + entrada);
        } finally {
            gestor.getMutexBitacora().unlock();
        }
    }

    public void logConexion(String cliente) {
        log("CONEXION", cliente + " conectado");
    }

    public void logDesconexion(String cliente) {
        log("DESCONEXION", cliente + " desconectado");
    }

    public void logReserva(Reserva r) {
        log("RESERVA_TEMPORAL", r.toString());
    }

    public void logConfirmacion(Reserva r) {
        log("CONFIRMACION", "Reserva " + r.getIdReserva()
                + " confirmada por " + r.getIdCliente());
    }

    public void logCancelacion(Reserva r, String motivo) {
        log("CANCELACION", "Reserva " + r.getIdReserva()
                + " cancelada. Motivo: " + motivo);
    }

    public void logExpiracion(Reserva r) {
        log("EXPIRACION_TTL", "Reserva " + r.getIdReserva()
                + " expirada. Cliente: " + r.getIdCliente());
    }

    public void logError(String descripcion) {
        log("ERROR", descripcion);
    }

    public List<String> getEntradas() {
        gestor.getMutexBitacora().lock();
        try { return new ArrayList<>(entradas); }
        finally { gestor.getMutexBitacora().unlock(); }
    }

    public List<String> getUltimas(int n) {
        gestor.getMutexBitacora().lock();
        try {
            int total = entradas.size();
            int desde = Math.max(0, total - n);
            return new ArrayList<>(entradas.subList(desde, total));
        } finally {
            gestor.getMutexBitacora().unlock();
        }
    }

    private void escribirDisco(String entrada) {
        try {
            FileWriter fw = new FileWriter(archivoLog, true);
            PrintWriter pw = new PrintWriter(fw);
            pw.println(entrada);
            pw.close();
        } catch (IOException e) {
            System.out.println("[ERROR] Bitacora disco: " + e.getMessage());
        }
    }
}
    

