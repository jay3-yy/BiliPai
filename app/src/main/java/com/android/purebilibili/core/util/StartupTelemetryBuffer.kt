package com.android.purebilibili.core.util

/** A bounded, memory-only startup queue. Disabled events can never be replayed later. */
internal class StartupTelemetryBuffer<T>(private val capacity: Int) {
    private val pending = java.util.ArrayDeque<T>()
    private var enabled = false
    private var sender: ((T) -> Unit)? = null

    init {
        require(capacity > 0)
    }

    @Synchronized
    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) pending.clear()
    }

    @Synchronized
    fun submit(event: T): Boolean {
        if (!enabled) return false
        val readySender = sender
        if (readySender != null) {
            readySender(event)
        } else {
            if (pending.size >= capacity) pending.removeFirst()
            pending.addLast(event)
        }
        return true
    }

    @Synchronized
    fun activate(send: (T) -> Unit) {
        while (pending.isNotEmpty()) {
            send(pending.peekFirst())
            pending.removeFirst()
        }
        sender = send
    }
}
