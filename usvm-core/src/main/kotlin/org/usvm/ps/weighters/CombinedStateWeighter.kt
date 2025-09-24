package org.usvm.ps.weighters

open class CombinedStateWeighter<in State, Weight> : StateWeighterWithReport<State, Weight> {

    override val weighterName = "CombinedStateWeighter"
    private val arithmetic: Arithmetic<Weight>

    private val weightersWithWeights: List<Pair<StateWeighterWithReport<State, Weight>, Weight>>

    constructor(
        weighters: List<StateWeighterWithReport<State, Weight>>,
        arithmetic: Arithmetic<Weight>
    ) : this(weighters, weighters.map { arithmetic.one }, arithmetic)

    constructor(
        weighters: List<StateWeighterWithReport<State, Weight>>,
        metaWeights: List<Weight>,
        arithmetic: Arithmetic<Weight>
    ) {
        check(weighters.isNotEmpty()) { "CombinedStateWeighter must have at least one weighter" }
        this.weightersWithWeights = weighters.zip(metaWeights)
        this.arithmetic = arithmetic
    }

    override fun weight(state: State): Weight = weightWithReport(state).weight

    override fun weightWithReport(state: State) = with(arithmetic) {
        val reports = mutableListOf<WeighterReport<Weight>>()
        val result = weightersWithWeights.fold(zero) { sum, (weighter, metaWeight) ->
            val report = weighter.weightWithReport(state)
            val weight = report.weight.mulTo(metaWeight)
            reports.add(report)
            sum.plusTo(weight)
        }

        CombinedWeighterReport(result, weighterName, reports)
    }
}
