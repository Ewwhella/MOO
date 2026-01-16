package org.simulator.util;

import org.simulator.core.Node;
import org.simulator.core.Task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Classe utilitaire fournissant des fonctions pour la manipulation de tâches,
 * le tri topologique, la gestion des fichiers et la mesure du temps.
 */
public class Utils {

    /**
     * Convertit un tableau d'affectation en map tâche -> noeud.
     *
     * @param assignment Tableau où assignment[i] = index du noeud pour la tâche i
     * @param tasks Liste des tâches
     * @param nodes Liste des noeuds
     * @return Map associant l'ID de chaque tâche à l'ID du noeud assigné
     */
    public static Map<String, String> convert(int[] assignment, List<Task> tasks, List<Node> nodes) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < tasks.size(); i++) {
            int nodeIndex = assignment[i];
            map.put(tasks.get(i).getId(), nodes.get(nodeIndex).getId());
        }
        return map;
    }

    /**
     * Effectue un tri topologique sur un DAG de tâches.
     *
     * @param tasks Liste des tâches avec leurs dépendances
     * @return Liste triée en ordre topologique
     */
    public static List<Task> topoSort(List<Task> tasks) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, Task> byId = new HashMap<>();

        for (Task t : tasks) {
            indegree.put(t.getId(), 0);
            byId.put(t.getId(), t);
        }

        for (Task t : tasks) {
            for (Task p : t.getPredecessors()) {
                indegree.put(t.getId(), indegree.get(t.getId()) + 1);
            }
        }

        Queue<Task> q = new ArrayDeque<>();
        for (Task t : tasks) {
            if (indegree.get(t.getId()) == 0) q.add(t);
        }

        List<Task> sorted = new ArrayList<>();

        while (!q.isEmpty()) {
            Task cur = q.poll();
            sorted.add(cur);

            for (Task succ : tasks) {
                if (succ.getPredecessors().contains(cur)) {
                    int deg = indegree.get(succ.getId()) - 1;
                    indegree.put(succ.getId(), deg);
                    if (deg == 0) q.add(succ);
                }
            }
        }

        return sorted;
    }

    /**
     * Formate le temps écoulé depuis un instant donné.
     *
     * @param startNano Instant de début en nanosecondes
     * @return Chaîne formatée "X.XXX s"
     */
    public static String formatSeconds(long startNano) {
        double sec = (System.nanoTime() - startNano) / 1_000_000_000.0;
        return String.format("%.3f s", sec);
    }

    /**
     * Crée un répertoire.
     *
     * @param dir Chemin du répertoire à créer
     * @throws RuntimeException Si la création échoue
     */
    public static void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + dir, e);
        }
    }

    /**
     * Calcule le temps écoulé en secondes depuis un instant donné.
     *
     * @param startNano Instant de début en nanosecondes
     * @return Temps écoulé en secondes
     */
    public static double secondsSince(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000_000.0;
    }

    /**
     * Initialise le fichier CSV de résumé des exécutions.
     *
     * @param summaryCsv Chemin du fichier CSV
     */
    public static void initScenarioSummary(Path summaryCsv) {
        if (Files.exists(summaryCsv)) return;

        try {
            Files.write(summaryCsv, ("run,seed,ref_f1,ref_f2,ref_f3,"
                    + "pareto_mojs,pareto_aco,pareto_random,pareto_greedy,"
                    + "hv_mojs,hv_aco,hv_random,hv_greedy,"
                    + "time_mojs_s,time_aco_s,time_random_s,time_greedy_s,time_total_s\n").getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to init summary CSV: " + summaryCsv, e);
        }
    }

    /**
     * Ajoute une ligne de résultats au fichier CSV de résumé.
     *
     * @param summaryCsv Chemin du fichier CSV
     * @param runIdx Numéro de l'exécution
     * @param seed Graine aléatoire utilisée
     * @param ref Point de référence pour l'hypervolume
     * @param szJS Taille du front Pareto MOJS
     * @param szACO Taille du front Pareto MO-ACO
     * @param szR Taille du front Pareto Random
     * @param szG Taille du front Pareto Greedy
     * @param hvJS Hypervolume MOJS
     * @param hvACO Hypervolume MO-ACO
     * @param hvR Hypervolume Random
     * @param hvG Hypervolume Greedy
     * @param tJS Temps d'exécution MOJS
     * @param tACO Temps d'exécution MO-ACO
     * @param tR Temps d'exécution Random
     * @param tG Temps d'exécution Greedy
     * @param tTotal Temps total de l'exécution
     */
    public static void appendScenarioSummary(
            Path summaryCsv,
            int runIdx, long seed,
            double[] ref,
            int szJS, int szACO, int szR, int szG,
            double hvJS, double hvACO, double hvR, double hvG,
            double tJS, double tACO, double tR, double tG,
            double tTotal
    ) {
        String line = runIdx + "," + seed + ","
                + ref[0] + "," + ref[1] + "," + ref[2] + ","
                + szJS + "," + szACO + "," + szR + "," + szG + ","
                + hvJS + "," + hvACO + "," + hvR + "," + hvG + ","
                + tJS + "," + tACO + "," + tR + "," + tG + "," + tTotal
                + "\n";
        try {
            Files.write(summaryCsv, line.getBytes(), java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException("Failed to append summary CSV: " + summaryCsv, e);
        }
    }

}
