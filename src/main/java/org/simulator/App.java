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
import org.simulator.sim.TopologyScenario;
import org.simulator.util.RunConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.simulator.eval.ModelingUtils.printNodeSummary;
import static org.simulator.eval.ModelingUtils.printNetworkSanity;
import static org.simulator.sim.TopologyScenario.resolveScenarios;
import static org.simulator.util.LoadConfig.loadYaml;
import static org.simulator.util.PythonUtils.*;
import static org.simulator.util.Utils.*;

public class App {

    // Chemin du fichier YAML
    private static final String DEFAULT_CONFIG_PATH = "configs/experiment.yaml";

    // Dossiers
    private static final String RESULTS_ROOT = "results";
    private static final String PY_SCRIPT_PATH_PRIMARY = "scripts/plot_pareto.py";
    private static final String PY_SCRIPT_PATH_FALLBACK = "plot_pareto.py";
    private static final String PY_AGGREGATE_SCRIPT = "scripts/aggregate_results.py";

    public static void main(String[] args) {

        // 0) Load config
        String configPath = (args.length > 0) ? args[0] : DEFAULT_CONFIG_PATH;
        RunConfig cfg = loadYaml(configPath);

        // 1) Workflow
        boolean useCyberShake = "CYBERSHAKE".equalsIgnoreCase(cfg.workflow.type);
        int cyberSize = cfg.workflow.cybershake_size;

        String workflowTag = useCyberShake ? ("CyberShake-" + cyberSize) : "DamSHM";
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String experimentFolderName = "exp_" + workflowTag + "_" + ts;

        // 2) Execution
        long baseSeed = cfg.execution.base_seed;
        int runsPerScenario = cfg.execution.runs_per_scenario;

        // 3) Network
        double propagationSpeedKmPerSec = cfg.network.propagation_speed_km_per_sec;

        boolean enableNetworkVariability = cfg.network.variability.enabled;
        double networkJitterMaxSec = cfg.network.variability.latency_jitter_max_sec;
        double networkBwJitterRatio = cfg.network.variability.bandwidth_jitter_ratio;

        // 4) Scenarios
        List<TopologyScenario> scenarios = resolveScenarios(cfg.scenarios);

        System.out.println("Workflow Simulator – Multi-Objective Scheduling (Batch)");
        System.out.println("Config: " + Paths.get(configPath).toAbsolutePath());
        System.out.println("Workflow: " + workflowTag);
        System.out.println("Experiment: " + experimentFolderName);
        System.out.println("Base seed: " + baseSeed);
        System.out.println("Runs/scenario: " + runsPerScenario);
        System.out.println("Network variability: " + (enableNetworkVariability
                ? ("ON (jitterMaxSec=" + networkJitterMaxSec + "s, bwJitterRatio=" + networkBwJitterRatio + ")")
                : "OFF"));

        long globalStart = System.nanoTime();

        // 0) Load workflow
        List<Task> tasks;
        if (!useCyberShake) {
            tasks = Workflows.createDamShmWorkflow();
            System.out.println("\nLoaded workflow: Dam SHM (barrage)");
            System.out.println("Task count = " + tasks.size());
        } else {
            tasks = Workflows.loadCyberShake(cyberSize);
            System.out.println("\nLoaded workflow: CyberShake-" + cyberSize);
            System.out.println("Task count = " + tasks.size());
        }

        ensureDir(Paths.get(RESULTS_ROOT));

        Path experimentRoot = Paths.get(RESULTS_ROOT, experimentFolderName);
        ensureDir(experimentRoot);

        System.out.println("\nResults root = " + experimentRoot.toAbsolutePath());

        // Python script path
        Path pyScript = pickPythonScript(Paths.get(PY_SCRIPT_PATH_PRIMARY), Paths.get(PY_SCRIPT_PATH_FALLBACK));
        boolean canPlot = (pyScript != null);
        if (!canPlot) {
            System.out.println("[WARN] plot_pareto.py not found (expected at '" + PY_SCRIPT_PATH_PRIMARY
                    + "' or '" + PY_SCRIPT_PATH_FALLBACK + "'). PNG generation will be skipped.");
        } else {
            System.out.println("[INFO] Using plot script: " + pyScript.toAbsolutePath());
        }

        // 1) Boucle scénarios
        for (TopologyScenario scenario : scenarios) {

            System.out.println("\n==============================================");
            System.out.println("TOPOLOGY SCENARIO: " + scenario.name());
            System.out.println("Zone centers (km): dam=(" + scenario.damX + "," + scenario.damY + "), fog=("
                    + scenario.fogX + "," + scenario.fogY + "), cloud=(" + scenario.cloudX + "," + scenario.cloudY + ")");
            System.out.println("Placement jitter (km): edge=" + scenario.edgeJitterKm + ", fog=" + scenario.fogJitterKm + ", cloud=" + scenario.cloudJitterKm);
            System.out.println("==============================================");

            Path scenarioDir = experimentRoot.resolve(scenario.name());
            ensureDir(scenarioDir);

            // summary CSV
            Path summaryCsv = scenarioDir.resolve("summary.csv");
            initScenarioSummary(summaryCsv);

            // 2) Runs multiples
            for (int runIdx = 1; runIdx <= runsPerScenario; runIdx++) {

                long runSeed = baseSeed + (runIdx - 1);
                Path runDir = scenarioDir.resolve(String.format("run_%02d_seed_%d", runIdx, runSeed));
                ensureDir(runDir);

                System.out.println("\n--- RUN " + runIdx + "/" + runsPerScenario
                        + " | scenario=" + scenario.name()
                        + " | seed=" + runSeed
                        + " | out=" + runDir.toAbsolutePath());

                long t0 = System.nanoTime();

                // 1. Nodes (EDGE, FOG, CLOUD)
                List<Node> nodes = buildNodesForScenario(scenario, runSeed, cfg);

                System.out.println("\nNodes created: " + nodes.size()
                        + " (edge=" + cfg.nodes.edge.count
                        + ", fog=" + cfg.nodes.fog.count
                        + ", cloud=" + cfg.nodes.cloud.count + ")");
                printNodeSummary(nodes);

                // 2. Network Model (latence géographique + variabilité optionnelle)
                TopologyBuilder.Params tp = new TopologyBuilder.Params();
                tp.seed = runSeed;
                tp.propagationSpeedKmPerSec = propagationSpeedKmPerSec;

                // presets latences/bw par scénario
                scenario.applyTo(tp);

                if (enableNetworkVariability) {
                    tp.jitterMaxSec = networkJitterMaxSec;
                    tp.bwJitterRatio = networkBwJitterRatio;
                } else {
                    tp.jitterMaxSec = 0.0;
                    tp.bwJitterRatio = 0.0;
                }

                NetworkModel net = TopologyBuilder.build(nodes, tp);

                System.out.println("\nNetwork model built:");
                System.out.println("  propagationSpeedKmPerSec = " + tp.propagationSpeedKmPerSec);
                System.out.println("  baseLatenciesSec = {sameTier=" + tp.baseSameTier
                        + ", edgeFog=" + tp.baseEdgeFog
                        + ", fogCloud=" + tp.baseFogCloud
                        + ", edgeCloud=" + tp.baseEdgeCloud + "}");
                System.out.println("  bandwidthMBps = {sameTier=" + tp.bwSameTierMBps
                        + ", edgeFog=" + tp.bwEdgeFogMBps
                        + ", fogCloud=" + tp.bwFogCloudMBps
                        + ", edgeCloud=" + tp.bwEdgeCloudMBps + "}");
                System.out.println("  jitterMaxSec = " + tp.jitterMaxSec);
                System.out.println("  bwJitterRatio = " + tp.bwJitterRatio);

                printNetworkSanity(nodes, net);

                // 3. RefPoint
                double[] refPoint = ParetoMetrics.computeAutoRefPoint(tasks, nodes, net);
                System.out.println("\nAuto-refPoint = ["
                        + refPoint[0] + ", "
                        + refPoint[1] + ", "
                        + refPoint[2] + "]");

                // 4. Algorithmes
                System.out.println("\nRunning MOJS...");
                long start = System.nanoTime();
                MOJellyfishOptimizer mojs =
                        new MOJellyfishOptimizer(tasks, nodes, net,
                                40,
                                60,
                                50);
                List<SchedulingSolution> paretoJS = mojs.run(refPoint);
                double mojsSec = secondsSince(start);
                System.out.println("MOJS done in " + String.format("%.3f s", mojsSec));
                ModelingUtils.exportHypervolumeCSV(mojs.getHypervolumeHistory(), runDir.resolve("hv_mojs.csv").toString());

                System.out.println("\nRunning MO-ACO...");
                start = System.nanoTime();
                MOACOOptimizer aco =
                        new MOACOOptimizer(tasks, nodes, net,
                                40,
                                60,
                                50,
                                0.1,
                                1.0);
                List<SchedulingSolution> paretoACO = aco.run(refPoint);
                double acoSec = secondsSince(start);
                System.out.println("MO-ACO done in " + String.format("%.3f s", acoSec));
                ModelingUtils.exportHypervolumeCSV(aco.getHypervolumeHistory(), runDir.resolve("hv_aco.csv").toString());

                System.out.println("\nRunning RANDOM baseline...");
                start = System.nanoTime();
                RandomSelection randomSel = new RandomSelection(tasks, nodes, net, 100, 50, new java.util.Random(42));
                List<SchedulingSolution> paretoRandom = randomSel.run();
                double randomSec = secondsSince(start);
                System.out.println("RANDOM done in " + String.format("%.3f s", randomSec));

                System.out.println("\nRunning GREEDY baseline...");
                start = System.nanoTime();
                GreedyAlgorithm greedy = new GreedyAlgorithm(tasks, nodes, net);
                List<SchedulingSolution> paretoGreedy = greedy.run();
                double greedySec = secondsSince(start);
                System.out.println("GREEDY done in " + String.format("%.3f s", greedySec));

                // 5. Exports CSV dans le dossier du run
                ParetoMetrics.exportCSV(paretoJS, runDir.resolve("pareto_mojs.csv").toString());
                ParetoMetrics.exportCSV(paretoACO, runDir.resolve("pareto_aco.csv").toString());
                ParetoMetrics.exportCSV(paretoRandom, runDir.resolve("pareto_random.csv").toString());
                ParetoMetrics.exportCSV(paretoGreedy, runDir.resolve("pareto_greedy.csv").toString());

                System.out.println("\nCSV exported for all fronts -> " + runDir.toAbsolutePath());

                // 6. Résumé + métriques
                double hvJS = ParetoMetrics.hypervolume(paretoJS, refPoint);
                double hvACO = ParetoMetrics.hypervolume(paretoACO, refPoint);
                double hvR = ParetoMetrics.hypervolume(paretoRandom, refPoint);
                double hvG = ParetoMetrics.hypervolume(paretoGreedy, refPoint);

                double totalRunSec = secondsSince(t0);

                appendScenarioSummary(summaryCsv,
                        runIdx, runSeed,
                        refPoint,
                        paretoJS.size(), paretoACO.size(), paretoRandom.size(), paretoGreedy.size(),
                        hvJS, hvACO, hvR, hvG,
                        mojsSec, acoSec, randomSec, greedySec,
                        totalRunSec
                );

                // 7. Plots python dans le dossier run
                if (canPlot) {
                    boolean ok = runPythonPlot(runDir, pyScript);
                    if (!ok) {
                        System.out.println("[WARN] Plot failed for: " + runDir.toAbsolutePath());
                    }
                }

                System.out.println("\nRun total runtime = " + String.format("%.3f s", totalRunSec));
            }

            System.out.println("\nScenario done: " + scenario.name());
            System.out.println("Summary -> " + summaryCsv.toAbsolutePath());
        }

        System.out.println("\nAll scenarios completed.");
        System.out.println("Global runtime = " + formatSeconds(globalStart));
        System.out.println("Results root = " + experimentRoot.toAbsolutePath());

        // 8) Agrégation globale
        Path aggregateScript = Paths.get(PY_AGGREGATE_SCRIPT);
        if (Files.exists(aggregateScript)) {
            System.out.println("\nRunning global aggregation on experiment folder...");
            boolean ok = runPythonAggregate(experimentRoot, aggregateScript);
            if (!ok) {
                System.out.println("[WARN] Aggregation failed for: " + experimentRoot.toAbsolutePath());
            }
        } else {
            System.out.println("[WARN] aggregate_results.py not found (expected at '"
                    + PY_AGGREGATE_SCRIPT + "'). Aggregation will be skipped.");
        }
    }

