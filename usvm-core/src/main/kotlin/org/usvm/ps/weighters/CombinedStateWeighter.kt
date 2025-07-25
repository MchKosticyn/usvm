package org.usvm.ps.weighters

import org.usvm.ps.StateWeighter

open class CombinedStateWeighter<in State, out Weight, MetaWeight> : StateWeighter<State, Weight> {

    private val weightersWithWeights: List<Pair<StateWeighter<State, Weight>, MetaWeight?>>
    private val weightCombiner: (Weight, Weight) -> Weight
    private val metaWeightApplier: ((Weight, MetaWeight) -> Weight)?

    constructor(
        weighters: List<StateWeighter<State, Weight>>,
        weightCombiner: (Weight, Weight) -> Weight
    ) {
        check(weighters.isNotEmpty()) { "CombinedStateWeighter must have at least one weighter" }
        this.weightersWithWeights = weighters.map { it to null }
        this.weightCombiner = weightCombiner
        this.metaWeightApplier = null
    }

    constructor(
        weighters: List<StateWeighter<State, Weight>>,
        metaWeights: List<MetaWeight>,
        weightCombiner: (Weight, Weight) -> Weight,
        metaWeightApplier: (Weight, MetaWeight) -> Weight
    ) {
        check(weighters.isNotEmpty()) { "CombinedStateWeighter must have at least one weighter" }
        this.weightersWithWeights = weighters.zip(metaWeights)
        this.weightCombiner = weightCombiner
        this.metaWeightApplier = metaWeightApplier
    }

    override fun weight(state: State): Weight {
        var result: Weight? = null
        if (metaWeightApplier != null) {
            val applier: ((Weight, MetaWeight) -> Weight) = metaWeightApplier
            for ((weighter, metaWeight) in weightersWithWeights) {
                val stateWeight = weighter.weight(state)
                val weight = applier(stateWeight, metaWeight!!)
                result = if (result == null) weight else weightCombiner(weight, result)
            }

            return result!!
        }

        for ((weighter, _) in weightersWithWeights) {
            val weight = weighter.weight(state)
            result = if (result == null) weight else weightCombiner(weight, result)
        }

        return result!!
    }
}
