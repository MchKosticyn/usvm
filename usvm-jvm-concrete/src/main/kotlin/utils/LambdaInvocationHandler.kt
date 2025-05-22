package utils

import machine.JcConcreteMemoryClassLoader
import org.jacodb.api.jvm.JcMethod
import org.jacodb.approximation.JcEnrichedVirtualMethod
import org.usvm.jvm.util.invoke
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method

class LambdaInvocationHandler : InvocationHandler {

    private var methodName: String? = null
    private var actualMethod: JcMethod? = null
    private var closureArgs: List<Any?> = listOf()

    fun init(actualMethod: JcMethod, methodName: String, args: List<Any?>) {
        check(actualMethod !is JcEnrichedVirtualMethod)
        this.methodName = methodName
        this.actualMethod = actualMethod
        closureArgs = args
    }

    override fun invoke(proxy: Any?, method: Method, args: Array<Any?>?): Any? {
        if (methodName != null && methodName == method.name) {
            var allArgs =
                if (args == null) closureArgs
                else closureArgs + args
            var thisArg: Any? = null
            val methodToInvoke = actualMethod!!
            if (!methodToInvoke.isStatic) {
                thisArg = allArgs[0]
                allArgs = allArgs.drop(1)
            }
            return methodToInvoke.invoke(JcConcreteMemoryClassLoader, thisArg, allArgs)

        }

        return null
    }
}
