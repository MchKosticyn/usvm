package org.usvm.jvm.rendering.baseRenderer

import org.jacodb.api.jvm.JvmType
import org.jacodb.impl.types.JcTypeVariableDeclarationImpl
import org.jacodb.impl.types.JcTypeVariableImpl
import org.jacodb.impl.types.signature.JvmBoundWildcard
import org.jacodb.impl.types.signature.JvmParameterizedType
import org.jacodb.impl.types.signature.JvmTypeVariable

object JcTypeVariableExt {

    val JcTypeVariableImpl.isRecursive: Boolean get() {
        val declaration = (declaration as? JcTypeVariableDeclarationImpl) ?: return false
        val jvmBounds = declaration.jvmBounds
        val existingSymbols = HashSet<String>(listOf(declaration.symbol))
        return jvmBounds.any { bound -> bound.presentInOrRecursive(existingSymbols) }
    }

    private fun JvmType.presentInOrRecursive(existingSymbols: HashSet<String>): Boolean {
        if (this is JvmTypeVariable) {
            if (this.symbol in existingSymbols) return true

            val bounds = this.declaration?.bounds ?: return false

            return bounds.any { bound ->
                bound.presentInOrRecursive(HashSet(existingSymbols + this.symbol))
            }
        }

        if (this is JvmBoundWildcard) {
            return bound.presentInOrRecursive(existingSymbols)
        }

        if (this is JvmParameterizedType) {
            return this.parameterTypes.any { param ->
                param.presentInOrRecursive(HashSet(existingSymbols))
            }
        }

        return false
    }
}