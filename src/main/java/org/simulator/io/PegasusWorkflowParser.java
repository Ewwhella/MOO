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
 * Parseur de workflows au format Pegasus DAX tels que CyberShake.
 */
public final class PegasusWorkflowParser {

    private static final double DEFAULT_RUNTIME_TO_MI = 10000.0;

    private PegasusWorkflowParser() {}

    /**
     * Parse une chaine en double.
     * Gere les valeurs nulles, vides et les séparateurs.
     *
     * @param s Chaine a parser
     * @return Valeur numérique ou 0.0 si invalide
     */
    private static double parseDoubleSafe(String s) {
        if (s == null || s.isEmpty()) return 0.0;
        return Double.parseDouble(s.replace(',', '.'));
    }

    /**
     * Charge un workflow depuis un fichier Pegasus DAX avec le facteur de conversion par défaut.
     *
     * @param path Chemin vers le fichier XML
     * @return Liste des taches en ordre topologique
     * @throws RuntimeException Si le fichier est introuvable ou invalide
     */
    public static List<Task> loadFromPegasusWorkflow(String path) {
        return loadFromPegasusWorkflow(path, DEFAULT_RUNTIME_TO_MI);
    }

    /**
     * Charge un workflow depuis un fichier Pegasus DAX avec un facteur de conversion personnalisé.
     *
     * @param path Chemin vers le fichier XML
     * @param runtimeToMiFactor Facteur multiplicatif runtime (s) vers MI
     * @return Liste des taches en ordre topologique
     * @throws RuntimeException Si le parsing echoue
     */
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

    /**
     * Charge un workflow depuis un flux InputStream.
     * Permet de charger des workflows embarqués dans le JAR ou le classpath.
     *
     * @param is Flux d'entree XML
     * @return Liste des taches en ordre topologique
     * @throws RuntimeException Si le flux est invalide
     */
    public static List<Task> loadFromStream(InputStream is) {
        return loadFromStream(is, DEFAULT_RUNTIME_TO_MI);
    }

    /**
     * Charge un workflow depuis un flux InputStream avec un facteur de conversion personnalisé.
     *
     * @param is Flux d'entree XML
     * @param runtimeToMiFactor Facteur multiplicatif runtime (s) vers MI
     * @return Liste des taches en ordre topologique
     * @throws RuntimeException Si le parsing echoue
     */
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

    /**
     * Parse le document XML et construit la liste des taches avec leurs dependances.
     *
     * Algorithme en trois etapes :
     * 1. Extraction des jobs : creation des objets Task avec charge de calcul et taille de sortie
     * 2. Construction du DAG : etablissement des relations de dependance entre taches
     * 3. Tri topologique : ordonnancement des taches pour respecter les dependances
     *
     * @param doc Document XML
     * @param runtimeToMiFactor Facteur de conversion runtime vers MI
     * @return Liste des taches triees topologiquement
     * @throws IllegalStateException Si le workflow contient des references invalides
     */
    private static List<Task> parseDocument(Document doc, double runtimeToMiFactor) {

        Map<String, Task> taskMap = new HashMap<>();

        // Jobs -> tâches
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

        // Dépendances parent -> child
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
