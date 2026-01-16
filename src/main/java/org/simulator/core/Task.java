package org.simulator.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Représente une tâche dans un workflow.
 * Une tâche possède une charge de calcul, produit des données de sortie
 * et peut dépendre d'autres tâches.
 */
public class Task {

    private final String id;
    private final double workMI;          // charge de calcul (Millions d'instructions)
    private final double outputDataMB;    // taille des données produites (Mo)
    private final List<Task> predecessors = new ArrayList<>();

    /**
     * Constructeur d'une tâche.
     *
     * @param id Identifiant unique
     * @param workMI Charge de calcul en millions d'instructions
     * @param outputDataMB Taille des données de sortie en Mo
     */
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

    /**
     * Ajoute une tâche prédécesseure.
     *
     * @param t Tâche prédécesseure
     */
    public void addPredecessor(Task t) {
        predecessors.add(t);
    }
}
