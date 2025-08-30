package com.example.organizadoremocional.ui.tareas

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.viewModels
import com.example.organizadoremocional.R
import com.example.organizadoremocional.model.Emocion
import com.example.organizadoremocional.model.Tarea
import com.example.organizadoremocional.ui.home.BaseActivity
import com.example.organizadoremocional.ui.home.HomeActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


/**
 * Activity para crear una tareas y editarlas.
 */
class CrearTareaActivity : BaseActivity() {

    private var editarTarea : Tarea? = null;

    private val tareaViewModel : TareaViewModel by viewModels()

    private lateinit var tituloEditable: TextInputEditText
    private lateinit var descripcionEditable: TextInputEditText
    private lateinit var prioridadEditable: RadioGroup
    private lateinit var emocionEditable: RadioGroup
    private lateinit var crearBoton: Button
    private lateinit var fechaBoton: MaterialButton
    private lateinit var aplazarTarea:CheckBox

    private var fechaSeleccionada: Date? = null
    private val calendario = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crear_tarea)

        //Inicializar los componentes
        tituloEditable = findViewById(R.id.tituloTareaEditable)
        descripcionEditable = findViewById(R.id.descripcionTareaEditable)
        prioridadEditable = findViewById(R.id.radioGroupPrioridad)
        emocionEditable = findViewById(R.id.radioGroupEmociones)
        crearBoton = findViewById(R.id.guardarTareaBoton)
        fechaBoton = findViewById(R.id.seleccionarFechaBoton)
        aplazarTarea = findViewById(R.id.checkbox_aplazarT)

        setupDatePicker()

        //Si se le pasa una tarea, se carga para editar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            editarTarea = intent.getSerializableExtra("tarea", Tarea::class.java)
        } else {
            @Suppress("DEPRECATION")
            editarTarea = intent.getSerializableExtra("tarea") as? Tarea
        }

        editarTarea?.let { cargarDatosTarea(it) }

        crearBoton.setOnClickListener {
            val titulo = tituloEditable.text.toString().trim()
            val descripcion = descripcionEditable.text.toString().trim()

            //Obtener prioridad
            val prioridadId = prioridadEditable.checkedRadioButtonId
            val prioridad = when (prioridadId){
                R.id.radioButtonAlta -> "Alta"
                R.id.radioButtonMedia -> "Media"
                R.id.radioButtonBaja -> "Baja"
                else -> ""
            }

            //Obtener emocion
            val emocionId = emocionEditable.checkedRadioButtonId
            val emocion = when (emocionId) {
                    R.id.radioButtonAlegria -> Emocion.ALEGRIA
                    R.id.radioButtonTristeza -> Emocion.TRISTEZA
                    R.id.radioButtonIra -> Emocion.IRA
                    R.id.radioButtonMiedo -> Emocion.MIEDO
                    R.id.radioButtonNeutra -> Emocion.NEUTRA
                    else -> Emocion.NEUTRA
            }

            if (titulo.isNotBlank() && fechaSeleccionada !=null && prioridad.isNotEmpty() && emocion != null) {
                if(editarTarea !=null){
                    //editar tarea
                    val tareaEditada = editarTarea!!.copy(
                        titulo = titulo,
                        descripcion = descripcion,
                        prioridad = prioridad,
                        fechaRealizar = fechaSeleccionada,
                        aplazar = aplazarTarea.isChecked,
                        emocion = emocion
                    )
                    tareaViewModel.editarTarea(tareaEditada)
                }else{
                    //crear tarea
                    tareaViewModel.crearTarea(
                        titulo ,
                        descripcion,
                        prioridad,
                        fechaSeleccionada!!,
                        aplazarTarea.isChecked,
                        emocion
                    )
                }

            } else {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
            }
        }

        // Comprobar si se ha creado la tarea
        tareaViewModel.tareas.observe(this) { creada ->
            if (creada) {
                limpiarCampos()
                tareaViewModel.resetCrearTarea()
                finish()
            }
        }

        //Comprobar si la tarea se ha editado correctamente
        tareaViewModel.tareaEditada.observe(this) { editada ->
            if (editada) {
                Toast.makeText(this, "Tarea editada con éxito", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this, "Error al editar la tarea", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Función para mostrar selector de fecha
     */
    private fun setupDatePicker() {
        fechaBoton.setOnClickListener {
            showDatePickerDialog()
        }
    }

    /**
     * Fución para configurar la selección de fecha.
     */
    private fun showDatePickerDialog() {
        // Fecha actual por defecto
        val year = calendario.get(Calendar.YEAR)
        val month = calendario.get(Calendar.MONTH)
        val day = calendario.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                calendario.set(selectedYear, selectedMonth, selectedDay)
                fechaSeleccionada = calendario.time

                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                fechaBoton.text = dateFormat.format(fechaSeleccionada!!)
            }, year, month, day
        )

        // No admitir fechas pasadas
        val today = Calendar.getInstance()
        datePickerDialog.datePicker.minDate = today.timeInMillis

        // Mostrar el diálogo
        datePickerDialog.show()
    }


    /**
     * Limpia todos los campos del formulario
     */
    private fun limpiarCampos() {
        tituloEditable.text?.clear()
        descripcionEditable.text?.clear()
        prioridadEditable.clearCheck()
        emocionEditable.clearCheck()
        fechaSeleccionada = null
        fechaBoton.text = "Selecciona fecha"
        aplazarTarea.isChecked = false
    }

    /**
     * Fución para volver al home
     */
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }

    /**
     *Funcion para cargar todos los datos de una tarea. Modo edición.
     *
     */
    fun cargarDatosTarea(tarea: Tarea){
        tituloEditable.setText(tarea.titulo)
        descripcionEditable.setText(tarea.descripcion)

        when (tarea.prioridad) {
            "Alta" -> prioridadEditable.check(R.id.radioButtonAlta)
            "Media" -> prioridadEditable.check(R.id.radioButtonMedia)
            "Baja" -> prioridadEditable.check(R.id.radioButtonBaja)
        }

        fechaSeleccionada = tarea.fechaRealizar
        fechaBoton.text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(fechaSeleccionada!!)
        aplazarTarea.isChecked = tarea.aplazar

        when (tarea.emocion) {
            Emocion.ALEGRIA -> emocionEditable.check(R.id.radioButtonAlegria)
            Emocion.TRISTEZA -> emocionEditable.check(R.id.radioButtonTristeza)
            Emocion.IRA -> emocionEditable.check(R.id.radioButtonIra)
            Emocion.MIEDO -> emocionEditable.check(R.id.radioButtonMiedo)
            Emocion.NEUTRA -> emocionEditable.check(R.id.radioButtonNeutra)
            else -> {}
        }

    }
}