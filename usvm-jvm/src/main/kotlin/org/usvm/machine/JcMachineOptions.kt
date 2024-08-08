package org.usvm.machine

import org.jacodb.api.jvm.RegisteredLocation

/**
 * JcMachine specific options.
 * */
data class JcMachineOptions(
    /**
     * During virtual call resolution the machine should consider all possible call implementations.
     * By default, the machine forks on few concrete implementation and ignore remaining.
     * */
    val forkOnRemainingTypes: Boolean = false,

    /**
     * Controls, whether the machine should analyze states with implicit exceptions (e.g. NPE).
     * */
    val forkOnImplicitExceptions: Boolean = true,

    /**
     * Hard constraint for maximal array size.
     * */
    val arrayMaxSize: Int = 1_500,

    val mockNonConcreteVirtualCalls: Boolean = false,
    val useStaticAddressForConstantString: Boolean = true,
    val useConcreteAddressForModelCompletion: Boolean = false,
    val forceRelevantClassInitializers: Boolean = false,
    val skipIrrelevantClassInitializers: Boolean = false,
    val mockComplexMethods: Boolean = false,
    val projectLocations: Set<RegisteredLocation>? = null,
    val dependenciesLocations: Set<RegisteredLocation>? = null,
)
