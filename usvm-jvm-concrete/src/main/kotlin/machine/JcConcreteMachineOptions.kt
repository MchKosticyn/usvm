package machine

import org.jacodb.api.jvm.JcByteCodeLocation

data class JcConcreteMachineOptions(
    val projectLocations: List<JcByteCodeLocation>,
    val dependenciesLocations: List<JcByteCodeLocation>,
)
