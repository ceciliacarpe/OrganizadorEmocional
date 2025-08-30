package com.example.organizadoremocional.ui.tareas

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.organizadoremocional.R
import com.example.organizadoremocional.ui.home.TareaAdapter
import com.example.organizadoremocional.model.EstadoDeAnimoTipo
import com.example.organizadoremocional.ui.home.BaseActivity

/**
 * Activity para listar las tareas no completadas de hoy y ordenadas
 */
class TareasOrdenadasActivity : BaseActivity(){
    private lateinit var adapter: TareaAdapter
    private val tareaViewModel: TareaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tareas_ordenadas)

        adapter = TareaAdapter(emptyList()) { tarea ->
            startActivity(Intent(this, DetalleTareaActivity::class.java).apply {
                putExtra("idTarea", tarea.idTarea)
            })
        }
        val recyclerViewTareasOrdenadas = findViewById<RecyclerView>(R.id.recyclerViewTareasOrdenadas)

        recyclerViewTareasOrdenadas.layoutManager = LinearLayoutManager(this)
        recyclerViewTareasOrdenadas.adapter = adapter

        val estadoActual = intent
            .getSerializableExtra("estadoDeAnimo")
                as? EstadoDeAnimoTipo
            ?: EstadoDeAnimoTipo.MOTIVADO

        tareaViewModel.tareasHoyOrdenadas.observe(this) { lista ->
            adapter.updateTareas(lista)
        }

        tareaViewModel.obtenerTareasHoyOrdenadas(estadoActual)
    }

    override fun onResume() {
        super.onResume()
        val estadoActual = intent
            .getSerializableExtra("estadoDeAnimo")
                as? EstadoDeAnimoTipo
            ?: EstadoDeAnimoTipo.MOTIVADO
        tareaViewModel.obtenerTareasHoyOrdenadas(estadoActual)
    }
}