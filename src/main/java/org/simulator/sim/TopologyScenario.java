package org.simulator.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Scénarios topologiques prédéfinis pour les expérimentations.
 *
 * Les scénarios permettent d'évaluer les performances des algorithmes dans différents contextes :
 * cloud proche ou distant, fog dense, réseau degradé, etc.
 */
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

    /**
     * Constructeur d'un scenario topologique.
     *
     * @param damX Coordonnee X de la zone dam
     * @param damY Coordonnee Y de la zone dam
     * @param fogX Coordonnee X de la zone fog
     * @param fogY Coordonnee Y de la zone fog
     * @param cloudX Coordonnee X de la zone cloud
     * @param cloudY Coordonnee Y de la zone cloud
     * @param edgeJitterKm Variabilite du placement des noeuds edge
     * @param fogJitterKm Variabilite du placement des noeuds fog
     * @param cloudJitterKm Variabilite du placement des noeuds cloud
     * @param baseSameTierSec Latence de base entre noeuds du meme niveau
     * @param baseEdgeFogSec Latence de base edge-fog
     * @param baseFogCloudSec Latence de base fog-cloud
     * @param baseEdgeCloudSec Latence de base edge-cloud
     * @param bwSameTierMBps Bande passante entre noeuds du meme niveau
     * @param bwEdgeFogMBps Bande passante edge-fog
     * @param bwFogCloudMBps Bande passante fog-cloud
     * @param bwEdgeCloudMBps Bande passante edge-cloud
     */
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

    /**
     * Applique les paramètres de ce scénario aux paramètres du constructeur de topologie.
     *
     * @param tp Paramètres du constructeur de topologie à modifier
     */
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

    /**
     * Résout une liste de noms de scénarios en objets TopologyScenario.
     * Si la liste est vide ou nulle, retourne le scénario par défaut.
     *
     * @param scenarioNames Liste des noms de scénarios
     * @return Liste des scénarios correspondants
     */
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
