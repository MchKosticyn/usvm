import org.usvm.logger
import java.io.File
import kotlin.system.measureNanoTime
import kotlin.time.Duration.Companion.nanoseconds

fun createOrClear(file: File) {
    if (file.exists()) {
        file.listFiles()?.forEach { it.deleteRecursively() }
    } else {
        file.mkdirs()
    }
}

fun <T> logTime(message: String, body: () -> T): T {
    val result: T
    val time = measureNanoTime {
        result = body()
    }
    logger.info { "Time: $message | ${time.nanoseconds}" }
    return result
}
