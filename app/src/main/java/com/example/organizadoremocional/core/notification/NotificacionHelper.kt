package com.example.organizadoremocional.core.notification

import android.app.*
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.organizadoremocional.R

/**
 * Helper para mostar notificaciones.
 * Crea el canal y muestra el título y mensaje.
 */
object NotificacionHelper {

    //Id del canal
    private const val CHANNEL_ID = "motivational_channel"

    /**
     * Muestra la notificación
     * @param context Contexto de la aplicación.
     * @param titulo Título de la notificación.
     * @param mensaje mensaje que aparecerá en la notificación.
     */
    fun mostrarNotificacion(context: Context, titulo: String, mensaje: String) {

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mensajes Motivacionales",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setSmallIcon(R.mipmap.ic_app)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify((0..9999).random(), notification)
    }
}
