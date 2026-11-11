package com.todoroo.astrid.activity

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todoroo.astrid.activity.MainActivity.Companion.LOAD_FILTER
import com.todoroo.astrid.activity.MainActivity.Companion.OPEN_FILTER
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.tasks.data.dao.CaldavDao
import org.tasks.data.entity.Task
import org.tasks.filters.Filter
import org.tasks.filters.SearchFilter
import org.tasks.preferences.DefaultFilterProvider
import org.tasks.viewmodel.OnboardingRouting
import org.tasks.viewmodel.OnboardingState
import org.tasks.viewmodel.DrawerViewModel
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val defaultFilterProvider: DefaultFilterProvider,
    @ApplicationContext private val applicationContext: Context,
    private val caldavDao: CaldavDao,
    val drawerViewModel: DrawerViewModel,
) : ViewModel() {

    data class State(
        val filter: Filter,
        val task: Task? = null,
    )

    private val _state = MutableStateFlow(
        State(
            filter = savedStateHandle.get<Filter>(OPEN_FILTER)
                ?: savedStateHandle.get<String>(LOAD_FILTER)?.let {
                    runBlocking { defaultFilterProvider.getFilterFromPreference(it) }
                }
                ?: runBlocking { defaultFilterProvider.getStartupFilter() },
            task = savedStateHandle.get<Task>(EXTRA_TASK),
        )
    )
    val state = _state.asStateFlow()

    companion object {
        private const val EXTRA_TASK = "extra_task"
        private const val KEY_WAS_IN_ONBOARDING = "was_in_onboarding"

    }

    val accountExists: Flow<Boolean>
        get() = caldavDao.watchAccountExists()

    private var wasInOnboarding: Boolean
        get() = savedStateHandle[KEY_WAS_IN_ONBOARDING] ?: false
        set(value) { savedStateHandle[KEY_WAS_IN_ONBOARDING] = value }

    fun routeOnboarding(hasAccount: Boolean?, isImporting: Boolean): OnboardingRouting {
        val routing = org.tasks.viewmodel.routeOnboarding(
            OnboardingState(wasInOnboarding),
            hasAccount,
            isImporting,
        )
        wasInOnboarding = routing.state.wasInOnboarding
        return routing
    }

    init {
        drawerViewModel.setSelectedFilter(_state.value.filter)
    }

    suspend fun resetFilter() {
        setFilter(defaultFilterProvider.getDefaultOpenFilter())
    }

    fun setFilter(
        filter: Filter,
        task: Task? = null,
    ) {
        if (filter == _state.value.filter && task == null) {
            return
        }
        savedStateHandle[EXTRA_TASK] = task
        _state.update {
            it.copy(
                filter = filter,
                task = task,
            )
        }
        drawerViewModel.setSelectedFilter(filter)
        if (filter !is SearchFilter) {
            defaultFilterProvider.setLastViewedFilter(filter)
        }
    }

    fun setTask(task: Task?) {
        savedStateHandle[EXTRA_TASK] = task
        _state.update { it.copy(task = task) }
    }

    suspend fun getAccount(id: Long) = caldavDao.getAccount(id)


    fun openLastViewedFilter() = viewModelScope.launch {
        setFilter(defaultFilterProvider.getLastViewedFilter())
    }
}
