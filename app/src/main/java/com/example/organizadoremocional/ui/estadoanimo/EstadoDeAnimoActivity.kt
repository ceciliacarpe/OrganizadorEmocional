package com.example.organizadoremocional.ui.estadoanimo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.airbnb.lottie.LottieAnimationView
import com.example.organizadoremocional.R
import com.example.organizadoremocional.model.EstadoDeAnimo
import com.example.organizadoremocional.model.EstadoDeAnimoTipo
import com.example.organizadoremocional.ui.home.HomeActivity
import java.util.Date

/**
 * Gestiona la selección de estado de ánimo.
 */
class EstadoDeAnimoActivity : AppCompatActivity() {

    private lateinit var radioGroupEstado: RadioGroup
    private lateinit var botonGuardarEstado: Button
    private val estadoViewModel: EstadoDeAnimoViewModel by viewModels()
    private lateinit var lottieFondo: LottieAnimationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_estado_de_animo)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root = findViewById<View>(R.id.rootContainer)
        radioGroupEstado = findViewById(R.id.radioGroupEstadoAnimo)
        botonGuardarEstado = findViewById(R.id.btnGuardarEstado)
        lottieFondo = findViewById(R.id.lottieFondo)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom)
            insets
        }

        // Al iniciar, comprueba si ya hay definido “hoy”
        estadoViewModel.existeEstadoAHoy()
        estadoViewModel.existeHoy.observe(this) { ya ->
            if (ya) {
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            }
        }

        //Boton guardar estado de ánimo de hoy.
        botonGuardarEstado.setOnClickListener {
            val estadoDeAnimoSeleccionado = when (radioGroupEstado.checkedRadioButtonId) {
                R.id.radioMotivado -> EstadoDeAnimoTipo.MOTIVADO
                R.id.radioDesmotivado -> EstadoDeAnimoTipo.DESMOTIVADO
                R.id.radioEstresado -> EstadoDeAnimoTipo.ESTRESADO
                R.id.radioRelajado -> EstadoDeAnimoTipo.RELAJADO
                else -> null
            }

            if (estadoDeAnimoSeleccionado == null) {
                Toast.makeText(this, "Selecciona un estado", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val estado = EstadoDeAnimo(
                tipoEAnimo = estadoDeAnimoSeleccionado,
                fecha = Date()
            )

            estadoViewModel.guardarEstadoDeAnimo(estado) { ok ->
                if (ok) {
                    Toast.makeText(this, "Estado guardado", Toast.LENGTH_SHORT).show()
                    val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                    prefs.edit()
                        .putString("currentMood", estadoDeAnimoSeleccionado.name)
                        .commit()
                    val i = Intent(this, HomeActivity::class.java)
                    i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(i)

                } else {
                    Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show()
                }
            }
        }

        //Animaciones
        radioGroupEstado.setOnCheckedChangeListener { _, checkedId ->
            val animacion = when (checkedId) {
                R.id.radioMotivado -> "motiva.json"
                R.id.radioDesmotivado -> "desm.json"
                R.id.radioEstresado -> "estres.json"
                R.id.radioRelajado -> "relaj.json"
                else -> null
            }
            animacion?.let {
                lottieFondo.setAnimation(it)
                lottieFondo.playAnimation()
            }
        }
    }

}