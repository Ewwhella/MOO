package org.simulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GreedyAlgorithm {

    private final List<Task> tasks;
    private final List<Node> nodes;
    private final NetworkModel net;

    public GreedyAlgorithm(List<Task> tasks, List<Node> nodes, NetworkModel net) {
        this.tasks = tasks;
        this.nodes = nodes;
        this.net = net;
    }

    public List<SchedulingSolution> run() {

        int[] assign = new int[tasks.size()];

        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);

            double bestScore = Double.POSITIVE_INFINITY;
            int bestNode = 0;

            for (int n = 0; n < nodes.size(); n++) {
                Node node = nodes.get(n);

                double exec = t.getWorkMI() / node.getMips();
                double cost = exec * node.getCostPerSec();
                double energy = exec * node.getPowerPerSec();

                double score = exec + cost + energy;

                if (score < bestScore) {
                    bestScore = score;
                    bestNode = n;
                }
            }

            assign[i] = bestNode;
        }

        Map<String,String> map = Utils.convert(assign, tasks, nodes);

        Simulator.SimulationResult r =
                Simulator.simulate(tasks, nodes, map, net);

        SchedulingSolution sol = new SchedulingSolution(assign);
        sol.setObjectives(r.getMakespan(), r.getTotalCost(), r.getTotalEnergy());

        List<SchedulingSolution> list = new ArrayList<>();
        list.add(sol);
        return list;
    }
}
