package org.usvm.ps.weighters

import org.usvm.ps.StateWeighter

abstract class StateWeighterWithReport<in State, out Weight> : StateWeighter<State, Weight> {
    abstract val weighterName: String
    open fun weightWithReport(state: State): WeighterReport<Weight> = SingleWeighterReport(weight(state), weighterName)
}
