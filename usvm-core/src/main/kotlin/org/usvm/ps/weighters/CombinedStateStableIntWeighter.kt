package org.usvm.ps.weighters

class CombinedStateStableIntWeighter<in State> : CombinedStateWeighter<State, Int> {

    constructor(
        weighters: List<StateWeighterWithReport<State, Int>>
    ) : super(weighters, StableIntArithmetic)

    constructor(
        weighters: List<StateWeighterWithReport<State, Int>>,
        metaWeights: List<Int>
    ) : super(weighters, metaWeights, StableIntArithmetic)
}
