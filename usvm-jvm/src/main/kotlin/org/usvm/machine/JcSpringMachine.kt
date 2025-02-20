package org.usvm.machine

import bench.JcSpringTestGenMachineObserver
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.usvm.UMachineOptions
import org.usvm.api.spring.JcSpringTest
import org.usvm.api.targets.JcTarget
import org.usvm.machine.state.JcSpringState
import org.usvm.machine.state.JcState
import org.usvm.statistics.UMachineObserver

class JcSpringMachine(
    private val cp: JcClasspath,
    options: UMachineOptions,
    jcMachineOptions: JcMachineOptions = JcMachineOptions(),
    interpreterObserver: JcInterpreterObserver? = null,
) : JcMachine(cp, options, jcMachineOptions, interpreterObserver) {

    val testPool: MutableList<JcSpringTest> = mutableListOf()
    override fun JcState.toCorrespodingStateType(): JcSpringState = JcSpringState.defaultFromJcState(this)
    override fun analyze(method: JcMethod, targets: List<JcTarget>): List<JcSpringState> = analyze(
        listOf(method), targets, listOf(
            JcSpringTestGenMachineObserver(this, cp)
        )
    )

    override fun analyze(
        methods: List<JcMethod>,
        targets: List<JcTarget>,
        extraMachineObservers: List<UMachineObserver<*>>
    ): List<JcSpringState> =
        super.analyze(methods, targets, extraMachineObservers).map { JcSpringState.defaultFromJcState(it) }

}