package org.simulator;

import org.simulator.algo.GreedyAlgorithm;
import org.simulator.algo.MOACOOptimizer;
import org.simulator.algo.MOJellyfishOptimizer;
import org.simulator.algo.RandomSelection;
import org.simulator.core.*;
import org.simulator.eval.ModelingUtils;
import org.simulator.eval.ParetoMetrics;

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
        // 2. WORKFLOW CHOICE (DAM SHM / CYBERSHAKE100)
        // =========================================================

        // true  -> CyberShake100
        // false -> Workflow barrage initial
        boolean useRealDax = true;

        List<Task> tasks;

        if (useRealDax) {
            tasks = Workflows.loadCyberShakeFromDax("cybershake100.dax");
            System.out.println("Loaded workflow: CyberShake100 (REAL DAX)");
            System.out.println("Task count = " + tasks.size());
        } else {
            tasks = Workflows.createCyberShake100();  // version approximative
        }


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

        double[] refPoint = {10000000.0, 500.0, 200000.0};

        MOJellyfishOptimizer mojs = new MOJellyfishOptimizer(tasks, nodes, net, 40, 60, 50);
        List<SchedulingSolution> paretoJS = mojs.run(refPoint);
        ModelingUtils.exportHypervolumeCSV(mojs.getHypervolumeHistory(), "hv_mojs.csv");

        MOACOOptimizer aco = new MOACOOptimizer(tasks, nodes, net, 40, 60, 50, 0.1, 1.0);
        List<SchedulingSolution> paretoACO = aco.run(refPoint);
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
