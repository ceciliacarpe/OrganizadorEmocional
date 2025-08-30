package com.example.organizadoremocional.model

/**
 *Enum class para representar las emociones asociadas que puede tener una tarea.
 * Cada emoción tiene 3 propiedades
 * @property tipoEmocion nombre de la emoción
 * @property valencia determina el "grado de placer", define si la experiencia emocional es positiva o negativa.
 * @property activacion determinal el "nivel de energía" asociado a esa emoción
 */
enum class Emocion(val tipoEmocion: String, val valencia: Float, val activacion: Float){
    ALEGRIA("Alegria",2f,1f ),
    TRISTEZA("Tristeza",-2f,-1f),
    IRA("Ira",-2f,2f),
    MIEDO("Miedo",-1f,1f),
    NEUTRA("Neutra", 0f, 0f);

    /**
     * Devuelve nombre de la emoción (tipoEmocion)
     */
    override fun toString(): String {
        return tipoEmocion
    }
}
