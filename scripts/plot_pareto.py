import pandas as pd
import matplotlib.pyplot as plt
import os
from mpl_toolkits.mplot3d import Axes3D  # noqa: F401
import sys
import numpy as np

def main():

    if len(sys.argv) >= 2:
        CSV_DIR = sys.argv[1]
    else:
        CSV_DIR = os.path.dirname(os.path.abspath(__file__))

    print(f"[INFO] Loading CSV from: {CSV_DIR}")
    print(f"[INFO] Saving PNG to the same folder.")

    # Récupère le nom du scénario depuis l'arborescence
    scenario_dir = os.path.dirname(CSV_DIR)
    scenario = os.path.basename(scenario_dir)
    run_name = os.path.basename(CSV_DIR)
    title_suffix = f"{scenario} / {run_name}"

    # Chargement des CSVs
    mojs = pd.read_csv(os.path.join(CSV_DIR, "pareto_mojs.csv"))
    aco = pd.read_csv(os.path.join(CSV_DIR, "pareto_aco.csv"))
    random = pd.read_csv(os.path.join(CSV_DIR, "pareto_random.csv"))
    greedy = pd.read_csv(os.path.join(CSV_DIR, "pareto_greedy.csv"))

    algos = {
        "MOJS": mojs,
        "MO-ACO": aco,
        "RANDOM": random,
        "GREEDY": greedy,
    }

    colors = {
        "MOJS": "tab:blue",
        "MO-ACO": "tab:orange",
        "RANDOM": "tab:green",
        "GREEDY": "tab:red",
    }

    markers = {
        "MOJS": "o",
        "MO-ACO": "s",
        "RANDOM": "x",
        "GREEDY": "D",
    }

    # 2D : f1 vs f2
    plt.figure()
    for name, df in algos.items():
        plt.scatter(
            df["f1_makespan"],
            df["f2_cost"],
            label=name,
            marker=markers[name],
            color=colors[name],
            alpha=0.8
        )
    plt.xlabel("Makespan (f1)")
    plt.ylabel("Cost (f2)")
    plt.title(f"Pareto fronts: f1 vs f2 ({title_suffix})")
    plt.legend()
    plt.grid(True)
    plt.tight_layout()
    plt.savefig(os.path.join(CSV_DIR, "pareto_f1_f2.png"), dpi=300)

    # 2D : f1 vs f3
    plt.figure()
    for name, df in algos.items():
        plt.scatter(
            df["f1_makespan"],
            df["f3_energy"],
            label=name,
            marker=markers[name],
            color=colors[name],
            alpha=0.8
        )
    plt.xlabel("Makespan (f1)")
    plt.ylabel("Energy (f3)")
    plt.title(f"Pareto fronts: f1 vs f3 ({title_suffix})")
    plt.legend()
    plt.grid(True)
    plt.tight_layout()
    plt.savefig(os.path.join(CSV_DIR, "pareto_f1_f3.png"), dpi=300)

    # 2D : f2 vs f3
    plt.figure()
    for name, df in algos.items():
        plt.scatter(
            df["f2_cost"],
            df["f3_energy"],
            label=name,
            marker=markers[name],
            color=colors[name],
            alpha=0.8
        )
    plt.xlabel("Cost (f2)")
    plt.ylabel("Energy (f3)")
    plt.title(f"Pareto fronts: f2 vs f3 ({title_suffix})")
    plt.legend()
    plt.grid(True)
    plt.tight_layout()
    plt.savefig(os.path.join(CSV_DIR, "pareto_f2_f3.png"), dpi=300)

    # 3D : f1, f2, f3
    fig = plt.figure()
    ax = fig.add_subplot(111, projection="3d")

    for name, df in algos.items():
        ax.scatter(
            df["f1_makespan"],
            df["f2_cost"],
            df["f3_energy"],
            label=name,
            marker=markers[name],
            color=colors[name],
            alpha=0.8
        )

    ax.set_xlabel("Makespan (f1)")
    ax.set_ylabel("Cost (f2)")
    ax.set_zlabel("Energy (f3)")
    ax.set_title(f"3D Pareto fronts ({title_suffix})")
    ax.legend()
    plt.tight_layout()
    plt.savefig(os.path.join(CSV_DIR, "pareto_3d.png"), dpi=300)

    # Hypervolume Evolution
    try:
        hv_js = pd.read_csv(os.path.join(CSV_DIR, "hv_mojs.csv"))
        hv_aco = pd.read_csv(os.path.join(CSV_DIR, "hv_aco.csv"))

        plt.figure()
        plt.plot(hv_js["generation"], hv_js["hypervolume"], label="MOJS", color="tab:blue")
        plt.plot(hv_aco["generation"], hv_aco["hypervolume"], label="MO-ACO", color="tab:orange")

        plt.xlabel("Generation")
        plt.ylabel("Hypervolume")
        plt.title(f"Hypervolume Evolution Over Generations ({title_suffix})")
        plt.grid(True)
        plt.legend()
        plt.tight_layout()
        plt.savefig(os.path.join(CSV_DIR, "hypervolume_evolution.png"), dpi=300)

    except FileNotFoundError:
        print("[WARN] Hypervolume CSV files not found. Skipping HV plot.")


if __name__ == "__main__":
    main()
