package com.example.organizadoremocional.ui.tareas

import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.example.organizadoremocional.R
import com.example.organizadoremocional.model.Tarea
import com.example.organizadoremocional.ui.home.BaseActivity
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity para mostrar en detalle una tarea.
 */
class DetalleTareaActivity : BaseActivity() {

    //Elementos del layout
    private lateinit var txtTitulo: TextView
    private lateinit var txtDescripcion: TextView
    private lateinit var txtPrioridad: TextView
    private lateinit var txtEmocion: TextView
    private lateinit var txtFechaRealizar: TextView

    private lateinit var btnEliminarTarea : MaterialButton
    private lateinit var btnEditarTarea: MaterialButton
    private lateinit var btnMarcarCompletada: MaterialButton

    private lateinit var checkbox_aplazarT :CheckBox

    //ViewModel para interactuar con FireStore
    private val tareaViewModel: TareaViewModel by viewModels()
    private var tareaActual: Tarea? = null

    //Launcher para volver a la pantalla de edición.
    private val editarTareaLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            tareaViewModel.obtenerTareasNoCompletadas()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalle_tarea)

        //Inicializar vistas
        txtTitulo = findViewById(R.id.txtTitulo)
        txtDescripcion = findViewById(R.id.txtDescripcion)
        txtPrioridad = findViewById(R.id.txtPrioridad)
        txtEmocion = findViewById(R.id.txtEmocion)
        txtFechaRealizar = findViewById(R.id.txtFechaRealizar)
        btnEliminarTarea = findViewById(R.id.btnEliminarTareas)
        btnEditarTarea = findViewById(R.id.btnEditarTareas)
        btnMarcarCompletada = findViewById(R.id.btnMarcarCompletada)
        checkbox_aplazarT = findViewById(R.id.checkbox_aplazarT)


        val idTarea = intent.getStringExtra("idTarea")

        //Obtener tareas no completadas
        tareaViewModel.obtenerTareasNoCompletadas()

        //Ver tareas no completadas
        tareaViewModel.tareasNoCompletadas.observe(this) { tareas ->

            val tarea = tareas.find { it.idTarea == idTarea }

            if (tarea == null) {
                finish()
                return@observe
            }

            tareaActual = tarea

            // Mostrar detalles de la tarea
            txtTitulo.text = tarea.titulo
            txtDescripcion.text = tarea.descripcion
            txtPrioridad.text = tarea.prioridad
            txtEmocion.text = tarea.emocion?.toString() ?: "Sin emoción"
            checkbox_aplazarT.isChecked = tarea.aplazar

            tarea.fechaRealizar?.let{ fecha ->
                val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                txtFechaRealizar.text = formatoFecha.format(fecha)
            } ?: run {
                txtFechaRealizar.text = "No establecida"
            }
        }

        //botón editar
        btnEditarTarea.setOnClickListener {
            val intent = Intent(this, CrearTareaActivity::class.java)
            intent.putExtra("tarea", tareaActual)
            editarTareaLauncher.launch(intent)
        }

        // boton eliminar
        btnEliminarTarea.setOnClickListener {
            mostrarDialogoConfirmacion()
        }

        //Ver resultado confirmacion
        tareaViewModel.resultadoEliminacion.observe(this){ resultado ->
            if(resultado){
                Toast.makeText(this, "Tarea eliminada", Toast.LENGTH_SHORT).show()
                }else {
                Toast.makeText(this, "Error, no se ha podido eliminar la tarea", Toast.LENGTH_SHORT).show()
            }
        }

        // boton completar tarea
        btnMarcarCompletada.setOnClickListener {
            tareaActual?.let { tarea ->
                val tareaCompletada = tarea.copy (completada = true, fechaCompletada = Date())
                tareaViewModel.editarTarea(tareaCompletada)
                val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                if (prefs.getString("last_scheduled_hp", null) == tarea.idTarea) {
                    prefs.edit().remove("last_scheduled_hp").apply()
                }
                Toast.makeText(this, "Tarea marcada como completada", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    /**
     * Muestra el diálogo de confirmación para eliminar una tarea.
     */
    private fun mostrarDialogoConfirmacion(){
        AlertDialog.Builder(this).setTitle("Confirmar eliminacion")
            .setMessage("¿Seguro que quieres eliminar esta tarea?")
            .setPositiveButton("Eliminar"){_, _ ->
                tareaActual?.let { tarea ->
                    tareaViewModel.eliminarTarea(tarea.idTarea)

                }
            }
            .setNeutralButton("Cancelar", null)
            .show()
    }
}