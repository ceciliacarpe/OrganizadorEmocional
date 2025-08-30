package com.example.organizadoremocional.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.example.organizadoremocional.model.EstadoDeAnimoTipo

/**
 *  * Repositorio encargado de gestionar los registros de Mensajes motivacionales.
 *
 */
class MensajesMotivacionalesRepository {
    private val db = FirebaseFirestore.getInstance()

    fun fetchMensajes(
        onSuccess: (Map<EstadoDeAnimoTipo, List<String>>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection("mensajesMotivacionales")
            .get()
            .addOnSuccessListener { snap ->
                val mapa = snap.documents.mapNotNull { doc ->
                    val estado = EstadoDeAnimoTipo.values()
                        .find { it.name == doc.id } ?: return@mapNotNull null
                    val lista = doc.get("mensajes") as? List<*>
                        ?: return@mapNotNull null
                    estado to lista.filterIsInstance<String>()
                }.toMap()
                onSuccess(mapa)
            }
            .addOnFailureListener(onError)
    }
}
