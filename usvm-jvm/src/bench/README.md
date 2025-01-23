# USVM Benchmarking Suite

Infrastructure for running USVM on arbitrary repositories and collecting statistics.

## Overview
The USVM Benchmarking Suite provides a streamlined process for:
- Benchmarking the USVM on arbitrary repositories.
- Collecting and analyzing statistics.

## How to Use

### Step 1: Set Java Environment Variables
Before building the project, ensure the following environment variables are set to the corresponding Java home directories. Different projects may require different Java versions:

- `JAVA_8_HOME`
- `JAVA_11_HOME`
- `JAVA_17_HOME`
- `JAVA_21_HOME`

### Step 2: Clone the Repository to Benchmark
1. Clone the repository you want to benchmark.

### Step 3: Build the Benchmark Project
2. Run the `:usvm-jvm:buildBenchProject` Gradle task with the following arguments:
   ```
   <path to cloned repo> -o <directory to save built project files>
   ```
   - `<path to cloned repo>`: Path to the repository you cloned.
   - `-o <directory>`: Directory where the built project files will be saved.

3. The project will be built and saved as a consistent artifact, ready for benchmarking.

### Step 4: Locate the Built Project
4. Locate the built project directory in the path specified with `-o`.

### Step 5: Run the Benchmark
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

### Step 6: Visualize Results (Optional)
If you saved the results to a MongoDB database (`--mongo` flag), you can visualize the data using Python scripts in the `stats_analysis` directory.

#### Visualization Options
- **Bar Chart**: Coverage distribution.
- **Bar Chart**: Distribution of machine steps executed.
- **Markdown Report**: Aggregated information on internal failures.

## Benchmarks

Here are the projects (GitHub URL and commit) which have already been used to bench USVM:
```
https://github.com/FasterXML/jackson-databind.git 92181dfae2d857ed728df4a8c5ab904f9eb323c1
https://github.com/jgrapht/jgrapht.git 6324b1354547505d5b62f65a9bf507b54b04e41a
https://github.com/igniterealtime/Openfire.git 9fbe875827d9150f5ab1a225908ba03bb6a6d76d
https://github.com/sanluan/PublicCMS.git 7d648ceb0cd6300bfe2cb5a884a8bb7909dd2e7d
https://github.com/shopizer-ecommerce/shopizer 054a3bde1ea8894d13b0a8fb4e28f9db17262224
https://github.com/WebGoat/WebGoat.git 85103bbcad799e84d39ea9d2934adac217824a7f
https://github.com/alibaba/fastjson.git c942c83443117b73af5ad278cc780270998ba3e1
https://github.com/INRIA/spoon.git c7b2113a61b99cd8b99b293e08f89692cca72f3b
https://github.com/apache/incubator-seata.git 6799dc974ff78fc55e53b2acf41b1bbd5754dac8
https://github.com/datadog/datadog-api-client-java.git 68830497d8382fd01b434e26e5ecef45a4f7a83b
https://github.com/apache/fineract.git 56ee2eaaffe6ddee443264d69b18f1f2425658f2
https://github.com/apache/tinkerpop.git 0eef27f7fc39455d019e3a33cef6ea8a585aead7
https://github.com/exchange-core/exchange-core.git 2f8548749839e9095c8dc597e4b61521d259fa5d
https://github.com/visallo/vertexium.git 5d70548168a92f872abe2ea16651b5a6025ab6ac
https://github.com/geonetwork/core-geonetwork.git c5b3fa66fdc8b54b53c030185c3a304a07061e69
https://github.com/nashtech-garage/yas.git 3945ce6decbadd0c440ebfac0bfd85375cb523bd
https://github.com/SmartBear/soapui.git f46a66022ba800a31678ccd7813e2fdf77ec97d9
https://github.com/ohdsi/athena.git 31be79f02ca82e033abc008610bd7169f0bf8fb2
https://github.com/eclipse/kapua.git 38c4367fe7b5b3cea9f36aa8ea403460330b8d70
```
