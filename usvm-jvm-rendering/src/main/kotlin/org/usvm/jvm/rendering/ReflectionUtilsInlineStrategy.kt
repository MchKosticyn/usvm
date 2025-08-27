package org.usvm.jvm.rendering

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.SimpleName
import kotlin.jvm.optionals.getOrNull
import org.jacodb.api.jvm.JcClassOrInterface
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeImportManager

sealed interface ReflectionUtilsInlineStrategy {

    val shouldCollectUtilUsage: Boolean get() = this !is NoInline

    val isOpenForReflection: (JcClassOrInterface) -> Boolean

    class NoInline(override val isOpenForReflection: ((JcClassOrInterface) -> Boolean) = { false }) : ReflectionUtilsInlineStrategy {
        override fun addReflectionUtils(
            importManager: JcUnsafeImportManager,
            cu: CompilationUnit
        ): CompilationUnit {
            return cu
        }
    }

    class Inline(override val isOpenForReflection: ((JcClassOrInterface) -> Boolean) = { false }) : ReflectionUtilsInlineStrategy {
        override fun addReflectionUtils(
            importManager: JcUnsafeImportManager,
            cu: CompilationUnit
        ): CompilationUnit {
            val testClass = cu.types.singleOrNull()

            check(testClass != null) {
                "exactly one test class expected"
            }

            check(!testClass.getFieldByName("UNSAFE").isPresent) {
                "field and init blocks merge not yet supported"
            }

            val filteredUtilCu = filterReflectionUtilCu(importManager) ?: return cu

            val requiredUtilMembers = filteredUtilCu.getClassByName("ReflectionUtils").get().members

            for(member in requiredUtilMembers) {
                if (member.isMethodDeclaration) {
                    member.asMethodDeclaration().setModifiers(Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC)
                }
            }

            testClass.members.addAll(requiredUtilMembers)

            cu.imports.addAll(filteredUtilCu.imports)

            return cu
        }
    }

    class NestedClass(override val isOpenForReflection: (JcClassOrInterface) -> Boolean = { false }) : ReflectionUtilsInlineStrategy {
        override fun addReflectionUtils(
            importManager: JcUnsafeImportManager,
            cu: CompilationUnit
        ): CompilationUnit {
            val testClass = cu.types.singleOrNull()

            check(testClass != null) {
                "exactly one test class expected"
            }

            val filteredUtilCu = filterReflectionUtilCu(importManager) ?: return cu

            var utilsClass =
                testClass.members.firstOrNull {
                    it is ClassOrInterfaceDeclaration && it.isNestedType && it.name == SimpleName("ReflectionUtils")
                } as? ClassOrInterfaceDeclaration

            val requiredUtils = filteredUtilCu.getClassByName("ReflectionUtils").get()

            if (utilsClass != null) {
                mergeUtilClass(utilsClass, requiredUtils)
            } else {
                utilsClass = requiredUtils
                testClass.addMember(utilsClass)
            }

            cu.imports.addAll(filteredUtilCu.imports)

            utilsClass.setModifiers(Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC)

            return cu
        }
    }

    class OuterClass(override val isOpenForReflection: (JcClassOrInterface) -> Boolean = { false }) : ReflectionUtilsInlineStrategy {
        override fun addReflectionUtils(
            importManager: JcUnsafeImportManager,
            cu: CompilationUnit
        ): CompilationUnit {
            val filteredUtilCu = filterReflectionUtilCu(importManager)
            if (filteredUtilCu == null) return cu

            var currentUtilsClass = cu.getClassByName("ReflectionUtils").getOrNull()
            val requiredUtilsClass = filteredUtilCu.getClassByName("ReflectionUtils").get()

            if (currentUtilsClass != null) {
                mergeUtilClass(currentUtilsClass, requiredUtilsClass)
            } else {
                currentUtilsClass = requiredUtilsClass
                cu.addType(currentUtilsClass)
            }

            cu.imports.addAll(filteredUtilCu.imports)

            currentUtilsClass.modifiers = NodeList()

            return cu
        }
    }

    fun addReflectionUtils(importManager: JcUnsafeImportManager, cu: CompilationUnit): CompilationUnit

    companion object {
        private val utilsCu: CompilationUnit by lazy {
            this::class.java.classLoader.getResourceAsStream("ReflectionUtils.java").use { stream ->
                StaticJavaParser.parse(stream)
            }
        }

        protected fun filterReflectionUtilCu(importManager: JcUnsafeImportManager): CompilationUnit? {
            val usedMethods = importManager.extractUsedUsvmUtilMethods()
            if (usedMethods.isEmpty()) return null

            val cu = utilsCu.clone()
            val utilsClass = cu.getClassByName("ReflectionUtils").get()

            utilsClass.members.removeIf { it.isMethodDeclaration && (it.asMethodDeclaration().name.asString() !in usedMethods) }
            cu.allContainedComments.forEach { it.remove() }

            return cu
        }

        protected fun mergeUtilClass(prev: ClassOrInterfaceDeclaration, extra: ClassOrInterfaceDeclaration) {
            val declaredMembersNames =
                prev.members.mapNotNull { if (it.isMethodDeclaration) it.asMethodDeclaration().name else null }

            extra.members.filter { it.isMethodDeclaration }.forEach { declaration ->
                if (declaration.asMethodDeclaration().name !in declaredMembersNames) {
                    prev.addMember(declaration)
                }
            }
        }
    }
}
