package org.simulator.sim;

import java.util.ArrayList;
import java.util.List;

public enum TopologyScenario {

    DEFAULT(
            // Zones (km)
            0.0,   0.0,    // dam
            30.0,  10.0,   // fog
            300.0, 200.0,  // cloud

            // Jitter placement (km)
            2.0, 5.0, 20.0,

            // Network base latencies (s)
            0.002, 0.005, 0.020, 0.050,

            // Bandwidth (MB/s)
            800.0, 200.0, 500.0, 100.0
    ),

    NEAR_CLOUD(
            0.0,  0.0,
            30.0, 10.0,
            120.0, 60.0,   // cloud proche

            2.0, 5.0, 10.0,

            0.002, 0.005, 0.015, 0.030,  // moins de base vers cloud
            800.0, 200.0, 700.0, 200.0   // meilleur edge-cloud
    ),

    FAR_CLOUD(
            0.0,   0.0,
            30.0,  10.0,
            1200.0, 800.0, // cloud très loin

            2.0, 5.0, 40.0,

            0.002, 0.005, 0.030, 0.090,  // plus de base vers cloud
            800.0, 200.0, 400.0, 60.0    // edge-cloud plus limité
    ),

    DENSE_FOG(
            0.0,   0.0,
            10.0,  3.0,    // fog beaucoup plus proche du dam
            300.0, 200.0,

            2.0, 2.0, 20.0,  // fog très regroupé

            0.002, 0.003, 0.020, 0.050,  // edge-fog meilleur
            800.0, 350.0, 500.0, 100.0   // edge-fog plus rapide
    ),

    POOR_NETWORK(
            0.0,   0.0,
            30.0,  10.0,
            300.0, 200.0,

            2.0, 5.0, 20.0,

            0.005, 0.015, 0.060, 0.120,  // bases plus mauvaises partout
            200.0, 50.0, 150.0, 20.0     // bande passante réduite
    );

    public final double damX;
    public final double damY;
    public final double fogX;
    public final double fogY;
    public final double cloudX;
    public final double cloudY;

    public final double edgeJitterKm;
    public final double fogJitterKm;
    public final double cloudJitterKm;

    public final double baseSameTierSec;
    public final double baseEdgeFogSec;
    public final double baseFogCloudSec;
    public final double baseEdgeCloudSec;

    public final double bwSameTierMBps;
    public final double bwEdgeFogMBps;
    public final double bwFogCloudMBps;
    public final double bwEdgeCloudMBps;

    TopologyScenario(
            double damX, double damY,
            double fogX, double fogY,
            double cloudX, double cloudY,
            double edgeJitterKm, double fogJitterKm, double cloudJitterKm,
            double baseSameTierSec, double baseEdgeFogSec, double baseFogCloudSec, double baseEdgeCloudSec,
            double bwSameTierMBps, double bwEdgeFogMBps, double bwFogCloudMBps, double bwEdgeCloudMBps
    ) {
        this.damX = damX;
        this.damY = damY;
        this.fogX = fogX;
        this.fogY = fogY;
        this.cloudX = cloudX;
        this.cloudY = cloudY;

        this.edgeJitterKm = edgeJitterKm;
        this.fogJitterKm = fogJitterKm;
        this.cloudJitterKm = cloudJitterKm;

        this.baseSameTierSec = baseSameTierSec;
        this.baseEdgeFogSec = baseEdgeFogSec;
        this.baseFogCloudSec = baseFogCloudSec;
        this.baseEdgeCloudSec = baseEdgeCloudSec;

        this.bwSameTierMBps = bwSameTierMBps;
        this.bwEdgeFogMBps = bwEdgeFogMBps;
        this.bwFogCloudMBps = bwFogCloudMBps;
        this.bwEdgeCloudMBps = bwEdgeCloudMBps;
    }

    public void applyTo(TopologyBuilder.Params tp) {
        tp.baseSameTier = baseSameTierSec;
        tp.baseEdgeFog = baseEdgeFogSec;
        tp.baseFogCloud = baseFogCloudSec;
        tp.baseEdgeCloud = baseEdgeCloudSec;

        tp.bwSameTierMBps = bwSameTierMBps;
        tp.bwEdgeFogMBps = bwEdgeFogMBps;
        tp.bwFogCloudMBps = bwFogCloudMBps;
        tp.bwEdgeCloudMBps = bwEdgeCloudMBps;
    }

    public static List<TopologyScenario> resolveScenarios(List<String> scenarioNames) {
        List<TopologyScenario> out = new ArrayList<>();
        if (scenarioNames == null || scenarioNames.isEmpty()) {
            out.add(TopologyScenario.DEFAULT);
            return out;
        }
        for (String s : scenarioNames) {
            out.add(TopologyScenario.valueOf(s.trim().toUpperCase()));
        }
        return out;
    }
}
