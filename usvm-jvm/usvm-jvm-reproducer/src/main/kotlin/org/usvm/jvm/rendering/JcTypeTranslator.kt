package org.usvm.jvm.rendering

import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.type.ArrayType
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.PrimitiveType
import com.github.javaparser.ast.type.PrimitiveType.Primitive
import com.github.javaparser.ast.type.Type
import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcPrimitiveType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.types.JcClassTypeImpl

class JcTypeTranslator(private val importManager: JcImportManager) {

    fun typeReprOf(type: JcType, useSubst: Boolean = true): Type = when (type) {
        is JcPrimitiveType -> PrimitiveType(Primitive.byTypeName(type.typeName).get())
        is JcArrayType -> ArrayType(typeReprOf(type.elementType, useSubst))
        is JcClassType -> typeReprOf(type, useSubst)
        else -> error("throw IllegalStateException() for ${type.typeName}")
    }

    fun typeReprOf(type: JcClassType, useSubst: Boolean = true): ClassOrInterfaceType {
        importManager.tryAdd(type.name, type.jcClass.simpleName)
        return ClassOrInterfaceType(
            type.outerType?.let { typeReprOf(it, false) }, type.name
        ).apply {
            if (useSubst && type.typeArguments.isNotEmpty()) {
                this.setTypeArguments(NodeList(type.typeArguments.map {
                    typeReprOf(it.jcClass.toType())
                }))
            }
        }
    }

    private val JcClassType.name: String
        get() = if (this is JcClassTypeImpl) name else jcClass.name
}
