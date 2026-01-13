package org.simulator.algo;

import org.simulator.core.NetworkModel;
import org.simulator.core.Node;
import org.simulator.core.SchedulingSolution;
import org.simulator.core.Task;
import org.simulator.sim.Simulator;
import org.simulator.util.Utils;

import java.util.*;

/**
 * MOJS (Multi-Objective Jellyfish Search) from scratch:
 * - Representation: int[] assignment, assignment[i] = node index for task i
 * - Explicit objectives: (f1,f2,f3) via Simulator.simulate(...)
 * - Pareto archive: non-dominated set + truncation by crowding distance
 * - Optional hypervolume history: exact HV for 3 objectives (minimization) with a ref point
 *
 * No dependency on ParetoUtils / ParetoMetrics.
 */
public class MOJellyfishOptimizer {

    // Core parameters
    private final int populationSize;
    private final int maxIter;
    private final int archiveMaxSize;

    // Exploration / exploitation
    private final double mutationRate = 0.20;
    private final double restartRatio = 0.25;
    private final int stagnationLimit = 6;

    // Local search
    private final double eliteRatio = 0.15;
    private final double localSearchTasksRatio = 0.10;

    private final Random rand;

    private final List<Task> tasks;
    private final List<Node> nodes;
    private final NetworkModel network;

    private final List<Double> hypervolumeHistory = new ArrayList<>();
    public List<Double> getHypervolumeHistory() { return hypervolumeHistory; }

    public MOJellyfishOptimizer(List<Task> tasks,
                                List<Node> nodes,
                                NetworkModel network,
                                int populationSize,
                                int maxIter,
                                int archiveMaxSize) {
        this(tasks, nodes, network, populationSize, maxIter, archiveMaxSize, new Random());
    }

    public MOJellyfishOptimizer(List<Task> tasks,
                                List<Node> nodes,
                                NetworkModel network,
                                int populationSize,
                                int maxIter,
                                int archiveMaxSize,
                                Random rand) {
        this.tasks = tasks;
        this.nodes = nodes;
        this.network = network;
        this.populationSize = populationSize;
        this.maxIter = maxIter;
        this.archiveMaxSize = archiveMaxSize;
        this.rand = rand == null ? new Random() : rand;
    }

    // -----------------------------
    // Public API
    // -----------------------------

    /**
     * @param refPoint reference point for hypervolume in minimization space: [refF1, refF2, refF3]
     *                 Set to null if you do not want HV history.
     */
    public List<SchedulingSolution> run(double[] refPoint) {

        // Init population
        List<SchedulingSolution> population = new ArrayList<>(populationSize);
        for (int i = 0; i < populationSize; i++) {
            SchedulingSolution s = randomSolution();
            evaluate(s);
            population.add(s);
        }

        // Init archive
        List<SchedulingSolution> archive = updateArchive(Collections.emptyList(), population, archiveMaxSize);

        double bestHvSoFar = 0.0;
        int stagnationCounter = 0;

        for (int iter = 1; iter <= maxIter; iter++) {

            double t = (double) iter / (double) maxIter; // normalized time [0,1]

            double[] meanPos = computeMeanPosition(population);
            SchedulingSolution leader = selectLeader(archive, population);
            double[] leaderPos = toDoubleArray(leader.getAssignment());

            List<SchedulingSolution> newPop = new ArrayList<>(populationSize);

            for (int i = 0; i < populationSize; i++) {
                SchedulingSolution current = population.get(i);
                double[] curPos = toDoubleArray(current.getAssignment());
                double[] newPos = new double[curPos.length];

                double activeProb = 0.3 + 0.7 * t;
                boolean activePhase = rand.nextDouble() < activeProb;

                if (!activePhase) {
                    // Passive: drift to mean + Lévy noise
                    for (int d = 0; d < newPos.length; d++) {
                        double r1 = rand.nextDouble();
                        double drift = r1 * (meanPos[d] - curPos[d]);
                        double noise = 0.25 * levySmall();
                        newPos[d] = curPos[d] + drift + noise;
                    }
                } else {
                    boolean towardLeader = rand.nextDouble() < 0.6;
                    if (towardLeader) {
                        // Active: move toward leader
                        for (int d = 0; d < newPos.length; d++) {
                            double r2 = rand.nextDouble();
                            double step = (0.4 + 0.3 * t) * r2 * (leaderPos[d] - curPos[d]);
                            newPos[d] = curPos[d] + step;
                        }
                    } else {
                        // Active: interact with another jellyfish
                        SchedulingSolution other = population.get(rand.nextInt(populationSize));
                        double[] otherPos = toDoubleArray(other.getAssignment());
                        for (int d = 0; d < newPos.length; d++) {
                            double r3 = rand.nextDouble();
                            double diff = otherPos[d] - curPos[d];
                            double oscillation = 0.35 * levySmall() * Math.signum(diff);
                            newPos[d] = curPos[d] + r3 * diff + oscillation;
                        }
                    }
                }

                int[] discrete = discretize(newPos, nodes.size());
                mutate(discrete);

                SchedulingSolution child = new SchedulingSolution(discrete);
                evaluate(child);
                newPop.add(child);
            }

            // Local search on elites
            applyLocalSearch(newPop);

            population = newPop;

            // Update archive
            List<SchedulingSolution> oldArchive = archive;
            archive = updateArchive(oldArchive, population, archiveMaxSize);

            // Hypervolume history (optional)
            if (refPoint != null && refPoint.length == 3) {
                double hv = hypervolume3DMin(archive, refPoint);
                if (hv > bestHvSoFar) bestHvSoFar = hv;
                hypervolumeHistory.add(bestHvSoFar);

                // Stagnation detection on raw HV
                if (hv > bestHvSoFar + 1e-9) stagnationCounter = 0;
                else stagnationCounter++;

                if (stagnationCounter >= stagnationLimit) {
                    partialRestart(population);
                    stagnationCounter = 0;
                }
            } else {
                // still allow restart based on "no archive improvement" proxy
                boolean changed = (archive.size() != oldArchive.size());
                if (changed) stagnationCounter = 0;
                else stagnationCounter++;
                if (stagnationCounter >= stagnationLimit) {
                    partialRestart(population);
                    stagnationCounter = 0;
                }
            }
        }

        return archive;
    }

