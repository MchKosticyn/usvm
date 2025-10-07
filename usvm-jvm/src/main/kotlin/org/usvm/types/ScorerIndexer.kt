package org.usvm.types

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcDatabasePersistence
import org.jacodb.api.jvm.RegisteredLocation
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.usvm.util.ApproximationPaths
import java.util.concurrent.ConcurrentHashMap
import kotlin.sequences.orEmpty

class ScorerIndexer(
    private val cp: JcClasspath,
    private val scorer: (JcClassOrInterface) -> Double,
    private val persistence: JcDatabasePersistence,
    private val location: RegisteredLocation,
    approximationPaths: ApproximationPaths
) {
    private val cache = ConcurrentHashMap<Long, Double>()
    private val interner = persistence.symbolInterner

    private val isApproximationsLocation = approximationPaths.presentPaths.any { it in location.path }

    fun getScore(jcClass: JcClassOrInterface): Pair<Long, Double> {
        check(jcClass !is JcUnknownClass)
        val clazzSymbolId = interner.findOrNew(jcClass.name)
        return clazzSymbolId to
                cache.getOrPut(clazzSymbolId) {
                    if (isApproximationsLocation) Double.NEGATIVE_INFINITY else scorer(jcClass)
                }
    }

    val allClassesSorted by lazy {
        location.jcLocation?.classNames?.map {
            getScore(cp.findClass(it))
        }
            ?.sortedByDescending { it.second }
            ?.asSequence()
            ?.map { (id, result) -> result to persistence.findSymbolName(id) }
            .orEmpty()
    }
}
