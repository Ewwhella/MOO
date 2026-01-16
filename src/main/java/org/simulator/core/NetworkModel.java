package org.simulator.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Modèle de réseau pour représenter les liens entre les noeuds du système.
 * Stocke les informations de latence et de bande passante entre chaque paire de noeuds.
 */
public class NetworkModel {

    /** Coût par seconde pour l'utilisation du réseau (€/s) */
    private static final double COST_PER_SEC_NETWORK = 0.0001;

    /**
     * Clé pour identifier de manière unique un lien directionnel entre deux noeuds.
     */
    private static class LinkKey {
        private final String from;
        private final String to;

        LinkKey(String from, String to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LinkKey other)) return false;
            return from.equals(other.from) && to.equals(other.to);
        }

        @Override
        public int hashCode() {
            return from.hashCode() * 31 + to.hashCode();
        }
    }

    /** Map des latences pour chaque lien (en secondes) */
    private final Map<LinkKey, Double> latencySeconds = new HashMap<>();

    /** Map des bandes passantes pour chaque lien (en Mo/s) */
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
        return COST_PER_SEC_NETWORK;
    }
}
