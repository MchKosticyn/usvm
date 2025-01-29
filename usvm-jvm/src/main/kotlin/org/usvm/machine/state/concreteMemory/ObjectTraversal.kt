package org.usvm.machine.state.concreteMemory

import java.lang.reflect.Field
import java.util.LinkedList
import java.util.Queue

internal abstract class ObjectTraversal(
    private val threadLocalHelper: ThreadLocalHelper,
    private val skipExceptions: Boolean
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
                    handleArray(currentPhys, type)

                    if (skipArrayIndices(type.componentType))
                        continue

                    when (current) {
                        is Array<*> -> {
                            current.forEachIndexed { i, v ->
                                val child = PhysicalAddress(v)
                                handleArrayIndex(currentPhys, i, child)
                                queue.add(child)
                            }
                        }
                        is IntArray -> {
                            current.forEachIndexed { i, v ->
                                val child = PhysicalAddress(v)
                                handleArrayIndex(currentPhys, i, child)
                                queue.add(child)
                            }
                        }
                        is ByteArray -> {
                            current.forEachIndexed { i, v ->
                                val child = PhysicalAddress(v)
                                handleArrayIndex(currentPhys, i, child)
                                queue.add(child)
                            }
                        }
                        is CharArray -> {
                            current.forEachIndexed { i, v ->
                                val child = PhysicalAddress(v)
                                handleArrayIndex(currentPhys, i, child)
                                queue.add(child)
                            }
                        }
                        is LongArray -> {
                            current.forEachIndexed { i, v ->
                                val child = PhysicalAddress(v)
                                handleArrayIndex(currentPhys, i, child)
                                queue.add(child)
                            }
                        }
                        is FloatArray -> {
                            current.forEachIndexed { i, v ->
                                val child = PhysicalAddress(v)
                                handleArrayIndex(currentPhys, i, child)
                                queue.add(child)
                            }
                        }
                        is ShortArray -> {
                            current.forEachIndexed { i, v ->
                                val child = PhysicalAddress(v)
                                handleArrayIndex(currentPhys, i, child)
                                queue.add(child)
                            }
                        }
                        is DoubleArray -> {
                            current.forEachIndexed { i, v ->
                                val child = PhysicalAddress(v)
                                handleArrayIndex(currentPhys, i, child)
                                queue.add(child)
                            }
                        }
                        is BooleanArray -> {
                            current.forEachIndexed { i, v ->
                                val child = PhysicalAddress(v)
                                handleArrayIndex(currentPhys, i, child)
                                queue.add(child)
                            }
                        }
                        else -> error("ObjectTraversal.traverse: unexpected array $current")
                    }
                }
                else -> {
                    handleClass(currentPhys, type)

                    for (field in type.allInstanceFields) {
                        if (skipField(field))
                            continue
                        val value = try {
                            field.getFieldValue(current)
                        } catch (e: Exception) {
                            if (skipExceptions) continue
                            error("ObjectTraversal.traverse: ${type.name} failed on field ${field.name}, cause: ${e.message}")
                        }
                        val valuePhys = PhysicalAddress(value)
                        handleClassField(currentPhys, field, valuePhys)
                        queue.add(valuePhys)
                    }
                }
            }
        }
    }
}
