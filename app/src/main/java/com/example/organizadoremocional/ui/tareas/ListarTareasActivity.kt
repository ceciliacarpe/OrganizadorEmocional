package com.example.organizadoremocional.ui.tareas

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.organizadoremocional.R
import com.example.organizadoremocional.ui.home.BaseActivity
import com.example.organizadoremocional.ui.home.TareaAdapter

/**
 * Activity para listar las tareas no completadas
 */
class ListarTareasActivity : BaseActivity() {

    //Adaptador para mostrar las tareas
    private lateinit var adapter: TareaAdapter

    //ViewModel para interactuar con FireStore
    private val tareaViewModel: TareaViewModel by viewModels()

    /**
     * Launcher para obtener el resultado al volver al detalleActivity.
     * Al editar o eliminar tarea se actualiza.
     */
    private val detalleTareaLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            tareaViewModel.obtenerTareasNoCompletadas()
        }
    }

    /**
     * Configuración del RecyclerView para mostrar lista de tareas.
     */
    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewTareas)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = TareaAdapter(emptyList()) { tareaSeleccionada ->
            val intent = Intent(this, DetalleTareaActivity::class.java).apply {
                putExtra("idTarea", tareaSeleccionada.idTarea)
            }
            detalleTareaLauncher.launch(intent)
        }

        recyclerView.adapter = adapter
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_listar_tareas)

        setupRecyclerView()

        tareaViewModel.tareasNoCompletadas.observe(this){
            tareas -> adapter.updateTareas(tareas)
        }

        tareaViewModel.obtenerTareasNoCompletadas()


    }

    /**
     * Se llama cuando la actividad vuelve. Para actualizae lista de tareas.
     */
    override fun onResume(){
        super.onResume()
        tareaViewModel.obtenerTareasNoCompletadas()
    }
}