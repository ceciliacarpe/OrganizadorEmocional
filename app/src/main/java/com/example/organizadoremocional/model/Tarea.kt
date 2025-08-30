package com.example.organizadoremocional.model
import com.google.firebase.firestore.ServerTimestamp
import java.io.Serializable
import java.util.Date

/**
 * Clase de datos que representa uan tarea
 *
 * @property idTarea Id único de la tarea (generado automáticamentre por FireStore)
 * @property titulo Título de la tarea
 * @property descripcion Descripción de la tarea
 * @property prioridad Prioridad de la tarea, puede ser: "Alta", "Media" o "Baja".
 * @property fechaCreacion Fecha en la que la tarea fue creada (asignada automáticamente por FireStore)
 * @property fechaRealizar Fecha en la que se planea hacer la tarea.
 * @property fechaCompletada Fecha en la que la tarea fue marcada como completada.
 * @property aplazar Indica si una tarea no completada el día seleccionado se pospone al día siguiente o no.
 * @property completada Indica si una tarea ha sido marcada como completada o no.
 * @property emocion Emoción asoaciada a la tarea.
 */
data class Tarea(
    var idTarea: String = "",
    var titulo:String = "",
    var descripcion:String = "",
    var prioridad:String="",
    @ServerTimestamp var fechaCreacion:Date? = null,
    var fechaRealizar: Date? = null,
    var fechaCompletada: Date? = null,
    var aplazar: Boolean = false,
    var completada: Boolean = false,
    var emocion: Emocion? = Emocion.NEUTRA
) : Serializable

