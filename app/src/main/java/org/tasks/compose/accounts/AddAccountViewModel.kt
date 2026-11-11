package org.tasks.compose.accounts

import dagger.hilt.android.lifecycle.HiltViewModel
import org.tasks.data.dao.CaldavDao
import javax.inject.Inject

@HiltViewModel
class AddAccountViewModel @Inject constructor(
    caldavDao: CaldavDao,
) : org.tasks.viewmodel.AddAccountViewModel(caldavDao)
