package org.usvm.machine.state.concreteMemory

import java.lang.reflect.Field
import java.util.LinkedList
import java.util.Queue

internal abstract class ObjectTraversal(
    private val threadLocalHelper: ThreadLocalHelper,
    private val skipExceptions: Boolean = false,
) {

    abstract fun skip(phys: PhysicalAddress, type: Class<*>): Boolean

    abstract fun handleArray(phys: PhysicalAddress, type: Class<*>)

    abstract fun handleClass(phys: PhysicalAddress, type: Class<*>)

    abstract fun handleThreadLocal(
        threadLocalPhys: PhysicalAddress,
        valuePhys: PhysicalAddress
    )

    abstract fun handleArrayIndex(arrayPhys: PhysicalAddress, index: Int, valuePhys: PhysicalAddress)

    abstract fun handleClassField(parentPhys: PhysicalAddress, field: Field, valuePhys: PhysicalAddress)

    open fun skipField(field: Field): Boolean = false

    open fun skipArrayIndices(elementType: Class<*>): Boolean = false

    private fun traverseArray(phys: PhysicalAddress, type: Class<*>, traverseQueue: Queue<PhysicalAddress>) {
        handleArray(phys, type)

        if (skipArrayIndices(type.componentType))
            return

        when (val obj = phys.obj!!) {
            is Array<*> -> {
                obj.forEachIndexed { i, v ->
                    val child = PhysicalAddress(v)
                    handleArrayIndex(phys, i, child)
                    traverseQueue.add(child)
                }
            }
            is IntArray -> {
                obj.forEachIndexed { i, v ->
                    val child = PhysicalAddress(v)
                    handleArrayIndex(phys, i, child)
                    traverseQueue.add(child)
                }
            }
            is ByteArray -> {
                obj.forEachIndexed { i, v ->
                    val child = PhysicalAddress(v)
                    handleArrayIndex(phys, i, child)
                    traverseQueue.add(child)
                }
            }
            is CharArray -> {
                obj.forEachIndexed { i, v ->
                    val child = PhysicalAddress(v)
                    handleArrayIndex(phys, i, child)
                    traverseQueue.add(child)
                }
            }
            is LongArray -> {
                obj.forEachIndexed { i, v ->
                    val child = PhysicalAddress(v)
                    handleArrayIndex(phys, i, child)
                    traverseQueue.add(child)
                }
            }
            is FloatArray -> {
                obj.forEachIndexed { i, v ->
                    val child = PhysicalAddress(v)
                    handleArrayIndex(phys, i, child)
                    traverseQueue.add(child)
                }
            }
            is ShortArray -> {
                obj.forEachIndexed { i, v ->
                    val child = PhysicalAddress(v)
                    handleArrayIndex(phys, i, child)
                    traverseQueue.add(child)
                }
            }
            is DoubleArray -> {
                obj.forEachIndexed { i, v ->
                    val child = PhysicalAddress(v)
                    handleArrayIndex(phys, i, child)
                    traverseQueue.add(child)
                }
            }
            is BooleanArray -> {
                obj.forEachIndexed { i, v ->
                    val child = PhysicalAddress(v)
                    handleArrayIndex(phys, i, child)
                    traverseQueue.add(child)
                }
            }
            else -> error("ObjectTraversal.traverse: unexpected array $obj")
        }
    }

    private fun traverseClass(phys: PhysicalAddress, type: Class<*>, traverseQueue: Queue<PhysicalAddress>) {
        handleClass(phys, type)
        val obj = phys.obj!!

        for (field in type.allInstanceFields) {
            if (skipField(field))
                continue
            val value = try {
                field.getFieldValue(obj)
            } catch (e: Throwable) {
                if (skipExceptions) {
                    println("[WARNING] ObjectTraversal.traverse: ${type.name} failed on field ${field.name}, cause: ${e.message}")
                    continue
                }
                error("ObjectTraversal.traverse: ${type.name} failed on field ${field.name}, cause: ${e.message}")
            }
            val valuePhys = PhysicalAddress(value)
            handleClassField(phys, field, valuePhys)
            traverseQueue.add(valuePhys)
        }
    }

    fun traverse(obj: Any?) {
        obj ?: return
        val handledObjects = mutableSetOf<PhysicalAddress>()
        val queue: Queue<PhysicalAddress> = LinkedList()
        queue.add(PhysicalAddress(obj))
        while (queue.isNotEmpty()) {
            val currentPhys = queue.poll()
            val current = currentPhys.obj ?: continue
            val type = current.javaClass
            if (!handledObjects.add(currentPhys) || skip(currentPhys, type))
                continue

            when {
                type.isThreadLocal -> {
                    if (!threadLocalHelper.checkIsPresent(current))
                        continue

                    val valuePhys = PhysicalAddress(threadLocalHelper.getThreadLocalValue(current))
                    queue.add(valuePhys)
                    handleThreadLocal(currentPhys, valuePhys)
                }
                type.isArray -> {
                    traverseArray(currentPhys, type, queue)
                }

                // TODO: add special traverse for standard collections (ArrayList, ...) #CM
                //  care about not fully completed operations of those collections
                else -> {
                    traverseClass(currentPhys, type, queue)
                }
            }
        }
    }
}
