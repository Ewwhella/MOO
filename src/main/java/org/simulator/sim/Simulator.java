package org.simulator.sim;

import org.simulator.core.NetworkModel;
import org.simulator.core.Node;
import org.simulator.core.Task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulateur d'exécution de workflow.
 * Calcule les temps d'exécution, coûts et consommation énergétique
 * en tenant compte des dépendances, des communications réseau et de la disponibilité des ressources.
 */
public class Simulator {

    /**
     * Simule l'exécution d'un workflow pour une affectation donnée tâche -> noeud.
     *
     * @param tasks Liste des tâches en ordre topologique
     * @param nodes Liste des noeuds disponibles
     * @param assignment Map associant chaque tâche à un noeud (taskId -> nodeId)
     * @param networkModel Modèle de réseau
     * @return Résultat de la simulation (makespan, coût total, énergie totale)
     */
    public static SimulationResult simulate(
            List<Task> tasks,
            List<Node> nodes,
            Map<String, String> assignment,
            NetworkModel networkModel
    ) {
        // Indexation rapide : nodeId -> Node
        Map<String, Node> nodeById = new HashMap<>();
        // Instant où chaque noeud devient disponible (fin de la dernière tâche exécutée)
        Map<String, Double> nodeAvailableAt = new HashMap<>();
        for (Node node : nodes) {
            nodeAvailableAt.put(node.getId(), 0.0);
        }

        for (Node node : nodes) {
            nodeById.put(node.getId(), node);
        }

        // Temps de début et fin pour chaque tâche
        Map<String, Double> startTimes = new HashMap<>();
        Map<String, Double> finishTimes = new HashMap<>();

        double totalCost = 0.0;
        double totalEnergy = 0.0;
        double makespan = 0.0;

        // Parcours des tâches (ordre topologique)
        for (Task task : tasks) {
            String taskId = task.getId();
            String nodeId = assignment.get(taskId);

            if (nodeId == null) {
                throw new IllegalArgumentException("Aucune affectation pour la tâche " + taskId);
            }

            Node node = nodeById.get(nodeId);
            if (node == null) {
                throw new IllegalArgumentException("Node inexistant pour l'id " + nodeId);
            }

            // Temps de calcul sur ce noeud : work (MI) / MIPS => secondes
            double execTimeSeconds = task.getWorkMI() / node.getMips();

            // Calcul du plus tôt début possible en tenant compte des dépendances
            double earliestStart = 0.0;

            for (Task pred : task.getPredecessors()) {
                String predId = pred.getId();
                Double predFinish = finishTimes.get(predId);
                if (predFinish == null) {
                    throw new IllegalStateException("La tâche prédécesseur " + predId + " n'a pas encore été planifiée.");
                }

                String predNodeId = assignment.get(predId);

                double commTime = 0.0;
                if (!predNodeId.equals(nodeId)) {
                    // Communication entre noeuds différents
                    double latency = networkModel.getLatency(predNodeId, nodeId);
                    double bandwidth = networkModel.getBandwidth(predNodeId, nodeId);

                    double dataMB = pred.getOutputDataMB();
                    double transferTime = dataMB / bandwidth;

                    commTime = latency + transferTime;

                    double commCost = commTime * networkModel.getCostPerSecNetwork();
                    totalCost += commCost;
                }

                double candidateStart = predFinish + commTime;
                if (candidateStart > earliestStart) {
                    earliestStart = candidateStart;
                }
            }

            // Contrainte de ressource : la tâche ne peut démarrer que lorsque le noeud est libre
            double resourceReady = nodeAvailableAt.get(nodeId);

            // Temps de début = max(contrainte DAG + communications, disponibilité du noeud)
            double startTime = Math.max(earliestStart, resourceReady);

            double finishTime = startTime + execTimeSeconds;
            nodeAvailableAt.put(nodeId, finishTime);

            startTimes.put(taskId, startTime);
            finishTimes.put(taskId, finishTime);

            // Coût et énergie de calcul
            double computeCost = execTimeSeconds * node.getCostPerSec();
            double computeEnergy = execTimeSeconds * node.getPowerPerSec();

            totalCost += computeCost;
            totalEnergy += computeEnergy;

            if (finishTime > makespan) {
                makespan = finishTime;
            }
        }

        return new SimulationResult(makespan, totalCost, totalEnergy);
    }

    /**
     * Résultat d'une simulation de workflow.
     */
    public static class SimulationResult {
        private final double makespan;
        private final double totalCost;
        private final double totalEnergy;

        public SimulationResult(double makespan, double totalCost, double totalEnergy) {
            this.makespan = makespan;
            this.totalCost = totalCost;
            this.totalEnergy = totalEnergy;
        }

        public double getMakespan() {
            return makespan;
        }

        public double getTotalCost() {
            return totalCost;
        }

        public double getTotalEnergy() {
            return totalEnergy;
        }

        @Override
        public String toString() {
            return "SimulationResult{" +
                    "makespan=" + makespan +
                    ", totalCost=" + totalCost +
                    ", totalEnergy=" + totalEnergy +
                    '}';
        }
    }
}
