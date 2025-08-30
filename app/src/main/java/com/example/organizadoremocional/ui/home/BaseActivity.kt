package com.example.organizadoremocional.ui.home

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.organizadoremocional.R

/**
 * Actividad base de la aplicación.
 * Aplica tema según estado de ánimo.
 * Configurar la ventana para que el contenido se dibuje debajo de las barras del sistema.
 * Todas las actividades heredan de esta.
 */
abstract class BaseActivity(
    @StyleRes private val defaultTheme: Int = R.style.Theme_OrganizadorEmocional
) : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Tema dinámico por "mood"
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val mood = prefs.getString("currentMood", "MOTIVADO") ?: "MOTIVADO"
        val themeRes = when (mood) {
            "MOTIVADO"    -> R.style.AppTheme_MOTIVADO
            "RELAJADO"    -> R.style.AppTheme_RELAJADO
            "ESTRESADO"   -> R.style.AppTheme_ESTRESADO
            "DESMOTIVADO" -> R.style.AppTheme_DESMOTIVADO
            else          -> defaultTheme
        }
        setTheme(themeRes)

        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun onContentChanged() {
        super.onContentChanged()
        applyInsetsSafely()
    }

    private fun applyInsetsSafely() {
        val root: View = findViewById(R.id.rootContainer) ?: findViewById(android.R.id.content)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bars.bottom)
            insets
        }
    }
}
