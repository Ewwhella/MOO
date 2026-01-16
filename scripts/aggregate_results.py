import os
import sys
import pandas as pd
import matplotlib.pyplot as plt


def find_summary_csvs(root_dir: str):
    """
    Cherche tous les fichiers 'summary.csv' sous root_dir.
    """
    summary_paths = []
    for dirpath, dirnames, filenames in os.walk(root_dir):
        if "summary.csv" in filenames:
            summary_paths.append(os.path.join(dirpath, "summary.csv"))
    return sorted(summary_paths)


def parse_metadata_from_path(summary_csv_path: str):
    """
    Extrait experiment, workflow, scenario depuis le chemin.
    """
    scenario_dir = os.path.dirname(summary_csv_path)
    experiment_dir = os.path.dirname(scenario_dir)

    scenario = os.path.basename(scenario_dir)
    experiment = os.path.basename(experiment_dir)

    workflow = "UNKNOWN"
    if experiment.startswith("exp_"):
        parts = experiment.split("_")
        if len(parts) >= 4:
            workflow = parts[1]
        elif len(parts) >= 2:
            workflow = parts[1]

    return experiment, workflow, scenario, scenario_dir


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


def mean_col(df: pd.DataFrame, col: str):
    if col not in df.columns:
        return float("nan")
    return float(pd.to_numeric(df[col], errors="coerce").mean())


def barplot_one_metric(algo_labels, values, ylabel, title, out_path):
    plt.figure(figsize=(9, 5))
    plt.bar(algo_labels, values)
    plt.ylabel(ylabel)
    plt.title(title)
    plt.grid(True, axis="y", linestyle="--", alpha=0.5)
    plt.tight_layout()
    plt.savefig(out_path, dpi=300)
    plt.close()


def main():
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
        meta_experiment, meta_workflow, meta_scenario, meta_scenario_dir = parse_metadata_from_path(path)
        df = safe_read_summary(path)
        if df is None or df.empty:
            continue

        df.insert(0, "scenario", meta_scenario)
        df.insert(0, "workflow", meta_workflow)
        df.insert(0, "experiment", meta_experiment)
        df.insert(0, "summary_path", path)
        df.insert(0, "scenario_dir", meta_scenario_dir)

        all_rows.append(df)

    if not all_rows:
        print("[ERROR] All summary.csv were unreadable/empty.")
        sys.exit(1)

    full = pd.concat(all_rows, ignore_index=True)

    # Colonnes attendues
    expected_cols = [
        "run", "seed",
        "ref_f1", "ref_f2", "ref_f3",
        "pareto_mojs", "pareto_aco", "pareto_random", "pareto_greedy",
        "hv_mojs", "hv_aco", "hv_random", "hv_greedy",
    ]
    missing = [c for c in expected_cols if c not in full.columns]
    if missing:
        print("[WARN] Missing columns in summaries (ok if not yet added):", missing)

    experiments = sorted(full["experiment"].unique())
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

    # Output: on écrit dans chaque dossier scenario
    algo_labels = ["MOJS", "ACO", "RANDOM", "GREEDY"]

    metrics_hv_cols = ["hv_mojs", "hv_aco", "hv_random", "hv_greedy"]

    # groupe par scenario
    for scenario, df_sc in df_sel.groupby("scenario"):
        scenario_dir = df_sc["scenario_dir"].iloc[0]
        out_dir = scenario_dir
        ensure_dir(out_dir)

        # 1) CSV runs
        full_csv_path = os.path.join(out_dir, "all_runs.csv")
        df_sc.to_csv(full_csv_path, index=False)
        print(f"[INFO] Wrote: {full_csv_path}")

        # 2) HV mean by algo
        hv_vals = [mean_col(df_sc, c) for c in metrics_hv_cols]
        hv_plot_path = os.path.join(out_dir, f"hv_mean_by_algo.png")
        barplot_one_metric(
            algo_labels,
            hv_vals,
            ylabel="Hypervolume (mean over runs)",
            title=f"HV mean by algorithm ({selected_experiment} | {scenario})",
            out_path=hv_plot_path
        )
        print(f"[INFO] Wrote: {hv_plot_path}")


    print("\n[OK] Aggregation done.")


if __name__ == "__main__":
    main()
