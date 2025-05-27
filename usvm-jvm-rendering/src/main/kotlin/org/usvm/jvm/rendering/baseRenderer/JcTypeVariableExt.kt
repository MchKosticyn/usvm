package org.usvm.jvm.rendering.baseRenderer

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcTypeVariable
import org.jacodb.api.jvm.JvmType
import org.jacodb.api.jvm.ext.findType
import org.jacodb.impl.types.JcTypeVariableDeclarationImpl
import org.jacodb.impl.types.JcTypeVariableImpl
import org.jacodb.impl.types.signature.JvmBoundWildcard
import org.jacodb.impl.types.signature.JvmClassRefType
import org.jacodb.impl.types.signature.JvmParameterizedType
import org.jacodb.impl.types.signature.JvmRefType
import org.jacodb.impl.types.signature.JvmTypeParameterDeclarationImpl
import org.jacodb.impl.types.signature.JvmTypeVariable
import org.jacodb.impl.types.signature.JvmUnboundWildcard

/*
 * TODO: JcTypeVariable require info about declaration
 */
object JcTypeVariableExt {

    val JcTypeVariableImpl.isRecursive: Boolean get() {
        val declaration = (declaration as? JcTypeVariableDeclarationImpl) ?: return false
        val jvmBounds = declaration.jvmBounds

        val existingSymbols = HashSet<JvmType>()

        existingSymbols.add(this.toJvmTypeOrNull()!!)

        return jvmBounds.any { bound -> bound.presentInOrRecursive(existingSymbols, this.classpath) }
    }

    private fun JcTypeVariable.toJvmTypeOrNull(): JvmType? {
        this as? JcTypeVariableImpl ?: return null
        val declaration = (declaration as? JcTypeVariableDeclarationImpl) ?: return null
        val thisTypeDecl = JvmTypeParameterDeclarationImpl(declaration.symbol, declaration.owner, declaration.jvmBounds)
        val thisType = JvmTypeVariable(thisTypeDecl, this.nullable, this.annotations)
        return thisType
    }

    private fun JvmType.presentInOrRecursive(existingSymbols: HashSet<JvmType>, cp: JcClasspath): Boolean {
        return when (this) {

            is JvmTypeVariable -> {
                if (!existingSymbols.add(this)) return true

                val bounds = this.declaration?.bounds ?: return false

                bounds.any { bound ->
                    bound.presentInOrRecursive(HashSet(existingSymbols), cp)
                }
            }

            is JvmBoundWildcard -> {
                if (!existingSymbols.add(bound)) return true

                bound.presentInOrRecursive(existingSymbols, cp)
            }

            is JvmParameterizedType -> {
                if (!existingSymbols.add(this)) return true
                val decl = cp.findType(this.name) as JcClassType

                val params = decl.typeParameters.zip(this.parameterTypes).map { (declParam, realParam) ->
                    if (realParam !is JvmUnboundWildcard) {
                        realParam
                    }
                    else {
                        check(declParam is JcTypeVariableDeclarationImpl) {
                            "JcClassType declaration expected not to have ?"
                        }
                        declParam.jvmBounds.first()
                    }
                }

                return params.any { bound ->
                    bound.presentInOrRecursive(HashSet(existingSymbols), cp)
                }
            }

            is JvmRefType -> {
                if (!existingSymbols.add(this)) return true
                this as? JvmClassRefType ?: return false
                (cp.findType(this.name) as JcClassType).typeParameters.any { declParam ->
                    check(declParam is JcTypeVariableDeclarationImpl) {
                        "JcClassType declaration expected not to have ?"
                    }
                    declParam.jvmBounds.first().presentInOrRecursive(existingSymbols, cp)
                }
            }

            else -> false
        }
    }
}
