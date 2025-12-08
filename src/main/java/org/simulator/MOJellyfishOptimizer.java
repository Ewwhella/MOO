package org.simulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Map;

public class MOJellyfishOptimizer {

    private final int populationSize;
    private final int maxIter;
    private final int archiveMaxSize;
    private final Random rand = new Random();

    private final List<Task> tasks;
    private final List<Node> nodes;
    private final NetworkModel network;

    public MOJellyfishOptimizer(List<Task> tasks, List<Node> nodes, NetworkModel network,
                                int populationSize, int maxIter, int archiveMaxSize) {
        this.tasks = tasks;
        this.nodes = nodes;
        this.network = network;
        this.populationSize = populationSize;
        this.maxIter = maxIter;
        this.archiveMaxSize = archiveMaxSize;
    }

    private JellyfishSolution randomSolution() {
        int[] assign = new int[tasks.size()];
        for (int i = 0; i < assign.length; i++) {
            assign[i] = rand.nextInt(nodes.size());
        }
        return new JellyfishSolution(assign);
    }

    private void evaluate(JellyfishSolution sol) {
        Map<String, String> assignmentMap = Utils.convert(sol.getAssignment(), tasks, nodes);

        Simulator.SimulationResult r = Simulator.simulate(tasks, nodes, assignmentMap, network);

        sol.setObjectives(r.getMakespan(), r.getTotalCost(), r.getTotalEnergy());
    }

    public List<JellyfishSolution> run() {

        List<JellyfishSolution> population = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            JellyfishSolution s = randomSolution();
            evaluate(s);
            population.add(s);
        }

        List<JellyfishSolution> archive = ParetoUtils.updateArchive(new ArrayList<>(), population, archiveMaxSize);

        for (int iter = 1; iter <= maxIter; iter++) {

            double tNorm = (double) iter / maxIter;

            List<JellyfishSolution> newPop = new ArrayList<>();

            for (JellyfishSolution sol : population) {

                JellyfishSolution leader = archive.get(rand.nextInt(archive.size()));

                double[] newPos = new double[sol.getAssignment().length];
                double[] curPos = toDoubleArray(sol.getAssignment());
                double[] leadPos = toDoubleArray(leader.getAssignment());

                boolean passive = rand.nextDouble() > tNorm;

                if (passive) {
                    for (int d = 0; d < newPos.length; d++) {
                        newPos[d] = curPos[d] +
                                rand.nextDouble() * (leadPos[d] - curPos[d]);
                    }
                } else {
                    JellyfishSolution other = population.get(rand.nextInt(populationSize));
                    double[] otherPos = toDoubleArray(other.getAssignment());
                    for (int d = 0; d < newPos.length; d++) {
                        newPos[d] = curPos[d] + (rand.nextDouble() * (otherPos[d] - curPos[d]));
                    }
                }

                int[] disc = discretize(newPos, nodes.size());

                JellyfishSolution child = new JellyfishSolution(disc);
                evaluate(child);
                newPop.add(child);
            }

            population = newPop;

            archive = ParetoUtils.updateArchive(archive, population, archiveMaxSize);
        }

        return archive;
    }

    private double[] toDoubleArray(int[] a) {
        double[] x = new double[a.length];
        for (int i = 0; i < a.length; i++) x[i] = a[i];
        return x;
    }

    private int[] discretize(double[] pos, int nodeCount) {
        int[] r = new int[pos.length];
        for (int i = 0; i < pos.length; i++) {
            int v = (int) Math.round(pos[i]);
            if (v < 0) v = 0;
            if (v >= nodeCount) v = nodeCount - 1;
            r[i] = v;
        }
        return r;
    }
}
