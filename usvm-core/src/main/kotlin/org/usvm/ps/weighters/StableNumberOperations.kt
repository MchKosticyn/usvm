package org.usvm.ps.weighters

abstract class Arithmetic<T> {
    abstract val maxValue: T
    abstract val minValue: T
    abstract val zero: T
    abstract val one: T
    abstract val negativeOne: T

    open val comparator: Comparator<T>? = null

    abstract fun plus(left: T, right: T): T
    abstract fun minus(left: T, right: T): T
    abstract fun mul(left: T, right: T): T
    abstract fun div(left: T, right: T): T

    open fun negate(value: T) = mul(negativeOne, value)

    open fun compare(left: T, right: T) = comparator?.compare(left, right)
        ?: error("Comparator was not defined for arithmetic")

    open fun isGreater(left: T, right: T) = compare(left, right) > 0
    open fun isLess(left: T, right: T) = compare(left, right) < 0
    open fun isEquals(left: T, right: T) = compare(left, right) == 0

    fun max(left: T, right: T) = if (left.isGreaterTo(right)) left else right
    fun min(left: T, right: T) = if (left.isLessTo(right)) left else right

    fun T.plusTo(other: T) = plus(this, other)
    fun T.minusTo(other: T) = minus(this, other)
    fun T.mulTo(other: T) = mul(this, other)
    fun T.divTo(other: T) = div(this, other)
    fun T.negateTo() = negate(this)

    fun T.compareTo(other: T) = compare(this, other)
    fun T.isGreaterTo(other: T) = isGreater(this, other)
    fun T.isLessTo(other: T) = isLess(this, other)
    fun T.isEqualsTo(other: T) = isEquals(this, other)
}

object StableIntArithmetic : Arithmetic<Int>() {
    override val maxValue = Int.MAX_VALUE
    override val minValue = Int.MIN_VALUE
    override val zero = 0
    override val one = 1
    override val negativeOne = -1

    override val comparator: Comparator<Int> = compareBy<Int> { it }

    private fun Long.stableToInt() = when {
        this < minValue -> minValue
        this > maxValue -> maxValue
        else -> this.toInt()
    }

    override fun plus(left: Int, right: Int) = (left.toLong() + right).stableToInt()
    override fun minus(left: Int, right: Int) = plus(negate(right), left)
    override fun mul(left: Int, right: Int) = (left.toLong() * right).stableToInt()
    override fun div(left: Int, right: Int) = (left.toLong() / right).stableToInt()

    override fun negate(value: Int) = if (value == minValue) maxValue else -value
}

object StableFloatArithmetic : Arithmetic<Float>() {
    override val maxValue = Float.POSITIVE_INFINITY
    override val minValue = Float.NEGATIVE_INFINITY
    override val zero = 0f
    override val one = 1f
    override val negativeOne = -1f

    override val comparator: Comparator<Float> = compareBy<Float> { it }

    private fun Double.stableToFloat() = when {
        this < minValue -> minValue
        this > maxValue -> maxValue
        else -> this.toFloat()
    }

    override fun plus(left: Float, right: Float) = (left.toDouble() + right).stableToFloat()
    override fun minus(left: Float, right: Float) = plus(negate(right), left)
    override fun mul(left: Float, right: Float) = (left.toDouble() * right).stableToFloat()
    override fun div(left: Float, right: Float) = (left.toDouble() / right).stableToFloat()

    override fun negate(value: Float) = if (value == minValue) maxValue else -value
}
