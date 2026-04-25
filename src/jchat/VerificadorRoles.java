package jchat;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Verifica si una cédula pertenece a un rol privilegiado (DOCENTE o DECANATURA)
 * leyendo los archivos profesores.txt y decanos.txt
 *
 * Formato de archivos: una cédula por línea, sin espacios
 * Ubicación: raíz del proyecto (donde corre el servidor)
 */
public class VerificadorRoles {

    private static final String ARCHIVO_PROFESORES = "profesores.txt";
    private static final String ARCHIVO_DECANOS    = "decanos.txt";

    private static Set<String> profesores = new HashSet<>();
    private static Set<String> decanos    = new HashSet<>();
    private static boolean     cargado    = false;

    // Carga los archivos una sola vez
    public static synchronized void cargar() {
        profesores = cargarArchivo(ARCHIVO_PROFESORES);
        decanos    = cargarArchivo(ARCHIVO_DECANOS);
        cargado    = true;
        System.out.println("[ROLES] Profesores cargados: " + profesores.size());
        System.out.println("[ROLES] Decanos cargados: "    + decanos.size());
    }

    private static Set<String> cargarArchivo(String archivo) {
        Set<String> set = new HashSet<>();
        File f = new File(archivo);
        if (!f.exists()) {
            System.out.println("[ROLES] Archivo no encontrado: " + archivo);
            return set;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (!linea.isEmpty()) set.add(linea);
            }
        } catch (IOException e) {
            System.out.println("[ROLES] Error leyendo " + archivo + ": " + e.getMessage());
        }
        return set;
    }

    /**
     * Verifica si la cédula puede usar el rol solicitado.ESTUDIANTE: siempre permitido
 DOCENTE: debe estar en profesores.txt
 DECANATURA: debe estar en decanos.txt
     * @param cedula
     * @param rol
     * @return 
     */
    public static boolean puedeUsarRol(String cedula, String rol) {
        if (!cargado) cargar();
        cedula = cedula.trim();
        switch (rol.toUpperCase()) {
            case "ESTUDIANTE":  return true;
            case "DOCENTE":     return profesores.contains(cedula);
            case "DECANATURA":  return decanos.contains(cedula);
            default:            return false;
        }
    }
}