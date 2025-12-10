package org.simulator.eval;

import org.simulator.core.SchedulingSolution;

import java.util.ArrayList;
import java.util.List;

public class ParetoUtils {

    public static boolean dominates(SchedulingSolution a, SchedulingSolution b) {
        boolean strictlyBetter = false;

        if (a.getF1() > b.getF1() || a.getF2() > b.getF2() || a.getF3() > b.getF3()) {
            return false;
        }

        if (a.getF1() < b.getF1()) strictlyBetter = true;
        if (a.getF2() < b.getF2()) strictlyBetter = true;
        if (a.getF3() < b.getF3()) strictlyBetter = true;

        return strictlyBetter;
    }

    public static List<SchedulingSolution> updateArchive(List<SchedulingSolution> archive, List<SchedulingSolution> population, int maxSize) {

        List<SchedulingSolution> merged = new ArrayList<>();
        merged.addAll(archive);
        merged.addAll(population);

        List<SchedulingSolution> newArchive = new ArrayList<>();

        for (SchedulingSolution s : merged) {
            boolean dominated = false;
            for (SchedulingSolution t : merged) {
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
