package org.simulator.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * Classe utilitaire pour l'invocation de scripts Python depuis Java.
 * Permet d'exécuter les scripts de visualisation et d'agrégation de résultats.
 */
public class PythonUtils {

    private static final String PYTHON_CMD = "python";

    /**
     * Sélectionne un script Python en testant plusieurs emplacements.
     *
     * @param primaryPath Chemin principal à tester
     * @param fallbackPath Chemin de secours
     * @return Chemin du script trouvé, ou null si aucun n'existe
     */
    public static Path pickPythonScript(Path primaryPath, Path fallbackPath) {
        if (Files.exists(primaryPath)) return primaryPath;
        if (Files.exists(fallbackPath)) return fallbackPath;
        return null;
    }

    /**
     * Exécute le script Python de génération des graphiques Pareto.
     *
     * @param runDir Répertoire contenant les fichiers CSV de résultats
     * @param plotScript Chemin vers le script de visualisation
     * @return true si l'exécution s'est terminée avec succès
     */
    public static boolean runPythonPlot(Path runDir, Path plotScript) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    PYTHON_CMD,
                    plotScript.toString(),
                    runDir.toString()
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

    /**
     * Exécute le script Python d'agrégation des résultats multi-runs.
     *
     * @param experimentDir Répertoire de l'expérimentation
     * @param aggregateScript Chemin vers le script d'agrégation
     * @return true si l'exécution s'est terminée avec succès
     */
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
