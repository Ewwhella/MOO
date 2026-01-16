package org.simulator.algo;

import org.simulator.core.NetworkModel;
import org.simulator.core.Node;
import org.simulator.core.SchedulingSolution;
import org.simulator.core.Task;
import org.simulator.sim.Simulator;
import org.simulator.util.Utils;

import java.util.*;

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

        // Etat de planification incrémental
        Map<String, String> assignment = new HashMap<>();     // taskId -> nodeId choisi
        Map<String, Double> finishTimes = new HashMap<>();    // taskId -> finish time
        Map<String, Double> nodeAvailableAt = new HashMap<>();// nodeId -> prochain instant libre

        Map<String, Node> nodeById = new HashMap<>();
        for (Node n : nodes) {
            nodeById.put(n.getId(), n);
            nodeAvailableAt.put(n.getId(), 0.0);
        }

        double totalCost = 0.0;
        double totalEnergy = 0.0;
        double makespan = 0.0;

        for (Task t : tasks) {
            String taskId = t.getId();

            String bestNodeId = null;
            double bestFinish = Double.POSITIVE_INFINITY;
            double bestCostDelta = Double.POSITIVE_INFINITY;
            double bestEnergyDelta = Double.POSITIVE_INFINITY;

            for (Node candidate : nodes) {
                String nodeId = candidate.getId();

                // Temps d'exécution sur ce noeud
                double execTime = t.getWorkMI() / candidate.getMips();

                // Contraintes dépendances + comm
                double earliestStart = 0.0;

                for (Task pred : t.getPredecessors()) {
                    String predId = pred.getId();
                    Double predFinish = finishTimes.get(predId);
                    if (predFinish == null) {
                        throw new IllegalStateException("Prédécesseur non planifié: " + predId);
                    }

                    String predNodeId = assignment.get(predId);
                    double commTime = 0.0;

                    if (!predNodeId.equals(nodeId)) {
                        double latency = net.getLatency(predNodeId, nodeId);
                        double bandwidth = net.getBandwidth(predNodeId, nodeId); // MB/s
                        double dataMB = pred.getOutputDataMB();
                        double transfer = dataMB / bandwidth;
                        commTime = latency + transfer;
                    }

                    double candStart = predFinish + commTime;
                    if (candStart > earliestStart) earliestStart = candStart;
                }

                // Disponibilité ressource
                double resourceReady = nodeAvailableAt.get(nodeId);

                double start = Math.max(earliestStart, resourceReady);
                double finish = start + execTime;

                // Variation coût/énergie qui augmente (compute + comm)
                double costDelta = execTime * candidate.getCostPerSec();
                double energyDelta = execTime * candidate.getPowerPerSec();

                for (Task pred : t.getPredecessors()) {
                    String predId = pred.getId();
                    String predNodeId = assignment.get(predId);

                    if (!predNodeId.equals(nodeId)) {
                        // on recalcule le même commTime qu'au dessus pour le coût réseau
                        double latency = net.getLatency(predNodeId, nodeId);
                        double bandwidth = net.getBandwidth(predNodeId, nodeId);
                        double dataMB = pred.getOutputDataMB();
                        double transfer = dataMB / bandwidth;
                        double commTime = latency + transfer;

                        costDelta += commTime * net.getCostPerSecNetwork();
                    }
                }

                // Critère greedy: minimiser le finish (impact makespan local),
                // tie-break coût puis énergie
                boolean better =
                        (finish < bestFinish - 1e-9) ||
                                (Math.abs(finish - bestFinish) <= 1e-9 && costDelta < bestCostDelta - 1e-12) ||
                                (Math.abs(finish - bestFinish) <= 1e-9 && Math.abs(costDelta - bestCostDelta) <= 1e-12 && energyDelta < bestEnergyDelta - 1e-12);

                if (better) {
                    bestFinish = finish;
                    bestCostDelta = costDelta;
                    bestEnergyDelta = energyDelta;
                    bestNodeId = nodeId;
                }
            }

            // Appliquer le meilleur choix
            assignment.put(taskId, bestNodeId);

            Node chosen = nodeById.get(bestNodeId);
            double execTime = t.getWorkMI() / chosen.getMips();

            // Recalcul exact start/finish pour mettre à jour l'état
            double earliestStart = 0.0;
            for (Task pred : t.getPredecessors()) {
                String predId = pred.getId();
                double predFinish = finishTimes.get(predId);

                String predNodeId = assignment.get(predId);
                double commTime = 0.0;

                if (!predNodeId.equals(bestNodeId)) {
                    double latency = net.getLatency(predNodeId, bestNodeId);
                    double bandwidth = net.getBandwidth(predNodeId, bestNodeId);
                    double dataMB = pred.getOutputDataMB();
                    double transfer = dataMB / bandwidth;
                    commTime = latency + transfer;

                    totalCost += commTime * net.getCostPerSecNetwork();
                }

                double candStart = predFinish + commTime;
                if (candStart > earliestStart) earliestStart = candStart;
            }

            double start = Math.max(earliestStart, nodeAvailableAt.get(bestNodeId));
            double finish = start + execTime;

            nodeAvailableAt.put(bestNodeId, finish);
            finishTimes.put(taskId, finish);

            totalCost += execTime * chosen.getCostPerSec();
            totalEnergy += execTime * chosen.getPowerPerSec();

            if (finish > makespan) makespan = finish;
        }

        // Convertir en int[] assignment (index nodes) pour SchedulingSolution
        int[] assignIdx = new int[tasks.size()];
        Map<String, Integer> nodeIndexById = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) nodeIndexById.put(nodes.get(i).getId(), i);

        for (int i = 0; i < tasks.size(); i++) {
            String taskId = tasks.get(i).getId();
            assignIdx[i] = nodeIndexById.get(assignment.get(taskId));
        }

        SchedulingSolution sol = new SchedulingSolution(assignIdx);
        sol.setObjectives(makespan, totalCost, totalEnergy);

        List<SchedulingSolution> out = new ArrayList<>();
        out.add(sol);
        return out;
    }
}
