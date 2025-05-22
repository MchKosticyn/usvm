package machine.state.concreteMemory

import machine.JcConcreteMemoryClassLoader
import org.usvm.jvm.util.JcExecutor
import utils.isThreadLocal

internal class JcConcreteExecutor: JcExecutor(JcConcreteMemoryClassLoader), ThreadLocalHelper {
    private val threadLocalType by lazy { ThreadLocal::class.java }

    override fun checkIsPresent(threadLocal: Any): Boolean {
        check(threadLocal.javaClass.isThreadLocal)
        val isPresentMethod = threadLocalType.declaredMethods.find { it.name == "isPresent" }!!
        isPresentMethod.isAccessible = true
        var value = false
        execute {
            val args = if (isPresentMethod.parameterCount == 0) emptyArray<Any>() else arrayOf(Thread.currentThread())
            try {
                value = isPresentMethod.invoke(threadLocal, *args) as Boolean
            } catch (e: Throwable) {
                error("unable to check thread local value is present: $e")
            }
        }

        return value
    }

    override fun getThreadLocalValue(threadLocal: Any): Any? {
        check(threadLocal.javaClass.isThreadLocal)
        val getMethod = threadLocalType.getMethod("get")
        var value: Any? = null
        execute {
            try {
                value = getMethod.invoke(threadLocal)
            } catch (e: Throwable) {
                error("unable to get thread local value: $e")
            }
        }

        check(value == null || !value!!.javaClass.isThreadLocal)
        return value
    }

    override fun setThreadLocalValue(threadLocal: Any, value: Any?) {
        check(threadLocal.javaClass.isThreadLocal)
        check(value == null || !value.javaClass.isThreadLocal)
        val setMethod = threadLocalType.getMethod("set", Any::class.java)
        execute {
            try {
                setMethod.invoke(threadLocal, value)
            } catch (e: Throwable) {
                error("unable to set thread local value: $e")
            }
        }
    }
}
