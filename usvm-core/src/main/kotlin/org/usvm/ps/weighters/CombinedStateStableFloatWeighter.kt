package org.usvm.ps.weighters

class CombinedStateStableFloatWeighter<in State> : CombinedStateWeighter<State, Float> {

    constructor(
        weighters: List<StateWeighterWithReport<State, Float>>
    ) : super(weighters, ARITHMETIC)

    constructor(
        weighters: List<StateWeighterWithReport<State, Float>>,
        metaWeights: List<Float>
    ) : super(weighters, metaWeights, ARITHMETIC)

    companion object {
        fun <State> withNorm(weighters: List<NormalizableWeighter<State, Float>>, metaWeights: List<Float>) =
            CombinedStateStableFloatWeighter<State>(weighters.map { it.normalize() }, metaWeights)

        fun <State> fromInt(
            weighters: List<StateWeighterWithReport<State, Int>>,
            metaWeights: List<Float>
        ) = CombinedStateStableFloatWeighter(
            weighters.map { StateWeighterWithCast(it, Int::toFloat) },
            metaWeights
        )

        private val ARITHMETIC = StableFloatArithmetic
    }
}
