package org.simulator;

import java.util.List;

public class App
{
    public static void main( String[] args )
    {

        System.out.println("Workflow Simulator – Dam SHM Workflow (Edge–Fog–Cloud)");

        // =========================================================
        // 1. NODES (EDGE, FOG, CLOUD)
        // =========================================================

        Node edge = new Node(
                "edge1",
                Node.Type.EDGE,
                1500,      // MIPS (lent mais très économe)
                0.00045,   // coût / sec
                15         // W (faible énergie)
        );

        Node fog = new Node(
                "fog1",
                Node.Type.FOG,
                6000,      // MIPS (milieu)
                0.00120,   // coût / sec (le moins cher par MI)
                90         // W (moyenne énergie)
        );

        Node cloud = new Node(
                "cloud1",
                Node.Type.CLOUD,
                20000,     // MIPS (le plus rapide)
                0.01000,   // coût / sec (le plus cher)
                600        // W (le plus énergivore)
        );

        List<Node> nodes = List.of(edge, fog, cloud);

        // =========================================================
        // 2. WORKFLOW TASKS (T = {1..n}) – SHM Barrage
        // =========================================================

        Task acqVib  = new Task("acq_vibration",   9000, 12);
        Task acqAco  = new Task("acq_acoustic",   11000, 15);
        Task acqPres = new Task("acq_pressure",    7000,  8);

        Task filtVib  = new Task("filter_vibration", 6000, 6);
        Task filtAco  = new Task("filter_acoustic",  7000, 7);
        Task filtPres = new Task("filter_pressure",  5000, 5);

        Task fftVib   = new Task("fft_vibration",  14000, 4);
        Task fftAco   = new Task("fft_acoustic",   16000, 4);
        Task featPres = new Task("feat_pressure",   8000, 3);

        Task fusion    = new Task("fusion",        10000, 5);
        Task detection = new Task("detection",     25000, 2);
        Task decision  = new Task("decision",       4000, 0);

        // =========================================================
        // 3. DEPENDENCIES — DAG (E = arcs i → j)
        // =========================================================

        filtVib.addPredecessor(acqVib);
        filtAco.addPredecessor(acqAco);
        filtPres.addPredecessor(acqPres);

        fftVib.addPredecessor(filtVib);
        fftAco.addPredecessor(filtAco);
        featPres.addPredecessor(filtPres);

        fusion.addPredecessor(fftVib);
        fusion.addPredecessor(fftAco);
        fusion.addPredecessor(featPres);

        detection.addPredecessor(fusion);
        decision.addPredecessor(detection);

        List<Task> tasks = List.of(
                acqVib, acqAco, acqPres,
                filtVib, filtAco, filtPres,
                fftVib, fftAco, featPres,
                fusion,
                detection,
                decision
        );

        // =========================================================
        // 4. Topological Sort (obligatoire pour le simulateur)
        // =========================================================
        tasks = Utils.topoSort(tasks);

        // =========================================================
        // 5. NETWORK MODEL – (λkl, bkl)
        // =========================================================

        NetworkModel net = new NetworkModel();

        // edge <-> fog (proche du capteur)
        net.setLink("edge1", "fog1", 0.01, 50);
        net.setLink("fog1",  "edge1", 0.01, 50);

        // fog <-> cloud (backbone)
        net.setLink("fog1",   "cloud1", 0.05, 100);
        net.setLink("cloud1", "fog1",   0.05, 100);

        // edge <-> cloud (longue distance)
        net.setLink("edge1",  "cloud1", 0.10, 20);
        net.setLink("cloud1", "edge1",  0.10, 20);

        // =========================================================
        // 6. METAHEURISTIC (Jellyfish MOO)
        // =========================================================

        MOJellyfishOptimizer mojs = new MOJellyfishOptimizer(
                tasks,
                nodes,
                net,
                40,     // population
                60,     // iterations
                50      // archive max
        );

        List<JellyfishSolution> pareto = mojs.run();

        // =========================================================
        // 7. SECOND METAHEURISTIC (MO-ACO)
        // =========================================================

        MOACOOptimizer aco = new MOACOOptimizer(
                tasks,
                nodes,
                net,
                40,     // ant count
                60,     // iterations
                50,     // archive max
                0.1,    // evaporation
                1.0     // initial pheromone
        );

        List<JellyfishSolution> paretoACO = aco.run();

        // =========================================================
        // 8. CLEAN PARETO OUTPUT
        // =========================================================

        System.out.println("\n=== PARETO SOLUTIONS (MOJS) ===");
        System.out.printf("%-4s %-12s %-15s %-12s%n",
                "#", "Makespan", "Cost", "Energy");
        System.out.println("---------------------------------------------------------");

        int idx = 1;
        for (JellyfishSolution s : pareto) {
            System.out.printf(
                    "%-4d %-12.3f %-15.6f %-12.3f%n",
                    idx++,
                    s.getF1(),
                    s.getF2(),
                    s.getF3()
            );
        }
        System.out.println("\n=== PARETO SOLUTIONS (MO-ACO) ===");
        System.out.printf("%-4s %-12s %-15s %-12s%n",
                "#", "Makespan", "Cost", "Energy");
        System.out.println("---------------------------------------------------------");

        idx = 1;
        for (JellyfishSolution s : paretoACO) {
            System.out.printf(
                    "%-4d %-12.3f %-15.6f %-12.3f%n",
                    idx++,
                    s.getF1(),
                    s.getF2(),
                    s.getF3()
            );
        }
        // =========================================================
        //  PERFORMANCE METRICS (Hypervolume + Spacing)
        // =========================================================

        // Point de référence dominé par toutes les solutions (à adapter si nécessaire)
        double[] refPoint = {100.0, 1.0, 5000.0};

        // Hypervolume
        double hvJS  = ParetoMetrics.hypervolume(pareto, refPoint);
        double hvACO = ParetoMetrics.hypervolume(paretoACO, refPoint);

        // Spacing
        double spJS  = ParetoMetrics.spacing(pareto);
        double spACO = ParetoMetrics.spacing(paretoACO);

        System.out.println("\n=== PERFORMANCE METRICS ===");
        System.out.println("Hypervolume (MOJS):  " + hvJS);
        System.out.println("Hypervolume (ACO):   " + hvACO);
        System.out.println("Spacing (MOJS):      " + spJS);
        System.out.println("Spacing (ACO):       " + spACO);

        // Export CSV
        ParetoMetrics.exportCSV(pareto,    "pareto_mojs.csv");
        ParetoMetrics.exportCSV(paretoACO, "pareto_aco.csv");

        System.out.println("\nCSV exported: pareto_mojs.csv, pareto_aco.csv");


    }
}
