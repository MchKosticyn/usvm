package org.usvm.ps.weighters

class StateWeighterWithCast<in State, InWeight, OutWeight>(
    val weighter: StateWeighterWithReport<State, InWeight>,
    val cast: (InWeight) -> OutWeight
): StateWeighterWithReport<State, OutWeight>() {
    override val weighterName = "${weighter.weighterName} (cast.)"

    override fun weight(state: State) = cast(weighter.weight(state))
    override fun weightWithReport(state: State) =
        CastWeighterReport(weighterName, weighter.weightWithReport(state), cast)
}
