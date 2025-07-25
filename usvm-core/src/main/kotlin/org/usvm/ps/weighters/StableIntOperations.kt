package org.usvm.ps.weighters

fun Int.stableAdd(other: Int): Int {
    val longResult = toLong() + other
    val result =
        if (longResult < Int.MIN_VALUE)
            Int.MIN_VALUE
        else if (longResult > Int.MAX_VALUE)
            Int.MAX_VALUE
        else longResult.toInt()

    return result
}

fun Int.stableMul(other: Int): Int {
    val longResult = toLong() * other
    val result =
        if (longResult < Int.MIN_VALUE)
            Int.MIN_VALUE
        else if (longResult > Int.MAX_VALUE)
            Int.MAX_VALUE
        else longResult.toInt()

    return result
}

fun Int.stableUnaryMinus(other: Int): Int {
    return if (this == Int.MIN_VALUE) Int.MAX_VALUE else -this
}
