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
}
