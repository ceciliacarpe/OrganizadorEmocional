package com.example.organizadoremocional.ui.home

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.example.organizadoremocional.core.workers.CalendarioSyncWorker
import com.example.organizadoremocional.R
import com.example.organizadoremocional.core.sync.CalendarioSync
import com.example.organizadoremocional.ui.auth.AuthViewModel
import com.example.organizadoremocional.ui.estadoanimo.EstadoDeAnimoViewModel
import com.example.organizadoremocional.model.EstadoDeAnimoTipo
import com.example.organizadoremocional.core.workers.NotificacionesDiaWorker
import com.example.organizadoremocional.ui.auth.LoginActivity
import com.example.organizadoremocional.ui.estadoanimo.EstadoDeAnimoActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    companion object {
        private const val PREFS_NAME = "settings"
        private const val KEY_SYNC_ENABLED = "calendarSyncEnabled"
        private const val KEY_SYNC_EMAIL = "syncEmail"
        private const val REQUEST_POST_NOTIFICATIONS = 1001
    }

    private val authViewModel: AuthViewModel by viewModels()
    private val estadoDeAnimoViewModel: EstadoDeAnimoViewModel by viewModels()

    @Suppress("DEPRECATION")
    private fun setTransparentStatusBar() {
        window.apply {
            statusBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insetsController?.setSystemBarsAppearance(
                    0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setTransparentStatusBar()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_SYNC_ENABLED, false)

        val email = prefs.getString(KEY_SYNC_EMAIL, null)
        if (enabled && email != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                CalendarioSync
                    .syncAllPendingTasks(this@MainActivity, email)
            }
        }

        prefs.edit().remove("currentMood").apply()

        authViewModel.authState.observe(this, Observer { state ->
            when (state) {
                is AuthViewModel.AuthState.Autenticado -> {
                    estadoDeAnimoViewModel.getEstadoDeAnimo()
                    estadoDeAnimoViewModel.estadoHoy.observe(this) { hoy ->
                        val mood = hoy?.tipoEAnimo?.name ?: EstadoDeAnimoTipo.MOTIVADO.name
                        prefs.edit().putString("currentMood", mood).apply()
                        val dest = if (hoy != null) HomeActivity::class.java else EstadoDeAnimoActivity::class.java

                        verificarYReprogramarNotificaciones()

                        verificarYReprogramarCalendarSync()

                        startActivity(Intent(this, dest))
                        finish()
                    }
                }
                is AuthViewModel.AuthState.Noautenticado -> {
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                is AuthViewModel.AuthState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                else -> {}
            }
        })

        authViewModel.checkUsuarioActual()
    }

    /**
     * Verifica si las notificaciones están activadas y las reprograma automáticamente
     * cuando el usuario vuelve a abrir la app
     */
    private fun verificarYReprogramarNotificaciones() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val notifEnabled = prefs.getBoolean("notificaciones_activadas", false)

        if (notifEnabled) {
            NotificacionesDiaWorker.cancelarSoloDiarias(this)
            NotificacionesDiaWorker.programarNotificacionesDiarias(this)

        } else {
            NotificacionesDiaWorker.cancelarNotificaciones(this)
        }
    }

    /**
     * Verifica si la sincronización de Calendar está activada y la reprograma automáticamente
     * cuando el usuario vuelve a abrir la app
     */
    private fun verificarYReprogramarCalendarSync() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val syncEnabled = prefs.getBoolean(KEY_SYNC_ENABLED, false)
        val email = prefs.getString(KEY_SYNC_EMAIL, null)

        if (syncEnabled && email != null) {
            CalendarioSyncWorker.programarSincronizacionDiaria(this)
        } else {
            CalendarioSyncWorker.cancelarSincronizacion(this)
        }
    }
}

