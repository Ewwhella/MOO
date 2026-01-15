import os
import sys
import pandas as pd
import matplotlib.pyplot as plt


def find_summary_csvs(root_dir: str):
    """
    Cherche tous les fichiers 'summary.csv' sous root_dir.
    Structure attendue typique :
      results/exp_<workflow>_<timestamp>/<SCENARIO>/summary.csv
    """
    summary_paths = []
    for dirpath, dirnames, filenames in os.walk(root_dir):
        if "summary.csv" in filenames:
            summary_paths.append(os.path.join(dirpath, "summary.csv"))
    return sorted(summary_paths)


def parse_metadata_from_path(summary_csv_path: str):
    """
    Extrait experiment, workflow, scenario depuis le chemin.
    Exemple :
      results/exp_CyberShake-1000_2026-01-15_10-22-33/DEFAULT/summary.csv
    """
    # .../<experiment>/<scenario>/summary.csv
    scenario_dir = os.path.dirname(summary_csv_path)
    experiment_dir = os.path.dirname(scenario_dir)

    scenario = os.path.basename(scenario_dir)
    experiment = os.path.basename(experiment_dir)

    workflow = "UNKNOWN"
    # exp_<workflow>_<timestamp>
    if experiment.startswith("exp_"):
        parts = experiment.split("_")
        # parts = ["exp", "<workflow...>", "<yyyy-mm-dd>", "<hh-mm-ss>"]
        if len(parts) >= 4:
            workflow = parts[1]
        elif len(parts) >= 2:
            workflow = parts[1]

    return experiment, workflow, scenario


def safe_read_summary(path: str):
    """
    Lit le summary.csv. Retourne df ou None si problème.
    """
    try:
        df = pd.read_csv(path)
        return df
    except Exception as e:
        print(f"[WARN] Failed to read {path}: {e}")
        return None


def ensure_dir(path: str):
    os.makedirs(path, exist_ok=True)


def main():
    # Usage:
    #   python aggregate_results.py
    #   python aggregate_results.py results
    #   python aggregate_results.py results/exp_CyberShake-1000_2026-01-15_10-22-33
    root = sys.argv[1] if len(sys.argv) >= 2 else "results"
    root = os.path.abspath(root)

    if not os.path.isdir(root):
        print(f"[ERROR] Not a directory: {root}")
        sys.exit(1)

    print(f"[INFO] Aggregating results from: {root}")

    summary_csvs = find_summary_csvs(root)
    if not summary_csvs:
        print("[ERROR] No summary.csv found under:", root)
        sys.exit(1)

    all_rows = []

    for path in summary_csvs:
        meta_experiment, meta_workflow, meta_scenario = parse_metadata_from_path(path)
        df = safe_read_summary(path)
        if df is None or df.empty:
            continue

        # Ajout meta
        df.insert(0, "scenario", meta_scenario)
        df.insert(0, "workflow", meta_workflow)
        df.insert(0, "experiment", meta_experiment)
        df.insert(0, "summary_path", path)

        all_rows.append(df)

    if not all_rows:
        print("[ERROR] All summary.csv were unreadable/empty.")
        sys.exit(1)

    full = pd.concat(all_rows, ignore_index=True)

    # Normalise colonnes attendues (sans runtime)
    expected_cols = [
        "run", "seed",
        "ref_f1", "ref_f2", "ref_f3",
        "pareto_mojs", "pareto_aco", "pareto_random", "pareto_greedy",
        "hv_mojs", "hv_aco", "hv_random", "hv_greedy",
    ]
    missing = [c for c in expected_cols if c not in full.columns]
    if missing:
        print("[WARN] Missing columns in summaries:", missing)

    # Dossier de sortie
    out_dir = os.path.join(root, "_aggregate")
    ensure_dir(out_dir)

    # 1) CSV global
    full_csv_path = os.path.join(out_dir, "all_runs.csv")
    full.to_csv(full_csv_path, index=False)
    print(f"[INFO] Wrote: {full_csv_path}")

    # 2) Agrégation par (experiment, workflow, scenario)
    group_keys = ["experiment", "workflow", "scenario"]

    metrics_hv = ["hv_mojs", "hv_aco", "hv_random", "hv_greedy"]
    metrics_size = ["pareto_mojs", "pareto_aco", "pareto_random", "pareto_greedy"]

    agg_cols = metrics_hv + metrics_size
    existing_agg_cols = [c for c in agg_cols if c in full.columns]

    agg = (
        full.groupby(group_keys)[existing_agg_cols]
        .agg(["mean", "std", "min", "max", "count"])
        .reset_index()
    )

    # Aplatissement des colonnes multi-index
    agg.columns = [
        col if isinstance(col, str) else f"{col[0]}_{col[1]}"
        for col in agg.columns
    ]

    agg_csv_path = os.path.join(out_dir, "scenario_aggregate.csv")
    agg.to_csv(agg_csv_path, index=False)
    print(f"[INFO] Wrote: {agg_csv_path}")

    # Pour plots : on fait une vue "par scenario" sur le dernier experiment (ou tous)
    experiments = sorted(full["experiment"].unique())
    selected_experiment = None

    base_name = os.path.basename(root)
    if base_name.startswith("exp_"):
        selected_experiment = base_name
    else:
        selected_experiment = experiments[-1]  # heuristique simple

    df_sel = full[full["experiment"] == selected_experiment].copy()
    if df_sel.empty:
        df_sel = full.copy()
        selected_experiment = "ALL"

    print(f"[INFO] Plot selection: experiment={selected_experiment}")

    # 3) Barplot HV moyen par scénario (4 algos)
    hv_means = (
        df_sel.groupby("scenario")[metrics_hv]
        .mean(numeric_only=True)
        .reset_index()
        .sort_values("scenario")
    )

    plt.figure(figsize=(10, 5))
    x = range(len(hv_means["scenario"]))
    width = 0.2

    offsets = {
        "hv_mojs": -1.5 * width,
        "hv_aco": -0.5 * width,
        "hv_random": 0.5 * width,
        "hv_greedy": 1.5 * width
    }

    for m in metrics_hv:
        if m not in hv_means.columns:
            continue
        plt.bar(
            [i + offsets[m] for i in x],
            hv_means[m].values,
            width=width,
            label=m.replace("hv_", "").upper()
        )

    plt.xticks(list(x), hv_means["scenario"].tolist(), rotation=20, ha="right")
    plt.ylabel("Hypervolume (mean)")
    plt.title(f"Hypervolume mean by scenario ({selected_experiment})")
    plt.grid(True, axis="y", linestyle="--", alpha=0.5)
    plt.legend()
    plt.tight_layout()

    hv_plot_path = os.path.join(out_dir, f"hv_mean_by_scenario_{selected_experiment}.png")
    plt.savefig(hv_plot_path, dpi=300)
    print(f"[INFO] Wrote: {hv_plot_path}")

    # 4) Petit CSV HV par scenario pour l’experiment sélectionné
    mini_path = os.path.join(out_dir, f"selected_experiment_hv_summary_{selected_experiment}.csv")
    hv_means.to_csv(mini_path, index=False)
    print(f"[INFO] Wrote: {mini_path}")

    print("\n[OK] Aggregation done.")
    print(f"[INFO] Output folder: {out_dir}")


if __name__ == "__main__":
    main()
