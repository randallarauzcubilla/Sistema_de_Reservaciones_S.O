package jchat;

/**
 * SERVIDOR DE BINGO - Autoridad central del juego
 *
 * Responsabilidades:
 * - Generar y almacenar cartones únicos para cada cliente
 * - Administrar la secuencia de números cantados (sin repetición)
 * - Verificar ganadores de forma centralizada
 * - Transmitir números sorteados a todos los clientes conectados
 *
 * Protocolo de comunicación:
 *   Cliente → Servidor : "PEDIR_CARTON"       → Servidor responde con datos del cartón
 *   Cliente → Servidor : "BINGO:<nombre>"      → Servidor verifica si es ganador
 *   Servidor → Clientes: "CARTON:<datos>"      → Entrega de cartón serializado
 *   Servidor → Clientes: "NUMERO:<n>"          → Número sorteado
 *   Servidor → Clientes: "GANADOR:<nombre>"    → Anuncio de ganador
 *   Servidor → Clientes: "MSG:<texto>"         → Mensaje de chat/sistema
 */

import java.io.*;
import java.net.*;
import java.util.*;

public class Servidor extends Thread {

    // Lista de usuarios conectados (sincronizada para acceso concurrente)
    public static final Vector<Usuario> usuarios = new Vector<>();

    // Números ya sorteados en la partida actual (sin repetición)
    public static final Set<Integer> numerosCantados = Collections.synchronizedSet(new LinkedHashSet<>());

    // Todos los números del bingo disponibles (1-75), mezclados aleatoriamente
    private static final List<Integer> bolillero = new ArrayList<>();

    // Índice del próximo número a cantar
    private static int indiceBolillero = 0;

    // Estado de la partida
    public static volatile boolean partidaActiva = false;
    public static volatile boolean hayGanador = false;

    /**
     * Inicializa el bolillero con números del 1 al 75 en orden aleatorio.
     * Se usa Collections.shuffle para garantizar aleatoriedad sin repetición.
     * Esto es distinto al enfoque original que usaba Math.random() con riesgo de colisiones.
     */
    public static synchronized void inicializarBolillero() {
        bolillero.clear();
        numerosCantados.clear();
        indiceBolillero = 0;
        hayGanador = false;

        for (int i = 1; i <= 75; i++) {
            bolillero.add(i);
        }
        Collections.shuffle(bolillero); // Mezcla garantizada sin repeticiones
        System.out.println("[SERVIDOR] Bolillero inicializado con " + bolillero.size() + " números.");
    }

    /**
     * Retorna el siguiente número del bolillero.
     * @return número sorteado, o -1 si ya se cantaron todos
     */
    public static synchronized int siguienteNumero() {
        if (indiceBolillero >= bolillero.size()) {
            return -1; // Todos los números fueron cantados
        }
        int numero = bolillero.get(indiceBolillero++);
        numerosCantados.add(numero);
        return numero;
    }

    /**
     * Genera un cartón BINGO único de 5x5.
     * Columnas: B(1-15), I(16-30), N(31-45), G(46-60), O(61-75)
     * Centro N[2][2] = 0 (FREE SPACE)
     *
     * Algoritmo mejorado: usa Collections.shuffle sobre rangos fijos
     * garantizando unicidad dentro de cada columna.
     */
    public static int[][] generarCarton() {
        int[][] carton = new int[5][5];

        // Rangos por columna BINGO
        int[][] rangos = {
            {1, 15},   // B
            {16, 30},  // I
            {31, 45},  // N
            {46, 60},  // G
            {61, 75}   // O
        };

        for (int col = 0; col < 5; col++) {
            List<Integer> pool = new ArrayList<>();
            for (int n = rangos[col][0]; n <= rangos[col][1]; n++) {
                pool.add(n);
            }
            Collections.shuffle(pool);
            for (int row = 0; row < 5; row++) {
                carton[row][col] = pool.get(row);
            }
        }

        // FREE SPACE en el centro
        carton[2][2] = 0;
        return carton;
    }

    /**
     * Serializa un cartón a String para transmisión por red.
     * Formato: "CARTON:n,n,n,n,n|n,n,n,n,n|...|n,n,n,n,n"
     * Cada fila separada por '|', valores por ','
     */
    public static String serializarCarton(int[][] carton) {
        StringBuilder sb = new StringBuilder("CARTON:");
        for (int i = 0; i < carton.length; i++) {
            for (int j = 0; j < carton[0].length; j++) {
                sb.append(carton[i][j]);
                if (j < carton[0].length - 1) sb.append(",");
            }
            if (i < carton.length - 1) sb.append("|");
        }
        return sb.toString();
    }

    /**
     * Verifica si el cartón de un usuario tiene BINGO
     * (línea completa: fila, columna o diagonal)
     */
    public static boolean verificarBingo(int[][] carton, Set<Integer> cantados) {
        // Marcar casillas: true si número fue cantado (o es FREE SPACE)
        boolean[][] marcado = new boolean[5][5];
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                marcado[i][j] = (carton[i][j] == 0) || cantados.contains(carton[i][j]);
            }
        }

        // Verificar filas
        for (int i = 0; i < 5; i++) {
            boolean fila = true;
            for (int j = 0; j < 5; j++) fila = fila && marcado[i][j];
            if (fila) return true;
        }

        // Verificar columnas
        for (int j = 0; j < 5; j++) {
            boolean col = true;
            for (int i = 0; i < 5; i++) col = col && marcado[i][j];
            if (col) return true;
        }

        // Verificar diagonal principal
        boolean diag1 = true;
        for (int i = 0; i < 5; i++) diag1 = diag1 && marcado[i][i];
        if (diag1) return true;

        // Verificar diagonal secundaria
        boolean diag2 = true;
        for (int i = 0; i < 5; i++) diag2 = diag2 && marcado[i][4 - i];
        return diag2;
    }

    public static void main(String[] args) {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(8000);
            System.out.println("--------------------------------");
            System.out.println(" SERVIDOR BINGO - Puerto 8000   ");
            System.out.println("   Esperando jugadores...       ");
            System.out.println("--------------------------------");

            // Inicializar bolillero al arrancar
            inicializarBolillero();

            // Mostrar interfaz gráfica del servidor
            FrmServidor ventana = new FrmServidor();
            ventana.setVisible(true);

            // Ciclo de aceptación de conexiones
            while (true) {
                Socket clienteSocket = serverSocket.accept();

                DataInputStream entrada = new DataInputStream(
                    new BufferedInputStream(clienteSocket.getInputStream()));
                String nombre = entrada.readUTF();

                System.out.println("[SERVIDOR] Nueva conexión: " + nombre);

                // Generar cartón único para este cliente
                int[][] carton = generarCarton();

                // Crear hilo de comunicación para este cliente
                Flujo flujo = new Flujo(clienteSocket, nombre, carton);
                flujo.start();
            }

        } catch (IOException e) {
            System.out.println("[ERROR] Servidor: " + e.getMessage());
        }
    }
}