    // -----------------------------
    // Solution building + evaluation
    // -----------------------------

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

    // -----------------------------
    // Pareto: dominance + archive
    // -----------------------------

    private boolean dominates(SchedulingSolution a, SchedulingSolution b) {
        // Minimization on f1,f2,f3
        boolean strictlyBetter = false;

        if (a.getF1() > b.getF1()) return false;
        if (a.getF2() > b.getF2()) return false;
        if (a.getF3() > b.getF3()) return false;

        if (a.getF1() < b.getF1()) strictlyBetter = true;
        if (a.getF2() < b.getF2()) strictlyBetter = true;
        if (a.getF3() < b.getF3()) strictlyBetter = true;

        return strictlyBetter;
    }

    private boolean sameAssignment(SchedulingSolution a, SchedulingSolution b) {
        int[] x = a.getAssignment();
        int[] y = b.getAssignment();
        if (x.length != y.length) return false;
        for (int i = 0; i < x.length; i++) if (x[i] != y[i]) return false;
        return true;
    }

    private List<SchedulingSolution> updateArchive(List<SchedulingSolution> archive,
                                                   List<SchedulingSolution> candidates,
                                                   int maxSize) {
        List<SchedulingSolution> merged = new ArrayList<>(
                (archive == null ? 0 : archive.size()) + (candidates == null ? 0 : candidates.size())
        );
        if (archive != null) merged.addAll(archive);
        if (candidates != null) merged.addAll(candidates);

        // Remove exact duplicates (by assignment). Keeps first occurrence.
        merged = removeDuplicateAssignments(merged);

        // Keep only non-dominated
        List<SchedulingSolution> nd = getNonDominated(merged);

        if (nd.size() <= maxSize) return nd;

        // Truncate by crowding distance (bigger is better)
        Map<SchedulingSolution, Double> crowd = crowdingDistance(nd);
        nd.sort((s1, s2) -> Double.compare(crowd.getOrDefault(s2, 0.0), crowd.getOrDefault(s1, 0.0)));
        return new ArrayList<>(nd.subList(0, maxSize));
    }

    private List<SchedulingSolution> removeDuplicateAssignments(List<SchedulingSolution> sols) {
        List<SchedulingSolution> out = new ArrayList<>();
        for (SchedulingSolution s : sols) {
            boolean exists = false;
            for (SchedulingSolution t : out) {
                if (sameAssignment(s, t)) { exists = true; break; }
            }
            if (!exists) out.add(s);
        }
        return out;
    }

    private List<SchedulingSolution> getNonDominated(List<SchedulingSolution> sols) {
        List<SchedulingSolution> nd = new ArrayList<>();
        for (int i = 0; i < sols.size(); i++) {
            SchedulingSolution s = sols.get(i);
            boolean dominated = false;
            for (int j = 0; j < sols.size(); j++) {
                if (i == j) continue;
                if (dominates(sols.get(j), s)) { dominated = true; break; }
            }
            if (!dominated) nd.add(s);
        }
        return nd;
    }

