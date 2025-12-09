package org.simulator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Simulator {

    /**
     * Simule l'exécution d'un workflow pour une affectation donnée tâche -> nœud.
     *
     * @param tasks       liste de tâches du workflow (on suppose ici qu'elles sont déjà en ordre topologique :
     *                    les prédécesseurs apparaissent avant les successeurs dans la liste).
     * @param nodes       liste des nœuds Fog / Cloud.
     * @param assignment  affectation tâche -> nodeId (x_ik implicite).
     * @param networkModel modèle réseau pour les latences et bandes passantes.
     * @return les métriques agrégées : makespan, coût total, énergie totale.
     */
    public static SimulationResult simulate(
            List<Task> tasks,
            List<Node> nodes,
            Map<String, String> assignment,
            NetworkModel networkModel
    ) {
        // indexation rapide : nodeId -> Node
        Map<String, Node> nodeById = new HashMap<>();
        // instant où chaque nœud devient disponible (fin de la dernière tâche exécutée sur ce nœud)
        Map<String, Double> nodeAvailableAt = new HashMap<>();
        for (Node node : nodes) {
            nodeAvailableAt.put(node.getId(), 0.0);
        }

        for (Node node : nodes) {
            nodeById.put(node.getId(), node);
        }

        // si, ci
        Map<String, Double> startTimes = new HashMap<>();
        Map<String, Double> finishTimes = new HashMap<>();

        double totalCost = 0.0;
        double totalEnergy = 0.0;
        double makespan = 0.0;

        // Parcours des tâches (on suppose la liste en ordre topologique)
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

            // Temps de calcul sur ce nœud : work (MI) / MIPS => secondes
            double execTimeSeconds = task.getWorkMI() / node.getMips();

            // Calcul du plus tôt début possible (si)
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
                    // Communication préd -> tâche courante
                    double latency = networkModel.getLatency(predNodeId, nodeId);
                    double bandwidth = networkModel.getBandwidth(predNodeId, nodeId); // en Mo/s (MB/s)

                    double dataMB = pred.getOutputDataMB(); // simplification : même volume pour tous les successeurs
                    double transferTime = dataMB / bandwidth; // secondes

                    commTime = latency + transferTime;

                    double commCost = commTime * networkModel.getCostPerSecNetwork();
                    totalCost += commCost;
                }

                double candidateStart = predFinish + commTime;
                if (candidateStart > earliestStart) {
                    earliestStart = candidateStart;
                }
            }

            // contrainte de ressource : la tâche ne peut démarrer que lorsque le nœud est libre
            double resourceReady = nodeAvailableAt.get(nodeId);

            // startTime = max(constraint DAG + communications, disponibilité du nœud)
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
