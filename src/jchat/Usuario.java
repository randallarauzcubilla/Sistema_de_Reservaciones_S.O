package jchat;

public class Usuario {
    public enum Rol { ESTUDIANTE, DOCENTE, DECANATURA }
    private final String nombre;
    private final Rol rol;
    public Usuario(String nombre, Rol rol) {
        this.nombre = nombre;
        this.rol = rol;
    }
    public String getNombre() { return nombre; }
    public Rol getRol()       { return rol; }
    @Override
    public String toString()  { return nombre + " [" + rol + "]"; }
}
