package org.simulator.io;

import org.simulator.core.Task;
import org.simulator.util.Utils;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.util.*;

/**
 * Parser Pegasus (.dax/.xml) pour workflows cyberShake, Montage, LIGO, etc.
 */
public final class PegasusWorkflowParser {

    private static final double DEFAULT_RUNTIME_TO_MI = 10000.0;

    private PegasusWorkflowParser() {}

    private static double parseDoubleSafe(String s) {
        if (s == null || s.isEmpty()) return 0.0;
        return Double.parseDouble(s.replace(',', '.'));
    }

    // =====================================================================
    // 1) Fichier classique
    // =====================================================================
    public static List<Task> loadFromPegasusWorkflow(String path) {
        return loadFromPegasusWorkflow(path, DEFAULT_RUNTIME_TO_MI);
    }

    public static List<Task> loadFromPegasusWorkflow(String path, double runtimeToMiFactor) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            DocumentBuilder b = f.newDocumentBuilder();
            Document doc = b.parse(new File(path));
            doc.getDocumentElement().normalize();
            return parseDocument(doc, runtimeToMiFactor);

        } catch (Exception ex) {
            throw new RuntimeException("Error parsing Pegasus workflow file '" + path + "'", ex);
        }
    }

    // =====================================================================
    // 2) Chargement depuis InputStream (classpath, jar, IDE)
    // =====================================================================
    public static List<Task> loadFromStream(InputStream is) {
        return loadFromStream(is, DEFAULT_RUNTIME_TO_MI);
    }

    public static List<Task> loadFromStream(InputStream is, double runtimeToMiFactor) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            DocumentBuilder b = f.newDocumentBuilder();
            Document doc = b.parse(is);
            doc.getDocumentElement().normalize();
            return parseDocument(doc, runtimeToMiFactor);

        } catch (Exception ex) {
            throw new RuntimeException("Error parsing Pegasus workflow stream", ex);
        }
    }

    // =====================================================================
    // 3) Parser DOM commun pour fichier + stream
    // =====================================================================
    private static List<Task> parseDocument(Document doc, double runtimeToMiFactor) {

        Map<String, Task> taskMap = new HashMap<>();

        // ------------------------------------------------------------
        // Jobs → tâches
        // ------------------------------------------------------------
        NodeList jobNodes = doc.getElementsByTagName("job");

        for (int i = 0; i < jobNodes.getLength(); i++) {
            Element e = (Element) jobNodes.item(i);

            String id = e.getAttribute("id");
            double runtimeSec = parseDoubleSafe(e.getAttribute("runtime"));
            double workMI = runtimeSec * runtimeToMiFactor;

            double outMB = 0.0;
            NodeList uses = e.getElementsByTagName("uses");

            for (int u = 0; u < uses.getLength(); u++) {
                Element ue = (Element) uses.item(u);
                if ("output".equals(ue.getAttribute("link"))) {
                    double bytes = parseDoubleSafe(ue.getAttribute("size"));
                    outMB += bytes / (1024.0 * 1024.0);
                }
            }

            Task t = new Task(id, workMI, outMB);
            taskMap.put(id, t);
        }

        // ------------------------------------------------------------
        // Dépendances parent → child
        // ------------------------------------------------------------
        NodeList childNodes = doc.getElementsByTagName("child");

        for (int i = 0; i < childNodes.getLength(); i++) {
            Element child = (Element) childNodes.item(i);
            Task childTask = taskMap.get(child.getAttribute("ref"));

            NodeList parents = child.getElementsByTagName("parent");
            for (int p = 0; p < parents.getLength(); p++) {
                Element pe = (Element) parents.item(p);
                Task parentTask = taskMap.get(pe.getAttribute("ref"));

                if (childTask == null || parentTask == null) {
                    throw new IllegalStateException("Unknown parent/child ref in workflow");
                }
                childTask.addPredecessor(parentTask);
            }
        }

        return Utils.topoSort(new ArrayList<>(taskMap.values()));
    }
}
