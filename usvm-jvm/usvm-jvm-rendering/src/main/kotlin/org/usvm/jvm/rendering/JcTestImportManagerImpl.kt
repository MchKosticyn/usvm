package org.usvm.jvm.rendering

import com.github.javaparser.ast.CompilationUnit
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcType
import org.usvm.jvm.rendering.Utils.parseClassOrInterface
import org.usvm.test.api.UTestCastExpression
import org.usvm.test.api.UTestClassExpression
import org.usvm.test.api.UTestConstructorCall
import org.usvm.test.api.UTestGetStaticFieldExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestMethodCall
import org.usvm.test.api.UTestSetStaticFieldStatement
import org.usvm.test.api.UTestStaticMethodCall


class JcTestImportManagerImpl(private val cu: CompilationUnit) : JcTestImportManager {
    private val importedFullNames: MutableSet<String>
    private val importedPackages: MutableSet<String>
    private val fullToSimple: MutableMap<String, String>

    init {
        val (asterisk, nonAsterisk) = cu.imports.partition { import -> import.isAsterisk }
        importedPackages = asterisk.map { decl -> decl.nameAsString }.toMutableSet()
        importedFullNames = nonAsterisk.map { decl -> decl.nameAsString }.toMutableSet()
        fullToSimple = importedFullNames.associateByTo(mutableMapOf()) { it.split(".").last() }
    }

    override fun tryAdd(jcType: JcClassOrInterface) {
        tryAdd(jcType.outerTransitive().name)
    }

    override fun tryAdd(type: String) {
        val clazz = parseClassOrInterface(type).removeTypeArguments()
        if (!fullToSimple.values.contains(clazz.nameAsString)) {
            fullToSimple.putIfAbsent(clazz.nameWithScope, clazz.nameAsString)
            clazz.scope.ifPresent { scope ->
                if (!importedPackages.contains(scope.nameWithScope))
                    cu.addImport(clazz.nameWithScope)
            }
        }
    }

    override fun on(inst: UTestInst) {
        when (inst) {
            is UTestMethodCall -> {
                tryAdd(inst.method.enclosingClass.outerTransitive().name)
            }

            is UTestStaticMethodCall, is UTestConstructorCall -> {
                tryAdd(inst.method!!.enclosingClass.outerTransitive().name)
            }

            is UTestCastExpression, is UTestClassExpression -> {
                if (inst.type != null) tryAdd(inst.type!!.classNameOrTypeName())
            }

            is UTestGetStaticFieldExpression -> {
                tryAdd(inst.field.enclosingClass.outerTransitive().name)
            }

            is UTestSetStaticFieldStatement -> {
                tryAdd(inst.field.enclosingClass.outerTransitive().name)
            }

            else -> {}
        }
    }
    private fun JcClassOrInterface.outerTransitive(): JcClassOrInterface = this.outerClass?.outerTransitive() ?: this
    private fun JcClassType.outerTransitive(): JcClassType = this.outerType?.outerTransitive() ?: this

    private fun JcType.classNameOrTypeName() = when (this) {
        is JcClassType -> this.outerTransitive().typeName
        is JcClassOrInterface -> this.outerTransitive().name
        else -> typeName
    }
}