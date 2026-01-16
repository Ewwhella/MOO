package org.simulator.eval;

import org.simulator.core.NetworkModel;
import org.simulator.core.Node;
import org.simulator.core.SchedulingSolution;

import java.io.FileWriter;
import java.util.List;

/**
 * Classe utilitaire pour l'affichage et l'exportation des résultats d'optimisation.
 * Fournit des méthodes pour visualiser les fronts Pareto, les métriques de performance
 * et les informations sur la topologie réseau.
 */
public class ModelingUtils {

    /**
     * Affiche de manière formatée un front Pareto dans la console.
     *
     * @param name Nom de l'algorithme
     * @param pareto Liste des solutions Pareto
     */
    public static void printPareto(String name, List<SchedulingSolution> pareto) {
        System.out.println("\n=== PARETO SOLUTIONS (" + name + ") ===");
        System.out.printf("%-4s %-12s %-15s %-12s%n", "#", "Makespan", "Cost", "Energy");
        System.out.println("---------------------------------------------------------");

        int idx = 1;
        for (SchedulingSolution s : pareto) {
            System.out.printf("%-4d %-12.3f %-15.6f %-12.3f%n",
                    idx++, s.getF1(), s.getF2(), s.getF3());
        }
    }

    /**
     * Affiche les métriques de performance pour tous les algorithmes.
     *
     * @param js Front Pareto de MOJS
     * @param aco Front Pareto de MO-ACO
     * @param random Front Pareto de l'approche aléatoire
     * @param greedy Front Pareto de l'approche gloutonne
     * @param refPoint Point de référence pour le calcul de l'hypervolume
     */
    public static void printMetrics(List<SchedulingSolution> js,
                                    List<SchedulingSolution> aco,
                                    List<SchedulingSolution> random,
                                    List<SchedulingSolution> greedy,
                                    double[] refPoint) {

        System.out.println("\n=== PERFORMANCE METRICS ===");

        System.out.println("\nHypervolume (exact 3D):");
        System.out.println("MOJS   : " + ParetoMetrics.hypervolume(js, refPoint));
        System.out.println("MO-ACO : " + ParetoMetrics.hypervolume(aco, refPoint));
        System.out.println("RANDOM : " + ParetoMetrics.hypervolume(random, refPoint));
        System.out.println("GREEDY : " + ParetoMetrics.hypervolume(greedy, refPoint));

        System.out.println("\nSpacing:");
        System.out.println("MOJS   : " + ParetoMetrics.spacing(js));
        System.out.println("MO-ACO : " + ParetoMetrics.spacing(aco));
        System.out.println("RANDOM : " + ParetoMetrics.spacing(random));
        System.out.println("GREEDY : " + ParetoMetrics.spacing(greedy));
    }

    /**
     * Exporte l'évolution de l'hypervolume au format CSV.
     *
     * @param hvList Liste des valeurs d'hypervolume par génération
     * @param filename Chemin du fichier de sortie
     */
    public static void exportHypervolumeCSV(List<Double> hvList, String filename) {
        try (FileWriter fw = new FileWriter(filename)) {
            fw.write("generation,hypervolume\n");
            for (int i = 0; i < hvList.size(); i++) {
                fw.write(i + "," + hvList.get(i) + "\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Affiche un résumé des caractéristiques des noeuds principaux.
     *
     * @param nodes Liste des noeuds de l'infrastructure
     */
    public static void printNodeSummary(List<Node> nodes) {
        for (Node n : nodes) {
            if ("edge1".equals(n.getId()) || "fog1".equals(n.getId()) || "cloud1".equals(n.getId())) {
                System.out.println("  " + n.getId()
                        + " type=" + n.getType()
                        + " zone=" + n.getZone()
                        + " mips=" + n.getMips()
                        + " cost=" + n.getCostPerSec()
                        + " power=" + n.getPowerPerSec()
                        + " pos=(" + String.format("%.2f", n.getX()) + "," + String.format("%.2f", n.getY()) + ")"
                );
            }
        }
    }

    /**
     * Affiche un contrôle de cohérence du modèle réseau en testant quelques liens.
     *
     * @param nodes Liste des noeuds
     * @param net Modèle de réseau
     */
    public static void printNetworkSanity(List<Node> nodes, NetworkModel net) {
        Node edge1 = null, fog1 = null, cloud1 = null;

        for (Node n : nodes) {
            if ("edge1".equals(n.getId())) edge1 = n;
            if ("fog1".equals(n.getId())) fog1 = n;
            if ("cloud1".equals(n.getId())) cloud1 = n;
        }

        if (edge1 == null || fog1 == null || cloud1 == null) return;

        System.out.println("\nNetwork sanity check (example links):");

        double dEF = edge1.distanceTo(fog1);
        double lEF = net.getLatency(edge1.getId(), fog1.getId());
        double bEF = net.getBandwidth(edge1.getId(), fog1.getId());

        System.out.println("  edge1 -> fog1 : distKm=" + String.format("%.2f", dEF)
                + " latencySec=" + String.format("%.6f", lEF)
                + " bwMBps=" + String.format("%.2f", bEF));

        double dFC = fog1.distanceTo(cloud1);
        double lFC = net.getLatency(fog1.getId(), cloud1.getId());
        double bFC = net.getBandwidth(fog1.getId(), cloud1.getId());

        System.out.println("  fog1  -> cloud1: distKm=" + String.format("%.2f", dFC)
                + " latencySec=" + String.format("%.6f", lFC)
                + " bwMBps=" + String.format("%.2f", bFC));

        double dEC = edge1.distanceTo(cloud1);
        double lEC = net.getLatency(edge1.getId(), cloud1.getId());
        double bEC = net.getBandwidth(edge1.getId(), cloud1.getId());

        System.out.println("  edge1 -> cloud1: distKm=" + String.format("%.2f", dEC)
                + " latencySec=" + String.format("%.6f", lEC)
                + " bwMBps=" + String.format("%.2f", bEC));
    }
}
