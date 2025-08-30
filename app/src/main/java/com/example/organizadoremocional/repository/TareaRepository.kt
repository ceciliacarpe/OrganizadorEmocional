package com.example.organizadoremocional.repository
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.CollectionReference
import com.example.organizadoremocional.model.Emocion
import com.example.organizadoremocional.model.Tarea
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

/**
 * Repositorio encargado de gestionar los registros de Tareas.
.
 */
class TareaRepository {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // /usuarios/{uid}/tareas
    private fun tareasCol() =
        db.collection("usuarios")
            .document(auth.currentUser?.uid ?: error("No hay usuario"))
            .collection("tareas")

    /**
     * Crear una nueva tarea en FireStore
     *
     * @param titulo Títilo de la tarea.
     * @param descripcion Descripción de la tarea.
     * @param prioridad Nivel de prioridad ("Alta", "Media" o "Baja").
     * @param fecha Fecha en la que se debe realizar la tarea.
     * @param emocion Emoción asociada a la tarea.
     * @param onSuccess Callback que se ejecuta si la tarea se guarda con éxito.
     * @param onFailure Callback que se ejecuta si ocurre un error.
     */
    fun crearTarea(titulo:String, descripcion:String, prioridad:String, fecha:Date,aplazar:Boolean, emocion: Emocion,
                    onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {

        val doc = tareasCol().document()
        val fechaActual = Date()

        val tareaMap = hashMapOf(
            "idTarea" to doc.id,
            "titulo" to titulo,
            "descripcion" to descripcion,
            "prioridad" to prioridad,
            "fechaCreacion" to fechaActual,
            "fechaRealizar" to fecha,
            "fechaCompletada" to null,
            "aplazar" to aplazar,
            "completada" to false,
            "emocion" to emocion.name,
        )
        doc.set(tareaMap)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }

    }

    /**
     * Obtiene todas las tareas que no han sido marcadas como completadas.
     *
     * @param callback Fución que recibe la lista de tareas no completadas.
     */
    fun getTareasNoCompletadas(callback: (List<Tarea>) -> Unit) {

        val hoy:Date = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        tareasCol()
            .whereEqualTo("completada", false)
            .get()
            .addOnSuccessListener { result ->
                val lista = result.documents.mapNotNull { doc ->
                    doc.toObject(Tarea::class.java)?.apply {
                        idTarea = doc.id
                        emocion = Emocion.valueOf(doc.getString("emocion") ?: Emocion.NEUTRA.name)
                    }
                }

                //Filtrar: no pasadas o aplazables
                val filtrada = lista.filter { tarea ->
                    tarea.fechaRealizar?.let { fecha ->
                        !fecha.before(hoy) || tarea.aplazar
                    } ?: true
                }
                callback(filtrada)
            }
            .addOnFailureListener {
                callback(emptyList())
            }
    }

    /** Suspende hasta devolver la lista filtrada de tareas no completadas. */
    suspend fun getTareasNoCompletadasSuspend(): List<Tarea> = withContext(Dispatchers.IO) {
        val hoy = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val snapshot = tareasCol()
            .whereEqualTo("completada", false)
            .get()
            .await()

        snapshot.documents.mapNotNull { doc ->
            doc.toObject(Tarea::class.java)?.apply {
                idTarea = doc.id
                emocion = Emocion.valueOf(
                    doc.getString("emocion") ?: Emocion.NEUTRA.name
                )
            }
        }.filter { tarea ->
            tarea.fechaRealizar?.let { fecha ->
                // mantén las de hoy en adelante, o si son aplazables
                !fecha.before(hoy) || tarea.aplazar
            } ?: true
        }
    }


    /**
     * Fución para eliminar tareas.
     *
     * @param idTarea id de la tarea a eliminar.
     * @param callback Fución que recibe true si se eliminó correctamente o false si falla.
     *
     */
    fun eliminarTarea(idTarea: String, callback: (Boolean) -> Unit) {
        tareasCol()
            .document(idTarea)
            .delete()
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    /**
     * Fución para editar los datos de una tarea.
     * @param tarea Tarea que se va a actualizar.
     * @param onSuccess Callback en caso de éxito.
     * @param onFailure Callback en caso de fallo.
     */
    fun editarTarea(tarea: Tarea, onSuccess: () -> Unit, onFailure: (Exception) -> Unit ){

        val tareaMap = mutableMapOf<String, Any>(
            "titulo" to tarea.titulo,
            "descripcion" to tarea.descripcion,
            "prioridad" to tarea.prioridad,
            "fechaRealizar" to tarea.fechaRealizar!!,
            "aplazar" to tarea.aplazar,
            "completada" to tarea.completada,
            "emocion" to (tarea.emocion?.name ?:"NEUTRA")
        )
        tarea.fechaCompletada?.let { tareaMap["fechaCompletada"] = it }
        tareasCol().document(tarea.idTarea)
            .update(tareaMap)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }


    }


