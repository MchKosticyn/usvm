package org.usvm.ps.weighters

import org.usvm.ps.StateWeighter

class CombinedStateIntWeighter<in State> : CombinedStateWeighter<State, Int, Int> {

    constructor(
        weighters: List<StateWeighter<State, Int>>
    ) : super(weighters, Int::plus)

    constructor(
        weighters: List<StateWeighter<State, Int>>,
        metaWeights: List<Int>
    ) : super(weighters, metaWeights, Int::plus, Int::times)
}
