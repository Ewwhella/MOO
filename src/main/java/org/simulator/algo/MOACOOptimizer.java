package org.simulator.algo;

import org.simulator.core.NetworkModel;
import org.simulator.core.Node;
import org.simulator.core.SchedulingSolution;
import org.simulator.core.Task;
import org.simulator.eval.ParetoMetrics;
import org.simulator.eval.ParetoUtils;
import org.simulator.sim.Simulator;
import org.simulator.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Multi-Objective Ant Colony Optimization (MO-ACO)
 * Compatible avec ton architecture :
 * - une solution = int[] assignment (comme Jellyfish)
 * - evaluation via Simulator
 * - archive via ParetoUtils
 */
public class MOACOOptimizer {

    private final List<Task> tasks;
    private final List<Node> nodes;
    private final NetworkModel network;

    private final int antCount;
    private final int maxIter;
    private final int archiveMaxSize;

    private final double evaporation;

    private final double[][] pheromone;    // pheromone[tâche][nodeIndex]

    private final List<Double> hypervolumeHistory = new ArrayList<>();
    public List<Double> getHypervolumeHistory() { return hypervolumeHistory; }

    private final Random rand = new Random();

    public MOACOOptimizer(
            List<Task> tasks,
            List<Node> nodes,
            NetworkModel network,
            int antCount,
            int maxIter,
            int archiveMaxSize,
            double evaporation,
            double initialPheromone
    ) {
        this.tasks = tasks;
        this.nodes = nodes;
        this.network = network;
        this.antCount = antCount;
        this.maxIter = maxIter;
        this.archiveMaxSize = archiveMaxSize;
        this.evaporation = evaporation;

        pheromone = new double[tasks.size()][nodes.size()];
        for (int i = 0; i < tasks.size(); i++) {
            for (int k = 0; k < nodes.size(); k++) {
                pheromone[i][k] = initialPheromone;
            }
        }
    }

    private SchedulingSolution constructSolution() {
        int[] assign = new int[tasks.size()];

        for (int i = 0; i < tasks.size(); i++) {
            double sum = 0.0;
            for (int k = 0; k < nodes.size(); k++) {
                sum += pheromone[i][k];
            }

            double r = rand.nextDouble() * sum;
            double acc = 0.0;

            int choice = 0;
            for (int k = 0; k < nodes.size(); k++) {
                acc += pheromone[i][k];
                if (r <= acc) {
                    choice = k;
                    break;
                }
            }
            assign[i] = choice;
        }

        return new SchedulingSolution(assign);
    }

    private void evaluate(SchedulingSolution sol) {
        Map<String, String> assignment = Utils.convert(sol.getAssignment(), tasks, nodes);
        Simulator.SimulationResult r = Simulator.simulate(tasks, nodes, assignment, network);
        sol.setObjectives(r.getMakespan(), r.getTotalCost(), r.getTotalEnergy());
    }

    private void evaporate() {
        for (int i = 0; i < tasks.size(); i++) {
            for (int k = 0; k < nodes.size(); k++) {
                pheromone[i][k] *= (1.0 - evaporation);
                if (pheromone[i][k] < 1e-6) pheromone[i][k] = 1e-6;
            }
        }
    }

    private void deposit(List<SchedulingSolution> archive) {
        if (archive.isEmpty()) return;

        for (SchedulingSolution s : archive) {
            double q = 1.0 / (1.0 + s.getF1() + 100 * s.getF2() + 0.01 * s.getF3());

            int[] assign = s.getAssignment();
            for (int i = 0; i < assign.length; i++) {
                int nodeIndex = assign[i];
                pheromone[i][nodeIndex] += q;
            }
        }
    }

    public List<SchedulingSolution> run(double[] refPoint) {

        List<SchedulingSolution> archive = new ArrayList<>();

        for (int iter = 1; iter <= maxIter; iter++) {

            List<SchedulingSolution> ants = new ArrayList<>();

            for (int a = 0; a < antCount; a++) {
                SchedulingSolution sol = constructSolution();
                evaluate(sol);
                ants.add(sol);
            }

            archive = ParetoUtils.updateArchive(archive, ants, archiveMaxSize);

            // === TRACK HYPERVOLUME ===
            double hv = ParetoMetrics.hypervolume(archive, refPoint);
            hypervolumeHistory.add(hv);

            evaporate();
            deposit(archive);
        }

        return archive;
    }
}
