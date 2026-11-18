package com.jetsport.ft10csync

import android.content.Context
import android.os.Handler
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Retains the application-scoped composition root across Activity recreation.
 * AppCore receives only application context and the main-thread Handler, never an Activity.
 */
internal class AppCoreViewModel(context: Context, mainHandler: Handler) : ViewModel() {
    val core = AppCore(context.applicationContext, mainHandler)

    override fun onCleared() {
        core.close()
        super.onCleared()
    }

    class Factory(
        private val context: Context,
        private val mainHandler: Handler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(AppCoreViewModel::class.java)) {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
            return AppCoreViewModel(context.applicationContext, mainHandler) as T
        }
    }
}
