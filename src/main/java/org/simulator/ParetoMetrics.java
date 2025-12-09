package org.simulator;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class ParetoMetrics {

    /**
     * Hypervolume indicator (3D) : volume dominé jusqu'au point de référence.
     * On suppose minimisation de f1, f2, f3.
     */
    public static double hypervolume(List<JellyfishSolution> sols, double[] ref) {
        double hv = 0.0;

        for (JellyfishSolution s : sols) {
            double dx = Math.max(0, ref[0] - s.getF1());
            double dy = Math.max(0, ref[1] - s.getF2());
            double dz = Math.max(0, ref[2] - s.getF3());

            hv += dx * dy * dz;
        }
        return hv;
    }

    /**
     * Spacing metric :
     * Mesure la régularité de l'espacement des points du front Pareto.
     */
    public static double spacing(List<JellyfishSolution> sols) {
        List<Double> distances = new ArrayList<>();

        for (int i = 0; i < sols.size(); i++) {
            double min = Double.MAX_VALUE;

            for (int j = 0; j < sols.size(); j++) {
                if (i == j) continue;

                double d =
                        Math.abs(sols.get(i).getF1() - sols.get(j).getF1()) +
                                Math.abs(sols.get(i).getF2() - sols.get(j).getF2()) +
                                Math.abs(sols.get(i).getF3() - sols.get(j).getF3());

                if (d < min) min = d;
            }
            distances.add(min);
        }

        double mean = distances.stream().mapToDouble(x -> x).average().orElse(0);
        double sum = 0;

        for (double d : distances) sum += Math.pow(d - mean, 2);

        return Math.sqrt(sum / distances.size());
    }

    /**
     * Export CSV pour tracer les fronts Pareto (Python/Excel).
     */
    public static void exportCSV(List<JellyfishSolution> sols, String file) {
        try (FileWriter fw = new FileWriter(file)) {
            fw.write("f1_makespan,f2_cost,f3_energy\n");
            for (JellyfishSolution s : sols) {
                fw.write(s.getF1() + "," + s.getF2() + "," + s.getF3() + "\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
