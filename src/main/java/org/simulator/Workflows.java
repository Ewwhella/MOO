package org.simulator;

import java.util.ArrayList;
import java.util.List;

/**
 * Présets de workflows :
 * - Dam SHM (ton workflow initial)
 * - CyberShake100 (approximation à 100 tâches)
 */
public final class Workflows {

    private Workflows() { }

    // ============================
    // 1) Workflow barrage
    // ============================
    public static List<Task> createDamShmWorkflow() {

        Task acqVib   = new Task("acq_vibration",  9000, 12);
        Task acqAco   = new Task("acq_acoustic",  11000, 15);
        Task acqPres  = new Task("acq_pressure",   7000,  8);

        Task filtVib  = new Task("filter_vibration",  6000, 6);
        Task filtAco  = new Task("filter_acoustic",   7000, 7);
        Task filtPres = new Task("filter_pressure",   5000, 5);

        Task fftVib   = new Task("fft_vibration", 14000, 4);
        Task fftAco   = new Task("fft_acoustic",  16000, 4);
        Task featPres = new Task("feat_pressure",  8000, 3);

        Task fusion   = new Task("fusion",   10000, 5);
        Task detection= new Task("detection",25000, 2);
        Task decision = new Task("decision",  4000, 0);

        // DAG
        filtVib.addPredecessor(acqVib);
        filtAco.addPredecessor(acqAco);
        filtPres.addPredecessor(acqPres);

        fftVib.addPredecessor(filtVib);
        fftAco.addPredecessor(filtAco);
        featPres.addPredecessor(filtPres);

        fusion.addPredecessor(fftVib);
        fusion.addPredecessor(fftAco);
        fusion.addPredecessor(featPres);

        detection.addPredecessor(fusion);
        decision.addPredecessor(detection);

        List<Task> tasks = new ArrayList<>();
        tasks.add(acqVib);   tasks.add(acqAco);   tasks.add(acqPres);
        tasks.add(filtVib);  tasks.add(filtAco);  tasks.add(filtPres);
        tasks.add(fftVib);   tasks.add(fftAco);   tasks.add(featPres);
        tasks.add(fusion);   tasks.add(detection);tasks.add(decision);

        return Utils.topoSort(tasks);
    }

    // ============================
    // 2) Workflow CyberShake100
    // ============================
    /**
     * CyberShake100 approx :
     * Niveaux :
     *  L0 : 1 tâche de configuration régionale
     *  L1 : 10 tâches de génération de ruptures
     *  L2 : 30 tâches de calcul SGT (3 par rupture)
     *  L3 : 30 tâches de synthèse de sismogrammes (1-1 avec SGT)
     *  L4 : 20 tâches de calcul d'intensité (IM) (chaque IM agrège 3 sismogrammes)
     *  L5 : 8 tâches de courbes d'aléa (hazard curves)
     *  L6 : 1 tâche d'agrégation finale
     * Total = 100 tâches.
     */
    public static List<Task> createCyberShake100() {

        List<Task> tasks = new ArrayList<>();

        // ----- L0 : setup régional -----
        Task regionSetup = new Task("cs_region_setup", 30000, 200);
        tasks.add(regionSetup);

        // ----- L1 : génération de ruptures -----
        List<Task> ruptures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Task t = new Task("cs_rupture_gen_" + i, 25000, 80);
            t.addPredecessor(regionSetup);
            tasks.add(t);
            ruptures.add(t);
        }

        // ----- L2 : calcul des SGT (3 par rupture -> 30 tâches) -----
        List<Task> sgts = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            Task parent = ruptures.get(i / 3); // 3 SGT par rupture
            Task t = new Task("cs_sgt_compute_" + i, 60000, 150);
            t.addPredecessor(parent);
            tasks.add(t);
            sgts.add(t);
        }

        // ----- L3 : synthèse des sismogrammes (1 par SGT -> 30) -----
        List<Task> seismos = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            Task parent = sgts.get(i);
            Task t = new Task("cs_seismogram_" + i, 45000, 100);
            t.addPredecessor(parent);
            tasks.add(t);
            seismos.add(t);
        }

        // ----- L4 : calcul des mesures d'intensité (IM) (20 tâches) -----
        List<Task> ims = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Task t = new Task("cs_intensity_measure_" + i, 15000, 40);

            // chaque IM dépend de 3 sismogrammes, pour créer de l'agrégation
            int base = (i * 3) % seismos.size();
            t.addPredecessor(seismos.get(base));
            t.addPredecessor(seismos.get((base + 1) % seismos.size()));
            t.addPredecessor(seismos.get((base + 2) % seismos.size()));

            tasks.add(t);
            ims.add(t);
        }

        // ----- L5 : courbes d'aléa (8 tâches) -----
        List<Task> hazardCurves = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Task t = new Task("cs_hazard_curve_" + i, 12000, 30);

            // chaque courbe regroupe plusieurs IM
            int base = (i * 2) % ims.size();
            t.addPredecessor(ims.get(base));
            t.addPredecessor(ims.get((base + 1) % ims.size()));
            t.addPredecessor(ims.get((base + 3) % ims.size()));

            tasks.add(t);
            hazardCurves.add(t);
        }

        // ----- L6 : agrégation finale -----
        Task aggregate = new Task("cs_hazard_aggregate", 8000, 10);
        for (Task h : hazardCurves) {
            aggregate.addPredecessor(h);
        }
        tasks.add(aggregate);

        // Tri topologique pour garantir l'ordre
        return Utils.topoSort(tasks);
    }

    public static List<Task> loadCyberShakeFromDax(String filename) {
        return DaxParser.loadFromDax(filename);
    }


}
