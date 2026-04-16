package com.cycling.workitout.ui.history

import androidx.lifecycle.ViewModel
import com.cycling.workitout.WorkItOutApplication
import com.cycling.workitout.data.database.CompletedRideEntity
import kotlinx.coroutines.flow.Flow

class HistoryViewModel : ViewModel() {

    val rides: Flow<List<CompletedRideEntity>> =
        WorkItOutApplication.database.completedRideDao().getAll()
}
