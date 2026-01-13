package org.simulator;

import org.simulator.algo.GreedyAlgorithm;
import org.simulator.algo.MOACOOptimizer;
import org.simulator.algo.MOJellyfishOptimizer;
import org.simulator.algo.RandomSelection;
import org.simulator.core.NetworkModel;
import org.simulator.core.Node;
import org.simulator.core.SchedulingSolution;
import org.simulator.core.Task;
import org.simulator.core.Workflows;
import org.simulator.eval.ModelingUtils;
import org.simulator.eval.ParetoMetrics;

import java.util.ArrayList;
import java.util.List;

public class App {


    // Choix du scénario

    // false -> workflow barrage (Dam SHM)
    // true  -> workflow CyberShake
    private static final boolean USE_CYBERSHAKE = true;

    // Taille du workflow CyberShake (30, 50 ou 100)
    private static final int CYBERSHAKE_SIZE = 50;

    public static void main(String[] args) {

        System.out.println("Workflow Simulator – Multi-Objective Scheduling");

        // 1. Nodes (EDGE, FOG, CLOUD)

        List<Node> nodes = new ArrayList<>();

        // Edge : 5 machines
        for (int i = 1; i <= 5; i++) {
            nodes.add(new Node(
                    "edge" + i,
                    Node.Type.EDGE,
                    1000.0,    // MIPS
                    0.0,       // coût(€)/secondes
                    0.700      // énergie (W) = 700 mW
            ));
        }

        // Fog : 5 machines
        for (int i = 1; i <= 5; i++) {
            nodes.add(new Node(
                    "fog" + i,
                    Node.Type.FOG,
                    1300.0,
                    0.48,
                    0.700      // 700 mW
            ));
        }

        // Cloud : 5 machines
        for (int i = 1; i <= 5; i++) {
            nodes.add(new Node(
                    "cloud" + i,
                    Node.Type.CLOUD,
                    1600.0,
                    0.96,
                    1.648      // 1648 mW
            ));
        }

        // 2. Workflow : Dam SHM ou CyberShake-30/50/100

        List<Task> tasks;

        if (!USE_CYBERSHAKE) {
            // Workflow barrage initial
            tasks = Workflows.createDamShmWorkflow();
            System.out.println("Loaded workflow: Dam SHM (barrage)");
            System.out.println("Task count = " + tasks.size());
        } else {
            // CyberShake XML original (Simulateur FogWorkflowSim) :
            // CyberShake_30.xml, CyberShake_50.xml, CyberShake_100.xml
            tasks = Workflows.loadCyberShake(CYBERSHAKE_SIZE);
            System.out.println("Loaded workflow: CyberShake-" + CYBERSHAKE_SIZE);
            System.out.println("Task count = " + tasks.size());
        }


        // 3. Network Model

        NetworkModel net = new NetworkModel();

        // Edge <-> Fog
        for (Node e : nodes) if (e.isEdge()) {
            for (Node f : nodes) if (f.isFog()) {
                net.setLink(e.getId(), f.getId(),
                        0.01,      // latence (s)
                        20480.0);  // bande passante (MB/s) environ 20480 Mbps
                net.setLink(f.getId(), e.getId(),
                        0.01,
                        20480.0);
            }
        }

        // Fog <-> Cloud
        for (Node f : nodes) if (f.isFog()) {
            for (Node c : nodes) if (c.isCloud()) {
                net.setLink(f.getId(), c.getId(),
                        0.05,      // latence (s)
                        10000.0);  // bande passante (MB/s)
                net.setLink(c.getId(), f.getId(),
                        0.05,
                        10000.0);
            }
        }

        // Edge <-> Cloud (lien plus faible)
        for (Node e : nodes) if (e.isEdge()) {
            for (Node c : nodes) if (c.isCloud()) {
                net.setLink(e.getId(), c.getId(),
                        0.10,
                        100.0);    // MB/s
                net.setLink(c.getId(), e.getId(),
                        0.10,
                        100.0);
            }
        }

        // 4. refPoint (calculé automatiquement)

        double[] refPoint = ParetoMetrics.computeAutoRefPoint(tasks, nodes, net);
        System.out.println("Auto-refPoint = ["
                + refPoint[0] + ", "
                + refPoint[1] + ", "
                + refPoint[2] + "]");

        // 5. Métaheuristiques — MOJS + MO-ACO

        MOJellyfishOptimizer mojs =
                new MOJellyfishOptimizer(tasks, nodes, net,
                        40,   // taille de la population
                        60,   // itérations (générations)
                        50);  // taille de l'archive

        List<SchedulingSolution> paretoJS = mojs.run(refPoint);
        ModelingUtils.exportHypervolumeCSV(mojs.getHypervolumeHistory(), "hv_mojs.csv");

        MOACOOptimizer aco =
                new MOACOOptimizer(tasks, nodes, net,
                        40,   // taille de la population
                        60,   // itérations (générations)
                        50,   // taille de l'archive
                        0.1,  // évaporation
                        1.0); // phéromones initiaux

        List<SchedulingSolution> paretoACO = aco.run(refPoint);
        ModelingUtils.exportHypervolumeCSV(aco.getHypervolumeHistory(), "hv_aco.csv");

        // 6. Algorithmes naïfs

        // Random selection
        RandomSelection randomSel = new RandomSelection(tasks, nodes, net, 100, 50, new java.util.Random(42));
        List<SchedulingSolution> paretoRandom = randomSel.run();

        // Greedy algorithm
        GreedyAlgorithm greedy = new GreedyAlgorithm(tasks, nodes, net);
        List<SchedulingSolution> paretoGreedy = greedy.run();


        // 6. Affichage des fronts de Pareto

        ModelingUtils.printPareto("MOJS", paretoJS);
        ModelingUtils.printPareto("MO-ACO", paretoACO);
        ModelingUtils.printPareto("RANDOM", paretoRandom);
        ModelingUtils.printPareto("GREEDY", paretoGreedy);

        // 7. Métriques de performance

        ModelingUtils.printMetrics(
                paretoJS, paretoACO, paretoRandom, paretoGreedy, refPoint
        );

        // CSV
        ParetoMetrics.exportCSV(paretoJS, "pareto_mojs.csv");
        ParetoMetrics.exportCSV(paretoACO, "pareto_aco.csv");
        ParetoMetrics.exportCSV(paretoRandom, "pareto_random.csv");
        ParetoMetrics.exportCSV(paretoGreedy, "pareto_greedy.csv");

        System.out.println("\nCSV exported for all fronts.");

        // 8. Affichage

        String workflowName = "!!À DÉFINIR!!" + tasks.size();
        if(USE_CYBERSHAKE) {
            workflowName = "CyberShake" + tasks.size();
        }
        ModelingUtils.runPythonPlot(workflowName);
    }
}
