package machine

import org.jacodb.api.jvm.JcByteCodeLocation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.RegisteredLocation

data class JcConcreteMachineOptions(
    val projectLocations: List<JcByteCodeLocation> = emptyList(),
    val dependenciesLocations: List<JcByteCodeLocation> = emptyList(),
) {

    fun isProjectLocation(location: RegisteredLocation): Boolean {
        return projectLocations.any { it == location.jcLocation }
    }

    fun isProjectLocation(method: JcMethod): Boolean {
        return isProjectLocation(method.declaration.location)
    }

    fun isProjectLocation(clazz: JcClassOrInterface): Boolean {
        return isProjectLocation(clazz.declaration.location)
    }
}
