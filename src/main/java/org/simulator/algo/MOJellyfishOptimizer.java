package org.simulator.algo;

import org.simulator.core.NetworkModel;
import org.simulator.core.Node;
import org.simulator.core.SchedulingSolution;
import org.simulator.core.Task;
import org.simulator.sim.Simulator;
import org.simulator.util.Utils;

import java.util.*;

import static org.simulator.eval.ParetoUtils.updateArchive;

/**
 * MOJS (Multi-Objective Jellyfish Search) from scratch (discretisé) :
 * - Représentation : int[] assignment, assignment[i] = index du noeud pour la tâche i
 * - Calcul explicite des objectifs (f1, f2, f3) via Simulator.simulate(...)
 * - Archivage Pareto : solutions non dominées + troncature par crowding distance (via ParetoUtils)
 *
 * Remarque importante :
 * Le Jellyfish est continu, mais notre problème est discret (affectation tâche -> noeud).
 * Donc on remplace le simple arrondi (round) par une discrétisation "operator-based" :
 * - on modifie seulement un sous-ensemble de tâches
 * - on applique soit un mouvement guidé (vers la position continue proposée),
 *   soit un mouvement aléatoire (exploration)
 */
public class MOJellyfishOptimizer {

    // Paramètres principaux
    private final int populationSize;
    private final int maxIter;
    private final int archiveMaxSize;

    // Exploration / exploitation
    private final double mutationRate = 0.20; // (conservé mais plus utilisé dans la boucle principale)
    private final double restartRatio = 0.25;
    private final int stagnationLimit = 6;

    // Recherche locale
    private final double eliteRatio = 0.15;
    private final double localSearchTasksRatio = 0.10;

    // -----------------------------
    // Paramètres de discrétisation (nouveau)
    // -----------------------------
    // Probabilité de suivre le "guide" (arrondi local) au début et à la fin
    private final double guidedProbStart = 0.85;   // au début : plus guidé
    private final double guidedProbEnd   = 0.55;   // à la fin : plus d'exploration

    // Proportion de tâches modifiées par itération (décroît avec t)
    private final double changeRatioMin  = 0.04;   // au minimum 4% des tâches
    private final double changeRatioMax  = 0.20;   // au début jusqu'à 20% des tâches

    // Petit bruit additionnel (évite les solutions identiques)
    private final double extraMutation   = 0.03;

    private final Random rand;

    private final List<Task> tasks;
    private final List<Node> nodes;
    private final NetworkModel network;

    // Historique hypervolume (si refPoint fourni)
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

