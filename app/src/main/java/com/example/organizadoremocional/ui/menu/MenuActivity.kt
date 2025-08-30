package com.example.organizadoremocional.ui.menu

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.organizadoremocional.R
import com.example.organizadoremocional.model.EstadoDeAnimo
import com.example.organizadoremocional.model.EstadoDeAnimoTipo
import com.example.organizadoremocional.ui.estadoanimo.EstadoDeAnimoViewModel
import com.google.android.material.button.MaterialButton
import android.provider.Settings
import com.example.organizadoremocional.core.workers.NotificacionesDiaWorker
import com.example.organizadoremocional.ui.home.BaseActivity
import com.example.organizadoremocional.ui.estadisticas.EstadisticasActivity
import java.util.*

/**
 * Clase para el menu de opciones
 */
class MenuActivity : BaseActivity() {

    private val estadoViewModel: EstadoDeAnimoViewModel by viewModels()
    private lateinit var txtEAnimo: TextView
    private lateinit var btnActualizarEAnimo: MaterialButton
    private lateinit var switchNotif: Switch

    private val prefs by lazy {
        getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    private val REQUEST_POST_NOTIFICATIONS = 1001
    private val REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = 1002


    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        txtEAnimo = findViewById(R.id.txtEAnimo)
        btnActualizarEAnimo = findViewById(R.id.btnActualizarEAnimo)
        switchNotif = findViewById(R.id.switch_notifications)

        // Mostrar el estado de ánimo actual
        estadoViewModel.getEstadoDeAnimo()
        estadoViewModel.estadoHoy.observe(this) { estado ->
            txtEAnimo.text = estado?.tipoEAnimo?.name?.lowercase() ?: "sin_estado"
        }

        // Selector de estado
        btnActualizarEAnimo.setOnClickListener { mostrarSelectorDeEstado() }

        // Inicializar switch según prefs
        switchNotif.isChecked = prefs.getBoolean("notificaciones_activadas", false)
        switchNotif.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notificaciones_activadas", isChecked).apply()
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations()) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivityForResult(intent, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                } else {
                    pedirPermisoYProgramar()
                }
            } else {
                NotificacionesDiaWorker.cancelarNotificaciones(this)
                Toast.makeText(this, "Notificaciones desactivadas", Toast.LENGTH_SHORT).show()
            }
        }

        // Botón de estadísticas
        findViewById<MaterialButton>(R.id.estadisticas)
            .setOnClickListener {
                startActivity(Intent(this, EstadisticasActivity::class.java))
            }
    }

    /**
     * Solicita el permiso POST_NOTIFICATIONS (si es necesario según la versión de Android)
     * si es concedido, programa las notificaciones diarias mediante WorkManager.
     *
     */
    private fun pedirPermisoYProgramar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIFICATIONS
            )
            return
        }

        // Programar con WorkManager
        NotificacionesDiaWorker.programarNotificacionesDiarias(this)
        Toast.makeText(this, "Notificaciones programadas.", Toast.LENGTH_LONG).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_POST_NOTIFICATIONS) {
            if (grantResults.getOrNull(0) == PackageManager.PERMISSION_GRANTED) {
                NotificacionesDiaWorker.programarNotificacionesDiarias(this)
                Toast.makeText(this, "Notificaciones activadas", Toast.LENGTH_SHORT).show()
            } else {
                switchNotif.isChecked = false
                Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun isIgnoringBatteryOptimizations(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }
    }

    private fun mostrarSelectorDeEstado() {
        val tipos = EstadoDeAnimoTipo.values()
        val nombres = tipos.map {
            it.name.lowercase().replaceFirstChar { c -> c.titlecase() }
        }.toTypedArray()

        val actual = prefs.getString("currentMood", EstadoDeAnimoTipo.MOTIVADO.name)!!
        val idx = tipos.indexOfFirst { it.name == actual }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setSingleChoiceItems(nombres, idx) { dialog, which ->
                val elegido = tipos[which]
                estadoViewModel.guardarEstadoDeAnimo(EstadoDeAnimo(elegido, Date())) { }

                getSharedPreferences("settings", MODE_PRIVATE)
                    .edit()
                    .putString("currentMood", elegido.name)
                    .commit()

                dialog.dismiss()
                finish()
                startActivity(intent)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }


    private fun applySavedTheme() {
        val mood = prefs.getString("currentMood", EstadoDeAnimoTipo.MOTIVADO.name)!!
        val themeRes = when (EstadoDeAnimoTipo.valueOf(mood)) {
            EstadoDeAnimoTipo.MOTIVADO -> R.style.AppTheme_MOTIVADO
            EstadoDeAnimoTipo.RELAJADO -> R.style.AppTheme_RELAJADO
            EstadoDeAnimoTipo.ESTRESADO -> R.style.AppTheme_ESTRESADO
            EstadoDeAnimoTipo.DESMOTIVADO -> R.style.AppTheme_DESMOTIVADO
        }
        setTheme(themeRes)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) {
            if (isIgnoringBatteryOptimizations()) {
                pedirPermisoYProgramar()
            } else {
                switchNotif.isChecked = false
                Toast.makeText(
                    this,
                    "Para recibir notificaciones debes desactivar optimización de batería",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
