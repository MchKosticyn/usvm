import matplotlib.pyplot as plt
import pandas as pd
import os
import numpy as np
from pymongo import MongoClient

RESULTS_DIR = "<insert path to save results here>"

# Connect to MongoDB
client = MongoClient("mongodb://localhost:27017/")  # Update connection string as needed
db = client["usvm_bench"]

# Fetch data from the collections
results = list(
    db["results"].find(
        {}, {"configId": 1, "coverage": 1, "timeElapsedMillis": 1, "stepsMade": 1}
    )
)
configs = list(
    db["configs"].find(
        {},
        {"id": 1, "timeoutMillis": 1, "project": 1, "pathSelectors": 1, "comment": 1},
    )
)
failures = list(db["failures"].find({}, {"configId": 1, "exception": 1, "method": 1}))

# Convert collections into DataFrames
df_results = pd.DataFrame(results)
df_configs = pd.DataFrame(configs)
df_failures = pd.DataFrame(failures)

# Ensure necessary fields exist
if not {"configId", "coverage", "timeElapsedMillis"}.issubset(df_results.columns):
    print("Error: Required fields are missing in the results collection.")
    exit()

if not {"id", "timeoutMillis"}.issubset(df_configs.columns):
    print("Error: Required fields are missing in the configs collection.")
    exit()

if "configId" not in df_failures.columns:
    print("Error: 'configId' field is missing in the failures collection.")
    exit()

# Prepare configs DataFrame for merging
df_configs = df_configs.rename(columns={"id": "configId"})
df_configs["timeoutMillis"] = df_configs["timeoutMillis"].astype(int)

# Merge configs with results
df_results = pd.merge(df_results, df_configs, on="configId", how="left")

# Count the number of failures for each configId
failure_counts = df_failures["configId"].value_counts()

# Merge configs with failures
df_failures = pd.merge(df_failures, df_configs, on="configId", how="left")

# Define bins for histogram
bins = [0, 1, 10, 20, 30, 40, 50, 60, 70, 80, 90, 99, 100]
labels = [
    "0",
    "1-10",
    "11-20",
    "21-30",
    "31-40",
    "41-50",
    "51-60",
    "61-70",
    "71-80",
    "81-90",
    "91-99",
    "100",
]

# Define fixed bins and labels for stepsMade
steps_bins = [0, 1, 16, 101, 1001, 10001, 100001, 1000001, float("inf")]
steps_labels = [
    "0",
    "1-15",
    "16-100",
    "101-1000",
    "1001-10000",
    "10001-100000",
    "100001-1000000",
    ">1000000",
]

