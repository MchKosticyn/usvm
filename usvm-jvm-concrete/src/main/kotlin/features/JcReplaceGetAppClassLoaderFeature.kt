package features

import org.jacodb.api.jvm.JcInstExtFeature
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInstList
import org.jacodb.api.jvm.cfg.JcRawAssignInst
import org.jacodb.api.jvm.cfg.JcRawInst
import org.jacodb.api.jvm.cfg.JcRawLocalVar
import org.jacodb.api.jvm.cfg.JcRawReturnInst
import org.jacodb.api.jvm.cfg.JcRawStaticCallExpr
import org.jacodb.api.jvm.ext.packageName
import org.jacodb.impl.types.TypeNameImpl
import org.usvm.concrete.api.internal.ClassLoaderGetHelper
import org.usvm.jvm.util.javaName

private val CLASS_LOADER = "java.lang.ClassLoader"

// Sometimes libraries request a system classloader. We cant instrument it by replacing the code
// in the first available entry point (jdk.internal.loader.ClassLoader appClassLoader() for example),
// because it is the code of the base library. Therefore, we are forced to replace the functions of external packages.
//
// The base library code is loaded only when the app starts, and only by the
// system classloader (we can never load it with our own).
// However, even if we instrument the base library in Java, we have to run USVM with this instrumented code
// due to Java's specific features (the base library is embedded in the runtime).
object JcReplaceGetAppClassLoaderFeature: JcInstExtFeature {

    private val packageWhiteList = setOf(
        "org.hibernate.boot.registry.classloading.internal"
    )

    private val classWhiteList = setOf(
        "AggregatedClassLoader"
    )

    private val functionsToTransform = setOf(
        "locateSystemClassLoader"
    )

    private fun JcMethod.inPackageWhiteList() = packageWhiteList.contains(enclosingClass.packageName)
    private fun JcMethod.inClassWhiteList() = classWhiteList.contains(enclosingClass.simpleName)
    private fun JcMethod.shouldTransform() = functionsToTransform.contains(name)

    private fun shouldTransform(method: JcMethod, list: JcInstList<JcRawInst>) =
        method.inPackageWhiteList() && method.inClassWhiteList() && method.shouldTransform()

    override fun transformRawInstList(method: JcMethod, list: JcInstList<JcRawInst>): JcInstList<JcRawInst> {
        if (!shouldTransform(method, list)) return list

        val mutableList = list.toMutableList()

        val callVar = JcRawLocalVar(0, "v0", TypeNameImpl.fromTypeName(CLASS_LOADER))
        val callExpr = JcRawStaticCallExpr(
            declaringClass = TypeNameImpl.fromTypeName(ClassLoaderGetHelper::class.java.typeName),
            methodName = ClassLoaderGetHelper::replaceGetClassLoader.javaName,
            argumentTypes = emptyList(),
            returnType = TypeNameImpl.fromTypeName(CLASS_LOADER),
            args = emptyList()
        )

        val assign = JcRawAssignInst(method, callVar, callExpr)
        val ret = JcRawReturnInst(method, callVar)

        val lastInst = mutableList.last()
        mutableList.removeAll(mutableList.filter { it != lastInst })
        mutableList.insertBefore(lastInst, assign, ret)
        mutableList.remove(lastInst)

        return mutableList
    }
}
