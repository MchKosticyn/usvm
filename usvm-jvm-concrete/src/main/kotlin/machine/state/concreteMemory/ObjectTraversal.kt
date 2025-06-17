package machine.state.concreteMemory

import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue
import org.usvm.jvm.util.allInstanceFields
import utils.getFieldValue
import utils.isThreadLocal
import java.lang.reflect.Field
import java.util.IdentityHashMap

internal abstract class ObjectTraversal(
    private val threadLocalHelper: ThreadLocalHelper,
    private val skipExceptions: Boolean = false,
) {

    private var stopped = false

    fun stop() {
        stopped = true
    }

    abstract fun skip(obj: Any, type: Class<*>): Boolean

    abstract fun handleArray(array: Any, type: Class<*>)

    abstract fun handleClass(obj: Any, type: Class<*>)

    abstract fun handleThreadLocal(threadLocal: Any, value: Any?)

    open fun skipField(field: Field): Boolean = false

    open fun skipArrayIndices(elementType: Class<*>): Boolean = false

    private fun traverseArray(array: Any, type: Class<*>, traverseQueue: ObjectArrayFIFOQueue<Any>) {
        handleArray(array, type)

        if (skipArrayIndices(type.componentType))
            return

        when (array) {
            is Array<*> -> {
                for (e in array)
                    if (e != null)
                        traverseQueue.enqueue(e)
            }
            is IntArray -> {
                for (e in array)
                    traverseQueue.enqueue(e)
            }
            is ByteArray -> {
                for (e in array)
                    traverseQueue.enqueue(e)
            }
            is CharArray -> {
                for (e in array)
                    traverseQueue.enqueue(e)
            }
            is LongArray -> {
                for (e in array)
                    traverseQueue.enqueue(e)
            }
            is FloatArray -> {
                for (e in array)
                    traverseQueue.enqueue(e)
            }
            is ShortArray -> {
                for (e in array)
                    traverseQueue.enqueue(e)
            }
            is DoubleArray -> {
                for (e in array)
                    traverseQueue.enqueue(e)
            }
            is BooleanArray -> {
                for (e in array)
                    traverseQueue.enqueue(e)
            }
            else -> error("ObjectTraversal.traverse: unexpected array $array")
        }
    }

    private fun traverseClass(obj: Any, type: Class<*>, traverseQueue: ObjectArrayFIFOQueue<Any>) {
        handleClass(obj, type)

        for (field in type.allInstanceFields) {
            if (skipField(field))
                continue
            val value = try {
                field.getFieldValue(obj)
            } catch (e: Throwable) {
                if (skipExceptions) {
                    println("[WARNING] ObjectTraversal.traverse: ${type.typeName} failed on field ${field.name}, cause: ${e.message}")
                    continue
                }
                error("ObjectTraversal.traverse: ${type.typeName} failed on field ${field.name}, cause: ${e.message}")
            }
            if (value != null)
                traverseQueue.enqueue(value)
        }
    }

    fun traverse(obj: Any?): IdentityHashMap<Any, Unit> {
        val handledObjects = IdentityHashMap<Any, Unit>()
        obj ?: return handledObjects

        val queue: ObjectArrayFIFOQueue<Any> = ObjectArrayFIFOQueue()
        queue.enqueue(obj)
        while (!queue.isEmpty && !stopped) {
            val current = queue.dequeue() ?: continue
            val type = current.javaClass
            if (handledObjects.containsKey(current) || skip(current, type))
                continue

            handledObjects[current] = Unit
            when {
                type.isThreadLocal -> {
                    if (!threadLocalHelper.checkIsPresent(current))
                        continue

                    val value = threadLocalHelper.getThreadLocalValue(current)
                    handleThreadLocal(current, value)
                    queue.enqueue(value)
                }
                type.isArray -> {
                    traverseArray(current, type, queue)
                }

                // TODO: add special traverse for standard collections (ArrayList, ...) #CM
                //  care about not fully completed operations of those collections
                else -> {
                    traverseClass(current, type, queue)
                }
            }
        }

        return handledObjects
    }
}
