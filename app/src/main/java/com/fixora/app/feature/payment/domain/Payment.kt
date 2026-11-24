package com.fixora.app.feature.payment.domain

import java.time.Instant

/** Por qué el técnico cobra de más (API-36). */
enum class AdjustmentReason(val apiValue: String) {
    ExtraMaterials("materiales_extra"),
    ExtraTime("tiempo_adicional"),
    Other("otro");

    companion object {
        fun from(value: String): AdjustmentReason = entries.firstOrNull { it.apiValue == value } ?: Other
    }
}

enum class AdjustmentStatus {
    /** Espera la respuesta del cliente. */
    Pending,
    Approved,
    Rejected,

    /** Aprobado y ya cobrado junto con el servicio. */
    Paid;

    /** Suma al total del servicio. */
    val countsTowardTotal: Boolean get() = this == Approved || this == Paid

    companion object {
        fun from(value: String): AdjustmentStatus = when (value) {
            "aprobado" -> Approved
            "rechazado" -> Rejected
            "pagado" -> Paid
            else -> Pending
        }
    }
}

/** Lo que el técnico propone cobrar de más ya en el domicilio. */
data class PriceAdjustment(
    val id: String,
    val amount: String,
    val reason: AdjustmentReason,
    val description: String?,
    val status: AdjustmentStatus,
    val createdAt: Instant?,
)

/** Sin pasarela de pago: solo se registra lo que se cobra en persona. */
enum class PaymentMethod(val apiValue: String) {
    Cash("efectivo"),
    Transfer("transferencia");

    companion object {
        fun from(value: String): PaymentMethod = if (value == Transfer.apiValue) Transfer else Cash
    }
}

enum class PaymentStatus {
    /** El cliente lo registró y espera que el proveedor confirme que lo recibió. */
    Pending,
    Confirmed,

    /** El proveedor dice que no le llegó. */
    Failed;

    companion object {
        fun from(value: String): PaymentStatus = when (value) {
            "completado" -> Confirmed
            "fallido" -> Failed
            else -> Pending
        }
    }
}

data class Payment(
    val id: String,
    val amount: String,
    val method: PaymentMethod,
    val reference: String?,
    val status: PaymentStatus,
    val rejectionReason: String?,
    val createdAt: Instant?,
)

/** Cuánto se ha pagado y cuánto falta (API-37). Los montos vienen como «350.00». */
data class PaymentSummary(
    val totalAmount: String,
    val paid: String,
    val pendingConfirmation: String,
    val balanceDue: String,
    /** Finalizado y con saldo: es un adeudo. */
    val hasDebt: Boolean,
) {
    val isPaid: Boolean get() = MoneyRules.toCents(totalAmount) > 0 && MoneyRules.toCents(balanceDue) == 0L
    val awaitsConfirmation: Boolean get() = MoneyRules.toCents(pendingConfirmation) > 0
}

/** Servicio terminado con saldo: para el cliente lo que debe, para el proveedor lo que le deben. */
data class Debt(
    val requestId: String,
    val category: String,
    val completedAt: Instant?,
    val summary: PaymentSummary,
    val remindersSent: Int,
    val lastReminderAt: Instant?,
    /** Desde cuándo se puede mandar otro recordatorio; null si ya se puede. */
    val nextReminderAt: Instant?,
) {
    /** La API no deja recordar si el cliente ya registró un pago que espera confirmación. */
    val canRemind: Boolean get() = nextReminderAt == null && !summary.awaitsConfirmation
}

data class Debts(val totalDue: String, val debts: List<Debt>)

/** Montos en pesos como los maneja la API: texto con hasta 2 decimales. */
object MoneyRules {
    /** Tope por ajuste en la API: evita un cero de más sin limitar trabajos reales. */
    const val MAX_ADJUSTMENT_CENTS = 50_000_00L

    private val amountPattern = Regex("""^(\d{1,9})(?:\.(\d{1,2}))?$""")

    /** «350», «350.5» o «350.50» a centavos; null si no es un monto válido. */
    fun parseCents(text: String): Long? {
        val match = amountPattern.matchEntire(text.trim()) ?: return null
        val pesos = match.groupValues[1].toLong()
        val cents = match.groupValues[2].padEnd(2, '0').toLong()
        return pesos * 100 + cents
    }

    fun toCents(amount: String?): Long = amount?.let(::parseCents) ?: 0L

    /** Para mandarlo a la API: «470.00». */
    fun toAmount(cents: Long): String = "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

    /** Para mostrarlo: «$1,650» o «$350.50». */
    fun format(cents: Long): String {
        val pesos = (cents / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        val rest = cents % 100
        return if (rest == 0L) "$$pesos" else "$$pesos.${rest.toString().padStart(2, '0')}"
    }

    fun format(amount: String?): String = format(toCents(amount))

    /** Lo que se escribe en el campo de monto: «470» o «470.50». */
    fun toInput(cents: Long): String = format(cents).removePrefix("$").replace(",", "")
}
