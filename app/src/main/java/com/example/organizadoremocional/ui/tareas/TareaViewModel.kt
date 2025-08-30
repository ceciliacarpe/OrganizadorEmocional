package com.example.organizadoremocional.ui.tareas
import android.app.Application
import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.organizadoremocional.core.sync.CalendarioSync
import com.example.organizadoremocional.model.Emocion
import com.example.organizadoremocional.model.EstadoDeAnimoTipo
import com.example.organizadoremocional.model.Tarea
import com.example.organizadoremocional.repository.TareaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

/**
 * ViewModel que gestiona la lógica de las tareas.
 * Se comunica con el repositorio para las operaciones con FireStore.
 */
class TareaViewModel(app: Application) : AndroidViewModel(app){

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_SYNC_ENABLED = "calendarSyncEnabled"
        private const val KEY_SYNC_EMAIL = "syncEmail"
    }

    // para leer el flag y el email
    private val prefs = getApplication<Application>()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val repository = TareaRepository()

    //Ver si las tareas están completadas
    private val tareaCreada = MutableLiveData<Boolean>()
    val tareas: LiveData<Boolean> get() = tareaCreada

    //Almacenar la lista de tareas no completadas
    private val _tareasNoCompletadas = MutableLiveData<List<Tarea>>()
    val tareasNoCompletadas: LiveData<List<Tarea>> get() = _tareasNoCompletadas

    //Resultado de la eliminacion de la tarea
    private val _resultadoEliminacion = MutableLiveData<Boolean>()
    val resultadoEliminacion: LiveData<Boolean> get()=_resultadoEliminacion

    //Para editar tareas
    private val _tareaEditada = MutableLiveData<Boolean>()
    val tareaEditada: LiveData<Boolean> = _tareaEditada

    //Almacenar tareas ordenadas
    private val _tareasHoyOrdenadas = MutableLiveData<List<Tarea>>()
    val tareasHoyOrdenadas: LiveData<List<Tarea>> = _tareasHoyOrdenadas

    private val _highPriorityAlert = MutableLiveData<String?>()
    val highPriorityAlert: LiveData<String?> = _highPriorityAlert


    /**
     * Crear una nueva tarea en FireStore
     *
     * @param titulo Títilo de la tarea.
     * @param descripcion Descripción de la tarea.
     * @param prioridad Nivel de prioridad ("Alta", "Media" o "Baja").
     * @param fecha Fecha en la que se debe realizar la tarea.
     * @param emocion Emoción asociada a la tarea.
     * @param historial Historial al que pertenece la tarea.
     */
    fun crearTarea(titulo:String, descripcion:String, prioridad:String, fecha: Date, aplazar:Boolean, emocion: Emocion){
        repository.crearTarea(titulo,descripcion,prioridad,fecha,aplazar, emocion,
            onSuccess = {tareaCreada.value = true
                triggerCalendarSyncIfEnabled()
            }, onFailure = { tareaCreada.value = false})
    }

    /**
     * Obtiene todas las tareas que no han sido marcadas como completadas.
     */
    fun obtenerTareasNoCompletadas(){
        repository.getTareasNoCompletadas{tareas ->
            _tareasNoCompletadas.value = tareas
        }
    }

    /**
     * Elimina una tarea de FireStore
     *
     * @param idTarea id de la tarea a eliminar.
     */
    fun eliminarTarea(idTarea: String){
        repository.eliminarTarea(idTarea) { resultado ->
            if (resultado) {
                triggerCalendarSyncIfEnabled()
                obtenerTareasNoCompletadas()
            }
            _resultadoEliminacion.value = resultado
        }
    }


    /**
     * Edita una tarea que ya existe en FireStore.
     *
     * @param tarea Tarea que se va a actualizar.
     */
    fun editarTarea(tarea: Tarea){
        repository.editarTarea(tarea, onSuccess = {
            obtenerTareasNoCompletadas()
            _tareaEditada.value = true
            triggerCalendarSyncIfEnabled()
        }, onFailure = {
            _tareaEditada.value = false
        })
    }

    /**
     * Resetea el valor de creación de una tarea.
     * Para que no haya toast duplicados.
     */
    fun resetCrearTarea() {
        tareaCreada.value = false
    }

    /**
     * Calcular costo emocional.
     * @param emocion emocion asociada a la tarea.
     * @param estado estado de animo actual del usuario.
     * @param prioridad prioridad asociada a la tarea.
     * @return costo emocional de una tarea.
     */
    fun calcularCostoEmocional(emocion: Emocion, estado: EstadoDeAnimoTipo, prioridad: String):Double{

        //Distancia emocional
        val dx= (emocion.valencia - 1).toDouble()
        val dy = (emocion.activacion - 1).toDouble()
        val distancia = Math.hypot(dx, dy)

        //Congruencia emocional
        val ce = when{
            (emocion.valencia == estado.valencia) || (emocion.activacion == estado.activacion)  -> 0.5
            (Math.signum(emocion.valencia) == Math.signum(estado.valencia)) || (Math.signum(emocion.activacion) == Math.signum(estado.activacion))->1.0
            else -> 1.5
        }

        //Factor de prioridad
        val fp = when(prioridad.uppercase()){
            "ALTA" -> 0.7
            "MEDIA" -> 1.0
            "BAJA" -> 1.3
            else -> 1.0
        }

        return (distancia * ce) * fp
    }

    /**
     * Define el orden de las tareas segun el costo emocional y el estado de animo del usuario.
     * @param estadoDeAnimo estado de animo actual del usuario.
     */
    fun obtenerTareasHoyOrdenadas(estadoDeAnimo: EstadoDeAnimoTipo) {
        repository.getTareasNoCompletadas { todas ->

            // Definir rango: de inicio de hoy a inicio de mañana
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val inicioHoy = cal.time
            cal.add(Calendar.DAY_OF_MONTH, 1)
            val inicioManana = cal.time

            // Filtrar sólo las de hoy
            val tareasDeHoy = todas.filter { tarea ->
                tarea.fechaRealizar?.let { fecha ->
                    val esHoy = fecha >= inicioHoy && fecha < inicioManana
                    val esAplazable = fecha < inicioHoy && tarea.aplazar && !tarea.completada
                    esHoy || esAplazable
                } ?: false
            }

            if (tareasDeHoy.isEmpty()) {
                _tareasHoyOrdenadas.postValue(emptyList())
                return@getTareasNoCompletadas
            }

            //Calcular costos
            val costosMap: List<Pair<Tarea, Double>> = tareasDeHoy.map { tarea ->
                val costo = calcularCostoEmocional(
                    emocion = tarea.emocion!!,
                    estado = estadoDeAnimo,
                    prioridad = tarea.prioridad
                )
                Log.d(TAG, "Costo para '${tarea.titulo}': $costo")
                tarea to costo
            }

            //Extraer y ordenar costos
            val ascPairs = costosMap.sortedBy { it.second }
            val asc = ascPairs.map { it.first }
            val desc = asc.reversed()

            //Casos sin bloques
            when (estadoDeAnimo) {
                EstadoDeAnimoTipo.DESMOTIVADO -> {
                    Log.d(TAG, "Orden final (DESMOTIVADO asc): ${asc.map { it.titulo }}")
                    _tareasHoyOrdenadas.postValue(asc)
                    return@getTareasNoCompletadas
                }
                EstadoDeAnimoTipo.MOTIVADO -> {
                    Log.d(TAG, "Orden final (MOTIVADO desc): ${desc.map { it.titulo }}")
                    _tareasHoyOrdenadas.postValue(desc)
                    return@getTareasNoCompletadas
                }
                else -> { }
            }

            //Definir umbrales
            val valoresOrdenados = ascPairs.map { it.second } // ya ASC
            val n = valoresOrdenados.size
            val t1 = valoresOrdenados.getOrElse(n / 3) { valoresOrdenados.first() }
            val t2 = valoresOrdenados.getOrElse(2 * n / 3) { valoresOrdenados.last() }
            Log.d(TAG, "Umbrales: t1=$t1, t2=$t2")

            //Calcular si las tareas son fáciles, intermedias o difíciles
            val faciles = mutableListOf<Tarea>()
            val medias  = mutableListOf<Tarea>()
            val dificiles = mutableListOf<Tarea>()

            ascPairs.forEach { (tarea, costo) ->
               when {
                   costo <= t1 -> faciles += tarea
                   costo <= t2 -> medias  += tarea
                   else -> dificiles += tarea
               }
            }

            Log.d(TAG, "Tareas faciles (${faciles.size}): ${faciles.map { it.titulo }}")
            Log.d(TAG, "Tareas intermedias (${medias.size}): ${medias.map { it.titulo }}")
            Log.d(TAG, "Tareas dificiles (${dificiles.size}): ${dificiles.map { it.titulo }}")

            //Ordenar segun estado de animo bloques
            val ordenadas = when (estadoDeAnimo) {
                EstadoDeAnimoTipo.ESTRESADO -> medias + faciles + dificiles
                EstadoDeAnimoTipo.RELAJADO  -> faciles + medias + dificiles
                else -> asc
            }

            Log.d(TAG, "Orden final (${ordenadas.size}): ${ordenadas.map { it.titulo }}")
            _tareasHoyOrdenadas.postValue(ordenadas)
        }
    }

    fun triggerCalendarSyncIfEnabled() {
        if (!prefs.getBoolean(KEY_SYNC_ENABLED, false)) return
        val email = prefs.getString(KEY_SYNC_EMAIL, null) ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                CalendarioSync.syncAllPendingTasks(
                    getApplication(),
                    email
                )
            } catch (e: Exception) {
                Log.e("TareaViewModel", "Error al sincronizar Calendar", e)
            }
        }
    }


}