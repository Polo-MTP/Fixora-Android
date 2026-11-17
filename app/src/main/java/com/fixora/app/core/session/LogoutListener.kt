package com.fixora.app.core.session

/**
 * Algo que hay que hacer justo antes de cerrar sesión, con la sesión todavía guardada (la API pide
 * el token de acceso): desactivar el token de notificaciones, ponerse fuera de línea, etc. Cada
 * módulo agrega el suyo con @IntoSet y SessionRepository los corre todos a la vez.
 */
fun interface LogoutListener {
    /** Debe terminar en pocos segundos aunque no haya internet. */
    suspend fun beforeLogout()
}
