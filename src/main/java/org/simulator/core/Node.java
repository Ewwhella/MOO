package org.simulator.core;

public class Node {

    public enum Type {
        FOG,
        CLOUD,
        EDGE
    }

    private final String id;
    private final Type type;
    private final double mips;         // puissance CPU (Millions d'instructions / seconde)
    private final double costPerSec;   // coût €/seconde
    private final double powerPerSec;  // puissance W (énergie)
    private final double x;
    private final double y;
    private final String zone;

    public Node(String id,
                Type type,
                double mips,
                double costPerSec,
                double powerPerSec,
                double x,
                double y,
                String zone) {
        this.id = id;
        this.type = type;
        this.mips = mips;
        this.costPerSec = costPerSec;
        this.powerPerSec = powerPerSec;
        this.x = x;
        this.y = y;
        this.zone = zone;
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

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public String getZone() {
        return zone;
    }

    public boolean isFog() {
        return this.type == Type.FOG;
    }

    public boolean isCloud() {
        return this.type == Type.CLOUD;
    }

    public boolean isEdge() {
        return this.type == Type.EDGE;
    }

    public double distanceTo(Node other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
