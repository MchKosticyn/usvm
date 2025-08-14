package benchmarking

import java.nio.file.Path

class BenchDescription(
    val jarPath: Path,
    val libsPath: Path,
    val propertiesName: String?,
    val logPath: Path,
    val errorsPath: Path
)
