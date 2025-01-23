package org.usvm.bench.project

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.FileAppender
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import mu.KLogging
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory

class BuildProjectCliCommand : CliktCommand() {

    val projectsRootDir by argument(help = "Project's root dir")
        .path(mustExist = false, canBeFile = false, canBeDir = true)

    val outputDir by option("-o", help = "Project files output dir")
        .path(mustExist = false, canBeFile = false, canBeDir = true).required()

    val workingDir by option("-w", help = "Resolver working dir")
        .path(mustExist = false, canBeFile = false, canBeDir = true)

    override fun run() {
        configureLogger()
        workingDir?.createDirectories()
        val resolverWorkDir = workingDir ?: createTempDirectory("resolver")
        val projects = ProjectResolver.resolveProjects(projectsRootDir, resolverWorkDir)
        val projectIndexer = ProjectIndexer()
        projects.forEach {
            outputDir.createDirectories()
            val projectInfoDir = outputDir.resolve(it.sourceRoot.toString().trimStart(File.separatorChar).replace(File.separator, "_"))
            projectInfoDir.createDirectories()
            projectIndexer.indexProject(it, projectInfoDir)
        }
    }

    private fun configureLogger() {
        val rootLogger = LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger

        val ple = PatternLayoutEncoder().apply {
            pattern = "%d{HH:mm:ss.SSS} |%.-1level| %replace(%c{0}){'(\\\$Companion)?\\\$logger\\\$1',''} - %msg%n"
            context = rootLogger.loggerContext
        }

        val appender = FileAppender<ILoggingEvent>().apply {
            file = outputDir.resolve("usvm.log").absolutePathString()
            isAppend = false
            encoder = ple
            context = rootLogger.loggerContext
        }

        ple.start()
        appender.start()
        rootLogger.addAppender(appender)

        rootLogger.level = Level.DEBUG
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}

fun main(args: Array<String>) = BuildProjectCliCommand().main(args)
