package org.simulator.eval;

import org.simulator.core.SchedulingSolution;

import java.util.ArrayList;
import java.util.List;

public class ParetoUtils {

    // vérifie si a domine b (meilleur ou égal partout, strictement meilleur quelque part)
    public static boolean dominates(SchedulingSolution a, SchedulingSolution b) {
        boolean strictlyBetter = false;

        // si a est pire sur au moins un objectif -> pas de domination
        if (a.getF1() > b.getF1() || a.getF2() > b.getF2() || a.getF3() > b.getF3()) {
            return false;
        }

        // note s'il est meilleur sur au moins un critère
        if (a.getF1() < b.getF1()) strictlyBetter = true;
        if (a.getF2() < b.getF2()) strictlyBetter = true;
        if (a.getF3() < b.getF3()) strictlyBetter = true;

        return strictlyBetter;
    }

    // met à jour l'archive Pareto avec une nouvelle population
    public static List<SchedulingSolution> updateArchive(
            List<SchedulingSolution> archive,
            List<SchedulingSolution> population,
            int maxSize
    ) {

        // on combine archive actuelle et nouvelles solutions
        List<SchedulingSolution> merged = new ArrayList<>();
        merged.addAll(archive);
        merged.addAll(population);

        List<SchedulingSolution> newArchive = new ArrayList<>();

        // on ne garde que les solutions non dominées
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

        // si trop de solutions -> simple tronquage (archive fixe)
        if (newArchive.size() > maxSize) {
            newArchive = newArchive.subList(0, maxSize);
        }

        return newArchive;
    }
}