    /**
     * @param refPoint point de référence pour l'hypervolume : [refF1, refF2, refF3]
     *                 Mettre null si on ne veut pas calculer l'hypervolume.
     */
    public List<SchedulingSolution> run(double[] refPoint) {

        // 1) Initialisation population
        List<SchedulingSolution> population = new ArrayList<>(populationSize);
        for (int i = 0; i < populationSize; i++) {
            SchedulingSolution s = randomSolution();
            evaluate(s);
            population.add(s);
        }

        // 2) Initialisation archive Pareto
        List<SchedulingSolution> archive = updateArchive(Collections.emptyList(), population, archiveMaxSize);

        double bestHvSoFar = 0.0;
        int stagnationCounter = 0;

        // 3) Boucle d'itérations
        for (int iter = 1; iter <= maxIter; iter++) {

            double t = (double) iter / (double) maxIter; // temps normalisé [0,1]

            double[] meanPos = computeMeanPosition(population);
            SchedulingSolution leader = selectLeader(archive, population);
            double[] leaderPos = toDoubleArray(leader.getAssignment());

            List<SchedulingSolution> newPop = new ArrayList<>(populationSize);

            for (int i = 0; i < populationSize; i++) {
                SchedulingSolution current = population.get(i);

                // Représentation "continue" temporaire (juste pour générer une direction)
                double[] curPos = toDoubleArray(current.getAssignment());
                double[] newPos = new double[curPos.length];

                // Comportement Jellyfish : phase active ou passive
                double activeProb = 0.3 + 0.7 * t;
                boolean activePhase = rand.nextDouble() < activeProb;

                if (!activePhase) {
                    // Phase passive : dérive vers la moyenne + bruit Lévy
                    for (int d = 0; d < newPos.length; d++) {
                        double r1 = rand.nextDouble();
                        double drift = r1 * (meanPos[d] - curPos[d]);
                        double noise = 0.25 * levySmall();
                        newPos[d] = curPos[d] + drift + noise;
                    }
                } else {
                    boolean towardLeader = rand.nextDouble() < 0.6;
                    if (towardLeader) {
                        // Phase active : mouvement vers le leader
                        for (int d = 0; d < newPos.length; d++) {
                            double r2 = rand.nextDouble();
                            double step = (0.4 + 0.3 * t) * r2 * (leaderPos[d] - curPos[d]);
                            newPos[d] = curPos[d] + step;
                        }
                    } else {
                        // Phase active : interaction avec une autre méduse
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

                // -----------------------------
                // DISCRÉTISATION OPÉRATEUR-BASED (nouveau)
                // -----------------------------
                // Au lieu de faire round() sur tout le vecteur (souvent trop brutal),
                // on modifie seulement k tâches :
                // - soit on suit la direction suggérée par newPos (mouvement guidé)
                // - soit on réassigne aléatoirement (exploration)
                int[] nextAssign = applyDiscreteMove(current.getAssignment(), newPos, t);

                SchedulingSolution child = new SchedulingSolution(nextAssign);
                evaluate(child);
                newPop.add(child);
            }

            // 4) Recherche locale sur les élites
            applyLocalSearch(newPop);

            population = newPop;

            // 5) Mise à jour archive Pareto (centralisée dans ParetoUtils)
            List<SchedulingSolution> oldArchive = archive;
            archive = updateArchive(archive, population, archiveMaxSize);

            // 6) Hypervolume (optionnel) + redémarrage partiel si stagnation
            if (refPoint != null && refPoint.length == 3) {
                double hv = org.simulator.eval.ParetoMetrics.hypervolume(archive, refPoint);

                if (hv > bestHvSoFar + 1e-9) {
                    bestHvSoFar = hv;
                    stagnationCounter = 0;
                } else {
                    stagnationCounter++;
                }

                hypervolumeHistory.add(bestHvSoFar);

                if (stagnationCounter >= stagnationLimit) {
                    partialRestart(population);
                    stagnationCounter = 0;
                }
            } else {
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
    // Construction solution + évaluation
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
    // Recherche locale
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
    // Utilitaires mouvement
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

    // Scalarisation simple uniquement pour : sélection leader + tri élites (pas pour le Pareto)
    private double scalarScore(SchedulingSolution s) {
        return s.getF1() + 1000.0 * s.getF2() + 0.01 * s.getF3();
    }

    private double[] toDoubleArray(int[] a) {
        double[] x = new double[a.length];
        for (int i = 0; i < a.length; i++) x[i] = a[i];
        return x;
    }

    // -----------------------------
    // DISCRÉTISATION OPÉRATEUR-BASED (nouveau)
    // -----------------------------

    /**
     * Traduit un mouvement continu (targetPos) en une nouvelle affectation discrète :
     * - on part de l'affectation courante
     * - on choisit k tâches à modifier (k dépend de t)
     * - pour chaque tâche : soit on suit la suggestion (arrondi local), soit on explore aléatoirement
     * - on ajoute un léger bruit additionnel pour éviter la stagnation
     */
    private int[] applyDiscreteMove(int[] currentAssign, double[] targetPos, double t) {
        int nTasks = currentAssign.length;
        int nNodes = nodes.size();

        int[] next = currentAssign.clone();

        // Nombre de tâches modifiées : décroît avec le temps (plus d'exploration au début)
        double ratio = changeRatioMax - (changeRatioMax - changeRatioMin) * t;
        int k = Math.max(1, (int) Math.round(ratio * nTasks));

        // Probabilité de suivre la suggestion (guidé) : décroît avec t
        double guidedProb = guidedProbStart - (guidedProbStart - guidedProbEnd) * t;

        for (int i = 0; i < k; i++) {
            int taskIdx = rand.nextInt(nTasks);

            int suggestedNode = clamp((int) Math.round(targetPos[taskIdx]), 0, nNodes - 1);

            if (rand.nextDouble() < guidedProb) {
                // Réaffectation guidée : on suit la direction proposée
                next[taskIdx] = suggestedNode;
            } else {
                // Réaffectation aléatoire : exploration
                next[taskIdx] = rand.nextInt(nNodes);
            }
        }

        // Bruit additionnel léger (évite les solutions identiques)
        for (int i = 0; i < nTasks; i++) {
            if (rand.nextDouble() < extraMutation) {
                next[i] = rand.nextInt(nNodes);
            }
        }

        return next;
    }

    private int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    // -----------------------------
    // Anciennes méthodes de discrétisation (non utilisées maintenant)
    // -----------------------------

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

    // -----------------------------
    // Redémarrage + Lévy + Gamma
    // -----------------------------

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
        double tt = x + g + 0.5;
        return Math.sqrt(2 * Math.PI) * Math.pow(tt, x + 0.5) * Math.exp(-tt) * a;
    }
}
