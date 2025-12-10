package org.simulator.util;

import org.simulator.core.Node;
import org.simulator.core.Task;

import java.util.*;

public class Utils {

    public static Map<String, String> convert(int[] assignment, List<Task> tasks, List<Node> nodes) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < tasks.size(); i++) {
            int nodeIndex = assignment[i];
            map.put(tasks.get(i).getId(), nodes.get(nodeIndex).getId());
        }
        return map;
    }

    public static List<Task> topoSort(List<Task> tasks) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, Task> byId = new HashMap<>();

        for (Task t : tasks) {
            indegree.put(t.getId(), 0);
            byId.put(t.getId(), t);
        }

        for (Task t : tasks) {
            for (Task p : t.getPredecessors()) {
                indegree.put(t.getId(), indegree.get(t.getId()) + 1);
            }
        }

        Queue<Task> q = new ArrayDeque<>();
        for (Task t : tasks) {
            if (indegree.get(t.getId()) == 0) q.add(t);
        }

        List<Task> sorted = new ArrayList<>();

        while (!q.isEmpty()) {
            Task cur = q.poll();
            sorted.add(cur);

            for (Task succ : tasks) {
                if (succ.getPredecessors().contains(cur)) {
                    int deg = indegree.get(succ.getId()) - 1;
                    indegree.put(succ.getId(), deg);
                    if (deg == 0) q.add(succ);
                }
            }
        }

        return sorted;
    }

}

