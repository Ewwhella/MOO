package org.simulator;

public class SchedulingSolution {

    private int[] assignment; // assignment[i] = index du node pour la tâche i
    private double f1; // makespan
    private double f2; // cost
    private double f3; // energy

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

    public void setObjectives(double f1, double f2, double f3) {
        this.f1 = f1;
        this.f2 = f2;
        this.f3 = f3;
    }

    public SchedulingSolution copy() {
        int[] newA = new int[assignment.length];
        System.arraycopy(assignment, 0, newA, 0, assignment.length);
        SchedulingSolution c = new SchedulingSolution(newA);
        c.setObjectives(f1, f2, f3);
        return c;
    }
}
