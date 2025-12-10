package org.simulator;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.List;

public class ModelingUtils {

    // print one Pareto front
    public static void printPareto(String name, List<SchedulingSolution> pareto) {
        System.out.println("\n=== PARETO SOLUTIONS (" + name + ") ===");
        System.out.printf("%-4s %-12s %-15s %-12s%n", "#", "Makespan", "Cost", "Energy");
        System.out.println("---------------------------------------------------------");

        int idx = 1;
        for (SchedulingSolution s : pareto) {
            System.out.printf("%-4d %-12.3f %-15.6f %-12.3f%n",
                    idx++, s.getF1(), s.getF2(), s.getF3());
        }
    }

    // Print hypervolume and spacing for all methods
    public static void printMetrics(List<SchedulingSolution> js,
                                    List<SchedulingSolution> aco,
                                    List<SchedulingSolution> random,
                                    List<SchedulingSolution> greedy,
                                    double[] refPoint) {

        System.out.println("\n=== PERFORMANCE METRICS ===");

        System.out.println("\nHypervolume:");
        System.out.println("MOJS   : " + ParetoMetrics.hypervolume(js, refPoint));
        System.out.println("MO-ACO : " + ParetoMetrics.hypervolume(aco, refPoint));
        System.out.println("RANDOM : " + ParetoMetrics.hypervolume(random, refPoint));
        System.out.println("GREEDY : " + ParetoMetrics.hypervolume(greedy, refPoint));

        System.out.println("\nSpacing:");
        System.out.println("MOJS   : " + ParetoMetrics.spacing(js));
        System.out.println("MO-ACO : " + ParetoMetrics.spacing(aco));
        System.out.println("RANDOM : " + ParetoMetrics.spacing(random));
        System.out.println("GREEDY : " + ParetoMetrics.spacing(greedy));
    }

    public static void exportHypervolumeCSV(List<Double> hvList, String filename) {
        try (FileWriter fw = new FileWriter(filename)) {
            fw.write("generation,hypervolume\n");
            for (int i = 0; i < hvList.size(); i++) {
                fw.write(i + "," + hvList.get(i) + "\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // Call the Python plotting script
    public static void runPythonPlot() {
        try {
            String pythonExe = "py";  // replace with python3 if needed

            ProcessBuilder pb = new ProcessBuilder(
                    pythonExe,
                    "plot_pareto.py"
            );

            pb.redirectErrorStream(true);
            pb.directory(new java.io.File(System.getProperty("user.dir")));

            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[PYTHON] " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("Python script executed successfully.");
            } else {
                System.out.println("Python script exited with code " + exitCode);
            }

        } catch (Exception e) {
            System.err.println("Error while running Python script:");
            e.printStackTrace();
        }
    }
}