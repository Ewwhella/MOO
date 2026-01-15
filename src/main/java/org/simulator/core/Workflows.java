package org.simulator.core;

import org.simulator.io.PegasusWorkflowParser;
import org.simulator.util.Utils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class Workflows {

    private Workflows() { }

    // 1) Workflow barrage – Dam SHM

    public static List<Task> createDamShmWorkflow() {

        Task acqVib   = new Task("acq_vibration", 9000, 12);
        Task acqAco   = new Task("acq_acoustic", 11000, 15);
        Task acqPres  = new Task("acq_pressure", 7000,  8);

        Task filtVib  = new Task("filter_vibration", 6000, 6);
        Task filtAco  = new Task("filter_acoustic", 7000, 7);
        Task filtPres = new Task("filter_pressure", 5000, 5);

        Task fftVib   = new Task("fft_vibration", 14000, 4);
        Task fftAco   = new Task("fft_acoustic",  16000, 4);
        Task featPres = new Task("feat_pressure", 8000, 3);

        Task fusion   = new Task("fusion", 10000, 5);
        Task detection= new Task("detection", 25000, 2);
        Task decision = new Task("decision", 4000, 0);

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

    // 2) Workflow CyberShake

    public static List<Task> loadCyberShake(int size) {
        String filename;
        switch (size) {
            case 30:
                filename = "CyberShake_30.xml";
                break;
            case 50:
                filename = "CyberShake_50.xml";
                break;
            case 100:
                filename = "CyberShake_100.xml";
                break;
            case 1000:
                filename = "CyberShake_1000.xml";
                break;
            default:
                throw new IllegalArgumentException("Unsupported CyberShake size: " + size
                        + " (expected 30, 50, 100 or 1000)");
        }
        return loadCyberShakeFromXML(filename);
    }


    public static List<Task> loadCyberShakeFromXML(String resourceName) {
        try {
            // Charge la ressource depuis le classpath
            InputStream is = Workflows.class.getClassLoader().getResourceAsStream(resourceName);

            if (is == null) {
                throw new IllegalArgumentException("Resource not found on classpath: " + resourceName);
            }

            // Parse directement via InputStream (pour éviter les bugs de chemin Windows de Pierre...)
            return PegasusWorkflowParser.loadFromStream(is);

        } catch (Exception e) {
            throw new RuntimeException("Error loading XML resource: " + resourceName, e);
        }
    }

}
