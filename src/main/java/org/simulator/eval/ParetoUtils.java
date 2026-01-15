package org.simulator.eval;

import org.simulator.core.SchedulingSolution;

import java.util.*;

public class ParetoUtils {

    private ParetoUtils() {}

    // a domine b (minimisation)
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

    // archive update: merge -> remove duplicates -> keep non-dominated -> truncate by crowding distance
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

    // -----------------------------
    // Helpers: duplicates + crowding
    // -----------------------------

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

    private static boolean sameAssignment(SchedulingSolution a, SchedulingSolution b) {
        int[] x = a.getAssignment();
        int[] y = b.getAssignment();
        if (x == null || y == null) return false;
        if (x.length != y.length) return false;
        for (int i = 0; i < x.length; i++) if (x[i] != y[i]) return false;
        return true;
    }

    // crowding distance on a single front (bigger = keep)
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

    private static double obj(SchedulingSolution s, int idx) {
        if (idx == 1) return s.getF1();
        if (idx == 2) return s.getF2();
        return s.getF3();
    }
}
