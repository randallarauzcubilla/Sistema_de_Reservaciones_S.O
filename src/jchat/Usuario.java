package jchat;

/**
 * USUARIO - Modelo de datos de un jugador conectado
 *
 * Almacena la referencia al hilo de comunicación (Flujo),
 * el nombre del jugador y su cartón asignado por el servidor.
 */
public class Usuario {

    private final Flujo  flujo;
    private final String nombre;
    private final int[][] carton;

    public Usuario(Flujo flujo, String nombre, int[][] carton) {
        this.flujo  = flujo;
        this.nombre = nombre;
        this.carton = carton;
    }

    public Flujo   getFlujo()  { return flujo;  }
    public String  getNombre() { return nombre; }
    public int[][] getCarton() { return carton; }
}