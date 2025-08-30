package com.example.organizadoremocional.ui.home

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.organizadoremocional.R
import com.example.organizadoremocional.ui.tareas.TareaViewModel
import com.google.android.material.button.MaterialButton
import android.widget.TextView
import com.example.organizadoremocional.model.EstadoDeAnimoTipo
import com.example.organizadoremocional.core.workers.NotificacionesAltaPrioriadWorker
import com.example.organizadoremocional.ui.tareas.CrearTareaActivity
import com.example.organizadoremocional.ui.tareas.DetalleTareaActivity
import com.example.organizadoremocional.ui.estadoanimo.EstadoDeAnimoActivity
import com.example.organizadoremocional.ui.cuenta.GestionarCuentaActivity
import com.example.organizadoremocional.ui.tareas.ListarTareasActivity
import com.example.organizadoremocional.ui.menu.MenuActivity
import com.example.organizadoremocional.ui.tareas.TareasOrdenadasActivity
import com.example.organizadoremocional.ui.estadoanimo.EstadoDeAnimoViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity del home de la app
 */
class HomeActivity : BaseActivity() {

    //ViewModel para interactuar con FireStore
    private val tareaViewModel: TareaViewModel by viewModels()
    private val estadoDeAnimoViewModel: EstadoDeAnimoViewModel by viewModels()
    private val mensajeMotivacionalViewModel: MensajeMotivacionalViewModel by viewModels()

    //Elementos del layout
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnTareasOrdenadas: MaterialButton
    private lateinit var btnCrearTarea: MaterialButton
    private lateinit var btnVerTareas: MaterialButton
    private lateinit var mensajeMotivacional: TextView
    private lateinit var adapter: TareaAdapter
    private lateinit var comoTeSientes: TextView
    private var miEstadoActual: EstadoDeAnimoTipo = EstadoDeAnimoTipo.MOTIVADO
    private lateinit var btnPerfil: ImageButton
    private lateinit var btnMenu: ImageButton

    private lateinit var prefs: SharedPreferences
    private var lastMood = ""

    // Variables para control de verificación diaria
    private var dayCheckObserver: Observer<Boolean>? = null
    private var yaVerificadoHoy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("settings", MODE_PRIVATE)
        lastMood = prefs.getString("currentMood", EstadoDeAnimoTipo.MOTIVADO.name)!!

        setContentView(R.layout.activity_home)

        //Inicializar los componentes
        recyclerView = findViewById(R.id.recyclerVistaPrevia)
        btnTareasOrdenadas = findViewById(R.id.btnTareasOrdenadas)
        btnCrearTarea = findViewById(R.id.btnCrearTarea)
        btnVerTareas = findViewById(R.id.btnVerTareas)
        mensajeMotivacional = findViewById(R.id.txtMensajeMotivacional)
        comoTeSientes = findViewById(R.id.txtEEmocional)
        val txtEEmocional = findViewById<TextView>(R.id.txtEEmocional)
        btnPerfil= findViewById(R.id.btnPerfil)
        btnMenu= findViewById(R.id.btnMenu)

        recyclerView.layoutManager = LinearLayoutManager(this)

        // Observar estado de ánimo diario
        estadoDeAnimoViewModel.getEstadoDeAnimo()
        estadoDeAnimoViewModel.estadoHoy.observe(this) { estado ->
            if (estado != null) {
                miEstadoActual = estado.tipoEAnimo
                updateMensaje(estado?.tipoEAnimo)
                txtEEmocional.text = miEstadoActual.name
            } else {
                txtEEmocional.text = "sin_estado"
            }
            tareaViewModel.obtenerTareasHoyOrdenadas(miEstadoActual)

        }

        mensajeMotivacionalViewModel.mensajes.observe(this){
            updateMensaje(estadoDeAnimoViewModel.estadoHoy.value?.tipoEAnimo)
        }

        //Adapatador para mostrar tareas no completadas y al pulsar el detalle
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TareaAdapter(emptyList()) { tarea ->
            startActivity(Intent(this, DetalleTareaActivity::class.java).apply {
                putExtra("idTarea", tarea.idTarea)
            })
        }
        recyclerView.adapter = adapter

