package org.simulator;

import java.util.List;


public class App {

    public static void main(String[] args) {

        System.out.println("Workflow Simulator – Dam SHM Workflow (Edge–Fog–Cloud)");

        // =========================================================
        // 1. NODES (EDGE, FOG, CLOUD)
        // =========================================================

        Node edge = new Node("edge1", Node.Type.EDGE, 1500, 0.00045, 15);
        Node fog = new Node("fog1", Node.Type.FOG, 6000, 0.00120, 90);
        Node cloud = new Node("cloud1", Node.Type.CLOUD, 20000, 0.01000, 600);

        List<Node> nodes = List.of(edge, fog, cloud);

        // =========================================================
        // 2. WORKFLOW TASKS
        // =========================================================
        Task acqVib = new Task("acq_vibration", 9000, 12);
        Task acqAco = new Task("acq_acoustic", 11000, 15);
        Task acqPres = new Task("acq_pressure", 7000, 8);

        Task filtVib = new Task("filter_vibration", 6000, 6);
        Task filtAco = new Task("filter_acoustic", 7000, 7);
        Task filtPres = new Task("filter_pressure", 5000, 5);

        Task fftVib = new Task("fft_vibration", 14000, 4);
        Task fftAco = new Task("fft_acoustic", 16000, 4);
        Task featPres = new Task("feat_pressure", 8000, 3);

        Task fusion = new Task("fusion", 10000, 5);
        Task detection = new Task("detection", 25000, 2);
        Task decision = new Task("decision", 4000, 0);

        // DAG
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
                fusion, detection, decision
        );

        tasks = Utils.topoSort(tasks);

        // =========================================================
        // 3. NETWORK MODEL
        // =========================================================

        NetworkModel net = new NetworkModel();

        net.setLink("edge1", "fog1", 0.01, 50);
        net.setLink("fog1", "edge1", 0.01, 50);

        net.setLink("fog1", "cloud1", 0.05, 100);
        net.setLink("cloud1", "fog1", 0.05, 100);

        net.setLink("edge1", "cloud1", 0.10, 20);
        net.setLink("cloud1", "edge1", 0.10, 20);

        // =========================================================
        // 4. METAHEURISTICS — MOJS + MO-ACO
        // =========================================================

        MOJellyfishOptimizer mojs = new MOJellyfishOptimizer(tasks, nodes, net, 40, 60, 50);
        List<SchedulingSolution> paretoJS = mojs.run();
        ModelingUtils.exportHypervolumeCSV(mojs.getHypervolumeHistory(), "hv_mojs.csv");

        MOACOOptimizer aco = new MOACOOptimizer(tasks, nodes, net, 40, 60, 50, 0.1, 1.0);
        List<SchedulingSolution> paretoACO = aco.run();
        ModelingUtils.exportHypervolumeCSV(aco.getHypervolumeHistory(), "hv_aco.csv");

        // =========================================================
        // 5. BASELINES
        // =========================================================

        // BASELINE 1 : RANDOM SELECTION
        RandomSelection randomSel = new RandomSelection(tasks, nodes, net, 100);
        List<SchedulingSolution> paretoRandom = randomSel.run();

        // BASELINE 2 : GREEDY SELECTION
        GreedyAlgorithm greedy = new GreedyAlgorithm(tasks, nodes, net);
        List<SchedulingSolution> paretoGreedy = greedy.run();


        // =========================================================
        // 6. DISPLAY PARETO FRONTS
        // =========================================================

        ModelingUtils.printPareto("MOJS", paretoJS);
        ModelingUtils.printPareto("MO-ACO", paretoACO);
        ModelingUtils.printPareto("RANDOM", paretoRandom);
        ModelingUtils.printPareto("GREEDY", paretoGreedy);

        // =========================================================
        // 7. PERFORMANCE METRICS
        // =========================================================

        double[] refPoint = {100.0, 1.0, 5000.0};

        ModelingUtils.printMetrics(paretoJS, paretoACO, paretoRandom, paretoGreedy, refPoint);

        // CSV
        ParetoMetrics.exportCSV(paretoJS, "pareto_mojs.csv");
        ParetoMetrics.exportCSV(paretoACO, "pareto_aco.csv");
        ParetoMetrics.exportCSV(paretoRandom, "pareto_random.csv");
        ParetoMetrics.exportCSV(paretoGreedy, "pareto_greedy.csv");

        System.out.println("\nCSV exported for all fronts.");

        // =========================================================
        // 9. MODELING
        // =========================================================
        ModelingUtils.runPythonPlot();
    }
}
