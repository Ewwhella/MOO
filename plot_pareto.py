import pandas as pd
import matplotlib.pyplot as plt
import os
import sys
from mpl_toolkits.mplot3d import Axes3D  # noqa: F401


def main():

    # --- Determine folder where CSV are located ---
    CSV_DIR = os.path.dirname(os.path.abspath(__file__))
    print(f"[INFO] Loading CSV from: {CSV_DIR}")
    print(f"[INFO] Saving PNG to the same folder.")

    # Load CSVs
    mojs   = pd.read_csv(os.path.join(CSV_DIR, "pareto_mojs.csv"))
    aco    = pd.read_csv(os.path.join(CSV_DIR, "pareto_aco.csv"))
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

    # ---------- 2D : f1 vs f2 ----------
    plt.figure()
    for name, df in algos.items():
        plt.scatter(df["f1_makespan"], df["f2_cost"],
                    label=name, marker=markers[name], color=colors[name], alpha=0.8)
    plt.xlabel("Makespan (f1)")
    plt.ylabel("Cost (f2)")
    plt.title("Pareto fronts: f1 vs f2")
    plt.legend()
    plt.grid(True)
    plt.tight_layout()
    plt.savefig(os.path.join(CSV_DIR, "pareto_f1_f2.png"), dpi=300)

    # ---------- 2D : f1 vs f3 ----------
    plt.figure()
    for name, df in algos.items():
        plt.scatter(df["f1_makespan"], df["f3_energy"],
                    label=name, marker=markers[name], color=colors[name], alpha=0.8)
    plt.xlabel("Makespan (f1)")
    plt.ylabel("Energy (f3)")
    plt.title("Pareto fronts: f1 vs f3")
    plt.legend()
    plt.grid(True)
    plt.tight_layout()
    plt.savefig(os.path.join(CSV_DIR, "pareto_f1_f3.png"), dpi=300)

    # ---------- 3D : f1, f2, f3 ----------
    fig = plt.figure()
    ax = fig.add_subplot(111, projection="3d")

    for name, df in algos.items():
        ax.scatter(df["f1_makespan"], df["f2_cost"], df["f3_energy"],
                   label=name, marker=markers[name], color=colors[name], alpha=0.8)

    ax.set_xlabel("Makespan (f1)")
    ax.set_ylabel("Cost (f2)")
    ax.set_zlabel("Energy (f3)")
    ax.set_title("3D Pareto fronts")
    ax.legend()
    plt.tight_layout()
    plt.savefig(os.path.join(CSV_DIR, "pareto_3d.png"), dpi=300)

    # ---------- Hypervolume Evolution ----------
    try:
        hv_js = pd.read_csv(os.path.join(CSV_DIR, "hv_mojs.csv"))
        hv_aco = pd.read_csv(os.path.join(CSV_DIR, "hv_aco.csv"))

        plt.figure()
        plt.plot(hv_js["generation"], hv_js["hypervolume"], label="MOJS", color="tab:blue")
        plt.plot(hv_aco["generation"], hv_aco["hypervolume"], label="MO-ACO", color="tab:orange")

        plt.xlabel("Generation")
        plt.ylabel("Hypervolume")
        plt.title("Hypervolume Evolution Over Generations")
        plt.grid(True)
        plt.legend()
        plt.tight_layout()
        plt.savefig(os.path.join(CSV_DIR, "hypervolume_evolution.png"), dpi=300)

    except FileNotFoundError:
        print("[WARN] Hypervolume CSV files not found. Skipping HV plot.")


if __name__ == "__main__":
    main()
