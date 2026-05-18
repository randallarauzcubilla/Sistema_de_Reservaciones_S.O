package Core;

import java.io.File;

public class AppPaths {

    private static final String BASE_DIR;

    static {
        BASE_DIR = System.getProperty("user.dir")
                + File.separator + "data";
        new File(BASE_DIR).mkdirs();
        System.out.println("[SISTEMA] Data dir: " + BASE_DIR);
    }

    public static String getDataDir() {
        return BASE_DIR;
    }
}