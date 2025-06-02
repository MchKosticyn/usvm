package machine

import org.jacodb.api.jvm.JcAnnotated
import org.jacodb.api.jvm.JcByteCodeLocation
import org.jacodb.api.jvm.RegisteredLocation

data class JcConcreteMachineOptions(
    val projectLocations: List<JcByteCodeLocation> = emptyList(),
    val dependenciesLocations: List<JcByteCodeLocation> = emptyList(),
) {

    fun isProjectLocation(location: RegisteredLocation): Boolean {
        return projectLocations.any { it == location.jcLocation }
    }

    fun isProjectLocation(annotated: JcAnnotated) = isProjectLocation(annotated.declaration.location)
}