    private Map<SchedulingSolution, Double> crowdingDistance(List<SchedulingSolution> front) {
        Map<SchedulingSolution, Double> dist = new HashMap<>();
        for (SchedulingSolution s : front) dist.put(s, 0.0);

        if (front.size() <= 2) {
            for (SchedulingSolution s : front) dist.put(s, Double.POSITIVE_INFINITY);
            return dist;
        }

        crowdOnObjective(front, dist, 1);
        crowdOnObjective(front, dist, 2);
        crowdOnObjective(front, dist, 3);

        return dist;
    }

    private void crowdOnObjective(List<SchedulingSolution> front,
                                  Map<SchedulingSolution, Double> dist,
                                  int objIdx) {
        front.sort(Comparator.comparingDouble(s -> obj(s, objIdx)));

        int n = front.size();
        SchedulingSolution minS = front.get(0);
        SchedulingSolution maxS = front.get(n - 1);

        dist.put(minS, Double.POSITIVE_INFINITY);
        dist.put(maxS, Double.POSITIVE_INFINITY);

        double min = obj(minS, objIdx);
        double max = obj(maxS, objIdx);
        double range = max - min;
        if (Math.abs(range) < 1e-12) return;

        for (int i = 1; i < n - 1; i++) {
            SchedulingSolution s = front.get(i);
            if (Double.isInfinite(dist.get(s))) continue;
            double prev = obj(front.get(i - 1), objIdx);
            double next = obj(front.get(i + 1), objIdx);
            double add = (next - prev) / range;
            dist.put(s, dist.get(s) + add);
        }
    }

    private double obj(SchedulingSolution s, int idx) {
        if (idx == 1) return s.getF1();
        if (idx == 2) return s.getF2();
        return s.getF3();
    }

    // -----------------------------
    // Hypervolume (exact) in 3D minimization
    // -----------------------------

    /**
     * Exact hypervolume for 3 objectives with minimization.
     * Uses transformation to maximization: (x,y,z) = (ref - f).
     * Computes union of boxes from origin to each point in transformed space.
     */
    private double hypervolume3DMin(List<SchedulingSolution> archive, double[] ref) {
        if (archive == null || archive.isEmpty()) return 0.0;

        // Keep only points strictly within ref (otherwise contribute 0 or negative)
        List<double[]> pts = new ArrayList<>();
        for (SchedulingSolution s : archive) {
            double x = ref[0] - s.getF1();
            double y = ref[1] - s.getF2();
            double z = ref[2] - s.getF3();
            if (x > 0 && y > 0 && z > 0) pts.add(new double[]{x, y, z});
        }
        if (pts.isEmpty()) return 0.0;

        // Remove dominated in transformed maximization space
        pts = nonDominatedMax(pts);

        // Sort by x descending (bigger x = better)
        pts.sort((a, b) -> Double.compare(b[0], a[0]));

        double hv = 0.0;

        for (int i = 0; i < pts.size(); i++) {
            double x_i = pts.get(i)[0];
            double x_next = (i + 1 < pts.size()) ? pts.get(i + 1)[0] : 0.0;
            double dx = x_i - x_next;
            if (dx <= 0) continue;

            // Collect (y,z) of points with x >= x_next (which is pts[0..i])
            List<double[]> yz = new ArrayList<>(i + 1);
            for (int k = 0; k <= i; k++) yz.add(new double[]{pts.get(k)[1], pts.get(k)[2]});

            double area = hypervolume2DMax(yz);
            hv += dx * area;
        }

        return hv;
    }

