package org.simulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

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

    // ============================
    // Initialisation population
    // ============================
    private SchedulingSolution randomSolution() {
        int[] assign = new int[tasks.size()];
        for (int i = 0; i < assign.length; i++) {
            assign[i] = rand.nextInt(nodes.size());
        }
        return new SchedulingSolution(assign);
    }

    private void evaluate(SchedulingSolution sol) {
        Map<String, String> assignmentMap = Utils.convert(sol.getAssignment(), tasks, nodes);
        Simulator.SimulationResult r = Simulator.simulate(tasks, nodes, assignmentMap, network);
        sol.setObjectives(r.getMakespan(), r.getTotalCost(), r.getTotalEnergy());
    }

    // ============================
    // Jellyfish Search crédible (Option 2)
    // ============================
    public List<SchedulingSolution> run() {

        // Population initiale
        List<SchedulingSolution> population = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            SchedulingSolution s = randomSolution();
            evaluate(s);
            population.add(s);
        }

        // Archive Pareto initiale
        List<SchedulingSolution> archive =
                ParetoUtils.updateArchive(new ArrayList<>(), population, archiveMaxSize);

        // Boucle principale
        for (int iter = 1; iter <= maxIter; iter++) {

            double tNorm = (double) iter / maxIter;

            // Centre du banc (moyenne des positions)
            double[] meanPos = computeMeanPosition(population);

            // "Meilleure" solution (scalaire simple sur f1,f2,f3)
            SchedulingSolution best = selectBestScalar(archive.isEmpty() ? population : archive);
            double[] bestPos = toDoubleArray(best.getAssignment());

            List<SchedulingSolution> newPop = new ArrayList<>();

            for (int i = 0; i < populationSize; i++) {
                SchedulingSolution current = population.get(i);
                double[] curPos = toDoubleArray(current.getAssignment());
                double[] newPos = new double[curPos.length];

                boolean activePhase = rand.nextDouble() < tNorm; // début : plutôt passif, fin : plutôt actif

                if (!activePhase) {
                    // ============================
                    // Phase PASSIVE
                    // ============================
                    // X_{t+1} = X_t + r * (X_mean - X_t) + petite perturbation
                    for (int d = 0; d < newPos.length; d++) {
                        double r1 = rand.nextDouble();
                        double drift = r1 * (meanPos[d] - curPos[d]);
                        double noise = levySmall() * (rand.nextDouble() - 0.5);
                        newPos[d] = curPos[d] + drift + noise;
                    }
                } else {
                    // ============================
                    // Phase ACTIVE
                    // ============================
                    // Deux modes : se diriger vers "best" ou interagir avec une autre méduse
                    boolean towardBest = rand.nextBoolean();

                    if (towardBest) {
                        // Mode courant / attraction vers best
                        // X_{t+1} = X_t + r * (X_best - X_t)
                        for (int d = 0; d < newPos.length; d++) {
                            double r2 = rand.nextDouble();
                            double currentTerm = r2 * (bestPos[d] - curPos[d]);
                            newPos[d] = curPos[d] + currentTerm;
                        }
                    } else {
                        // Mode oscillatoire entre deux solutions
                        SchedulingSolution other = population.get(rand.nextInt(populationSize));
                        double[] otherPos = toDoubleArray(other.getAssignment());

                        for (int d = 0; d < newPos.length; d++) {
                            double r3 = rand.nextDouble();
                            double diff = otherPos[d] - curPos[d];
                            double osc = levySmall() * Math.signum(diff);
                            newPos[d] = curPos[d] + r3 * diff + osc;
                        }
                    }
                }

                // Discrétisation vers indices de nœuds valides
                int[] disc = discretize(newPos, nodes.size());

                SchedulingSolution child = new SchedulingSolution(disc);
                evaluate(child);
                newPop.add(child);
            }

            population = newPop;

            archive = ParetoUtils.updateArchive(archive, population, archiveMaxSize);
        }

        return archive;
    }

    // ============================
    // Helpers numériques
    // ============================

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

    private double[] computeMeanPosition(List<SchedulingSolution> pop) {
        int dim = tasks.size();
        double[] mean = new double[dim];

        if (pop.isEmpty()) return mean;

        for (SchedulingSolution s : pop) {
            int[] a = s.getAssignment();
            for (int d = 0; d < dim; d++) {
                mean[d] += a[d];
            }
        }
        for (int d = 0; d < dim; d++) {
            mean[d] /= pop.size();
        }
        return mean;
    }

    // Sélection scalaire simple pour choisir un "best" parmi archive/population
    private SchedulingSolution selectBestScalar(List<SchedulingSolution> sols) {
        SchedulingSolution best = sols.get(0);
        double bestScore = scalarScore(best);

        for (int i = 1; i < sols.size(); i++) {
            double sc = scalarScore(sols.get(i));
            if (sc < bestScore) {
                bestScore = sc;
                best = sols.get(i);
            }
        }
        return best;
    }

    // Combinaison simple des 3 objectifs (pour guider le courant)
    private double scalarScore(SchedulingSolution s) {
        return s.getF1() + 1000.0 * s.getF2() + 0.01 * s.getF3();
    }

    // "Lévy-like" simple : petit pas lourd-taillé
    private double levySmall() {
        double u = rand.nextGaussian();
        double v = rand.nextGaussian();
        double beta = 1.5; // paramètre Lévy
        double sigma = Math.pow(
                gamma(1 + beta) * Math.sin(Math.PI * beta / 2) /
                        (gamma((1 + beta) / 2) * beta * Math.pow(2, (beta - 1) / 2))
                , 1.0 / beta);
        double step = u * sigma / Math.pow(Math.abs(v), 1.0 / beta);
        // on réduit fortement l'amplitude pour rester local
        return 0.1 * step;
    }

    // Approximation Gamma simple pour beta=1.5 (pas critique ici)
    private double gamma(double x) {
        // Approximation de Lanczos très grossière mais suffisante pour ce contexte
        double[] p = {
                676.5203681218851,
                -1259.1392167224028,
                771.32342877765313,
                -176.61502916214059,
                12.507343278686905,
                -0.13857109526572012,
                9.9843695780195716e-6,
                1.5056327351493116e-7
        };
        int g = 7;
        if (x < 0.5) {
            return Math.PI / (Math.sin(Math.PI * x) * gamma(1 - x));
        }
        x -= 1;
        double a = 0.99999999999980993;
        for (int i = 0; i < p.length; i++) {
            a += p[i] / (x + i + 1);
        }
        double t = x + g + 0.5;
        return Math.sqrt(2 * Math.PI) * Math.pow(t, x + 0.5) * Math.exp(-t) * a;
    }
}
