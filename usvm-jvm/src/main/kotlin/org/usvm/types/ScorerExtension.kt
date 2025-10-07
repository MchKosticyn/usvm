package org.usvm.types

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcDatabasePersistence
import org.jacodb.api.jvm.RegisteredLocation
import org.jacodb.api.jvm.ext.CONSTRUCTOR
import org.jacodb.api.jvm.ext.findClass
import org.usvm.algorithms.cached
import org.usvm.util.ApproximationPaths
import java.util.PriorityQueue
import java.util.concurrent.ConcurrentHashMap

open class ScorerExtension(
    private val cp: JcClasspath,
    private val persistence: JcDatabasePersistence,
    private val approximationPaths: ApproximationPaths
) {
    private val cache = ConcurrentHashMap<Long, ScorerIndexer>()

    open val scorer = ScorerImpl(cp) {
        addFeature {
            if (location.isRuntime)
                score += if (!name.startsWith("java.")) -1000 else 2
        }

        addConditionFeature(4.0) { jcClass.isPublic }
        addConditionFeature(3.0) { !jcClass.isAbstract && !jcClass.isInterface }
        addFeature { score -= jcClass.declaredFields.size / 10.0 }

        // Prefer easy instantiable classes
        addFeature {
            val emptyPublicConstructorPresents = jcClass.declaredMethods.any {
                it.name == CONSTRUCTOR && it.isPublic && it.parameters.isEmpty()
            }
            if (emptyPublicConstructorPresents) score += 5
            else {
                val publicConstructorPresents =
                    jcClass.declaredMethods.any { it.name == CONSTRUCTOR && it.isPublic }
                if (publicConstructorPresents) score += 3
            }
        }

        addConditionFeature(3.0) { jcClass.outerClass == null }
        addConditionFeature(2.0) { jcClass.isFinal }
        addConditionFeature(10.0) { hasApproximation() }
    }

    private fun newIndexer(location: RegisteredLocation) =
        ScorerIndexer(
            cp,
            scorer::score,
            persistence,
            location,
            approximationPaths
        )

    fun getScore(jcClass: JcClassOrInterface): Double {
        val location = jcClass.declaration.location
        return cache.getOrPut(location.id) { newIndexer(location) }.getScore(jcClass).second
    }

    fun sortedClasses(location: RegisteredLocation): Sequence<Pair<Double, String>> {
        val indexer = cache.getOrPut(location.id) { newIndexer(location) }
        return cache.getOrPut(location.id) { indexer }.allClassesSorted
    }

    val allClassesSorted: Sequence<JcClassOrInterface> by lazy {
        data class Node(
            val result: Double,
            val className: String,
            val other: Iterator<Pair<Double, String>>,
        ) : Comparable<Node> {
            override fun compareTo(other: Node): Int = -result.compareTo(other.result)
        }

        sequence {
            val queue = PriorityQueue<Node>()

            fun advance(iterator: Iterator<Pair<Double, String>>) {
                if (!iterator.hasNext()) {
                    return
                }
                val (result, className) = iterator.next()
                queue.add(Node(result, className, iterator))
            }

            for (location in cp.registeredLocations) {
                val iterator = sortedClasses(location).iterator()
                advance(iterator)
            }

            while (queue.isNotEmpty()) {
                val top = queue.poll()
                val (_, className, iterator) = top
                yield(cp.findClass(className))
                advance(iterator)
            }
        }.cached()
    }
}
