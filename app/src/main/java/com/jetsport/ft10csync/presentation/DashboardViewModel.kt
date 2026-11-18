package com.jetsport.ft10csync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jetsport.ft10csync.StepMetrics
import com.jetsport.ft10csync.StepProfile
import com.jetsport.ft10csync.data.MeasurementRepository
import com.jetsport.ft10csync.domain.CalculateDailyActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Immutable presentation state for the Today dashboard. */
data class DashboardUiState(
    val isLoading: Boolean = false,
    val snapshot: MeasurementRepository.DashboardSnapshot? = null,
    val activity: StepMetrics? = null,
    val errorMessage: String? = null
)

/** Owns dashboard loading and derived activity state; the Activity only renders it. */
class DashboardViewModel(
    private val repository: MeasurementRepository,
    private val calculateDailyActivity: CalculateDailyActivity
) : ViewModel() {
    private val mutableState = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = mutableState.asStateFlow()
    private var requestGeneration = 0L

    fun refresh(dayKey: String, profile: StepProfile) {
        val generation = ++requestGeneration
        mutableState.value = mutableState.value.copy(isLoading = true, errorMessage = null)
        repository.loadDashboard(dayKey) { result ->
            if (generation != requestGeneration) return@loadDashboard
            result.fold(
                onSuccess = { snapshot ->
                    mutableState.value = DashboardUiState(
                        isLoading = false,
                        snapshot = snapshot,
                        activity = calculateDailyActivity.execute(snapshot, profile)
                    )
                },
                onFailure = { error ->
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        errorMessage = "${error.javaClass.simpleName}: ${error.message}"
                    )
                }
            )
        }
    }

    class Factory(private val repository: MeasurementRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
            return DashboardViewModel(
                repository,
                CalculateDailyActivity()
            ) as T
        }
    }
}
