package org.simulator.sim;

import org.simulator.core.NetworkModel;
import org.simulator.core.Node;

import java.util.List;
import java.util.Random;

public class TopologyBuilder {

    public static class Params {
        // vitesse de propagation
        public double propagationSpeedKmPerSec = 200000.0;

        // latences de base
        public double baseSameTier  = 0.02;
        public double baseEdgeFog   = 0.05;
        public double baseFogCloud  = 0.20;
        public double baseEdgeCloud = 0.50;

        // bandes passantes
        public double bwSameTierMBps  = 800.0;
        public double bwEdgeFogMBps   = 200.0;
        public double bwFogCloudMBps  = 500.0;
        public double bwEdgeCloudMBps = 100.0;

        // variabilité réseau
        public double jitterMaxSec = 0.0;     // ex: 0.003 = 3ms
        public double bwJitterRatio = 0.0;    // ex: 0.10 = ±10%

        public long seed = 0L;
    }

    public static NetworkModel build(List<Node> nodes, Params p) {
        NetworkModel net = new NetworkModel();

        Random rnd = null;
        if (p.jitterMaxSec > 0.0 || p.bwJitterRatio > 0.0) {
            rnd = new Random(p.seed);
        }

        for (int i = 0; i < nodes.size(); i++) {
            Node a = nodes.get(i);
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;

                Node b = nodes.get(j);

                // --- latence géographique ---
                double base = baseLatency(a.getType(), b.getType(), p);
                double distKm = a.distanceTo(b);
                double propagation = distKm / p.propagationSpeedKmPerSec;

                double latencySec = base + propagation;

                // jitter structuré
                if (rnd != null && p.jitterMaxSec > 0.0) {
                    latencySec += rnd.nextDouble() * p.jitterMaxSec;
                }

                // --- bande passante ---
                double bw = bandwidth(a.getType(), b.getType(), p);

                if (rnd != null && p.bwJitterRatio > 0.0) {
                    double r = (rnd.nextDouble() * 2.0 - 1.0) * p.bwJitterRatio;
                    bw = Math.max(1e-9, bw * (1.0 + r));
                }

                net.setLink(a.getId(), b.getId(), latencySec, bw);
            }
        }

        return net;
    }

    private static double baseLatency(Node.Type ta, Node.Type tb, Params p) {
        if (ta == tb) return p.baseSameTier;

        if ((ta == Node.Type.EDGE && tb == Node.Type.FOG) || (ta == Node.Type.FOG && tb == Node.Type.EDGE)) {
            return p.baseEdgeFog;
        }

        if ((ta == Node.Type.FOG && tb == Node.Type.CLOUD) || (ta == Node.Type.CLOUD && tb == Node.Type.FOG)) {
            return p.baseFogCloud;
        }

        return p.baseEdgeCloud;
    }

    private static double bandwidth(Node.Type ta, Node.Type tb, Params p) {
        if (ta == tb) return p.bwSameTierMBps;

        if ((ta == Node.Type.EDGE && tb == Node.Type.FOG) || (ta == Node.Type.FOG && tb == Node.Type.EDGE)) {
            return p.bwEdgeFogMBps;
        }

        if ((ta == Node.Type.FOG && tb == Node.Type.CLOUD) || (ta == Node.Type.CLOUD && tb == Node.Type.FOG)) {
            return p.bwFogCloudMBps;
        }

        return p.bwEdgeCloudMBps;
    }
}
