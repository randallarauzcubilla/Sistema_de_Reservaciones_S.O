package jchat;

/**
 * FLUJO - Hilo de comunicación por cliente
 *
 * Cada instancia maneja la conexión con UN cliente específico.
 * Interpreta el protocolo de mensajes y delega lógica al Servidor.
 *
 * Protocolo entrante (Cliente → Servidor):
 *   "PEDIR_CARTON"    → Reenvía el cartón (ya fue enviado al conectar)
 *   "BINGO:<nombre>"  → Verifica si ese usuario ganó
 *   Cualquier otro    → Reenvía como mensaje de chat a todos
 *
 * NOTA DE SINCRONIZACIÓN:
 *   El cartón se envía automáticamente al conectarse (en run()) para garantizar
 *   que el cliente lo tenga antes de que comiencen a llegar números sorteados.
 *   PEDIR_CARTON queda como respaldo por si el cliente necesita re-solicitarlo.
 */

import java.io.*;
import java.net.*;
import java.util.logging.*;

public class Flujo extends Thread {

    private final Socket socket;
    private final String nombre;
    private final int[][] carton;

    DataInputStream flujoLectura;
    DataOutputStream flujoEscritura;

    public Flujo(Socket socket, String nombre, int[][] carton) {
        this.socket = socket;
        this.nombre = nombre;
        this.carton = carton;
        try {
            flujoLectura  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            flujoEscritura = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        } catch (IOException e) {
            System.out.println("[ERROR] Flujo constructor: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        // Registrar usuario
        Servidor.usuarios.add(new Usuario(this, nombre, carton));
        broadcast("MSG:" + nombre + " se ha unido a la partida.");
        System.out.println("[FLUJO] " + nombre + " conectado. Usuarios: " + Servidor.usuarios.size());

        // Enviar cartón INMEDIATAMENTE al conectarse (sincronización garantizada)
        // Así el cliente tiene su cartón antes de recibir cualquier NUMERO:
        enviarCartonAlCliente();

        // Si ya hay números cantados (cliente se une en partida en curso),
        // enviarlos todos para que pueda ponerse al día
        if (!Servidor.numerosCantados.isEmpty()) {
            for (int n : Servidor.numerosCantados) {
                try {
                    synchronized (flujoEscritura) {
                        flujoEscritura.writeUTF("NUMERO:" + n);
                        flujoEscritura.flush();
                    }
                } catch (IOException e) {
                    System.out.println("[ERROR] Reenvío de historial a " + nombre + ": " + e.getMessage());
                }
            }
            System.out.println("[FLUJO] Historial de " + Servidor.numerosCantados.size() + " números enviado a " + nombre);
        }

        while (true) {
            try {
                String mensaje = flujoLectura.readUTF().trim();
                if (mensaje.isEmpty()) continue;

                if (mensaje.equals("PEDIR_CARTON")) {
                    // ── Protocolo: cliente solicita su cartón ──
                    enviarCartonAlCliente();

                } else if (mensaje.startsWith("BINGO:")) {
                    // ── Protocolo: cliente declara BINGO ──
                    String solicitante = mensaje.substring(6).trim();
                    procesarDeclaracionBingo(solicitante);

                } else {
                    // ── Mensaje de chat normal ──
                    broadcast("MSG:" + nombre + "> " + mensaje);
                }

            } catch (IOException e) {
                // Cliente desconectado
                Servidor.usuarios.removeIf(u -> u.getNombre().equals(nombre));
                broadcast("MSG:" + nombre + " se ha desconectado.");
                System.out.println("[FLUJO] " + nombre + " desconectado.");
                break;
            }
        }
    }

    /**
     * Envía el cartón de este cliente serializado.
     * Formato: "CARTON:n,n,n,n,n|n,n,n,n,n|..."
     */
    private void enviarCartonAlCliente() {
        try {
            String datos = Servidor.serializarCarton(this.carton);
            synchronized (flujoEscritura) {
                flujoEscritura.writeUTF(datos);
                flujoEscritura.flush();
            }
            System.out.println("[FLUJO] Cartón enviado a " + nombre);
        } catch (IOException e) {
            System.out.println("[ERROR] Enviar cartón a " + nombre + ": " + e.getMessage());
        }
    }

    /**
     * Verifica centralizadamente si el usuario que declara BINGO realmente ganó.
     */
    private void procesarDeclaracionBingo(String nombreJugador) {
        if (Servidor.hayGanador) return; // Ya hubo ganador

        for (Usuario u : Servidor.usuarios) {
            if (u.getNombre().equals(nombreJugador)) {
                boolean esValido = Servidor.verificarBingo(u.getCarton(), Servidor.numerosCantados);
                if (esValido) {
                    Servidor.hayGanador = true;
                    broadcast("GANADOR:" + nombreJugador);
                    System.out.println("[BINGO] ¡Ganador verificado: " + nombreJugador + "!");
                } else {
                    try {
                        synchronized (flujoEscritura) {
                            flujoEscritura.writeUTF("MSG:Tu BINGO no es válido todavía. ¡Sigue jugando!");
                            flujoEscritura.flush();
                        }
                    } catch (IOException e) {
                        System.out.println("[ERROR] Notificar BINGO inválido: " + e.getMessage());
                    }
                }
                return;
            }
        }
    }

    /**
     * Transmite un mensaje a TODOS los clientes conectados.
     */
    public void broadcast(String mensaje) {
        synchronized (Servidor.usuarios) {
            for (Usuario u : Servidor.usuarios) {
                Flujo f = u.getFlujo();
                try {
                    synchronized (f.flujoEscritura) {
                        f.flujoEscritura.writeUTF(mensaje);
                        f.flujoEscritura.flush();
                    }
                } catch (IOException e) {
                    System.out.println("[ERROR] Broadcast a " + u.getNombre() + ": " + e.getMessage());
                }
            }
        }
    }

    public String getNombre() { return nombre; }
    public int[][] getCarton() { return carton; }
}