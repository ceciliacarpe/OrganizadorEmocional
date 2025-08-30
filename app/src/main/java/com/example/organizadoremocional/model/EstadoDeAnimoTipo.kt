package com.example.organizadoremocional.model

/**
 * Enum class para representar los estados de Animo diarios del usuario.
 * Cada emoción tiene 3 propiedades
 * @property tipoEAnimo nombre del estado de animo
 */
enum class EstadoDeAnimoTipo (val tipoEAnimo:String, val valencia: Float, val activacion: Float){
    MOTIVADO("Motivado", 2f, 2f),
    RELAJADO("Relajado", 1f, -1f),
    ESTRESADO("Estresado", -1f,1f),
    DESMOTIVADO("Desmotivado", -2f, -2f);

    /**
     * Devuelve nombre del estado de animo (tipoEAnimo)
     */
    override fun toString(): String {
        return tipoEAnimo
    }

}