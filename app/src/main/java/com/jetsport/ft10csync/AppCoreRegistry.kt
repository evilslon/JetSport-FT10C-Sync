package com.jetsport.ft10csync

import android.content.Context
import android.os.Handler

/** Process-wide AppCore identity shared by Activity instances and the BLE service. */
internal object AppCoreRegistry {
    @Volatile
    private var current: AppCore? = null

    @Synchronized
    fun obtain(context: Context, mainHandler: Handler): AppCore {
        return current ?: AppCore(context.applicationContext, mainHandler).also { current = it }
    }

    /** Idempotent release; only the current owner can retire the process-wide instance. */
    @Synchronized
    fun release(core: AppCore) {
        if (current === core) {
            current = null
            core.close()
        }
    }
}
