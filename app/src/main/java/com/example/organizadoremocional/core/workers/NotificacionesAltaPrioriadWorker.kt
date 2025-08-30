package com.example.organizadoremocional.core.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.organizadoremocional.core.notification.NotificacionHelper
import java.util.concurrent.TimeUnit


private object HPTPrefs {
    private const val PREFS = "settings"
    private const val KEY_SET = "hp_tasks"

    /**
     * Devuelve true si ya se ha notificado esa tarea en ese dispositivo.
     */
    fun repite(context: Context, tareaId: String): Boolean {
        val set = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_SET, emptySet()) ?: emptySet()
        return set.contains(tareaId)
    }

    /**
     * Marca la tarea como notificada.
     */
    fun notificada(context: Context, taskId: String) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = java.util.HashSet(sp.getStringSet(KEY_SET, emptySet()) ?: emptySet())
        current.add(taskId)
        sp.edit().putStringSet(KEY_SET, current).apply()
    }
}

/**
 * Worker para tareas de alta prioridad
 */
class NotificacionesAltaPrioriadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "HighPriorityTask"
        private const val KEY_TASK_ID = "task_id"
        private const val KEY_TASK_TITLE = "task_title"

        /**
         * Programa notificaión de tarea de alta prioridad
         * @param context Contexto de la aplicación.
         * @param tareaId id de la tarea.
         * @param tituloTarea titulo de la tarea a avisar.
         * @param delayMinutos delay de minutos tras colocarse la primera en la lista.
         */
        fun schedule(context: Context, tareaId: String, tituloTarea: String, delayMinutos: Long = 10) {

            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

            if (!prefs.getBoolean("notificaciones_activadas", false)) return

            if (HPTPrefs.repite(context, tareaId)) return

            val req = OneTimeWorkRequestBuilder<NotificacionesAltaPrioriadWorker>()
                .setInitialDelay(delayMinutos, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_TASK_ID to tareaId, KEY_TASK_TITLE to tituloTarea))
                .addTag("hp_work")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork("hp_$tareaId", ExistingWorkPolicy.KEEP, req)

            Log.d(TAG, "Programado notificacion alta prioridad para $tareaId en $delayMinutos min")
        }

        /**
         * Por si se completa la tarea antes del aviso y quieres cancelar el pendiente.
         * @param context Contexto de la aplicación.
         * @param tareaId id de la tarea.
         * */
        fun cancelFor(context: Context, tareaId: String) {
            WorkManager.getInstance(context).cancelUniqueWork("hp_$tareaId")
        }
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TASK_TITLE) ?: "tarea importante"

        return try {
            val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("notificaciones_activadas", false)) return Result.failure()

            if (HPTPrefs.repite(applicationContext, taskId)) return Result.success()

            NotificacionHelper.mostrarNotificacion(applicationContext, "¡Tarea de prioridad alta!", "Tu próxima es de prioridad alta: $title")

            HPTPrefs.notificada(applicationContext, taskId)
            Result.success()
        } catch (e: Exception) {
            Log.e("HighPriorityTask", "Error notificación tarea alta prioridad: ${e.message}", e)
            Result.retry()
        }
    }
}
