package org.simulator.eval;

import org.simulator.algo.RandomSelection;
import org.simulator.core.NetworkModel;
import org.simulator.core.Node;
import org.simulator.core.SchedulingSolution;
import org.simulator.core.Task;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class ParetoMetrics {


    // Hypervolume : volume dominé jusqu'au point de référence

    public static double hypervolume(List<SchedulingSolution> sols, double[] ref) {
        double hv = 0.0;

        for (SchedulingSolution s : sols) {
            double dx = Math.max(0, ref[0] - s.getF1());
            double dy = Math.max(0, ref[1] - s.getF2());
            double dz = Math.max(0, ref[2] - s.getF3());

            hv += dx * dy * dz;
        }
        return hv;
    }


     // Spacing :  Mesure la régularité de l'espacement des points du front de Pareto

    public static double spacing(List<SchedulingSolution> sols) {
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


     // Export CSV pour tracer les fronts de Pareto en Python

    public static void exportCSV(List<SchedulingSolution> sols, String file) {
        try (FileWriter fw = new FileWriter(file)) {
            fw.write("f1_makespan,f2_cost,f3_energy\n");
            for (SchedulingSolution s : sols) {
                fw.write(s.getF1() + "," + s.getF2() + "," + s.getF3() + "\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Calcul automatique du refPoint pour l'adapter à nos différents cas (Cybershake30, 50, etc...)
    public static double[] computeAutoRefPoint(List<Task> tasks, List<Node> nodes, NetworkModel net) {

        RandomSelection rs = new RandomSelection(tasks, nodes, net, 30);  // 30 solutions devraient suffire
        double maxF1 = 0, maxF2 = 0, maxF3 = 0;

        for (SchedulingSolution s : rs.run()) {
            maxF1 = Math.max(maxF1, s.getF1());
            maxF2 = Math.max(maxF2, s.getF2());
            maxF3 = Math.max(maxF3, s.getF3());
        }

        double m = 1.10;   // +10% de marge

        return new double[]{
                maxF1 * m,
                maxF2 * m,
                maxF3 * m
        };
    }

}
