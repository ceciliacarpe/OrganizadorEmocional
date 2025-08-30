package com.example.organizadoremocional.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Clase de datos que representa Estado de animo.
 *
 */
data class EstadoDeAnimo(
    val tipoEAnimo: EstadoDeAnimoTipo = EstadoDeAnimoTipo.MOTIVADO,
    @ServerTimestamp val fecha: Date? = null
)
