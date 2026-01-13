package org.simulator.eval;

import org.simulator.algo.RandomSelection;
import org.simulator.core.NetworkModel;
import org.simulator.core.Node;
import org.simulator.core.SchedulingSolution;
import org.simulator.core.Task;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ParetoMetrics {

    // -----------------------------
    // Hypervolume exact 3D (minimisation)
    // -----------------------------
    public static double hypervolume(List<SchedulingSolution> sols, double[] ref) {
        if (sols == null || sols.isEmpty()) return 0.0;
        if (ref == null || ref.length != 3) throw new IllegalArgumentException("refPoint must be [3]");

        // Transform to maximization space: x = ref - f (must be > 0)
        List<double[]> pts = new ArrayList<>();
        for (SchedulingSolution s : sols) {
            double x = ref[0] - s.getF1();
            double y = ref[1] - s.getF2();
            double z = ref[2] - s.getF3();
            if (x > 0 && y > 0 && z > 0) pts.add(new double[]{x, y, z});
        }
        if (pts.isEmpty()) return 0.0;

        pts = nonDominatedMax3D(pts);

        // Sort by x desc
        pts.sort((a, b) -> Double.compare(b[0], a[0]));

        double hv = 0.0;
        for (int i = 0; i < pts.size(); i++) {
            double x_i = pts.get(i)[0];
            double x_next = (i + 1 < pts.size()) ? pts.get(i + 1)[0] : 0.0;
            double dx = x_i - x_next;
            if (dx <= 0) continue;

            List<double[]> yz = new ArrayList<>(i + 1);
            for (int k = 0; k <= i; k++) yz.add(new double[]{pts.get(k)[1], pts.get(k)[2]});

            double area = hypervolume2DMax(yz);
            hv += dx * area;
        }
        return hv;
    }

    private static List<double[]> nonDominatedMax3D(List<double[]> pts) {
        List<double[]> nd = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            double[] p = pts.get(i);
            boolean dom = false;
            for (int j = 0; j < pts.size(); j++) {
                if (i == j) continue;
                if (dominatesMax3D(pts.get(j), p)) { dom = true; break; }
            }
            if (!dom) nd.add(p);
        }
        return nd;
    }

    private static boolean dominatesMax3D(double[] a, double[] b) {
        boolean strictly = false;
        if (a[0] < b[0]) return false;
        if (a[1] < b[1]) return false;
        if (a[2] < b[2]) return false;
        if (a[0] > b[0]) strictly = true;
        if (a[1] > b[1]) strictly = true;
        if (a[2] > b[2]) strictly = true;
        return strictly;
    }

    // Exact HV 2D (max) for rectangles [0,y]x[0,z]
    private static double hypervolume2DMax(List<double[]> yz) {
        if (yz.isEmpty()) return 0.0;

        yz = nonDominatedMax2D(yz);
        yz.sort((a, b) -> Double.compare(b[0], a[0])); // y desc

        double area = 0.0;
        double prevY = yz.get(0)[0];
        double bestZ = yz.get(0)[1];

        for (int i = 1; i < yz.size(); i++) {
            double y = yz.get(i)[0];
            double dy = prevY - y;
            if (dy > 0) area += dy * bestZ;

            double z = yz.get(i)[1];
            if (z > bestZ) bestZ = z;
            prevY = y;
        }

        if (prevY > 0) area += prevY * bestZ;
        return area;
    }

    private static List<double[]> nonDominatedMax2D(List<double[]> pts) {
        List<double[]> nd = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            double[] p = pts.get(i);
            boolean dom = false;
            for (int j = 0; j < pts.size(); j++) {
                if (i == j) continue;
                if (dominatesMax2D(pts.get(j), p)) { dom = true; break; }
            }
            if (!dom) nd.add(p);
        }
        return nd;
    }

    private static boolean dominatesMax2D(double[] a, double[] b) {
        boolean strictly = false;
        if (a[0] < b[0]) return false;
        if (a[1] < b[1]) return false;
        if (a[0] > b[0]) strictly = true;
        if (a[1] > b[1]) strictly = true;
        return strictly;
    }

    // -----------------------------
    // Spacing (inchangé, OK)
    // -----------------------------
    public static double spacing(List<SchedulingSolution> sols) {
        if (sols == null || sols.size() < 2) return 0.0;

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

    // -----------------------------
    // Export CSV
    // -----------------------------
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

    // -----------------------------
    // Auto ref point
    // -----------------------------
    public static double[] computeAutoRefPoint(List<Task> tasks, List<Node> nodes, NetworkModel net) {
        RandomSelection rs = new RandomSelection(tasks, nodes, net, 100, 50, new java.util.Random(42));

        double maxF1 = 0, maxF2 = 0, maxF3 = 0;

        for (SchedulingSolution s : rs.run()) {
            maxF1 = Math.max(maxF1, s.getF1());
            maxF2 = Math.max(maxF2, s.getF2());
            maxF3 = Math.max(maxF3, s.getF3());
        }

        double m = 1.10;
        return new double[]{ maxF1 * m, maxF2 * m, maxF3 * m };
    }
}
