import pandas as pd
import matplotlib.pyplot as plt

def main():
    # Chargement des CSV exportés par le Java
    mojs = pd.read_csv("pareto_mojs.csv")
    aco  = pd.read_csv("pareto_aco.csv")

    # --- 2D: f1 vs f2 (Makespan vs Cost) ---
    plt.figure()
    plt.scatter(mojs["f1_makespan"], mojs["f2_cost"], label="MOJS", marker="o")
    plt.scatter(aco["f1_makespan"],  aco["f2_cost"],  label="MO-ACO", marker="x")
    plt.xlabel("Makespan (f1)")
    plt.ylabel("Cost (f2)")
    plt.title("Pareto front: Makespan vs Cost")
    plt.legend()
    plt.grid(True)
    plt.tight_layout()
    plt.savefig("pareto_f1_f2.png", dpi=300)

    # --- 2D: f1 vs f3 (Makespan vs Energy) ---
    plt.figure()
    plt.scatter(mojs["f1_makespan"], mojs["f3_energy"], label="MOJS", marker="o")
    plt.scatter(aco["f1_makespan"],  aco["f3_energy"],  label="MO-ACO", marker="x")
    plt.xlabel("Makespan (f1)")
    plt.ylabel("Energy (f3)")
    plt.title("Pareto front: Makespan vs Energy")
    plt.legend()
    plt.grid(True)
    plt.tight_layout()
    plt.savefig("pareto_f1_f3.png", dpi=300)

    # --- 3D: f1, f2, f3 ---
    from mpl_toolkits.mplot3d import Axes3D  # noqa: F401

    fig = plt.figure()
    ax = fig.add_subplot(111, projection="3d")
    ax.scatter(mojs["f1_makespan"], mojs["f2_cost"], mojs["f3_energy"],
               label="MOJS", marker="o")
    ax.scatter(aco["f1_makespan"], aco["f2_cost"], aco["f3_energy"],
               label="MO-ACO", marker="^")
    ax.set_xlabel("Makespan (f1)")
    ax.set_ylabel("Cost (f2)")
    ax.set_zlabel("Energy (f3)")
    ax.set_title("3D Pareto front")
    plt.legend()
    plt.tight_layout()
    plt.savefig("pareto_3d.png", dpi=300)

    print("Plots generated: pareto_f1_f2.png, pareto_f1_f3.png, pareto_3d.png")

if __name__ == "__main__":
    main()
