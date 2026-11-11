package org.tasks.repeats

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import org.tasks.repeats.CustomRecurrenceActivity.Companion.EXTRA_DATE
import org.tasks.repeats.CustomRecurrenceActivity.Companion.EXTRA_RRULE
import javax.inject.Inject

@HiltViewModel
class CustomRecurrenceHiltViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : CustomRecurrenceViewModel(
    rrule = savedStateHandle.get<String>(EXTRA_RRULE),
    dueDate = savedStateHandle.get<Long>(EXTRA_DATE) ?: 0L,
)
