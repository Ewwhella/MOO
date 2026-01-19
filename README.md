# Simulateur de Workflow Scheduling — Optimisation Multi-Objectif

## Présentation

Ce projet implémente un simulateur de planification de workflows multi-objectifs dans un environnement Edge–Fog–Cloud.
Il traite le problème classique de workflow scheduling en affectant des tâches à des noeuds de calcul hétérogènes, tout en optimisant plusieurs objectifs contradictoires.
---

## Objectifs du projet

Le projet vise à :

* Modéliser un **problème de workflow scheduling** dans un contexte Edge–Fog–Cloud
* Décider **quel noeud exécute quelle tâche**
* Optimiser **plusieurs objectifs simultanément** :

    * Temps d’exécution total (makespan)
    * Coût
    * Consommation énergétique
* Comparer des **métaheuristiques multi-objectifs** à des algorithmes baselines
* Évaluer les solutions à l’aide de **fronts de Pareto et d’indicateurs de qualité**

---

## Définition du problème

* **Entrée**

    * Un workflow représenté par un DAG
    * Un ensemble de noeuds hétérogènes (Edge, Fog, Cloud)
    * Un réseau avec latences et bandes passantes

* **Variable de décision**

    * Affectation des tâches aux noeuds de calcul

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

## Scénarios de topologie

Plusieurs scénarios permettent d’étudier l’impact du réseau :

* `DEFAULT`
* `NEAR_CLOUD`
* `FAR_CLOUD`
* `DENSE_FOG`
* `POOR_NETWORK`

Chaque scénario est défini dans `TopologyScenario`. Il modifie :

* la position des noeuds,
* les latences de base,
* les bandes passantes.

---

## Configuration

Tous les paramètres sont définis et modifiables dans le fichier :

```
configs/experiment.yaml
```


---

## Lancer le projet

### Exécution

Le projet se lance en exécutant la classe `App`.


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

---

### Graphiques agrégés par scénario

À l’échelle d’un scénario (agrégation sur plusieurs runs), le graphique suivants est produit :

* Hypervolume moyen par algorithme.
