package org.simulator.io;

import org.simulator.core.Task;
import org.simulator.util.Utils;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;
import java.util.*;

public final class DaxParser {

    private DaxParser() {}

    // ------------------------------------------------------------
    // Convertisseur FR → EN pour parseDouble
    // ------------------------------------------------------------
    private static double parseDoubleSafe(String s) {
        if (s == null || s.isEmpty()) return 0.0;
        return Double.parseDouble(s.replace(',', '.'));
    }

    // ------------------------------------------------------------
    // Chargement d'un vrai workflow PEGASUS en .dax
    // ------------------------------------------------------------
    public static List<Task> loadFromDax(String path) {
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

                // Runtime Pegasus en secondes
                double runtimeSec = parseDoubleSafe(e.getAttribute("runtime"));

                // Conversion runtime → workMI (approximation réaliste)
                double workMI = runtimeSec * 10000.0;

                // Taille totale des outputs
                double out = 0.0;
                NodeList uses = e.getElementsByTagName("uses");
                for (int u = 0; u < uses.getLength(); u++) {
                    Element ue = (Element) uses.item(u);
                    if ("output".equals(ue.getAttribute("link"))) {
                        double sizeBytes = parseDoubleSafe(ue.getAttribute("size"));
                        out += sizeBytes / (1024.0 * 1024.0);  // convert bytes → MB
                    }
                }

                Task t = new Task(id, workMI, out);
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

                    childTask.addPredecessor(parentTask);
                }
            }

            // ------------------------------------------------------------
            // 3) Topological sort pour l'ordre d'exécution
            // ------------------------------------------------------------
            return Utils.topoSort(new ArrayList<>(taskMap.values()));

        } catch (Exception ex) {
            throw new RuntimeException("Error parsing DAX file: " + ex.getMessage(), ex);
        }
    }
}
