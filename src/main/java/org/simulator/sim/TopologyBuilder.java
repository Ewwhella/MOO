package org.simulator.sim;

import org.simulator.core.NetworkModel;
import org.simulator.core.Node;

import java.util.List;
import java.util.Random;

public class TopologyBuilder {

    public static class Params {
        public double propagationSpeedKmPerSec = 200000.0;

        public double baseSameTier = 0.002;
        public double baseEdgeFog = 0.005;
        public double baseFogCloud = 0.020;
        public double baseEdgeCloud = 0.050;

        public double bwSameTierMBps = 800.0;
        public double bwEdgeFogMBps = 200.0;
        public double bwFogCloudMBps = 500.0;
        public double bwEdgeCloudMBps = 100.0;

        public double jitterMaxSec = 0.0;
        public long seed = 0L;
    }

    public static NetworkModel build(List<Node> nodes, Params p) {
        NetworkModel net = new NetworkModel();

        Random rnd = null;
        if (p.jitterMaxSec > 0.0) {
            rnd = new Random(p.seed);
        }

        for (int i = 0; i < nodes.size(); i++) {
            Node a = nodes.get(i);
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;

                Node b = nodes.get(j);

                double base = baseLatency(a.getType(), b.getType(), p);
                double distKm = a.distanceTo(b);
                double prop = distKm / p.propagationSpeedKmPerSec;

                double jitter = 0.0;
                if (rnd != null) {
                    jitter = rnd.nextDouble() * p.jitterMaxSec;
                }

                double latencySec = base + prop + jitter;
                double bwMBps = bandwidth(a.getType(), b.getType(), p);

                net.setLink(a.getId(), b.getId(), latencySec, bwMBps);
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
