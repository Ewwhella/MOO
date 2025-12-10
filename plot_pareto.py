import pandas as pd
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d import Axes3D  # noqa: F401


def main():
    # Chargement des 4 fronts
    mojs   = pd.read_csv("pareto_mojs.csv")
    aco    = pd.read_csv("pareto_aco.csv")
    random = pd.read_csv("pareto_random.csv")
    greedy = pd.read_csv("pareto_greedy.csv")

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
        plt.scatter(
            df["f1_makespan"],
            df["f2_cost"],
            label=name,
            marker=markers.get(name, "o"),
            color=colors.get(name, None),
            alpha=0.8,
        )
    plt.xlabel("Makespan (f1)")
    plt.ylabel("Cost (f2)")
    plt.title("Pareto fronts: f1 vs f2")
    plt.legend()
    plt.grid(True)
    plt.tight_layout()
    plt.savefig("pareto_f1_f2.png", dpi=300)

    # ---------- 2D : f1 vs f3 ----------
    plt.figure()
    for name, df in algos.items():
        plt.scatter(
            df["f1_makespan"],
            df["f3_energy"],
            label=name,
            marker=markers.get(name, "o"),
            color=colors.get(name, None),
            alpha=0.8,
        )
    plt.xlabel("Makespan (f1)")
    plt.ylabel("Energy (f3)")
    plt.title("Pareto fronts: f1 vs f3")
    plt.legend()
    plt.grid(True)
    plt.tight_layout()
    plt.savefig("pareto_f1_f3.png", dpi=300)

    # ---------- 3D : f1, f2, f3 ----------
    fig = plt.figure()
    ax = fig.add_subplot(111, projection="3d")

    for name, df in algos.items():
        ax.scatter(
            df["f1_makespan"],
            df["f2_cost"],
            df["f3_energy"],
            label=name,
            marker=markers.get(name, "o"),
            color=colors.get(name, None),
            alpha=0.8,
        )

    ax.set_xlabel("Makespan (f1)")
    ax.set_ylabel("Cost (f2)")
    ax.set_zlabel("Energy (f3)")
    ax.set_title("3D Pareto fronts")
    ax.legend()
    plt.tight_layout()
    plt.savefig("pareto_3d.png", dpi=300)


if __name__ == "__main__":
    main()
