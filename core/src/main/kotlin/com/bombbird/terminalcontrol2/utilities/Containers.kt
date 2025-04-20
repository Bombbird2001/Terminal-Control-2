package com.bombbird.terminalcontrol2.utilities

class OffsetArray<T>(val minimumIndex: Int, val maximumIndex: Int): Iterable<T?> {
    private val array: Array<T?> = arrayOfNulls<Any?>(maximumIndex - minimumIndex + 1) as Array<T?>

    companion object {
        fun <T> createWith(minimumIndex: Int, maximumIndex: Int, createFn: () -> T): OffsetArray<T?> {
            val offsetArray = OffsetArray<T?>(minimumIndex, maximumIndex)
            for (i in minimumIndex..maximumIndex) {
                offsetArray[i] = createFn()
            }
            return offsetArray
        }
    }

    operator fun get(index: Int): T? {
        return if (minimumIndex <= index && index <= maximumIndex) {
            array[index - minimumIndex]
        } else null
    }

    operator fun set(index: Int, value: T?) {
        if (minimumIndex <= index && index <= maximumIndex) {
            array[index - minimumIndex] = value
        }
    }

    fun clear() {
        for (i in array.indices) {
            array[i] = null
        }
    }

    override fun iterator(): Iterator<T?> {
        return array.iterator()
    }
}