# Group by configId and create stacked histograms
for config_id, group in df_results.groupby("configId"):
    # Extract coverage values and timeout information
    coverage_values = group["coverage"]
    steps_values = group["stepsMade"]
    time_elapsed = group["timeElapsedMillis"]
    timeout = group["timeoutMillis"].iloc[0]
    project_name = group["project"].iloc[0]
    path_selectors = group["pathSelectors"].iloc[0]
    comment = group["comment"].iloc[0]

    # Sanitize folder name from the comment
    folder_name = "".join(c if c.isalnum() or c in " _-" else "_" for c in comment)
    folder_path = os.path.join(
        RESULTS_DIR,
        folder_name,
    )
    os.makedirs(folder_path, exist_ok=True)  # Create folder if it doesn't exist

    # Categorize results based on timeout
    timeout_exceeded = time_elapsed >= timeout
    timeout_not_exceeded = time_elapsed < timeout

    # Create histogram data
    timeout_hist = (
        pd.cut(
            coverage_values[timeout_exceeded],
            bins=bins,
            labels=labels,
            include_lowest=True,
        )
        .value_counts()
        .sort_index()
    )
    not_timeout_hist = (
        pd.cut(
            coverage_values[timeout_not_exceeded],
            bins=bins,
            labels=labels,
            include_lowest=True,
        )
        .value_counts()
        .sort_index()
    )

    # Initialize all bins to zero for consistency
    timeout_hist = timeout_hist.reindex(labels, fill_value=0)
    not_timeout_hist = not_timeout_hist.reindex(labels, fill_value=0)

    # Add failure counts to the 0-coverage bar
    # zero_coverage_failures = failure_counts.get(config_id, 0)
    # failures_hist = pd.Series(0, index=labels)
    # failures_hist["0"] = zero_coverage_failures

    # Plot the stacked histogram
    plt.figure(figsize=(10, 6))
    plt.bar(
        labels,
        not_timeout_hist.values,
        width=0.8,
        color="lightgreen",
        label="Timeout <",
        align="center",
    )
    plt.bar(
        labels,
        timeout_hist.values,
        width=0.8,
        bottom=not_timeout_hist.values,
        color="gray",
        label="Timeout >=",
        align="center",
    )
    # plt.bar(
    #     labels,
    #     failures_hist.values,
    #     width=0.8,
    #     bottom=(not_timeout_hist.values + timeout_hist.values),
    #     color="darkred",
    #     label="Failures",
    #     align="center",
    # )

    plt.title(
        f"Histogram of Coverage: {project_name}, t={timeout}ms, ps={path_selectors}"
    )
    plt.xlabel("Coverage Range")
    plt.ylabel("Frequency")
    plt.xticks(rotation=45)
    plt.legend()
    plt.grid(axis="y", linestyle="--", alpha=0.7)
    plt.tight_layout()

    # Save chart to folder
    chart_path = os.path.join(folder_path, f"{config_id}_coverage_histogram.png")
    if not os.path.exists(chart_path):
        plt.savefig(chart_path)
    plt.close()

    # Categorize results by coverage ranges
    coverage_0 = coverage_values == 0
    coverage_1_50 = (coverage_values > 0) & (coverage_values <= 50)
    coverage_51_99 = (coverage_values > 50) & (coverage_values < 100)
    coverage_100 = coverage_values == 100

    # Create histogram data for stepsMade
    hist_0 = (
        pd.cut(
            steps_values[coverage_0],
            bins=steps_bins,
            labels=steps_labels,
            include_lowest=True,
        )
        .value_counts()
        .sort_index()
    )
    hist_1_50 = (
        pd.cut(
            steps_values[coverage_1_50],
            bins=steps_bins,
            labels=steps_labels,
            include_lowest=True,
        )
        .value_counts()
        .sort_index()
    )
    hist_51_99 = (
        pd.cut(
            steps_values[coverage_51_99],
            bins=steps_bins,
            labels=steps_labels,
            include_lowest=True,
        )
        .value_counts()
        .sort_index()
    )
    hist_100 = (
        pd.cut(
            steps_values[coverage_100],
            bins=steps_bins,
            labels=steps_labels,
            include_lowest=True,
        )
        .value_counts()
        .sort_index()
    )

    # Initialize all bins to zero for consistency
    hist_0 = hist_0.reindex(steps_labels, fill_value=0)
    hist_1_50 = hist_1_50.reindex(steps_labels, fill_value=0)
    hist_51_99 = hist_51_99.reindex(steps_labels, fill_value=0)
    hist_100 = hist_100.reindex(steps_labels, fill_value=0)

    # Add failure counts to the 0-steps bar
    # zero_steps_failures = failure_counts.get(config_id, 0)
    # failures_hist = pd.Series(0, index=steps_labels)
    # failures_hist["0"] = zero_steps_failures

    # Plot the stacked histogram for stepsMade
    plt.figure(figsize=(10, 6))
    # plt.bar(
    #     steps_labels,
    #     failures_hist.values,
    #     width=0.8,
    #     color="darkred",
    #     label="Failures",
    #     align="center",
    # )
    plt.bar(
        steps_labels,
        hist_0.values,
        width=0.8,
        color="red",
        label="0 Coverage",
        align="center",
    )
    plt.bar(
        steps_labels,
        hist_1_50.values,
        width=0.8,
        bottom=(hist_0.values),
        color="yellow",
        label="1-50 Coverage",
        align="center",
    )
    plt.bar(
        steps_labels,
        hist_51_99.values,
        width=0.8,
        bottom=(hist_0.values + hist_1_50.values),
        color="lightgreen",
        label="51-99 Coverage",
        align="center",
    )
    plt.bar(
        steps_labels,
        hist_100.values,
        width=0.8,
        bottom=(hist_0.values + hist_1_50.values + hist_51_99.values),
        color="green",
        label="100 Coverage",
        align="center",
    )
    plt.title(f"Histogram of Steps: {project_name}, t={timeout}ms, ps={path_selectors}")
    plt.xlabel("Steps Range")
    plt.ylabel("Frequency")
    plt.xticks(rotation=45)
    plt.legend()
    plt.grid(axis="y", linestyle="--", alpha=0.7)
    plt.tight_layout()

    # Save steps chart
    chart_path = os.path.join(folder_path, f"{config_id}_steps_histogram.png")
    if not os.path.exists(chart_path):
        plt.savefig(chart_path)
    plt.close()

# Create Markdown files
for config_id, group in df_failures.groupby("configId"):
    comment = group["comment"].iloc[0]

    # Sanitize folder name from the comment
    folder_name = "".join(c if c.isalnum() or c in " _-" else "_" for c in comment)
    folder_path = os.path.join(
        RESULTS_DIR,
        folder_name,
    )
    os.makedirs(folder_path, exist_ok=True)  # Create folder if it doesn't exist

    # Path for the Markdown file
    markdown_path = os.path.join(folder_path, f"{config_id}_failures.md")

    # Prepare data for sorting exceptions
    exception_data = (
        group.groupby("exception")  # Group by exception
        .agg(
            methods=("method", lambda x: list(set(x))),  # Deduplicate methods
            count=("method", lambda x: len(set(x))),  # Count unique methods
        )
        .sort_values(by="count", ascending=False)  # Sort by method count (descending)
    )

    # Start writing Markdown content
    with open(markdown_path, "w") as md_file:
        md_file.write(f"# Failures for ConfigId: {config_id}\n\n")
        md_file.write(f"Comment: {comment}\n\n")

        # Write sorted exceptions
        for exception, data in exception_data.iterrows():
            methods = data["methods"]
            md_file.write(f"## Exception:\n")
            md_file.write(f"```\n{exception}\n```\n")
            md_file.write(f"- [ ] **Resolved**:\n")
            md_file.write(f"- **Number of Methods**: {data['count']}\n")
            md_file.write("- **Methods**:\n")
            for method in methods:
                md_file.write(f"  - {method}\n")
            md_file.write("\n")
