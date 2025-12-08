package org.simulator;

public class Node {

    public enum Type {
        FOG,
        CLOUD
    }

    private final String id;
    private final Type type;
    private final double mips;         // puissance CPU (Millions d'instructions / seconde)
    private final double costPerSec;   // coût €/seconde
    private final double powerPerSec;  // puissance W (énergie)

    public Node(String id, Type type, double mips, double costPerSec, double powerPerSec) {
        this.id = id;
        this.type = type;
        this.mips = mips;
        this.costPerSec = costPerSec;
        this.powerPerSec = powerPerSec;
    }

    public String getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public double getMips() {
        return mips;
    }

    public double getCostPerSec() {
        return costPerSec;
    }

    public double getPowerPerSec() {
        return powerPerSec;
    }
}
