package org.simulator.algo;

import org.simulator.sim.Simulator;
import org.simulator.core.NetworkModel;
import org.simulator.core.Node;
import org.simulator.core.SchedulingSolution;
import org.simulator.core.Task;
import org.simulator.eval.ParetoUtils;
import org.simulator.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RandomSelection {

    private final List<Task> tasks;
    private final List<Node> nodes;
    private final NetworkModel net;

    private final int sampleCount;
    private final int archiveMaxSize;

    // Random injecté pour reproductibilité
    private final Random rand;

    public RandomSelection(List<Task> tasks,
                           List<Node> nodes,
                           NetworkModel net,
                           int sampleCount,
                           int archiveMaxSize,
                           Random rand) {
        this.tasks = tasks;
        this.nodes = nodes;
        this.net = net;
        this.sampleCount = sampleCount;
        this.archiveMaxSize = archiveMaxSize;
        this.rand = (rand == null) ? new Random() : rand;
    }
    public RandomSelection(List<Task> tasks,
                           List<Node> nodes,
                           NetworkModel net,
                           int sampleCount,
                           int archiveMaxSize) {
        this(tasks, nodes, net, sampleCount, archiveMaxSize, new Random(42)); // seed par défaut
    }

    public List<SchedulingSolution> run() {

        List<SchedulingSolution> sols = new ArrayList<>();

        for (int s = 0; s < sampleCount; s++) {

            int[] assign = new int[tasks.size()];
            for (int i = 0; i < tasks.size(); i++) {
                assign[i] = rand.nextInt(nodes.size());
            }

            Map<String, String> map = Utils.convert(assign, tasks, nodes);

            Simulator.SimulationResult r =
                    Simulator.simulate(tasks, nodes, map, net);

            SchedulingSolution sol = new SchedulingSolution(assign);
            sol.setObjectives(r.getMakespan(), r.getTotalCost(), r.getTotalEnergy());

            sols.add(sol);
        }

        // Archive Pareto avec taille modifiable dans la config
        return ParetoUtils.updateArchive(new ArrayList<>(), sols, archiveMaxSize);
    }
}
