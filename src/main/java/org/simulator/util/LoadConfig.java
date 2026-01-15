package org.simulator.util;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class LoadConfig {

    public static RunConfig loadYaml(String path) {
        try (InputStream in = Files.newInputStream(Paths.get(path))) {
            LoaderOptions options = new LoaderOptions();
            Constructor ctor = new Constructor(RunConfig.class, options);
            Yaml yaml = new Yaml(ctor);
            RunConfig cfg = yaml.load(in);
            if (cfg == null) throw new RuntimeException("Empty YAML: " + path);
            cfg.validate();
            return cfg;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load YAML config '" + path + "': " + e.getMessage(), e);
        }
    }
}
