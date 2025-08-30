package com.example.organizadoremocional.core.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.organizadoremocional.core.notification.NotificacionHelper
import com.example.organizadoremocional.model.EstadoDeAnimo
import com.example.organizadoremocional.model.EstadoDeAnimoTipo
import com.example.organizadoremocional.repository.EstadoDeAnimoRepository
import com.example.organizadoremocional.repository.MensajesMotivacionalesRepository
import com.example.organizadoremocional.repository.TareaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Worker para programar notificacioens con mensajes motivacionales durante el día.
 */
class NotificacionesDiaWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DailyNotificationsWorker"
        private const val WORK_NAME = "daily_notifications"

        // Horarios de notificaciones
        private val horariosMotivacion = listOf(
            Pair(9, 0),
            Pair(12,0 ),
            Pair(15, 0),
            Pair(18, 0)
        )
        private val horarioTareas = Pair(21,0 )

        /**
         * Programa todas las notificaciones
         */
        fun programarNotificacionesDiarias(context: Context) {

            cancelarNotificaciones(context)

            // Verificar si están habilitadas
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val activada = prefs.getBoolean("notificaciones_activadas", false)

            if (!activada) {
                Log.d(TAG, "Notificaciones deshabilitadas")
                return
            }

            // Programar notificaciones motivacionales
            horariosMotivacion.forEachIndexed { index, (hora, minuto) ->
                programarNotificacion(context, hora, minuto, "MOTIV", "motiv_$index")
            }

            // Programar notificación de tareas pendientes
            programarNotificacion(context, horarioTareas.first, horarioTareas.second, "PEND", "tareas")

            Log.d(TAG, "Todas las notificaciones programadas")
        }


        /**
         * Programa una notificación diaria usando WorkManager.
         *
         * @param context contexto de la aplicación.
         * @param hora hora en la que debe lanzarse la notificación.
         * @param minuto minuto en la que debe lanzarse la notificación.
         * @param tipo tipo de notificación a programar.
         * @param workName nombre único del WorkManager.
         */
        private fun programarNotificacion(context: Context, hora: Int, minuto: Int, tipo: String, workName: String) {
            val delay = calcularDelayHastaHora(hora, minuto)

            val request = PeriodicWorkRequestBuilder<NotificacionesDiaWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(
                    "tipo" to tipo,
                    "hora" to hora,
                    "minuto" to minuto
                ))
                .addTag("daily_work")
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.REPLACE, request)

            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis() + delay
            }
        }

        private fun calcularDelayHastaHora(hora: Int, minuto: Int): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hora)
                set(Calendar.MINUTE, minuto)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                if (before(now)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            return target.timeInMillis - now.timeInMillis
        }

        fun cancelarNotificaciones(context: Context) {
            Log.d(TAG, "Cancelando todas las notificaciones")

            WorkManager.getInstance(context).cancelAllWorkByTag("daily_work")
            listOf("motiv_0","motiv_1","motiv_2","motiv_3","tareas").forEach {
                WorkManager.getInstance(context).cancelUniqueWork(it)
            }

            WorkManager.getInstance(context).cancelAllWorkByTag("hp_work")

            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit().remove("last_scheduled_hp").apply()
        }

        fun cancelarSoloDiarias(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag("daily_work")
            listOf("motiv_0","motiv_1","motiv_2","motiv_3","tareas").forEach {
                WorkManager.getInstance(context).cancelUniqueWork(it)
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val tipo = inputData.getString("tipo") ?: return@withContext Result.failure()
            val hora = inputData.getInt("hora", -1)
            val minuto = inputData.getInt("minuto", -1)

            Log.d(TAG, "Ejecutando notificación: $tipo a las ${hora}:${minuto.toString().padStart(2, '0')}")

            // Verificar que las notificaciones sigan habilitadas
            val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val activada = prefs.getBoolean("notificaciones_activadas", false)

            if (!activada) {
                Log.d(TAG, "Notificaciones deshabilitadas, cancelando trabajo")
                return@withContext Result.failure()
            }

            when (tipo) {
                "MOTIV" -> enviarNotificacionMotivacional()
                "PEND" -> enviarNotificacionTareasPendientes()
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando notificación: ${e.message}", e)
            Result.retry()
        }
    }

    private suspend fun enviarNotificacionMotivacional() {
        val repoEstado = EstadoDeAnimoRepository()
        val repoMensajes = MensajesMotivacionalesRepository()

        val estado: EstadoDeAnimo? = suspendCoroutine { cont ->
            repoEstado.getEstadoDeAnimo { cont.resume(it) }
        }

        val tipo = estado?.tipoEAnimo ?: EstadoDeAnimoTipo.MOTIVADO

        val mensajes: Map<EstadoDeAnimoTipo, List<String>> = suspendCoroutine { cont ->
            repoMensajes.fetchMensajes(
                onSuccess = { cont.resume(it) },
                onError   = { cont.resume(emptyMap()) }
            )
        }

        val mensaje = mensajes[tipo]?.randomOrNull()
            ?: "¡Ánimo! Tienes todo lo necesario para lograr tus objetivos."

        NotificacionHelper.mostrarNotificacion(
            applicationContext,
            "Motivación diaria",
            mensaje
        )
    }

    private suspend fun enviarNotificacionTareasPendientes() {
        val repoTarea = TareaRepository()
        val tareas = repoTarea.getTareasAsignadasHoyConAplazables()

        if (tareas.isNotEmpty()) {
            val mensaje = "¡Ánimo! Te quedan ${tareas.size} tareas por completar hoy."
            NotificacionHelper.mostrarNotificacion(
                applicationContext,
                "Tareas pendientes",
                mensaje
            )
        }
    }
}