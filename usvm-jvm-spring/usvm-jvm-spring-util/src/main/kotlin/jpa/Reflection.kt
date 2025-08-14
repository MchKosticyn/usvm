package jpa

import machine.JcConcreteMemoryClassLoader
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.allSuperHierarchy
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.impl.storage.jooq.tables.Fields
import org.jacodb.impl.util.adjustEmptyList
import org.usvm.jvm.util.allFields
import org.usvm.jvm.util.getFieldValue
import org.usvm.jvm.util.invoke
import org.usvm.jvm.util.withAccessibility

abstract class InstanceWrapper protected constructor(
    val cp: JcClasspath,
    private val className: String,
    private val instance: Any
) {
    constructor(cp: JcClasspath, className: String, args: List<Any>) :
            this(cp, className, createInstance(cp, className, args))

    init {
        check(instance.javaClass.name == className)
    }

    val clazz by lazy { cp.findClass(className) }

    fun getInstance() = instance

    fun callMethod(methodName: String, args: List<Any>, filter: (JcMethod) -> Boolean = { true } ) =
        allMethods(clazz)
            .filter { it.name == methodName && it.parameters.size == args.size }
            .single(filter)
            .invoke(JcConcreteMemoryClassLoader, instance, args)

    fun getFieldValue(fieldName: String) =
        allFields(clazz)
            .single { it.name == fieldName }
            .getFieldValue(JcConcreteMemoryClassLoader, instance)

    companion object {
        private fun createInstance(cp: JcClasspath, className: String, args: List<Any>) =
            cp.findClass(className).declaredMethods
                .single { it.name == JAVA_INIT && it.parameters.size == args.size }
                .invoke(JcConcreteMemoryClassLoader, null, args)!!
    }
}

private fun allMethods(clazz: JcClassOrInterface) =
    clazz.allSuperHierarchy.flatMap(JcClassOrInterface::declaredMethods) + clazz.declaredMethods
private fun allFields(clazz: JcClassOrInterface) =
    clazz.allSuperHierarchy.flatMap(JcClassOrInterface::declaredFields) + clazz.declaredFields

fun callStaticMethod(cp: JcClasspath, methodName: String, className: String, args: List<Any>) =
    cp.findClass(className).let { allMethods(it) }.filter(JcMethod::isStatic)
        .single { it.name == methodName && it.parameters.size == args.size }
        .invoke(JcConcreteMemoryClassLoader, null, args)

@Suppress("UNCHECKED_CAST")
// term could be loaded by one ClassLoader, but we work with other loader in usvm
// so we need to reassemble it with our ClassLoader
fun reloadJpaTerm(term : Any?) : Any? {
    if (term == null) return null

    val objType = term.javaClass

    if (term is java.util.List<*>) {
        val javaCtor = objType.constructors.singleOrNull { it.parameters.isEmpty() }
            ?: error { "unable to find constructor for ${objType.name}" }
        val list = javaCtor.withAccessibility { javaCtor.newInstance() } as java.util.List<Any?>
        for (e in term) list.add(reloadJpaTerm(e))
        return list
    }

    // check term package to not reassemble base type and etc
    if (objType.packageName.startsWith("org.usvm.spring.query")) {

        val newType = Class.forName(objType.name)

        val newTypeFields = newType.allFields.sortedBy { it.name }

        if (term is Enum<*>) {
            return newTypeFields.single { it.name == term.name }.getFieldValue(null)
        }

        val javaCtor = newType.constructors.single { it.parameters.isEmpty() }
        val instance = javaCtor.withAccessibility { javaCtor.newInstance() }
        val objFields = objType.allFields.sortedBy { it.name }

        objFields.zip(newTypeFields).forEach { (objField, newTypeField) ->
            val fieldValue = reloadJpaTerm(objField.get(term))
            newTypeField.set(instance, fieldValue)
        }

        return instance
    }

    return term
}
