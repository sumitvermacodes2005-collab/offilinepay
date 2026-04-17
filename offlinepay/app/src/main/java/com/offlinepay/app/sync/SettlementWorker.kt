package com.offlinepay.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.offlinepay.app.data.repo.PaymentRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SettlementWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: PaymentRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val pending = repo.pendingForSync()
        if (pending.isEmpty()) return Result.success()
        var anyFailed = false
        for (tx in pending) {
            val ok = repo.settleOne(tx)
            if (!ok) anyFailed = true
        }
        return if (anyFailed) Result.retry() else Result.success()
    }

    companion object {
        const val UNIQUE = "settlement_worker"

        fun enqueue(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<SettlementWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                UNIQUE, ExistingWorkPolicy.REPLACE, req
            )
        }
    }
}
