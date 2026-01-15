package org.simulator.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

public class PythonUtils {

    private static final String PYTHON_CMD = "python";

    public static Path pickPythonScript(Path primaryPath, Path fallbackPath) {
        if (Files.exists(primaryPath)) return primaryPath;
        if (Files.exists(fallbackPath)) return fallbackPath;
        return null;
    }

    public static boolean runPythonPlot(Path runDir, Path plotScript) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    PYTHON_CMD,
                    plotScript.toString(),
                    runDir.toString()   // <-- dossier des CSV
            );

            pb.redirectErrorStream(true);

            Process p = pb.start();

            try (Scanner sc = new Scanner(p.getInputStream())) {
                while (sc.hasNextLine()) {
                    System.out.println("[PY] " + sc.nextLine());
                }
            }

            int code = p.waitFor();
            return code == 0;

        } catch (Exception e) {
            System.out.println("[ERROR] Python plot failed: " + e.getMessage());
            return false;
        }
    }


    public static boolean runPythonAggregate(Path experimentDir, Path aggregateScript) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    PYTHON_CMD,
                    aggregateScript.toString(),
                    experimentDir.toString()
            );

            pb.redirectErrorStream(true);

            Process p = pb.start();

            try (Scanner sc = new Scanner(p.getInputStream())) {
                while (sc.hasNextLine()) {
                    System.out.println("[AGG] " + sc.nextLine());
                }
            }

            int code = p.waitFor();
            return code == 0;
        } catch (Exception e) {
            System.out.println("[ERROR] Aggregation failed: " + e.getMessage());
            return false;
        }
    }
}
