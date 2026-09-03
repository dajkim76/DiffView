package com.mdiwebma.base.helper

import androidx.core.util.Pools.SynchronizedPool

/**
 * @author djkim
 */
object StringBuilderPool {
    private val pool = SynchronizedPool<StringBuilder>(10)

    @JvmStatic
    fun obtain(): StringBuilder {
        val instance = pool.acquire()
        return instance ?: StringBuilder(80)
    }

    @JvmStatic
    fun recycle(sb: StringBuilder): String {
        val result = sb.toString()
        sb.setLength(0)
        pool.release(sb)
        return result
    }

    /** if params.length == 0, return null */
    fun concat(vararg params: Any?): String? {
        var result: String? = null

        if (params.isNotEmpty()) {
            val sb = obtain()
            for (`object` in params) {
                sb.append(`object`)
            }
            result = sb.toString()
            recycle(sb)
        }

        return result
    }
}
