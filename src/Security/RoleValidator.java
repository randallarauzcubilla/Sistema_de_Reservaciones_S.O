package Security;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates if an ID belongs to a privileged role (PROFESSOR or DEANERY)
 * by reading the professors.txt and deans.txt files.
 *
 * File format: one ID per line, no spaces.
 * Location: project root (where the server runs).
 */
public class RoleValidator {

    private static final String PROFESSORS_FILE = "profesores.txt";
    private static final String DEANS_FILE      = "decanos.txt";

    private static Set<String> professors = new HashSet<>();
    private static Set<String> deans      = new HashSet<>();
    private static boolean     loaded     = false;

    /**
     * Loads the role files into memory once.
     * Synchronized to ensure thread-safe initialization.
     */
    public static synchronized void load() {
        professors = loadFile(PROFESSORS_FILE);
        deans      = loadFile(DEANS_FILE);
        loaded     = true;
        System.out.println("[ROLES] Profesores cargados: " + professors.size());
        System.out.println("[ROLES] Decanos cargados: "    + deans.size());
    }

    /**
     * Reads a file and stores each line as an entry in a Set.
     *
     * @param file the path of the file to be loaded
     * @return a Set containing the trimmed lines from the file
     */
    private static Set<String> loadFile(String file) {
        Set<String> set = new HashSet<>();
        File f = new File(file);
        if (!f.exists()) {
            System.out.println("[ROLES] Archivo no encontrado: " + file);
            return set;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) set.add(line);
            }
        } catch (IOException e) {
            System.out.println("[ROLES] Error leyendo " + file + ": " 
                    + e.getMessage());
        }
        return set;
    }

    /**
     * Verifies if the provided ID is authorized to use the requested role.
     * STUDENT: Always permitted.
     * PROFESSOR: ID must exist in professors.txt.
     * DEANERY: ID must exist in deans.txt.
     *
     * @param id   the identification string to verify
     * @param role the role name to check against
     * @return true if the ID is authorized for the role, false otherwise
     */
    public static boolean canUseRole(String id, String role) {
        if (!loaded) load();
        id = id.trim();
        switch (role.toUpperCase()) {
            case "ESTUDIANTE":   return true;
            case "DOCENTE": return professors.contains(id);
            case "DECANATURA":   return deans.contains(id);
            default: return false;
        }
    }
}