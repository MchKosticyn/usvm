package org.usvm.bench

import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.runBlocking
import org.jacodb.api.jvm.JcMethod
import org.usvm.PathSelectionStrategy
import org.usvm.PathSelectorCombinationStrategy
import org.usvm.PathSelectorFairnessStrategy
import org.usvm.UMachineOptions
import org.usvm.bench.project.MethodId
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.state.JcState
import java.util.*

data class BenchConfigRecord(
    val timestamp: Long,
    val comment: String,
    val pathSelectors: List<PathSelectionStrategy>,
    val pathSelectorCombinationStrategy: PathSelectorCombinationStrategy,
    val pathSelectorFairnessStrategy: PathSelectorFairnessStrategy,
    val timeoutMillis: Long,
    val randomSeed: Int,
    val randomSamplesToTake: Int,
    val id: String,
    val project: String,
    val javaHome: String
)

data class BenchResultRecord(
    val configId: String,
    val method: String,
    val instsInMethod: Int,
    val successfulStatesCount: Int,
    val exceptionStatesCount: Int,
    val timeElapsedMillis: Long,
    val coverage: Float,
    val stepsMade: Int,
    val statesInPathSelector: Int
)

data class BenchInternalFailureRecord(
    val configId: String,
    val method: String,
    val exception: String
)

class BenchMongoReporter(private val databaseName: String, host: String = "localhost", port: Int = 27017) : BenchStatisticsReporter, AutoCloseable {

    private val client = MongoClient.create("mongodb://$host:$port")

    private val database = client.getDatabase(databaseName)

    private val configsCollectionName = "configs"
    private val resultsCollectionName = "results"
    private val failuresCollectionName = "failures"

    init {
        runBlocking {
            database.createCollection(configsCollectionName)
            database.createCollection(resultsCollectionName)
            database.createCollection(failuresCollectionName)
        }
    }

    override fun reportConfig(
        timestamp: Long,
        options: UMachineOptions,
        jcMachineOptions: JcMachineOptions,
        comment: String,
        randomSeed: Int,
        samplesToTake: Int,
        projectName: String
    ): String {
        val javaHome = System.getProperty("java.home")
        val id = UUID.randomUUID().toString()
        val record = BenchConfigRecord(
            timestamp,
            comment,
            options.pathSelectionStrategies,
            options.pathSelectorCombinationStrategy,
            options.pathSelectorFairnessStrategy,
            options.timeout.inWholeMilliseconds,
            randomSeed,
            samplesToTake,
            id,
            projectName,
            javaHome
        )
        runBlocking {
            val collection = database.getCollection(configsCollectionName, BenchConfigRecord::class.java)
            collection.insertOne(record)
        }
        return id
    }

    override fun reportResult(
        jcMethod: JcMethod,
        states: List<JcState>,
        configId: String,
        coverage: Float,
        timeElapsedMillis: Long,
        stepsMade: Int,
        statesInPathSelector: Int
    ) {
        val record = BenchResultRecord(
            configId,
            MethodId(jcMethod).encodedString,
            jcMethod.rawInstList.size,
            states.filterNot { it.isExceptional }.size,
            states.filter { it.isExceptional }.size,
            timeElapsedMillis,
            coverage,
            stepsMade,
            statesInPathSelector
        )
        runBlocking {
            val collection = database.getCollection(resultsCollectionName, BenchResultRecord::class.java)
            collection.insertOne(record)
        }
    }

    override fun reportInternalFailure(jcMethod: JcMethod, e: Throwable, configId: String) {
        val record = BenchInternalFailureRecord(
            configId,
            MethodId(jcMethod).encodedString,
            "$e ${e.stackTraceToString()}"
        )
        runBlocking {
            val collection = database.getCollection(failuresCollectionName, BenchInternalFailureRecord::class.java)
            collection.insertOne(record)
        }
    }

    override fun close() {
        client.close()
    }
}
