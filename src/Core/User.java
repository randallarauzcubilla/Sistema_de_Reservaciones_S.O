package Core;

public class User {

    public enum Rol {
        ESTUDIANTE, DOCENTE, DECANATURA
    }
    private final String nombre;
    private final Rol rol;

    public User(String nombre, Rol rol) {
        this.nombre = nombre;
        this.rol = rol;
    }

    public String getNombre() {
        return nombre;
    }

    public Rol getRol() {
        return rol;
    }

    @Override
    public String toString() {
        return nombre + " [" + rol + "]";
    }
}
