package org.usvm.jvm.rendering

import org.jacodb.api.jvm.JcMethod
import org.objectweb.asm.Opcodes

internal val String.normalized: String
    get() = this.replace("<", "").replace(">", "").replace("$", "")

internal val String.decapitalized: String
    get() = replaceFirstChar { it.lowercase() }

internal val String.capitalized: String
    get() = replaceFirstChar { it.titlecase() }

internal val JcMethod.isVararg: Boolean
    get() = access.and(Opcodes.ACC_VARARGS) == Opcodes.ACC_VARARGS