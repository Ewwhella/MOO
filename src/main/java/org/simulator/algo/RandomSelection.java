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

    public RandomSelection(List<Task> tasks, List<Node> nodes, NetworkModel net, int sampleCount) {
        this.tasks = tasks;
        this.nodes = nodes;
        this.net = net;
        this.sampleCount = sampleCount;
    }

    public List<SchedulingSolution> run() {

        List<SchedulingSolution> sols = new ArrayList<>();
        Random rd = new Random();

        for (int s = 0; s < sampleCount; s++) {

            int[] assign = new int[tasks.size()];
            for (int i = 0; i < tasks.size(); i++) {
                assign[i] = rd.nextInt(nodes.size());
            }

            Map<String,String> map = Utils.convert(assign, tasks, nodes);

            Simulator.SimulationResult r =
                    Simulator.simulate(tasks, nodes, map, net);

            SchedulingSolution sol = new SchedulingSolution(assign);
            sol.setObjectives(r.getMakespan(), r.getTotalCost(), r.getTotalEnergy());

            sols.add(sol);
        }

        return ParetoUtils.updateArchive(new ArrayList<>(), sols, 50);
    }
}
