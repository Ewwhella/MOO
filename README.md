# Simulateur de Workflow Scheduling — Optimisation Multi-Objectif

## Présentation

Ce projet implémente un **simulateur de planification de workflows multi-objectifs** dans un environnement **Edge–Fog–Cloud**.
Il traite le **problème classique de workflow scheduling** en affectant des tâches à des nœuds de calcul hétérogènes, tout en optimisant plusieurs objectifs contradictoires.

Le simulateur permet de comparer des **métaheuristiques Pareto-basées** et des algorithmes de référence sur différents **scénarios de topologie réseau**, à l’aide de métriques standard d’optimisation multi-objectif.

---

## Objectifs du projet

Le projet vise à :

* Modéliser un **problème de workflow scheduling** dans un contexte Edge–Fog–Cloud
* Décider **quel nœud exécute quelle tâche**
* Optimiser **plusieurs objectifs simultanément** :

    * Temps d’exécution total (makespan)
    * Coût
    * Consommation énergétique
* Comparer des **métaheuristiques multi-objectifs** à des algorithmes baselines
* Évaluer les solutions à l’aide de **fronts de Pareto et d’indicateurs de qualité**

---

## Définition du problème

* **Entrée**

    * Un workflow représenté par un DAG (tâches et dépendances)
    * Un ensemble de nœuds hétérogènes (Edge, Fog, Cloud)
    * Un réseau avec latences et bandes passantes

* **Variable de décision**

    * Affectation des tâches aux nœuds de calcul

* **Fonctions objectif**

    * Minimiser :

        * **Makespan (F1)** — temps total d’exécution du workflow
        * **Coût (F2)** — coût total de calcul et de communication
        * **Énergie (F3)** — consommation énergétique totale

---

## Algorithmes implémentés

Les algorithmes suivants sont comparés :

| Algorithme | Type            | Description                            |
| ---------- | --------------- | -------------------------------------- |
| **MOJS**   | Métaheuristique | Jellyfish Search multi-objectif        |
| **MO-ACO** | Métaheuristique | Ant Colony Optimization multi-objectif |
| **RANDOM** | Baseline        | Affectations aléatoires                |
| **GREEDY** | Baseline        | Heuristique gloutonne                  |

Chaque algorithme produit un **front de Pareto** de solutions non dominées.

---

## Modélisation du réseau et de l’exécution

### Infrastructure de calcul

* Trois types de nœuds :

    * **Edge**
    * **Fog**
    * **Cloud**
* Capacités de calcul, coûts et puissances hétérogènes

### Modèle réseau

* Latence = latence de base + délai de propagation (fonction de la distance)
* Bandes passantes dépendantes du type de lien
* Variabilité réseau optionnelle (jitter)

### Scénarios de topologie

Plusieurs scénarios permettent d’étudier l’impact du réseau :

* `DEFAULT`
* `NEAR_CLOUD`
* `FAR_CLOUD`
* `DENSE_FOG`
* `POOR_NETWORK`

Chaque scénario modifie :

* la position des nœuds,
* les latences de base,
* les bandes passantes.

---

## Métriques d’évaluation

Le projet utilise des métriques classiques en optimisation multi-objectif :

* **Fronts de Pareto**
* **Hypervolume (HV)**
* **Meilleures valeurs d’objectifs par algorithme**

    * Meilleur makespan
    * Meilleur coût
    * Meilleure énergie

Les résultats sont agrégés sur plusieurs runs pour garantir la robustesse statistique.

---

## Configuration

Tous les paramètres sont définis dans le fichier :

```
configs/experiment.yaml
```


---

## Lancer le projet

### Exécution

**Le projet se lance en exécutant la classe `App`.**

### Depuis un IDE (recommandé)

Lancer :

```
org.simulator.App
```

Il est possible de choisir un autre fichier de configuration en argument depuis la ligne de commande :

```bash
java org.simulator.App configs/experimentAlternative.yaml
```

---

## Résultats

Les résultats expérimentaux sont générés automatiquement dans le dossier `results/`.

Pour chaque expérience et chaque **scénario de topologie**, le simulateur produit :

### Graphiques par run

Dans chaque dossier `run_XX_seed_YY/`, les graphiques suivants sont générés :

* **Front de Pareto**

  * Projections 2D :

    * `pareto_f1_f2.png`
    * `pareto_f1_f3.png`
    * `pareto_f2_f3.png`
  * Visualisation 3D :

    * `pareto_3d.png`

* **Évolution de l’hypervolume**

  * `hypervolume_evolution.png`
  * Permet de suivre la convergence des métaheuristiques au cours des itérations

Ces graphes permettent d’analyser la diversité et la qualité des solutions obtenues par chaque algorithme pour un run donné.

---

### Graphiques agrégés par scénario

À l’échelle d’un scénario (agrégation sur plusieurs runs), les graphiques suivants sont produits :

* **Hypervolume moyen par algorithme**

  * `hv_mean_by_algo.png`

* **Makespan minimal moyen (sur le front de Pareto)**

  * `makespan_min_on_pareto_mean_by_algo.png`

* **Coût minimal moyen (sur le front de Pareto)**

  * `cost_min_on_pareto_mean_by_algo.png`

* **Énergie minimale moyenne (sur le front de Pareto)**

  * `energy_min_on_pareto_mean_by_algo.png`

Ces graphiques permettent une **comparaison directe des algorithmes** pour chaque objectif, dans un scénario donné.

---

### Utilité des résultats

Les graphiques produits permettent :

* de comparer les performances des algorithmes multi-objectifs et baselines,
* d’observer l’impact du scénario réseau sur les résultats,
* d’analyser les compromis entre makespan, coût et énergie,
* d’évaluer la qualité globale des fronts de Pareto via l’hypervolume.


---

## Reproductibilité

* Tous les tirages aléatoires utilisent des **graines contrôlées**
* Le point de référence pour l’hypervolume est recalculé de manière cohérente
* Les résultats sont reproductibles entre scénarios et runs

---

## Conclusion

Ce projet répond pleinement aux exigences du **problème de workflow scheduling** du sujet :

* Modélisation correcte du problème
* Optimisation multi-objectif
* Algorithmes Pareto-basés
* Métriques d’évaluation pertinentes
* Analyse expérimentale reproductible

Il constitue un cadre solide et extensible pour l’étude de la planification de workflows dans des environnements Edge–Fog–Cloud.
