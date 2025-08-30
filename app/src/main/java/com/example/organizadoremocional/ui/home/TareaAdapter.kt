package com.example.organizadoremocional.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.organizadoremocional.R
import com.example.organizadoremocional.model.Emocion
import com.example.organizadoremocional.model.Tarea

/**
 * Adaptador para mostrar lista de tareas no completadas en un RecyclerView.
 * Muestra el título y la descripción de la tarea, además del icono de la emcoión asociada.
 *
 * @param listaTareas Lista de tareas a mostrar.
 * @param onItemClick Acción que se ejecuta al hacer click en un ítem de la lista.
 */
class TareaAdapter(
    private var listaTareas: List<Tarea>,
    private val onItemClick: ((Tarea) -> Unit)? = null
) : RecyclerView.Adapter<TareaAdapter.TareaViewHolder>() {

    /**
     * ViewHolder de cada item
     */
    inner class TareaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titulo: TextView = itemView.findViewById(R.id.txtTituloTarea)
        val descripcion: TextView = itemView.findViewById(R.id.txtDescripcionTarea)
        val emocionIcon: ImageView = itemView.findViewById(R.id.imgEmocion)

        init {
            itemView.setOnClickListener{
                onItemClick?.invoke(listaTareas[adapterPosition])
            }
        }
    }

    /**
     * Llama al layout del item y crea un nuevo ViewHolder.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TareaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tarea, parent, false)
        return TareaViewHolder(view)
    }

    /**
     * Asocia los datos de una tarea con el item del viewhOlder
     */
    override fun onBindViewHolder(holder: TareaViewHolder, position: Int) {
        val tarea = listaTareas[position]
        holder.titulo.text = tarea.titulo
        holder.descripcion.text = tarea.descripcion

        val icon = when (tarea.emocion) {
            Emocion.ALEGRIA -> R.drawable.ic_e_feliz
            Emocion.TRISTEZA -> R.drawable.ic_e_tristeza
            Emocion.IRA -> R.drawable.ic_e_ira
            Emocion.MIEDO -> R.drawable.ic_e_miedo
            Emocion.NEUTRA ->R.drawable.ic_e_neutral
            else -> R.drawable.circle_background
        }
        holder.emocionIcon.setImageResource(icon)
    }

    /**
     * Devuelve el número de elementos de la lista.
     */
    override fun getItemCount(): Int = listaTareas.size

    /**
     * Actualiza la lista de tareas.
     * @param nuevaLista Nueva lista de tareas para mostrar.
     */
    fun updateTareas(nuevaLista: List<Tarea>){
        listaTareas = nuevaLista
        notifyDataSetChanged()
    }
}
