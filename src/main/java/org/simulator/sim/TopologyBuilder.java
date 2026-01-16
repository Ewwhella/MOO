package org.simulator.sim;

import org.simulator.core.NetworkModel;
import org.simulator.core.Node;

import java.util.List;
import java.util.Random;

/**
 * Constructeur de topologie réseau pour l'infrastructure fog/edge/cloud.
 * Calcule les latences et bandes passantes entre noeuds en fonction de leurs types,
 * de leur distance géographique et de la variabilité réseau configurée.
 */
public class TopologyBuilder {

    public static class Params {
        /** Vitesse de propagation du signal en km/s */
        public double propagationSpeedKmPerSec = 20000.0;

        /** Latence de base entre noeuds (secondes) */
        public double baseSameTier  = 0.02;
        public double baseEdgeFog   = 0.05;
        public double baseFogCloud  = 0.20;
        public double baseEdgeCloud = 0.50;

        /** Bande passante entre noeuds (Mo/s) */
        public double bwSameTierMBps  = 800.0;
        public double bwEdgeFogMBps   = 200.0;
        public double bwFogCloudMBps  = 500.0;
        public double bwEdgeCloudMBps = 100.0;

        /** Jitter maximal de latence en secondes (0 = pas de variabilité) */
        public double jitterMaxSec = 0.0;

        /** Ratio de variabilité de bande passante */
        public double bwJitterRatio = 0.0;

        /** Graine aléatoire pour la génération de variabilité */
        public long seed = 0L;
    }

    /**
     * Construit le modèle de réseau complet entre tous les noeuds.
     *
     * @param nodes Liste des noeuds de l'infrastructure
     * @param p Paramètres de configuration réseau
     * @return Modèle de réseau avec latences et bandes passantes configurées
     */
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

                // Calcul de la latence : base + propagation géographique + jitter
                double base = baseLatency(a.getType(), b.getType(), p);
                double distKm = a.distanceTo(b);
                double propagation = distKm / p.propagationSpeedKmPerSec;

                double latencySec = base + propagation;

                if (rnd != null && p.jitterMaxSec > 0.0) {
                    latencySec += rnd.nextDouble() * p.jitterMaxSec;
                }

                // Calcul de la bande passante avec variabilité
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

    /**
     * Détermine la latence de base selon les types de noeuds source et destination.
     */
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

    /**
     * Détermine la bande passante selon les types de noeuds source et destination.
     */
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
