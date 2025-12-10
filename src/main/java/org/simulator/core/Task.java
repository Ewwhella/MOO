package org.simulator.core;

import java.util.ArrayList;
import java.util.List;

public class Task {

    private final String id;
    private final double workMI;          // charge de calcul (Millions d'instructions)
    private final double outputDataMB;    // taille des données produites (Mo)
    private final List<Task> predecessors = new ArrayList<>();

    public Task(String id, double workMI, double outputDataMB) {
        this.id = id;
        this.workMI = workMI;
        this.outputDataMB = outputDataMB;
    }

    public String getId() {
        return id;
    }

    public double getWorkMI() {
        return workMI;
    }

    public double getOutputDataMB() {
        return outputDataMB;
    }

    public List<Task> getPredecessors() {
        return predecessors;
    }

    public void addPredecessor(Task t) {
        predecessors.add(t);
    }
}