    private List<double[]> nonDominatedMax(List<double[]> pts) {
        List<double[]> nd = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            double[] p = pts.get(i);
            boolean dom = false;
            for (int j = 0; j < pts.size(); j++) {
                if (i == j) continue;
                if (dominatesMax(pts.get(j), p)) { dom = true; break; }
            }
            if (!dom) nd.add(p);
        }
        return nd;
    }

    private boolean dominatesMax(double[] a, double[] b) {
        // Maximization dominance in 3D
        boolean strictly = false;
        if (a[0] < b[0]) return false;
        if (a[1] < b[1]) return false;
        if (a[2] < b[2]) return false;
        if (a[0] > b[0]) strictly = true;
        if (a[1] > b[1]) strictly = true;
        if (a[2] > b[2]) strictly = true;
        return strictly;
    }

    /**
     * Exact hypervolume in 2D maximization for boxes from origin to (y,z).
     * Standard O(n log n): sort by y desc, keep max z envelope.
     */
    private double hypervolume2DMax(List<double[]> yz) {
        if (yz.isEmpty()) return 0.0;

        // Remove dominated in 2D max
        yz = nonDominated2DMax(yz);

        // Sort by y descending
        yz.sort((a, b) -> Double.compare(b[0], a[0]));

        double area = 0.0;
        double prevY = 0.0;
        double bestZ = 0.0;

        for (int i = 0; i < yz.size(); i++) {
            double y = yz.get(i)[0];
            double z = yz.get(i)[1];

            if (i == 0) {
                prevY = y;
                bestZ = z;
                continue;
            }

            // strip from prevY down to y uses bestZ
            double dy = prevY - y;
            if (dy > 0) area += dy * bestZ;

            // update envelope
            if (z > bestZ) bestZ = z;
            prevY = y;
        }

        // last strip down to 0
        if (prevY > 0) area += prevY * bestZ;

        return area;
    }

    private List<double[]> nonDominated2DMax(List<double[]> pts) {
        List<double[]> nd = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            double[] p = pts.get(i);
            boolean dom = false;
            for (int j = 0; j < pts.size(); j++) {
                if (i == j) continue;
                if (dominates2DMax(pts.get(j), p)) { dom = true; break; }
            }
            if (!dom) nd.add(p);
        }
        return nd;
    }

    private boolean dominates2DMax(double[] a, double[] b) {
        boolean strictly = false;
        if (a[0] < b[0]) return false;
        if (a[1] < b[1]) return false;
        if (a[0] > b[0]) strictly = true;
        if (a[1] > b[1]) strictly = true;
        return strictly;
    }

    // -----------------------------
    // Local search
    // -----------------------------

    private void applyLocalSearch(List<SchedulingSolution> population) {
        int eliteCount = Math.max(1, (int) Math.round(population.size() * eliteRatio));
        population.sort(Comparator.comparingDouble(this::scalarScore));
        for (int i = 0; i < eliteCount; i++) {
            SchedulingSolution improved = localSearch(population.get(i));
            population.set(i, improved);
        }
    }

    private SchedulingSolution localSearch(SchedulingSolution base) {
        int[] bestAssign = base.getAssignment().clone();
        SchedulingSolution best = new SchedulingSolution(bestAssign);
        best.setObjectives(base.getF1(), base.getF2(), base.getF3());
        double bestScore = scalarScore(best);

        int moves = Math.max(1, (int) Math.round(tasks.size() * localSearchTasksRatio));

        for (int m = 0; m < moves; m++) {
            int[] candAssign = bestAssign.clone();
            int taskIndex = rand.nextInt(candAssign.length);
            int newNode = rand.nextInt(nodes.size());
            candAssign[taskIndex] = newNode;

            SchedulingSolution cand = new SchedulingSolution(candAssign);
            evaluate(cand);

            double sc = scalarScore(cand);
            if (sc < bestScore) {
                best = cand;
                bestAssign = candAssign;
                bestScore = sc;
            }
        }
        return best;
    }

    // -----------------------------
    // Movement utilities
    // -----------------------------

    private double[] computeMeanPosition(List<SchedulingSolution> pop) {
        int dim = tasks.size();
        double[] mean = new double[dim];
        if (pop == null || pop.isEmpty()) return mean;

        for (SchedulingSolution s : pop) {
            int[] a = s.getAssignment();
            for (int d = 0; d < dim; d++) mean[d] += a[d];
        }
        for (int d = 0; d < dim; d++) mean[d] /= pop.size();
        return mean;
    }

    private SchedulingSolution selectLeader(List<SchedulingSolution> archive, List<SchedulingSolution> population) {
        List<SchedulingSolution> pool = (archive == null || archive.isEmpty()) ? population : archive;

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

    // Simple scalarization only for leader selection and elite local search ordering
    private double scalarScore(SchedulingSolution s) {
        return s.getF1() + 1000.0 * s.getF2() + 0.01 * s.getF3();
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

    // Lévy step
    private double levySmall() {
        double u = rand.nextGaussian();
        double v = rand.nextGaussian();
        double beta = 1.5;
        double sigma = Math.pow(
                gamma(1 + beta) * Math.sin(Math.PI * beta / 2) /
                        (gamma((1 + beta) / 2) * beta * Math.pow(2, (beta - 1) / 2)),
                1.0 / beta
        );
        return u * sigma / Math.pow(Math.abs(v) + 1e-12, 1.0 / beta);
    }

    // Lanczos gamma approximation
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
        for (int i = 0; i < p.length; i++) a += p[i] / (x + i + 1);
        double t = x + g + 0.5;
        return Math.sqrt(2 * Math.PI) * Math.pow(t, x + 0.5) * Math.exp(-t) * a;
    }
}
