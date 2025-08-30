package com.example.organizadoremocional.ui.estadoanimo

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.organizadoremocional.model.EstadoDeAnimo
import com.example.organizadoremocional.repository.EstadoDeAnimoRepository

/**
 * ViewModel encargado de gestionar la lógica del estado de animo.
 */

class EstadoDeAnimoViewModel : ViewModel() {
    private val repository = EstadoDeAnimoRepository()

    private val _existeHoy = MutableLiveData<Boolean>()
    val existeHoy: LiveData<Boolean> = _existeHoy

    private val _estadoHoy = MutableLiveData<EstadoDeAnimo?>()
    val estadoHoy: LiveData<EstadoDeAnimo?> = _estadoHoy

    fun guardarEstadoDeAnimo(estadoDeAnimo: EstadoDeAnimo, onDone: (Boolean) -> Unit){
        repository.guardarEstadoDeAnimo(estadoDeAnimo, onSuccess={onDone(true)}, onFailure={onDone(false)})
    }
    fun existeEstadoAHoy(){
        repository.existeEstadoAHoy{_existeHoy.postValue(it)}
    }

    fun getEstadoDeAnimo(){
        repository.getEstadoDeAnimo{_estadoHoy.postValue(it) }
    }

}