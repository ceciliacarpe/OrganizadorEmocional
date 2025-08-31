package com.example.organizadoremocional.core.sync

import android.content.Context
import android.util.Log
import com.example.organizadoremocional.repository.TareaRepository
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.calendar.model.Event.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Sincronización con Google calendar
 */
object CalendarioSync {

    /**
     * Obtiene el token OAuth2.
     */
    private suspend fun getAuthToken(context: Context, accountName: String): String =
        withContext(Dispatchers.IO) {
            val scope = "oauth2:${CalendarScopes.CALENDAR_EVENTS}"
            try {
                GoogleAuthUtil.getToken(context, accountName, scope)
            } catch (e: UserRecoverableAuthException) {
                throw e
            }
        }

    /**
     * Crea el cliente de Google Calendar.
     */
    private fun buildService(token: String) =
        com.google.api.services.calendar.Calendar.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance()
        ) { request ->
            request.headers.authorization = "Bearer $token"
        }
            .setApplicationName("OrganizadorEmocional")
            .build()
    /**
     * Sincroniza todas las tareas con Google Calendar.
     *
     * @param context contexto.
     * @param accountName nombre de la cuenta de Google con la que se sincronizará el calendario.
     *
     * @throws GoogleJsonResponseException si ocurre un error en la API de Google Calendar.
     */
    suspend fun syncAllPendingTasks(context: Context, accountName: String) {
        val token = getAuthToken(context, accountName)
        val service = buildService(token)
        val repo = TareaRepository()

        // Marca inicio del día
        val inicioHoy = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time

        // Filtra tareas de hoy o pospuestas
        val tareas = repo.getTareasNoCompletadasSuspend().filter { tarea ->
            tarea.fechaRealizar?.let { f ->
                !f.before(inicioHoy) || (f.before(inicioHoy) && tarea.aplazar)
            } ?: false
        }

        // Carga eventos ya existentes
        val existingMap = service.events().list("primary")
            .setTimeMin(DateTime(System.currentTimeMillis()))
            .setSingleEvents(true)
            .setOrderBy("startTime")
            .execute()
            .items
            .associateBy { it.extendedProperties?.private?.get("tareaId") ?: "" }

        // Borra eventos que ya no existen
        val actualesIds = tareas.map { it.idTarea }.toSet()
        existingMap.forEach { (tid, ev) ->
            if (tid.isNotEmpty() && !actualesIds.contains(tid)) {
                try {
                    service.events().delete("primary", ev.id).execute()
                } catch (_: GoogleJsonResponseException) { }
            }
        }

        // Inserta o actualiza cada tarea
        tareas.forEach { tarea ->
            val base = tarea.fechaRealizar!!.takeUnless { it.before(inicioHoy) } ?: inicioHoy
            val startStr = formatYMD(base)
            val endDate = Calendar.getInstance().apply { time = base; add(Calendar.DATE, 1) }.time
            val endStr = formatYMD(endDate)

            val eventDescription = buildString {
                append("Emoción: ${tarea.emocion?.name}")
                append("\nPrioridad: ${tarea.prioridad}")
                tarea.descripcion
                    .takeIf { !it.isNullOrBlank() }
                    ?.let { append("\nDescripción: $it") }
            }

            val ev = Event().apply {
                summary = tarea.titulo
                description = eventDescription
                extendedProperties = Event.ExtendedProperties().setPrivate(mapOf("tareaId" to tarea.idTarea))
                reminders = Reminders().apply {
                    useDefault = false
                    overrides = emptyList()
                }
                start = EventDateTime().setDate(DateTime(startStr))
                end = EventDateTime().setDate(DateTime(endStr))
            }

            val existing = existingMap[tarea.idTarea]
            if (existing != null) {
                ev.id = existing.id
                service.events()
                    .update("primary", existing.id, ev)
                    .setSendNotifications(false)
                    .execute()
            } else {
                service.events()
                    .insert("primary", ev)
                    .setSendNotifications(false)
                    .execute()
            }
        }

        Log.d("CalendarioSync", "Sincronización completada: ${tareas.size} tareas.")
    }

    private fun formatYMD(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
}
