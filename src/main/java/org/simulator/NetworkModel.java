package org.simulator;

import java.util.HashMap;
import java.util.Map;

public class NetworkModel {

    private final double costPerSecNetwork = 0.0001;


    private static class LinkKey {
        private final String from;
        private final String to;
        // valeur par défaut, ajustable

        LinkKey(String from, String to) {
            this.from = from;
            this.to = to;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LinkKey)) return false;
            LinkKey other = (LinkKey) o;
            return from.equals(other.from) && to.equals(other.to);
        }

        @Override
        public int hashCode() {
            return from.hashCode() * 31 + to.hashCode();
        }
    }

    private final Map<LinkKey, Double> latencySeconds = new HashMap<>();
    private final Map<LinkKey, Double> bandwidthMbps = new HashMap<>();

    public void setLink(String fromId, String toId, double latencySec, double bandwidthMbPerSec) {
        LinkKey key = new LinkKey(fromId, toId);
        latencySeconds.put(key, latencySec);
        bandwidthMbps.put(key, bandwidthMbPerSec);
    }

    public double getLatency(String fromId, String toId) {
        return latencySeconds.getOrDefault(new LinkKey(fromId, toId), 0.0);
    }

    public double getBandwidth(String fromId, String toId) {
        return bandwidthMbps.getOrDefault(new LinkKey(fromId, toId), Double.POSITIVE_INFINITY);
    }

    public double getCostPerSecNetwork() {
        return costPerSecNetwork;
    }
}
