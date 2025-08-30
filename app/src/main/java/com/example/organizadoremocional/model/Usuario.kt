package com.example.organizadoremocional.model

/**
 * Clase de datos que representa a un usuario
 * @property id Identificador único del usuario
 * @property nombre Nombre del usuario.
 * @property email Correo electrónico del usuario.
 */
data class Usuario(
    val id: String ="",
    val nombre: String = "",
    val email: String = "",
)
