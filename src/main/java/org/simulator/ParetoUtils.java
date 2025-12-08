package org.simulator;

import java.util.ArrayList;
import java.util.List;

public class ParetoUtils {

    public static boolean dominates(JellyfishSolution a, JellyfishSolution b) {
        boolean strictlyBetter = false;

        if (a.getF1() > b.getF1() || a.getF2() > b.getF2() || a.getF3() > b.getF3()) {
            return false;
        }

        if (a.getF1() < b.getF1()) strictlyBetter = true;
        if (a.getF2() < b.getF2()) strictlyBetter = true;
        if (a.getF3() < b.getF3()) strictlyBetter = true;

        return strictlyBetter;
    }

    public static List<JellyfishSolution> updateArchive(List<JellyfishSolution> archive, List<JellyfishSolution> population, int maxSize) {

        List<JellyfishSolution> merged = new ArrayList<>();
        merged.addAll(archive);
        merged.addAll(population);

        List<JellyfishSolution> newArchive = new ArrayList<>();

        for (JellyfishSolution s : merged) {
            boolean dominated = false;
            for (JellyfishSolution t : merged) {
                if (t != s && dominates(t, s)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) newArchive.add(s);
        }

        if (newArchive.size() > maxSize) {
            newArchive = newArchive.subList(0, maxSize);
        }

        return newArchive;
    }
}
