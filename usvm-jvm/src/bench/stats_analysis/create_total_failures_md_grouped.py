import os
import pandas as pd
import re
from pymongo import MongoClient

# Connect to MongoDB
client = MongoClient("mongodb://localhost:27017/")  # Update connection string as needed
db = client["usvm_bench"]

# Fetch data from the collections
failures = list(db["failures"].find({}, {"configId": 1, "exception": 1, "method": 1}))
configs = list(db["configs"].find({}, {"id": 1, "comment": 1, "project": 1}))

# Convert collections into DataFrames
df_failures = pd.DataFrame(failures)
df_configs = pd.DataFrame(configs)

# Ensure necessary fields exist
if not {"configId", "exception", "method"}.issubset(df_failures.columns):
    print("Error: Required fields are missing in the failures collection.")
    exit()

if not {"id", "comment", "project"}.issubset(df_configs.columns):
    print("Error: Required fields are missing in the configs collection.")
    exit()

# Prepare configs DataFrame for merging
df_configs = df_configs.rename(columns={"id": "configId"})

# Merge configs with failures
df_failures = pd.merge(df_failures, df_configs, on="configId", how="left")

# Filter failures by specific comments
selected_comments = [
    "14.01.2025 touch sbft",
    "14.01.2025",
]  # Replace with the desired comments
df_filtered = df_failures[df_failures["comment"].isin(selected_comments)]


# Extract exception type and top 5 stack trace lines
def simplify_exception(exception_text):
    # Extract exception type
    exception_type_match = re.match(r"^([\w.$]+):", exception_text)
    exception_type = (
        exception_type_match.group(1) if exception_type_match else "UnknownException"
    )

    # Extract stack trace lines
    stack_trace_lines = re.findall(r"^\s*at\s+.+$", exception_text, re.MULTILINE)
    top_stack_trace = stack_trace_lines[:5]  # Take the first 5 lines
    top_stack_trace_text = "\n".join(top_stack_trace)

    return f"{exception_type}\n{top_stack_trace_text}"


df_filtered["simplified_exception"] = df_filtered["exception"].apply(simplify_exception)

# Prepare data for Markdown
exception_data = (
    df_filtered.groupby(
        ["simplified_exception", "project"]
    )  # Group by simplified exception and project
    .agg(
        methods=("method", lambda x: list(set(x)))  # Deduplicate methods
    )
    .reset_index()
)

# Calculate total method count for sorting simplified exceptions
exception_totals = (
    exception_data.groupby("simplified_exception")
    .agg(total_methods=("methods", lambda x: sum(len(methods) for methods in x)))
    .sort_values(by="total_methods", ascending=False)
    .reset_index()
)

# Path for the total failures Markdown file
markdown_path = "total_failures_simplified.md"

# Start writing Markdown content
with open(markdown_path, "w") as md_file:
    md_file.write("# Total Failures (Simplified)\n\n")
    md_file.write(f"Filtered by Comments: {', '.join(selected_comments)}\n\n")

    # Write sorted simplified exceptions
    for _, row in exception_totals.iterrows():
        simplified_exception = row["simplified_exception"]
        total_methods = row["total_methods"]

        md_file.write(f"## Simplified Exception:\n")
        md_file.write(f"```\n{simplified_exception}\n```\n")
        md_file.write(f"- **Total Methods**: {total_methods}\n\n")

        exception_group = exception_data[
            exception_data["simplified_exception"] == simplified_exception
        ]
        for _, project_row in exception_group.iterrows():
            project = project_row["project"]
            methods = project_row["methods"]

            md_file.write(f"- **Project**: {project}\n")
            md_file.write("  - **Methods**:\n")
            for method in methods:
                md_file.write(f"    - {method}\n")

        md_file.write("\n")
