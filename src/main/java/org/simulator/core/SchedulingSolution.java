package org.simulator.core;

/**
 * Représente une solution d'ordonnancement de workflow.
 * Contient l'affectation des tâches aux noeuds et les valeurs des trois objectifs à minimiser.
 */
public class SchedulingSolution {

    /** Tableau d'affectation : assignment[i] = index du noeud pour la tâche i */
    private int[] assignment;

    /** Objectif 1 : temps d'exécution total */
    private double f1;

    /** Objectif 2 : coût total */
    private double f2;

    /** Objectif 3 : consommation énergétique totale */
    private double f3;

    public SchedulingSolution(int[] assignment) {
        this.assignment = assignment;
    }

    public int[] getAssignment() {
        return assignment;
    }

    public double getF1() {
        return f1;
    }

    public double getF2() {
        return f2;
    }

    public double getF3() {
        return f3;
    }

    /**
     * Définit les valeurs des trois objectifs de la solution.
     *
     * @param f1 Makespan
     * @param f2 Coût total
     * @param f3 Énergie totale
     */
    public void setObjectives(double f1, double f2, double f3) {
        this.f1 = f1;
        this.f2 = f2;
        this.f3 = f3;
    }
}
