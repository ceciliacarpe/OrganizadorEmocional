package com.example.organizadoremocional.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.organizadoremocional.model.EstadoDeAnimoTipo
import com.example.organizadoremocional.repository.MensajesMotivacionalesRepository

/**
 * Mostrar el mensaje motivacional según estado de ánimo en el home.
 */
class MensajeMotivacionalViewModel : ViewModel() {
    private val repo = MensajesMotivacionalesRepository()
    private val _mensajes = MutableLiveData<Map<EstadoDeAnimoTipo, List<String>>>()
    val mensajes: LiveData<Map<EstadoDeAnimoTipo, List<String>>> = _mensajes

    init {
        repo.fetchMensajes(
            onSuccess = { _mensajes.postValue(it) },
            onError   = {  }
        )
    }

    /**
     * Obtiene el mensaje del array correspondiente al estado, rotando.
     */
    fun getMensaje(estado: EstadoDeAnimoTipo): String {
        val lista = _mensajes.value?.get(estado) ?: return ""
        val periodo = System.currentTimeMillis() / (5 * 60 * 60 * 1000)
        val idx = (periodo % lista.size).toInt()
        return lista[idx]
    }
}
