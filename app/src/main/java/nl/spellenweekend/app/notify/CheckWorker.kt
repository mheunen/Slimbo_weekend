package nl.spellenweekend.app.notify

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import nl.spellenweekend.app.data.Repository
import java.util.concurrent.TimeUnit

/**
 * Achtergrondcontrole: haalt elke ~15 minuten (het minimum dat Android toestaat)
 * de activiteiten op en maakt meldingen voor uitnodigingen, starts, wijzigingen
 * en herinneringen. Kost niets: het zijn gewone Firestore-leesacties.
 */
class CheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val repo = Repository(applicationContext)
        val eventId = repo.eventId ?: return Result.success()
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        return try {
            val snap = repo.fetchAll(eventId)
            ChangeChecker(applicationContext).check(eventId, me, snap)
            Result.success()
        } catch (e: Exception) {
            Log.w("CheckWorker", "controle mislukt", e)
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "spellenweekend-controle"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
