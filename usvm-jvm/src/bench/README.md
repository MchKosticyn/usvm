# USVM JVM Benchmarking Suite

Infrastructure for running USVM on arbitrary repositories and collecting statistics.

## Overview
The USVM Benchmarking Suite provides a streamlined process for:
- Benchmarking the USVM on arbitrary repositories.
- Collecting and analyzing statistics.

## How to Use

### Step 1: Clone the Repository to Benchmark
1. Clone the repository you want to benchmark.

### Step 2: Build the Benchmark Project
2. Run the `:usvm-jvm:buildBenchProject` Gradle task with the following arguments:
   ```
   <path to cloned repo> -o <directory to save built project files>
   ```
  - `<path to cloned repo>`: Path to the repository you cloned.
  - `-o <directory>`: Directory where the built project files will be saved.

3. The project will be built and saved as a consistent artifact, ready for benchmarking.

### Step 3: Locate the Built Project
4. Locate the built project directory in the path specified with `-o`.

### Step 4: Run the Benchmark
5. Run the `:usvm-jvm:runBench` Gradle task with the following arguments:
   ```
   <path to built>
   ```
  - `<path to built>`: Path to the built project directory.

   This will execute USVM on a random sample of methods from the project.

### Additional Options
The `:usvm-jvm:runBench` task supports the following additional options:
- **`-m`**: Run a specific method.
  - Format: `"FQN|descriptor|method_name"`
  - Example: `"org.jivesoftware.openfire.privacy.PrivacyList|(Lorg/dom4j/Element;Z)V|updateList"`
- **`-t`**: Specify a timeout (in seconds) for a method.
- **`-p`**: Specify the parallelism level.
- **`--mongo`**: Save results to a MongoDB database.
- **`-c`**: Add a run identifier (for MongoDB).

### Step 5: Visualize Results (Optional)
If you saved the results to a MongoDB database (`--mongo` flag), you can visualize the data using Python scripts in the `stats_analysis` directory.

#### Visualization Options
- **Bar Chart**: Coverage distribution.
- **Bar Chart**: Distribution of machine steps executed.
- **Markdown Report**: Aggregated information on internal failures.
