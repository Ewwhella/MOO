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
import org.simulator.sim.TopologyBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class App {


    // Choix du scénario

    // false -> workflow barrage (Dam SHM)
    // true  -> workflow CyberShake
    private static final boolean USE_CYBERSHAKE = true;

    // Taille du workflow CyberShake (30, 50, 100 ou 1000)
    private static final int CYBERSHAKE_SIZE = 1000;

    // Seed (reproductibilité)
    private static final long SEED = 42L;

    public static void main(String[] args) {

        System.out.println("Workflow Simulator – Multi-Objective Scheduling");

        // 1. Nodes (EDGE, FOG, CLOUD)

        List<Node> nodes = new ArrayList<>();

        Random rnd = new Random(SEED);

        // Zones (coordonnées abstraites en "km")
        double damX = 0.0;
        double damY = 0.0;

        double fogX = 30.0;
        double fogY = 10.0;

        double cloudX = 300.0;
        double cloudY = 200.0;

        // Jitter de placement (km)
        double edgeJitter = 2.0;
        double fogJitter = 5.0;
        double cloudJitter = 20.0;

        // Edge : 5 machines
        for (int i = 1; i <= 5; i++) {
            double x = damX + (rnd.nextDouble() * 2.0 - 1.0) * edgeJitter;
            double y = damY + (rnd.nextDouble() * 2.0 - 1.0) * edgeJitter;

            nodes.add(new Node(
                    "edge" + i,
                    Node.Type.EDGE,
                    1000.0,    // MIPS
                    0.5,       // coût(€)/secondes
                    0.700,     // énergie (W) = 700 mW
                    x,
                    y,
                    "EDGE_DAM"
            ));
        }

        // Fog : 5 machines
        for (int i = 1; i <= 5; i++) {
            double x = fogX + (rnd.nextDouble() * 2.0 - 1.0) * fogJitter;
            double y = fogY + (rnd.nextDouble() * 2.0 - 1.0) * fogJitter;

            nodes.add(new Node(
                    "fog" + i,
                    Node.Type.FOG,
                    1300.0,
                    0.48,
                    0.700,     // 700 mW
                    x,
                    y,
                    "FOG_CONTROL"
            ));
        }

        // Cloud : 5 machines
        for (int i = 1; i <= 5; i++) {
            double x = cloudX + (rnd.nextDouble() * 2.0 - 1.0) * cloudJitter;
            double y = cloudY + (rnd.nextDouble() * 2.0 - 1.0) * cloudJitter;

            nodes.add(new Node(
                    "cloud" + i,
                    Node.Type.CLOUD,
                    1600.0,
                    0.96,
                    1.648,     // 1648 mW
                    x,
                    y,
                    "CLOUD_REGION"
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

        TopologyBuilder.Params tp = new TopologyBuilder.Params();
        tp.seed = SEED;

        tp.propagationSpeedKmPerSec = 200000.0;

        tp.baseSameTier = 0.002;
        tp.baseEdgeFog = 0.005;
        tp.baseFogCloud = 0.020;
        tp.baseEdgeCloud = 0.050;

        tp.bwSameTierMBps = 800.0;
        tp.bwEdgeFogMBps = 200.0;
        tp.bwFogCloudMBps = 500.0;
        tp.bwEdgeCloudMBps = 100.0;

        tp.jitterMaxSec = 0.0;

        NetworkModel net = TopologyBuilder.build(nodes, tp);

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
