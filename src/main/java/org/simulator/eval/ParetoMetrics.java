package org.simulator.eval;

import org.simulator.algo.RandomSelection;
import org.simulator.core.NetworkModel;
import org.simulator.core.Node;
import org.simulator.core.SchedulingSolution;
import org.simulator.core.Task;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Classe fournissant les métriques pour l'évaluation des fronts Pareto.
 * 
 * Cette classe implémente deux métriques principales :
 * - Hypervolume : Volume de l'espace des objectifs dominé par le front Pareto
 * - Spacing : Uniformité de la distribution des solutions le long du front
 */
public class ParetoMetrics {

    /**
     * Calcule l'hypervolume exact en 3D pour un ensemble de solutions.
     * 
     * @param sols Liste des solutions du front Pareto
     * @param ref Point de reference [f1_max, f2_max, f3_max]
     * @return Valeur de l'hypervolume
     * @throws IllegalArgumentException Si le point de reference n'a pas 3 dimensions
     */
    public static double hypervolume(List<SchedulingSolution> sols, double[] ref) {
        if (sols == null || sols.isEmpty()) return 0.0;
        if (ref == null || ref.length != 3) throw new IllegalArgumentException("refPoint must be [3]");

        // Transformation vers l'espace de maximisation : x = ref - f (doit etre > 0)
        List<double[]> pts = new ArrayList<>();
        for (SchedulingSolution s : sols) {
            double x = ref[0] - s.getF1();
            double y = ref[1] - s.getF2();
            double z = ref[2] - s.getF3();
            if (x > 0 && y > 0 && z > 0) pts.add(new double[]{x, y, z});
        }
        if (pts.isEmpty()) return 0.0;

        pts = nonDominatedMax3D(pts);

        // Tri par x décroissant
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

    /**
     * Filtre les points non dominés en 3D (maximisation).
     * 
     * @param pts Liste de points a filtrer
     * @return Liste des points non dominés
     */
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

    /**
     * Vérifie si le point a domine strictement le point b en 3D (maximisation).
     * 
     * @param a Premier point
     * @param b Second point
     * @return true si a domine strictement b, false sinon
     */
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

    /**
     * Calcul exact de l'hypervolume 2D pour des rectangles (maximisation).
     * 
     * @param yz Liste de points dans l'espace 2D
     * @return Aire totale dominée
     */
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

    /**
     * Filtre les points non dominés en 2D (maximisation).
     * 
     * @param pts Liste de points a filtrer
     * @return Liste des points non dominés
     */
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

    /**
     * Vérifie si le point a domine strictement le point b en 2D (maximisation).
     * 
     * @param a Premier point
     * @param b Second point
     * @return true si a domine strictement b, false sinon
     */
    private static boolean dominatesMax2D(double[] a, double[] b) {
        boolean strictly = false;
        if (a[0] < b[0]) return false;
        if (a[1] < b[1]) return false;
        if (a[0] > b[0]) strictly = true;
        if (a[1] > b[1]) strictly = true;
        return strictly;
    }

    /**
     * Calcule la métrique de spacing pour mesurer l'uniformité de distribution du front.
     * Un spacing faible indique une meilleure distribution des solutions.
     * 
     * @param sols Liste des solutions du front Pareto
     * @return Valeur de spacing (plus c'est faible, meilleure est la distribution)
     */
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

    /**
     * Exporte un front Pareto au format CSV.
     * 
     * @param sols Liste des solutions du front
     * @param file Chemin du fichier de sortie
     */
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

    /**
     * Calcule automatiquement un point de reference pour l'hypervolume.
     * Génère des solutions aléatoires et prend les maxima des objectifs avec une marge de 10%.
     * 
     * @param tasks Liste des taches du workflow
     * @param nodes Liste des noeuds disponibles
     * @param net Modele de reseau
     * @param rnd Generateur aléatoire
     * @return Point de reference [f1_max, f2_max, f3_max]
     */
    public static double[] computeAutoRefPoint(List<Task> tasks, List<Node> nodes, NetworkModel net, Random rnd) {
        RandomSelection rs = new RandomSelection(tasks, nodes, net, 100, 50, rnd);

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
