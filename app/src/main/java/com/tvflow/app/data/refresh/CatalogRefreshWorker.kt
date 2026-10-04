package com.tvflow.app.data.refresh

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tvflow.app.data.repository.XtreamRepository
import java.util.concurrent.TimeUnit

class CatalogRefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun repository(): XtreamRepository
    }

    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).repository()
        return runCatching {
            repository.refreshCatalog()
            repository.refreshMediaCatalog()
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val UNIQUE_WORK = "tvflow_catalog_refresh"

        fun schedule(context: Context, intervalDays: Int) {
            val workManager = WorkManager.getInstance(context)
            if (intervalDays <= 0) {
                workManager.cancelUniqueWork(UNIQUE_WORK)
                return
            }
            val request = PeriodicWorkRequestBuilder<CatalogRefreshWorker>(intervalDays.toLong(), TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            workManager.enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
