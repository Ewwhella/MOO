package org.simulator.util;

import java.util.List;

public class RunConfig {

    public Workflow workflow;
    public Execution execution;
    public Nodes nodes;
    public Network network;
    public List<String> scenarios;

    public static class Workflow {
        public String type;            // CYBERSHAKE | DAM
        public int cybershake_size;    // 30 | 50 | 100 | 1000
    }

    public static class Execution {
        public long base_seed;
        public int runs;
    }

    public static class Nodes {
        public Tier edge;
        public Tier fog;
        public Tier cloud;

        public static class Tier {
            public int count;
            public double mips;
            public double cost;
            public double power;
            public String zone;
        }
    }

    public static class Network {
        public double propagation_speed_km_per_sec;
        public Variability variability;

        public static class Variability {
            public boolean enabled;
            public double latency_jitter_max_sec;
            public double bandwidth_jitter_ratio;
        }
    }

    public void validate() {
        if (workflow == null) throw new IllegalArgumentException("Missing 'workflow' in YAML");
        if (execution == null) throw new IllegalArgumentException("Missing 'execution' in YAML");
        if (nodes == null) throw new IllegalArgumentException("Missing 'nodes' in YAML");
        if (network == null) throw new IllegalArgumentException("Missing 'network' in YAML");
        if (workflow.type == null) throw new IllegalArgumentException("Missing 'workflow.type'");
        if (execution.runs <= 0) throw new IllegalArgumentException("'execution.runs_per_scenario' must be > 0");
        if (nodes.edge == null || nodes.fog == null || nodes.cloud == null)
            throw new IllegalArgumentException("Missing nodes.edge/fog/cloud");
    }
}
