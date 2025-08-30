package com.example.organizadoremocional.repository

import com.example.organizadoremocional.model.EstadoDeAnimo
import com.example.organizadoremocional.model.EstadoDeAnimoTipo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Repositorio encargado de gestionar los registros de EstadoDeÁnimo.
 */
class EstadoDeAnimoRepository {
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun coleccion() = db.collection("usuarios")
        .document(auth.currentUser?.uid ?: error("No hay usuario autenticado"))

    private val fmtId = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())


    /**
     * Guarda un nuevo estado de ánimo como un documento en:
     * usuarios/{uid}/estadosDeAnimo/{yyyy-MM-dd}/registros/{autoId}
     */
    fun guardarEstadoDeAnimo(estadoDeAnimo: EstadoDeAnimo, onSuccess:() -> Unit, onFailure:(Exception) -> Unit){

        val idDoc = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(estadoDeAnimo.fecha!!)

        coleccion()
            .collection("estadosDeAnimo")
            .document(idDoc)
            .collection("registros")
            .add(estadoDeAnimo)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener {  e-> onFailure(e)  }
    }

    /**
     * Comprueba si hay algún registro hoy
     */
    fun existeEstadoAHoy(callback: (Boolean) -> Unit){
        val idHoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date())

        coleccion()
            .collection("estadosDeAnimo")
            .document(idHoy)
            .collection("registros")
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                callback(!snap.isEmpty)
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    /**
     * Obtine el estado de ánimo más reciente
     */
    fun getEstadoDeAnimo(callback: (EstadoDeAnimo?) -> Unit){
        val idHoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date())

        coleccion()
            .collection("estadosDeAnimo")
            .document(idHoy)
            .collection("registros")
            .orderBy("fecha", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                val estado = if(snap.isEmpty){
                    null
                }else{
                    snap.documents[0].toObject(EstadoDeAnimo::class.java)
                }
                callback(estado)
            }
            .addOnFailureListener {
                callback(null)
            }
    }

    /**
     * Recorre todos los días entre start y end (incluidos)
     * y consulta `.collection("estadosDeAnimo").document(fecha).collection("registros")`.
     */
    private suspend fun getHistorialBetween(start: Date, end: Date): List<EstadoDeAnimo> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<EstadoDeAnimo>()
            val cal = Calendar.getInstance().apply {
                time = start
                zeroTime()
            }
            val endCal = Calendar.getInstance().apply {
                time = end
                zeroTime()
            }

            while (!cal.after(endCal)) {
                val dateId = fmtId.format(cal.time)
                val snap = coleccion()
                    .collection("estadosDeAnimo")
                    .document(dateId)
                    .collection("registros")
                    .orderBy("fecha", Query.Direction.ASCENDING)
                    .get()
                    .await()

                snap.documents
                    .mapNotNull { it.toObject(EstadoDeAnimo::class.java) }
                    .also { out.addAll(it) }

                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            out
        }

    /** Hoy, 00:00 a 23:59 */
    suspend fun getHistorialDeHoySuspend(): List<EstadoDeAnimo> {
        val start = Calendar.getInstance().apply { zeroTime() }.time
        val end   = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }.time
        return getHistorialBetween(start, end)
    }

    private fun Calendar.zeroTime() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    /**
     * Calcula el tiempo que el usuario ha pasado en cada estado de ánimo durante un día específico.
     */
    suspend fun getTiempoPorEstadoEnDia(dia: Date): Map<EstadoDeAnimoTipo, Long> {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return emptyMap()
        val db = FirebaseFirestore.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val docId = sdf.format(dia)

        val registrosSnap = db.collection("usuarios")
            .document(uid)
            .collection("estadosDeAnimo")
            .document(docId)
            .collection("registros")
            .orderBy("fecha")
            .get()
            .await()

        val registros = registrosSnap.documents.mapNotNull { doc ->
            val fecha = doc.getTimestamp("fecha")?.toDate() ?: return@mapNotNull null
            val tipoStr = doc.getString("tipoEAnimo") ?: return@mapNotNull null
            Pair(fecha, EstadoDeAnimoTipo.valueOf(tipoStr))
        }

        if (registros.isEmpty()) return emptyMap()

        val resultado = mutableMapOf<EstadoDeAnimoTipo, Long>()

        for (i in registros.indices) {
            val (inicio, tipo) = registros[i]
            val fin = if (i < registros.lastIndex) {
                registros[i + 1].first
            } else {
                val hoy = Calendar.getInstance().apply { zeroTime() }.time
                val registroEsDeHoy = inicio.after(hoy)
                if (registroEsDeHoy) {
                    Date() // hora actual
                } else {
                    Calendar.getInstance().apply {
                        time = inicio
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }.time
                }
            }

            val minutos = ((fin.time - inicio.time) / (1000 * 60)).coerceAtLeast(1)
            resultado[tipo] = resultado.getOrDefault(tipo, 0L) + minutos
        }

        return resultado
    }

    /**
     * Calcula el tiempo total (minutos) que el usuario ha permanecido en cada estado de ánimo
     * dentro de un intervalo.
     *
     * @param start fecha de inicio del intervalo.
     * @param end fecha de fin del intervalo.
     * @return tiempo total en minutos por cada tipo de estado de ánimo.
     */
    suspend fun getTiempoPorEstadoEnIntervalo(start: Date, end: Date): Map<EstadoDeAnimoTipo, Long> {
        val historial = getHistorialBetween(start, end)
            .filter { it.fecha != null && it.tipoEAnimo != null }
            .sortedBy { it.fecha }

        if (historial.isEmpty()) return emptyMap()

        val result = mutableMapOf<EstadoDeAnimoTipo, Long>()

        for (i in historial.indices) {
            val estado = historial[i]
            val fechaInicio = estado.fecha!!
            val tipo = estado.tipoEAnimo!!

            val fechaFin = if (i < historial.lastIndex) {
                historial[i + 1].fecha!!
            } else {
                val registroEsDeHoy = isSameDay(fechaInicio, Date())
                if (registroEsDeHoy) Date() else {
                    Calendar.getInstance().apply {
                        time = fechaInicio
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }.time
                }
            }

            val inicioReal = maxOf(fechaInicio, start)
            val finReal = minOf(fechaFin, end)

            if (inicioReal.before(finReal)) {
                val minutos = ((finReal.time - inicioReal.time) / (1000 * 60)).coerceAtLeast(1)
                result[tipo] = result.getOrDefault(tipo, 0L) + minutos
            }
        }
        return result
    }

    private fun isSameDay(d1: Date, d2: Date): Boolean {
        val c1 = Calendar.getInstance().apply { time = d1; zeroTime() }
        val c2 = Calendar.getInstance().apply { time = d2; zeroTime() }
        return c1.time == c2.time
    }

}

