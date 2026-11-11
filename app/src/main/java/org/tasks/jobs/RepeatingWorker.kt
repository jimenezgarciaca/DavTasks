package org.tasks.jobs

import android.content.Context
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking
import org.tasks.injection.BaseWorker

abstract class RepeatingWorker internal constructor(
        context: Context,
        workerParams: WorkerParameters,
) : BaseWorker(context, workerParams) {

    override fun doWork(): Result {
        val result = super.doWork()
        runBlocking {
            scheduleNext()
        }
        return result
    }

    protected abstract suspend fun scheduleNext()
}