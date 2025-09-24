package org.usvm.ps.weighters

abstract class WeighterReport<out Weight> {
    abstract val weight: Weight
    abstract val weighterName: String

    open fun report2String() = "[$weighterName $weight]"
}

class SingleWeighterReport<Weight>(
    override val weight: Weight,
    override val weighterName: String
) : WeighterReport<Weight>() {
}

class CombinedWeighterReport<Weight>(
    override val weight: Weight,
    override val weighterName: String,
    val reports: List<WeighterReport<Weight>>
) : WeighterReport<Weight>() {
    override fun report2String() = "[${reports.joinToString(", ") { it.report2String() }}]"
}

class CastWeighterReport<InWeight, OutWeight>(
    override val weighterName: String,
    val report: WeighterReport<InWeight>,
    val cast: (InWeight) -> OutWeight
) : WeighterReport<OutWeight>() {
    override val weight = cast(report.weight)
    override fun report2String() = report.report2String() + "*"
}
