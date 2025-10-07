package org.usvm.types

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.approximation.Approximations
import org.jacodb.approximation.OriginalClassName

interface Scorer {
    fun score(jcClass: JcClassOrInterface): Double
}

class ScorerImpl(
    val cp: JcClasspath,
    features: ScorerImpl.() -> Unit = {}
) : Scorer {

    private val approximationsFeature = cp.features!!.filterIsInstance<Approximations>().single()

    private val features: MutableList<ScorerContext.() -> Unit> = mutableListOf()

    init {
        features()
    }

    fun addFeature(feature: ScorerContext.() -> Unit) {
        features.add(feature)
    }

    fun addConditionFeature(
        condition: ScorerContext.() -> Boolean,
        trueBranch: ScorerContext.() -> Unit,
        falseBranch: ScorerContext.() -> Unit = {}
    ) = addFeature {
        if (condition()) trueBranch else falseBranch
    }

    fun addConditionFeature(
        condition: ScorerContext.() -> Boolean,
        trueBranch: Double,
        falseBranch: Double? = null,
    ) = addConditionFeature(
        condition,
        { score += trueBranch },
        { falseBranch?.let { score += falseBranch } }
    )

    fun addConditionFeature(trueBranch: Double, condition: ScorerContext.() -> Boolean) =
        addConditionFeature(condition, trueBranch)

    class ScorerContext(
        val jcClass: JcClassOrInterface,
        val approximationsFeature: Approximations,
        var score: Double = 0.0
    ) {
        val location by lazy { jcClass.declaration.location }
        val name by lazy { jcClass.name }

        fun hasApproximation() =
            approximationsFeature.findApproximationByOriginOrNull(OriginalClassName(name)) != null
    }

    override fun score(jcClass: JcClassOrInterface): Double {
        val ctx = ScorerContext(jcClass, approximationsFeature)
        features.map { it(ctx) }
        return ctx.score
    }
}