    // =========================
    // Nodes builder
    // =========================
    private static List<Node> buildNodesForScenario(TopologyScenario scenario, long seed, RunConfig cfg) {

        List<Node> nodes = new ArrayList<>();
        Random rnd = new Random(seed);

        double damX = scenario.damX;
        double damY = scenario.damY;

        double fogX = scenario.fogX;
        double fogY = scenario.fogY;

        double cloudX = scenario.cloudX;
        double cloudY = scenario.cloudY;

        double edgeJitter = scenario.edgeJitterKm;
        double fogJitter = scenario.fogJitterKm;
        double cloudJitter = scenario.cloudJitterKm;

        // Edge
        for (int i = 1; i <= cfg.nodes.edge.count; i++) {
            double x = damX + (rnd.nextDouble() * 2.0 - 1.0) * edgeJitter;
            double y = damY + (rnd.nextDouble() * 2.0 - 1.0) * edgeJitter;

            nodes.add(new Node(
                    "edge" + i,
                    Node.Type.EDGE,
                    cfg.nodes.edge.mips,
                    cfg.nodes.edge.cost,
                    cfg.nodes.edge.power,
                    x,
                    y,
                    cfg.nodes.edge.zone
            ));
        }

        // Fog
        for (int i = 1; i <= cfg.nodes.fog.count; i++) {
            double x = fogX + (rnd.nextDouble() * 2.0 - 1.0) * fogJitter;
            double y = fogY + (rnd.nextDouble() * 2.0 - 1.0) * fogJitter;

            nodes.add(new Node(
                    "fog" + i,
                    Node.Type.FOG,
                    cfg.nodes.fog.mips,
                    cfg.nodes.fog.cost,
                    cfg.nodes.fog.power,
                    x,
                    y,
                    cfg.nodes.fog.zone
            ));
        }

        // Cloud
        for (int i = 1; i <= cfg.nodes.cloud.count; i++) {
            double x = cloudX + (rnd.nextDouble() * 2.0 - 1.0) * cloudJitter;
            double y = cloudY + (rnd.nextDouble() * 2.0 - 1.0) * cloudJitter;

            nodes.add(new Node(
                    "cloud" + i,
                    Node.Type.CLOUD,
                    cfg.nodes.cloud.mips,
                    cfg.nodes.cloud.cost,
                    cfg.nodes.cloud.power,
                    x,
                    y,
                    cfg.nodes.cloud.zone
            ));
        }

        return nodes;
    }
}