    private fun Calendar.zeroTime() {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    private fun Calendar.endOfDay() {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }


    /**
     * Obtiene todas las tareas asignadas en un rango de fechas determinado.
     * Devuelve  las que han estado activas dentro del intervalo
     * @param start fecha de inicio del rango (inclusive).
     * @param end fecha de fin del rango (inclusive).
     * @return lista de tareas que cumplen con los criterios definidos.
     */
    suspend fun getTareasAsignadasEnRango(start: Date, end: Date): List<Tarea> = withContext(Dispatchers.IO) {
        val snap = tareasCol().get().await()

        snap.documents.mapNotNull { doc ->
            doc.toObject(Tarea::class.java)?.apply {
                idTarea = doc.id
                emocion = Emocion.valueOf(doc.getString("emocion") ?: Emocion.NEUTRA.name)
            }
        }.filter { tarea ->
            val fechaCreacion = tarea.fechaCreacion ?: return@filter false
            val fechaRealizar = tarea.fechaRealizar ?: return@filter false
            val fechaCompletada = tarea.fechaCompletada
            val hasta = fechaCompletada ?: fechaRealizar

            val estaEnRango = !(hasta.before(start) || fechaCreacion.after(end))

            val esAplazableNoCompletada =
                tarea.aplazar && !tarea.completada && fechaRealizar.before(start)

            val incluir = estaEnRango || esAplazableNoCompletada

            incluir
        }

    }


    /**
     * Devuelve las tareas completadas en [start, end]
     */
    suspend fun getTareasCompletadasEnRango(start: Date, end: Date): List<Tarea> = withContext(Dispatchers.IO) {
        val snap = tareasCol()
            .whereEqualTo("completada", true)
            .get()
            .await()

        snap.documents.mapNotNull { doc ->
            doc.toObject(Tarea::class.java)?.apply {
                idTarea = doc.id
                emocion = Emocion.valueOf(doc.getString("emocion") ?: Emocion.NEUTRA.name)
            }
        }.filter { tarea ->
            val desde = tarea.fechaCreacion ?: return@filter false
            val hasta = if (tarea.completada) tarea.fechaCompletada else tarea.fechaRealizar ?: return@filter false

            val visibleHoy = !hasta?.before(start)!! && !desde.after(end)

            val yaActiva = desde <= end && (tarea.fechaRealizar ?: end) <= end

            visibleHoy && yaActiva
        }
    }

    /**
     * Devuelve tareas completadas en el día actual.
     */
    suspend fun getTareasCompletadasHoy(): List<Tarea> {
        val cal0 = Calendar.getInstance().apply { zeroTime() }
        val start = cal0.time
        val end   = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY,23); set(Calendar.MINUTE,59); set(Calendar.SECOND,59)
        }.time
        return getTareasCompletadasEnRango(start, end)
    }


    /**
     * * Devuelve tareas completadas en la semana.
     */
    suspend fun getTareasCompletadasSemana(): List<Tarea> {
        val cal0 = Calendar.getInstance().apply { zeroTime(); add(Calendar.DAY_OF_YEAR, -6) }
        val start = cal0.time
        val end   = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY,23); set(Calendar.MINUTE,59); set(Calendar.SECOND,59)
        }.time
        return getTareasCompletadasEnRango(start, end)
    }

    /**
     * * Devuelve tareas completadas en el mes.
     */
    suspend fun getTareasCompletadasMes(): List<Tarea> {
        val cal0 = Calendar.getInstance().apply { zeroTime(); set(Calendar.DAY_OF_MONTH,1) }
        val start = cal0.time
        val end   = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY,23); set(Calendar.MINUTE,59); set(Calendar.SECOND,59)
        }.time
        return getTareasCompletadasEnRango(start, end)
    }

    /**
     * Devuelve tareas completadas en el año.
     */
    suspend fun getTareasCompletadasAnio(): List<Tarea> {
        val cal0 = Calendar.getInstance().apply { zeroTime(); set(Calendar.DAY_OF_YEAR,1) }
        val start = cal0.time
        val end   = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY,23); set(Calendar.MINUTE,59); set(Calendar.SECOND,59)
        }.time
        return getTareasCompletadasEnRango(start, end)
    }

    /**
     * Devuelve tareas pendientes de la semana..
     */
    suspend fun getTareasAsignadasSemana(): List<Tarea> {
        val cal0 = Calendar.getInstance().apply { zeroTime(); add(Calendar.DAY_OF_YEAR, -6) }
        val start = cal0.time
        val end = Calendar.getInstance().apply { endOfDay() }.time
        return getTareasAsignadasEnRango(start, end)
    }

    /**
     * Devuelve tareas pendientes del año.
     */
    suspend fun getTareasAsignadasAnio(): List<Tarea> {
        val cal0 = Calendar.getInstance().apply { zeroTime(); set(Calendar.DAY_OF_YEAR, 1) }
        val start = cal0.time
        val end = Calendar.getInstance().apply { endOfDay() }.time
        return getTareasAsignadasEnRango(start, end)
    }

    /**
     * Devuelve solo las tareas no completadas visibles hoy (con aplazables)
     */
    suspend fun getTareasAsignadasHoyConAplazables(): List<Tarea> = withContext(Dispatchers.IO) {
        val calInicio = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = calInicio.time

        val end = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time

        val snap = tareasCol()
            .whereEqualTo("completada", false)
            .get()
            .await()

        snap.documents.mapNotNull { doc ->
            doc.toObject(Tarea::class.java)?.apply {
                idTarea = doc.id
                emocion  = Emocion.valueOf(doc.getString("emocion") ?: Emocion.NEUTRA.name)
            }
        }.filter { tarea ->
            val fr = tarea.fechaRealizar
            val dueHoy = fr != null && !fr.before(start) && !fr.after(end)
            val aplazableArrastrada = tarea.aplazar && (fr == null || fr.before(start))
            dueHoy || aplazableArrastrada
        }
    }


    /**
     * Devuelve tareas pendientes del mes.
     */
    suspend fun getTareasAsignadasMes(): List<Tarea> {
        val cal0 = Calendar.getInstance().apply {
            zeroTime()
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val start = cal0.time
        val end = Calendar.getInstance().apply { endOfDay() }.time
        return getTareasAsignadasEnRango(start, end)
    }



}
