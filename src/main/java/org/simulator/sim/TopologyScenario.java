package org.simulator.sim;

import java.util.ArrayList;
import java.util.List;

public enum TopologyScenario {

    DEFAULT(
            // Zones (km)
            0.0,    0.0,     // dam
            250.0,  150.0,   // fog
            3200.0, 1800.0,  // cloud

            // Jitter placement (km)
            5.0, 20.0, 80.0,

            // Network base latencies (s)
            0.003, 0.020, 0.120, 0.250,

            // Bandwidth (MB/s)
            1200.0, 250.0, 120.0, 40.0
    ),

    NEAR_CLOUD(
            0.0,   0.0,
            250.0, 150.0,
            900.0, 600.0,

            5.0, 20.0, 60.0,

            0.003, 0.020, 0.070, 0.120,
            1200.0, 250.0, 220.0, 90.0
    ),

    FAR_CLOUD(
            0.0,    0.0,
            2500.0,  1500.0,
            12000.0, 6500.0,

            5.0, 20.0, 120.0,

            0.003, 0.025, 0.220, 0.500,
            1200.0, 220.0, 70.0,  12.0
    ),

    DENSE_FOG(
            0.0,   0.0,
            30.0,  15.0,
            3200.0, 1800.0,

            5.0, 5.0, 80.0,

            0.003, 0.008, 0.120, 0.250,
            1200.0, 600.0, 120.0, 40.0
    ),

    POOR_NETWORK(
            0.0,    0.0,
            250.0,  150.0,
            3200.0, 1800.0,

            5.0, 20.0, 80.0,

            0.020, 0.120, 0.800, 1.500,
            150.0,  15.0,  6.0,   1.5
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
