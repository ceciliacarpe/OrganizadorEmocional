package com.example.organizadoremocional.core.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.organizadoremocional.core.sync.CalendarioSync
import java.util.concurrent.TimeUnit
import java.util.Calendar

/**
 * Worker para la sincronización diaria con Google Calendar.
 */
class CalendarioSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CalendarSyncWorker"
        private const val WORK_NAME = "daily_calendar_sync"

        /**
         * Programa sincronización diaria con Google Calendar
         * @param context contexto
         */
        fun programarSincronizacionDiaria(context: Context) {
            Log.d(TAG, "Programando sincronización diaria de Calendar")

            // Verificar si está habilitada
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("calendarSyncEnabled", false)
            val email = prefs.getString("syncEmail", null)

            if (!enabled || email == null) {
                Log.d(TAG, "Sincronización deshabilitada o sin email")
                cancelarSincronizacion(context)
                return
            }

            // Calcular delay hasta medianoche (00:01)
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                // Si ya pasó medianoche, programar para mañana
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val delay = calendar.timeInMillis - System.currentTimeMillis()
            val delayMinutes = delay / (1000 * 60)

            val request = PeriodicWorkRequestBuilder<CalendarioSyncWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .addTag("calendar_sync")
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    request
                )

            // Guardar timestamp de cuando se programó
            prefs.edit().putLong("ultima_programacion_calendar", System.currentTimeMillis()).apply()

            Log.d(TAG, "Sincronización programada en $delayMinutes minutos (${calendar.time})")
        }

        fun cancelarSincronizacion(context: Context) {
            Log.d(TAG, "Cancelando sincronización de Calendar")
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
    override suspend fun doWork(): Result {
        return try {
            val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val activado = prefs.getBoolean("calendarSyncEnabled", false)
            val email = prefs.getString("syncEmail", null)

            if (activado && email != null) {
                CalendarioSync.syncAllPendingTasks(applicationContext, email)
                Log.d(TAG, "Sincronización completada")
            } else {
                Log.d(TAG, "Sincronización no configurada")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error en sincronización: ${e.message}", e)
            Result.retry()
        }
    }
}