        //Muestra tareas no completadas (máximo 4)S
        tareaViewModel.tareasHoyOrdenadas.observe(this) { tareas ->
            val preview = tareas.take(4)
            adapter.updateTareas(preview)

            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            val notifEnabled = prefs.getBoolean("notificaciones_activadas", false)

            val primeraPendiente = tareas.firstOrNull { !it.completada }
            val lastScheduled = prefs.getString("last_scheduled_hp", null)

            if (lastScheduled != null && (primeraPendiente == null || primeraPendiente.idTarea != lastScheduled)) {
                NotificacionesAltaPrioriadWorker.cancelFor(this, lastScheduled)
                prefs.edit().remove("last_scheduled_hp").apply()
            }

            // Si hay primera pendiente de prioridad Alta, programa el notificación.
            if (notifEnabled && primeraPendiente != null && primeraPendiente.prioridad.equals("Alta", true)) {
                NotificacionesAltaPrioriadWorker.schedule(
                    context = this,
                    tareaId = primeraPendiente.idTarea,
                    tituloTarea = primeraPendiente.titulo,
                    delayMinutos = 10
                )
                prefs.edit().putString("last_scheduled_hp", primeraPendiente.idTarea).apply()
            }
        }

        // Boton gestion de cuenta
        btnPerfil.setOnClickListener {
            startActivity(Intent(this, GestionarCuentaActivity::class.java))
        }

        // Boton opciones
        btnMenu.setOnClickListener {
            startActivity(Intent(this, MenuActivity::class.java))
        }

        //Botón ver tareas ordenadas
        btnTareasOrdenadas.setOnClickListener{
            val intent = Intent(this, TareasOrdenadasActivity::class.java).apply {
                putExtra("estadoDeAnimo", miEstadoActual)  // pasa tu EstadoDeAnimoTipo
            }
            startActivity(intent)
        }

        //Botón crear tarea
        btnCrearTarea.setOnClickListener {
            startActivity(Intent(this, CrearTareaActivity::class.java))
        }

        //Botón listar tareas
        btnVerTareas.setOnClickListener {
            startActivity(Intent(this, ListarTareasActivity::class.java))
        }
    }

    /**
     * Se llama cuando la actividad vuelve. Para actualizar lista de tareas.
     */
    override fun onResume(){
        super.onResume()

        if (!yaVerificadoHoy) {
            verificarCambioDeDia()
        }

        val current = prefs.getString("currentMood", lastMood)!!
        if (current != lastMood) {
            lastMood = current
            recreate()
            return
        }

        tareaViewModel.obtenerTareasHoyOrdenadas(miEstadoActual)
    }

    override fun onDestroy() {
        super.onDestroy()
        dayCheckObserver?.let { observer ->
            estadoDeAnimoViewModel.existeHoy.removeObserver(observer)
        }
    }

    private fun verificarCambioDeDia() {
        val ultimaFechaVerificada = prefs.getString("ultima_fecha_home", "")
        val fechaHoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (ultimaFechaVerificada != fechaHoy) {
            if (dayCheckObserver == null) {
                dayCheckObserver = Observer { existe ->
                    if (!existe) {
                        // No hay estado para hoy, ir a EstadoDeAnimoActivity
                        val intent = Intent(this@HomeActivity, EstadoDeAnimoActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        startActivity(intent)
                    }

                    prefs.edit().putString("ultima_fecha_home", fechaHoy).apply()
                    yaVerificadoHoy = true
                }

                estadoDeAnimoViewModel.existeHoy.observe(this, dayCheckObserver!!)
            }

            estadoDeAnimoViewModel.existeEstadoAHoy()
        } else {
            yaVerificadoHoy = true
        }
    }

    private fun updateMensaje(estado: EstadoDeAnimoTipo?) {
        if (estado == null) {
            mensajeMotivacional.text = ""
        } else {
            mensajeMotivacional.text = mensajeMotivacionalViewModel.getMensaje(estado)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity()
    }
}