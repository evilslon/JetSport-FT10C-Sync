package com.jetsport.ft10csync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jetsport.ft10csync.data.MeasurementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Immutable state owned exclusively by the History screen. */
data class HistoryUiState(
    val isLoading: Boolean = false,
    val snapshot: MeasurementRepository.HistorySnapshot? = null,
    val errorMessage: String? = null
)

class HistoryViewModel(private val repository: MeasurementRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = mutableState.asStateFlow()
    private var generation = 0L

    fun refresh() {
        val request = ++generation
        mutableState.value = mutableState.value.copy(isLoading = true, errorMessage = null)
        repository.loadHistory { result ->
            if (request != generation) return@loadHistory
            result.fold(
                onSuccess = { mutableState.value = HistoryUiState(snapshot = it) },
                onFailure = { error -> mutableState.value = mutableState.value.copy(isLoading = false, errorMessage = "${error.javaClass.simpleName}: ${error.message}") }
            )
        }
    }

    class Factory(private val repository: MeasurementRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(HistoryViewModel::class.java)) throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            return HistoryViewModel(repository) as T
        }
    }
}
