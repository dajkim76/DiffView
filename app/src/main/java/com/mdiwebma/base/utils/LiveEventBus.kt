package com.mdiwebma.base.utils

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import java.util.concurrent.atomic.AtomicBoolean

class SingleMutableLiveData<T> : MutableLiveData<T>() {
    private val pending = AtomicBoolean(false)

    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        super.observe(owner) { t ->
            if (pending.compareAndSet(true, false)) {
                observer.onChanged(t)
            }
        }
    }

    override fun setValue(t: T?) {
        pending.set(true)
        super.setValue(t)
    }

    // 편의 함수
    fun call() {
        value = null
    }

    fun post() {
        postValue(null)
    }
}


object LiveEventBus {
    private val bus = mutableMapOf<String, MutableLiveData<Any>>()

    fun <T> with(key: String): MutableLiveData<T> {
        if (!bus.containsKey(key)) {
            bus[key] = MutableLiveData()
        }
        @Suppress("UNCHECKED_CAST")
        return bus[key] as MutableLiveData<T>
    }

    fun <T> single(key: String): SingleMutableLiveData<T?> {
        if (!bus.containsKey(key)) {
            bus[key] = SingleMutableLiveData()
        }
        @Suppress("UNCHECKED_CAST")
        return bus[key] as SingleMutableLiveData<T?>
    }
}
