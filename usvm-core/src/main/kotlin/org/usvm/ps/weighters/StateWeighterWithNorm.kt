package org.usvm.ps.weighters

fun interface NormalizableWeighter<in State, out Weight> {
    fun normalize(): StateWeighterWithReport<State, Weight>
}

enum class WeightNormalizerType {
    POSITIVE, // normed values should be from [0; 1]
    NEGATIVE, // -.- from [-1; 0]
    CUSTOM    // own min and max values to norm
}

class StateWeighterWithNorm<in State, InWeight, OutWeight>(
    val weighter: StateWeighterWithReport<State, InWeight>,
    val cast: InWeight.() -> OutWeight,
    val maxWeight: OutWeight,
    val minWeight: OutWeight,
    val arithmetic: Arithmetic<OutWeight>,
    val type: WeightNormalizerType
) : StateWeighterWithReport<State, OutWeight>() {

    init {
        check(type != WeightNormalizerType.CUSTOM) { "CUSTOM type for StateWeighterWithNorm is not implemented" }
        check(arithmetic.isGreater(maxWeight, minWeight)) { "MAX_WEIGHT is bigger that MIN_WEIGHT in Weighter norm" }
    }

    override val weighterName = "${weighter.weighterName} (n.)"

    override fun weight(state: State) = with(arithmetic) {
        val weight = weighter.weight(state).cast()
        val norm = when {
            weight.isGreaterTo(maxWeight) -> one
            weight.isLessTo(minWeight) -> zero
            else -> weight.minusTo(minWeight).divTo(maxWeight.minusTo(minWeight))
        }

        if (type == WeightNormalizerType.POSITIVE) norm else norm.minusTo(one)
    }

    companion object {
        fun <State> normalizeInt(
            weighter: StateWeighterWithReport<State, Int>,
            maxWeight: Int, minWeight: Int,
            type: WeightNormalizerType
        ) = StateWeighterWithNorm(weighter, { this }, maxWeight, minWeight, StableIntArithmetic, type)

        fun <State> normalizeFloat(
            weighter: StateWeighterWithReport<State, Float>,
            maxWeight: Float,
            minWeight: Float,
            type: WeightNormalizerType
        ) = StateWeighterWithNorm(weighter, { this }, maxWeight, minWeight, StableFloatArithmetic, type)

        fun <State> normalizeIntToFloat(
            weighter: StateWeighterWithReport<State, Int>,
            maxWeight: Int,
            minWeight: Int,
            type: WeightNormalizerType
        ) = StateWeighterWithNorm(
            weighter,
            { this.toFloat() },
            maxWeight.toFloat(),
            minWeight.toFloat(),
            StableFloatArithmetic,
            type
        )
    }
}
