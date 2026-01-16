package org.simulator.eval;

import org.simulator.core.SchedulingSolution;

import java.util.*;

/**
 * Classe utilitaire pour la manipulation et la maintenance des fronts Pareto.
 */
public class ParetoUtils {

    private ParetoUtils() {}

    /**
     * Vérifie si la solution a domine strictement la solution b (minimisation).
     *
     * @param a Première solution
     * @param b Seconde solution
     * @return true si a domine strictement b, false sinon
     */
    public static boolean dominates(SchedulingSolution a, SchedulingSolution b) {
        boolean strictlyBetter = false;

        if (a.getF1() > b.getF1()) return false;
        if (a.getF2() > b.getF2()) return false;
        if (a.getF3() > b.getF3()) return false;

        if (a.getF1() < b.getF1()) strictlyBetter = true;
        if (a.getF2() < b.getF2()) strictlyBetter = true;
        if (a.getF3() < b.getF3()) strictlyBetter = true;

        return strictlyBetter;
    }

    /**
     * Met a jour l'archive Pareto en fusionnant l'archive existante et la nouvelle population.
     *
     * Algorithme en 4 étapes :
     * Merge -> Remove Duplicates -> Keep Non-Dominated -> Truncate by Crowding Distance
     *
     * @param archive Archive Pareto actuelle
     * @param population Nouvelle population de solutions
     * @param maxSize Taille maximale de l'archive
     * @return Archive mise à jour avec au plus maxSize solutions non dominées
     */
    public static List<SchedulingSolution> updateArchive(
            List<SchedulingSolution> archive,
            List<SchedulingSolution> population,
            int maxSize
    ) {
        List<SchedulingSolution> merged = new ArrayList<>();
        if (archive != null) merged.addAll(archive);
        if (population != null) merged.addAll(population);

        merged = removeDuplicateAssignments(merged);

        List<SchedulingSolution> nd = getNonDominated(merged);

        if (nd.size() <= maxSize) return nd;

        Map<SchedulingSolution, Double> crowd = crowdingDistance(nd);
        nd.sort((s1, s2) -> Double.compare(crowd.getOrDefault(s2, 0.0), crowd.getOrDefault(s1, 0.0)));

        return new ArrayList<>(nd.subList(0, maxSize));
    }

    /**
     * Filtre les solutions non dominées d'un ensemble
     *
     * @param sols Liste des solutions a filtrer
     * @return Liste des solutions non dominées
     */
    public static List<SchedulingSolution> getNonDominated(List<SchedulingSolution> sols) {
        List<SchedulingSolution> nd = new ArrayList<>();
        for (int i = 0; i < sols.size(); i++) {
            SchedulingSolution s = sols.get(i);
            boolean dominated = false;

            for (int j = 0; j < sols.size(); j++) {
                if (i == j) continue;
                if (dominates(sols.get(j), s)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) nd.add(s);
        }
        return nd;
    }

    /**
     * Supprime les solutions avec des affectations identiques.
     *
     * @param sols Liste des solutions
     * @return Liste sans doublons
     */
    private static List<SchedulingSolution> removeDuplicateAssignments(List<SchedulingSolution> sols) {
        List<SchedulingSolution> out = new ArrayList<>();
        for (SchedulingSolution s : sols) {
            boolean exists = false;
            for (SchedulingSolution t : out) {
                if (sameAssignment(s, t)) { exists = true; break; }
            }
            if (!exists) out.add(s);
        }
        return out;
    }

    /**
     * Vérifie si deux solutions ont la même affectation tache vers noeud.
     *
     * @param a Première solution
     * @param b Seconde solution
     * @return true si les affectations sont identiques, false sinon
     */
    private static boolean sameAssignment(SchedulingSolution a, SchedulingSolution b) {
        int[] x = a.getAssignment();
        int[] y = b.getAssignment();
        if (x == null || y == null) return false;
        if (x.length != y.length) return false;
        for (int i = 0; i < x.length; i++) if (x[i] != y[i]) return false;
        return true;
    }

    /**
     * Calcule la crowding distance pour un front de solutions.
     *
     * @param front Liste des solutions du front
     * @return Map associant chaque solution à sa crowding distance (bigger = keep)
     */
    private static Map<SchedulingSolution, Double> crowdingDistance(List<SchedulingSolution> front) {
        Map<SchedulingSolution, Double> dist = new HashMap<>();
        for (SchedulingSolution s : front) dist.put(s, 0.0);

        if (front.size() <= 2) {
            for (SchedulingSolution s : front) dist.put(s, Double.POSITIVE_INFINITY);
            return dist;
        }

        crowdOnObjective(front, dist, 1);
        crowdOnObjective(front, dist, 2);
        crowdOnObjective(front, dist, 3);

        return dist;
    }

    /**
     * Calcule la contribution à la crowding distance pour un objectif donne.
     *
     * Formule : distance[i] += (f(i+1) - f(i-1)) / (f_max - f_min)
     *
     * @param front Liste des solutions
     * @param dist Map des distances cumulées
     * @param objIdx Index de l'objectif
     */
    private static void crowdOnObjective(List<SchedulingSolution> front,
                                         Map<SchedulingSolution, Double> dist,
                                         int objIdx) {
        front.sort(Comparator.comparingDouble(s -> obj(s, objIdx)));

        int n = front.size();
        SchedulingSolution minS = front.get(0);
        SchedulingSolution maxS = front.get(n - 1);

        dist.put(minS, Double.POSITIVE_INFINITY);
        dist.put(maxS, Double.POSITIVE_INFINITY);

        double min = obj(minS, objIdx);
        double max = obj(maxS, objIdx);
        double range = max - min;
        if (Math.abs(range) < 1e-12) return;

        for (int i = 1; i < n - 1; i++) {
            SchedulingSolution s = front.get(i);
            if (Double.isInfinite(dist.get(s))) continue;

            double prev = obj(front.get(i - 1), objIdx);
            double next = obj(front.get(i + 1), objIdx);

            dist.put(s, dist.get(s) + (next - prev) / range);
        }
    }

    /**
     * Récupère la valeur d'un objectif pour une solution donnee.
     *
     * @param s Solution
     * @param idx Index de l'objectif
     * @return Valeur de l'objectif
     */
    private static double obj(SchedulingSolution s, int idx) {
        if (idx == 1) return s.getF1();
        if (idx == 2) return s.getF2();
        return s.getF3();
    }
}
