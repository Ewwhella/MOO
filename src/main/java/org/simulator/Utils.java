package org.simulator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utils {

    public static Map<String, String> convert(int[] assignment, List<Task> tasks, List<Node> nodes) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < tasks.size(); i++) {
            int nodeIndex = assignment[i];
            map.put(tasks.get(i).getId(), nodes.get(nodeIndex).getId());
        }
        return map;
    }
}

