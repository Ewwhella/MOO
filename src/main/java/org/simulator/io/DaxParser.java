package org.simulator.io;

import org.simulator.core.Task;
import org.simulator.util.Utils;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.*;

/**
 * Parseur de workflows Pegasus (.dax / .xml) pour CyberShake, etc.
 *
 * - Chaque <job> devient une Task.
 * - Le runtime (s) est converti en charge de calcul workMI via un facteur.
 * - Les dépendances <child>/<parent> construisent le DAG.
 */
public final class DaxParser {

    // facteur runtime (secondes) → workMI
    // (si runtime = 100 s et FACTOR = 10000, alors workMI = 1 000 000 MI)
    private static final double DEFAULT_RUNTIME_TO_MI = 10000.0;

    private DaxParser() {}

    // ------------------------------------------------------------
    // parseDouble FR/EN safe (remplace les virgules par des points)
    // ------------------------------------------------------------
    private static double parseDoubleSafe(String s) {
        if (s == null || s.isEmpty()) return 0.0;
        return Double.parseDouble(s.replace(',', '.'));
    }

    // ------------------------------------------------------------
    // API publique simple : utilises ce wrapper partout
    // ------------------------------------------------------------
    public static List<Task> loadFromDax(String path) {
        return loadFromDax(path, DEFAULT_RUNTIME_TO_MI);
    }

    // ------------------------------------------------------------
    // Version avec facteur runtime → workMI configurable
    // ------------------------------------------------------------
    public static List<Task> loadFromDax(String path, double runtimeToMiFactor) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            DocumentBuilder b = f.newDocumentBuilder();
            Document doc = b.parse(new File(path));

            doc.getDocumentElement().normalize();

            // ------------------------------------------------------------
            // 1) Lire les <job> = nœuds du DAG
            // ------------------------------------------------------------
            NodeList jobNodes = doc.getElementsByTagName("job");
            Map<String, Task> taskMap = new HashMap<>();

            for (int i = 0; i < jobNodes.getLength(); i++) {
                Element e = (Element) jobNodes.item(i);

                String id = e.getAttribute("id");

                // runtime Pegasus (secondes)
                double runtimeSec = parseDoubleSafe(e.getAttribute("runtime"));

                // conversion en travail CPU (Millions d'instructions)
                double workMI = runtimeSec * runtimeToMiFactor;

                // taille des outputs
                double outMB = 0.0;
                NodeList uses = e.getElementsByTagName("uses");
                for (int u = 0; u < uses.getLength(); u++) {
                    Element ue = (Element) uses.item(u);
                    if ("output".equals(ue.getAttribute("link"))) {
                        double sizeBytes = parseDoubleSafe(ue.getAttribute("size"));
                        outMB += sizeBytes / (1024.0 * 1024.0); // bytes → MB
                    }
                }

                Task t = new Task(id, workMI, outMB);
                taskMap.put(id, t);
            }

            // ------------------------------------------------------------
            // 2) Lire les dépendances : <child ref=""><parent ref="">
            // ------------------------------------------------------------
            NodeList childNodes = doc.getElementsByTagName("child");

            for (int i = 0; i < childNodes.getLength(); i++) {
                Element childEl = (Element) childNodes.item(i);
                String childId = childEl.getAttribute("ref");

                Task childTask = taskMap.get(childId);

                NodeList parents = childEl.getElementsByTagName("parent");
                for (int p = 0; p < parents.getLength(); p++) {
                    Element pe = (Element) parents.item(p);
                    String parentId = pe.getAttribute("ref");
                    Task parentTask = taskMap.get(parentId);

                    if (childTask == null || parentTask == null) {
                        throw new IllegalStateException("Unknown parent/child id in DAX: "
                                + parentId + " -> " + childId);
                    }

                    childTask.addPredecessor(parentTask);
                }
            }

            // ------------------------------------------------------------
            // 3) Topological sort pour l'ordre d'exécution
            // ------------------------------------------------------------
            List<Task> allTasks = new ArrayList<>(taskMap.values());
            return Utils.topoSort(allTasks);

        } catch (Exception ex) {
            throw new RuntimeException("Error parsing DAX file '" + path + "': " + ex.getMessage(), ex);
        }
    }
}
