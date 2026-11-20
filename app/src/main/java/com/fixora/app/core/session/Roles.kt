package com.fixora.app.core.session

/** Parte de la app que ve cada rol al iniciar sesión. */
enum class HomeArea { Client, Provider }

/** Roles tal como los manda la API en `user.role` (tabla roles). */
object Roles {
    const val CLIENT = "cliente"
    const val PROVIDER = "proveedor"
    const val MANAGER = "gerente"
    const val EMPLOYEE = "empleado"

    /**
     * El cliente tiene su inicio; el proveedor (encargado), el gerente y el empleado comparten el
     * del proveedor. Administrador y soporte trabajan en el panel web: devuelve null.
     */
    fun homeArea(role: String): HomeArea? = when (role.trim().lowercase()) {
        CLIENT -> HomeArea.Client
        PROVIDER, MANAGER, EMPLOYEE -> HomeArea.Provider
        else -> null
    }

    /** Estado de una cuenta que ya puede usar la app (`user.status` de la API). */
    const val ACTIVE_STATUS = "activo"

    /**
     * Proveedor (o su personal) cuya cuenta todavía no está activa: su solicitud sigue en revisión.
     * La API le deja entrar con una sesión limitada a su solicitud, pero la app no es para él aún.
     */
    fun isInReview(role: String, status: String): Boolean =
        homeArea(role) == HomeArea.Provider && status.trim().lowercase() != ACTIVE_STATUS
}
