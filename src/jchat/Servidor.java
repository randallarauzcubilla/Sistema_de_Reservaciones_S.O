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
        try (ServerSocket serverSocket = new ServerSocket(8000)) {
            System.out.println("[SERVIDOR] Auditorio en línea - Puerto 8000");

            // Hilo TTL como demonio
            HiloTTL hiloTTL = new HiloTTL(calendario, recursos, colaTTL, bitacora);
            hiloTTL.setDaemon(true);
            hiloTTL.start();

            // Interfaz gráfica del servidor
            FrmServidor ventana = new FrmServidor();
            ventana.setVisible(true);

            // Aceptar clientes
            while (true) {
                Socket clienteSocket = serverSocket.accept();
                DataInputStream entrada = new DataInputStream(
                        new BufferedInputStream(clienteSocket.getInputStream()));
                String idCliente = entrada.readUTF();
                System.out.println("[SERVIDOR] Cliente conectado: " + idCliente);

                HiloReserva hilo = new HiloReserva(
                        clienteSocket, idCliente,
                        calendario, recursos, colaTTL, bitacora);
                clientesConectados.add(hilo);
                hilo.start();
            }

        } catch (IOException e) {
            System.out.println("[ERROR] Servidor: " + e.getMessage());
        }
    }
}