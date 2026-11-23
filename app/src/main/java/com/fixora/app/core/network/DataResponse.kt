package com.fixora.app.core.network

import kotlinx.serialization.Serializable

/** Casi todas las respuestas de la API vienen como `{ success, message, data }`; aquí solo se lee `data`. */
@Serializable
data class DataResponse<T>(val data: T)
