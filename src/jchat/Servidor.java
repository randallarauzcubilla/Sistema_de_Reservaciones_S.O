package jchat;

import java.io.*;
import java.net.*;
import java.util.*;

public class Servidor {

    // GestorSincronizacion — base de todos los locks
    // Parámetros: capacidad=200, proyectores=3, micrófonos=5, sonido=2
    public static final GestorSincronizacion gestor =
            new GestorSincronizacion(200, 3, 5, 2);

    // Recursos compartidos R1 → R5
    public static final Calendario       calendario = new Calendario(gestor);
    public static final RecursoAuditorio recursos   = new RecursoAuditorio(gestor);
    public static final ColaTTL          colaTTL    = new ColaTTL(gestor);
    public static final Bitacora         bitacora   = new Bitacora(gestor);

    // Clientes conectados
    public static final List<HiloReserva> clientesConectados =
            Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
    // Solo lanza la GUI — el socket lo abre FrmServidor al presionar "Iniciar"
    javax.swing.SwingUtilities.invokeLater(() -> {
        try {
            javax.swing.UIManager.setLookAndFeel(
                javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        new FrmServidor().setVisible(true);
    });
}
}