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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MOJellyfishOptimizer {

    // Paramètres de l’algorithme
    private final int populationSize;
    private final int maxIter;
    private final int archiveMaxSize;

    // ++ exploration / exploitation
    private final double mutationRate       = 0.20;  // mutation plus forte
    private final double restartRatio       = 0.25;  // pour les restarts
    private final int    stagnationLimit    = 6;     // plus réactif

    // Local search élitiste
    private final double eliteRatio             = 0.15; // fraction de la pop améliorée
    private final double localSearchTasksRatio  = 0.10; // % de tâches modifiées

    private final Random rand = new Random();

    private final List<Task> tasks;
    private final List<Node> nodes;
    private final NetworkModel network;

    // historique d’hypervolume pour le tracé (monotone)
    private final List<Double> hypervolumeHistory = new ArrayList<>();
    public List<Double> getHypervolumeHistory() { return hypervolumeHistory; }

    public MOJellyfishOptimizer(List<Task> tasks,
                                List<Node> nodes,
                                NetworkModel network,
                                int populationSize,
                                int maxIter,
                                int archiveMaxSize) {
        this.tasks = tasks;
        this.nodes = nodes;
        this.network = network;
        this.populationSize = populationSize;
        this.maxIter = maxIter;
        this.archiveMaxSize = archiveMaxSize;
    }

    // ----------------------------------------------------------------------
    // Construction / évaluation d’une solution
    // ----------------------------------------------------------------------

    private SchedulingSolution randomSolution() {
        int[] assign = new int[tasks.size()];
        for (int i = 0; i < assign.length; i++) {
            assign[i] = rand.nextInt(nodes.size());
        }
        return new SchedulingSolution(assign);
    }

    private void evaluate(SchedulingSolution sol) {
        Map<String, String> assignmentMap =
                Utils.convert(sol.getAssignment(), tasks, nodes);
        Simulator.SimulationResult r =
                Simulator.simulate(tasks, nodes, assignmentMap, network);
        sol.setObjectives(r.getMakespan(), r.getTotalCost(), r.getTotalEnergy());
    }

    // ----------------------------------------------------------------------
    // Algorithme Jellyfish boosté
    // ----------------------------------------------------------------------

    public List<SchedulingSolution> run(double[] refPoint) {

        // Population initiale
        List<SchedulingSolution> population = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            SchedulingSolution s = randomSolution();
            evaluate(s);
            population.add(s);
        }

        // Archive Pareto
        List<SchedulingSolution> archive =
                ParetoUtils.updateArchive(new ArrayList<>(), population, archiveMaxSize);

        double bestHvSoFar = 0.0;
        int stagnationCounter = 0;

        for (int iter = 1; iter <= maxIter; iter++) {

            double t = (double) iter / maxIter;           // temps normalisé [0,1]

            double[] meanPos = computeMeanPosition(population);
            SchedulingSolution guide =
                    selectLeader(archive, population);
            double[] guidePos = toDoubleArray(guide.getAssignment());

            List<SchedulingSolution> newPop = new ArrayList<>();

            for (int i = 0; i < populationSize; i++) {
                SchedulingSolution current = population.get(i);
                double[] curPos = toDoubleArray(current.getAssignment());
                double[] newPos = new double[curPos.length];

                // probabilité d’être en phase ACTIVE augmente avec t
                double activeProb = 0.3 + 0.7 * t; // plus explorateur au début
                boolean activePhase = rand.nextDouble() < activeProb;

                if (!activePhase) {
                    // Phase passive : attraction vers le centre + bruit Lévy
                    for (int d = 0; d < newPos.length; d++) {
                        double r1 = rand.nextDouble();
                        double drift = r1 * (meanPos[d] - curPos[d]);
                        double noise = 0.25 * levySmall();   // exploration plus forte
                        newPos[d] = curPos[d] + drift + noise;
                    }
                } else {
                    // Phase active : soit vers le guide global, soit vers une autre méduse
                    boolean towardGuide = rand.nextDouble() < 0.6;

                    if (towardGuide) {
                        // mouvement vers un leader (archive)
                        for (int d = 0; d < newPos.length; d++) {
                            double r2 = rand.nextDouble();
                            double step = (0.4 + 0.3 * t) * r2 * (guidePos[d] - curPos[d]);
                            newPos[d] = curPos[d] + step;
                        }
                    } else {
                        // interaction avec une autre solution de la population
                        SchedulingSolution other =
                                population.get(rand.nextInt(populationSize));
                        double[] otherPos = toDoubleArray(other.getAssignment());
                        for (int d = 0; d < newPos.length; d++) {
                            double r3 = rand.nextDouble();
                            double diff = otherPos[d] - curPos[d];
                            double oscillation = 0.35 * levySmall() * Math.signum(diff);
                            newPos[d] = curPos[d] + r3 * diff + oscillation;
                        }
                    }
                }

                // Discrétisation + mutation
                int[] discrete = discretize(newPos, nodes.size());
                mutate(discrete);

                SchedulingSolution child = new SchedulingSolution(discrete);
                evaluate(child);
                newPop.add(child);
            }

            // Local search sur les élites de newPop
            applyLocalSearch(newPop);

            population = newPop;
            archive = ParetoUtils.updateArchive(archive, population, archiveMaxSize);

            // Hypervolume (monotone)
            double hv = ParetoMetrics.hypervolume(archive, refPoint);
            if (hv > bestHvSoFar) {
                bestHvSoFar = hv;
            }
            hypervolumeHistory.add(bestHvSoFar);

            // Détection stagnation réelle (sur HV brut)
            if (hv > bestHvSoFar + 1e-6) {
                stagnationCounter = 0;
            } else {
                stagnationCounter++;
            }

            // mini-restart si stagnation prolongée
            if (stagnationCounter >= stagnationLimit) {
                partialRestart(population);
                stagnationCounter = 0;
            }
        }

        return archive;
    }

    // ----------------------------------------------------------------------
    // Local search élitiste
    // ----------------------------------------------------------------------

    private void applyLocalSearch(List<SchedulingSolution> population) {
        int eliteCount = Math.max(1, (int) Math.round(population.size() * eliteRatio));

        // trier par score scalaire (plus petit = meilleur)
        population.sort(Comparator.comparingDouble(this::scalarScore));

        for (int i = 0; i < eliteCount; i++) {
            SchedulingSolution improved = localSearch(population.get(i));
            population.set(i, improved);
        }
    }

    private SchedulingSolution localSearch(SchedulingSolution base) {
        // copie de l’assignement
        int[] bestAssign = base.getAssignment().clone();
        SchedulingSolution best = new SchedulingSolution(bestAssign);
        best.setObjectives(base.getF1(), base.getF2(), base.getF3());
        double bestScore = scalarScore(best);

        int moves = Math.max(1, (int) Math.round(tasks.size() * localSearchTasksRatio));

        for (int m = 0; m < moves; m++) {
            int[] candidateAssign = bestAssign.clone();
            int taskIndex = rand.nextInt(candidateAssign.length);
            int newNode = rand.nextInt(nodes.size());
            candidateAssign[taskIndex] = newNode;

            SchedulingSolution cand = new SchedulingSolution(candidateAssign);
            evaluate(cand);
            double sc = scalarScore(cand);

            if (sc < bestScore) {
                best = cand;
                bestAssign = candidateAssign;
                bestScore = sc;
            }
        }
        return best;
    }

    // ----------------------------------------------------------------------
    // Outils numériques
    // ----------------------------------------------------------------------

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

    private void mutate(int[] assign) {
        for (int i = 0; i < assign.length; i++) {
            if (rand.nextDouble() < mutationRate) {
                assign[i] = rand.nextInt(nodes.size());
            }
        }
    }

    private void partialRestart(List<SchedulingSolution> pop) {
        int count = Math.max(1, (int) Math.round(pop.size() * restartRatio));
        for (int c = 0; c < count; c++) {
            int idx = rand.nextInt(pop.size());
            SchedulingSolution s = randomSolution();
            evaluate(s);
            pop.set(idx, s);
        }
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

    // sélection d’un leader (tournoi dans l’archive)
    private SchedulingSolution selectLeader(List<SchedulingSolution> archive,
                                            List<SchedulingSolution> population) {
        List<SchedulingSolution> pool =
                archive.isEmpty() ? population : archive;

        SchedulingSolution best = pool.get(rand.nextInt(pool.size()));
        double bestScore = scalarScore(best);

        int tournamentSize = Math.min(3, pool.size());
        for (int k = 1; k < tournamentSize; k++) {
            SchedulingSolution cand = pool.get(rand.nextInt(pool.size()));
            double sc = scalarScore(cand);
            if (sc < bestScore) {
                best = cand;
                bestScore = sc;
            }
        }
        return best;
    }

    // agrégation simple des 3 objectifs (à ajuster si besoin)
    private double scalarScore(SchedulingSolution s) {
        return s.getF1() + 1000.0 * s.getF2() + 0.01 * s.getF3();
    }

    // pas Lévy "boosté"
    private double levySmall() {
        double u = rand.nextGaussian();
        double v = rand.nextGaussian();
        double beta = 1.5;
        double sigma = Math.pow(
                gamma(1 + beta) * Math.sin(Math.PI * beta / 2) /
                        (gamma((1 + beta) / 2) * beta * Math.pow(2, (beta - 1) / 2)),
                1.0 / beta
        );
        double step = u * sigma / Math.pow(Math.abs(v) + 1e-12, 1.0 / beta);
        return step;
    }

    // Gamma approchée (Lanczos)
    private double gamma(double x) {
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
        return Math.sqrt(2 * Math.PI) *
                Math.pow(t, x + 0.5) *
                Math.exp(-t) * a;
    }
}
