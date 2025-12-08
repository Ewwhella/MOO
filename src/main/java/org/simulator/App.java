package org.simulator;

import java.util.List;
import java.util.Map;

public class App 
{
    public static void main( String[] args )
    {

        System.out.println("Workflow Simulator - Test simple");

        // 1 Fog, 1 Cloud
        Node fog = new Node("fog1", Node.Type.FOG, 5000, 0.001, 50);
        Node cloud = new Node("cloud1", Node.Type.CLOUD, 20000, 0.005, 200);

        // Deux tâches : t1 -> t2
        Task t1 = new Task("t1", 10000, 5); // 10 000 MI, 5 Mo sortants
        Task t2 = new Task("t2", 20000, 0); // 20 000 MI, pas de sortie spécifique
        t2.addPredecessor(t1);

        NetworkModel net = new NetworkModel();
        net.setLink("fog1", "cloud1", 0.05, 100); // 50 ms, 100 Mo/s
        net.setLink("cloud1", "fog1", 0.05, 100);

        Map<String, String> assignment = Map.of(
                "t1", "fog1",
                "t2", "cloud1"
        );

        List<Task> tasks = List.of(t1, t2);
        List<Node> nodes = List.of(fog, cloud);

        Simulator.SimulationResult result =
                Simulator.simulate(tasks, nodes, assignment, net);

        System.out.println(result);

        MOJellyfishOptimizer mojs = new MOJellyfishOptimizer(
                tasks, nodes, net,
                30,      // population
                50,      // iterations
                40       // archive max
        );

        List<JellyfishSolution> pareto = mojs.run();

        System.out.println("=== PARETO SOLUTIONS ===");
        for (JellyfishSolution s : pareto) {
            System.out.println(
                    s.getF1() + " | " + s.getF2() + " | " + s.getF3()
            );
        }

    }
}
