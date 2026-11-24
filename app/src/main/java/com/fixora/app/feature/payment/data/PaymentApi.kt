package com.fixora.app.feature.payment.data

import com.fixora.app.core.network.DataResponse
import com.fixora.app.feature.payment.domain.AdjustmentReason
import com.fixora.app.feature.payment.domain.AdjustmentStatus
import com.fixora.app.feature.payment.domain.Debt
import com.fixora.app.feature.payment.domain.Payment
import com.fixora.app.feature.payment.domain.PaymentMethod
import com.fixora.app.feature.payment.domain.PaymentStatus
import com.fixora.app.feature.payment.domain.PaymentSummary
import com.fixora.app.feature.payment.domain.PriceAdjustment
import com.fixora.app.feature.servicerequest.domain.parseApiInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Ajustes de precio (API-36), pagos y adeudos (API-37) y recordatorio manual (API-53). */
interface PaymentApi {
    /** Solo el técnico asignado y ya en el domicilio. */
    @POST("service-requests/{id}/price-adjustments")
    suspend fun proposeAdjustment(@Path("id") id: String, @Body body: ProposeAdjustmentBody): DataResponse<PriceAdjustmentDto>

    @POST("service-requests/{id}/price-adjustments/{adjustmentId}/approve")
    suspend fun approveAdjustment(@Path("id") id: String, @Path("adjustmentId") adjustmentId: String)

    @POST("service-requests/{id}/price-adjustments/{adjustmentId}/reject")
    suspend fun rejectAdjustment(@Path("id") id: String, @Path("adjustmentId") adjustmentId: String)

    /** El cliente registra que pagó; queda pendiente hasta que el proveedor confirme. */
    @POST("service-requests/{id}/payments")
    suspend fun registerPayment(@Path("id") id: String, @Body body: RegisterPaymentBody): DataResponse<PaymentDto>

    @POST("service-requests/{id}/payments/{paymentId}/confirm")
    suspend fun confirmPayment(@Path("id") id: String, @Path("paymentId") paymentId: String, @Body body: EmptyBody = EmptyBody())

    @POST("service-requests/{id}/payments/{paymentId}/reject")
    suspend fun rejectPayment(@Path("id") id: String, @Path("paymentId") paymentId: String, @Body body: RejectPaymentBody)

    @GET("me/debts")
    suspend fun debts(): DataResponse<DebtsDto>

    /** Uno cada 24 horas por servicio, contando los automáticos (API-53). */
    @POST("service-requests/{id}/debt-reminders")
    suspend fun sendReminder(@Path("id") id: String, @Body body: EmptyBody = EmptyBody())
}

@Serializable
class EmptyBody

@Serializable
data class ProposeAdjustmentBody(
    val amount: String,
    val reason: String,
    val description: String? = null,
    /** Si se reintenta con la misma llave, la API regresa el mismo ajuste en vez de duplicarlo. */
    @SerialName("client_uuid") val clientUuid: String,
)

@Serializable
data class RegisterPaymentBody(
    val method: String,
    val amount: String,
    val reference: String? = null,
    @SerialName("client_uuid") val clientUuid: String,
)

@Serializable
data class RejectPaymentBody(val reason: String? = null)

@Serializable
data class PriceAdjustmentDto(
    val id: String,
    val amount: String = "0.00",
    val reason: String = "",
    val description: String? = null,
    val status: String = "",
    @SerialName("created_at") val createdAt: String? = null,
) {
    fun toDomain() = PriceAdjustment(
        id = id,
        amount = amount,
        reason = AdjustmentReason.from(reason),
        description = description?.trim()?.takeIf { it.isNotEmpty() },
        status = AdjustmentStatus.from(status),
        createdAt = parseApiInstant(createdAt),
    )
}

@Serializable
data class PaymentDto(
    val id: String,
    val amount: String = "0.00",
    val method: String = "",
    val reference: String? = null,
    val status: String = "",
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    fun toDomain() = Payment(
        id = id,
        amount = amount,
        method = PaymentMethod.from(method),
        reference = reference?.trim()?.takeIf { it.isNotEmpty() },
        status = PaymentStatus.from(status),
        rejectionReason = rejectionReason?.trim()?.takeIf { it.isNotEmpty() },
        createdAt = parseApiInstant(createdAt),
    )
}

@Serializable
data class PaymentSummaryDto(
    @SerialName("total_amount") val totalAmount: String = "0.00",
    val paid: String = "0.00",
    @SerialName("pending_confirmation") val pendingConfirmation: String = "0.00",
    @SerialName("balance_due") val balanceDue: String = "0.00",
    @SerialName("has_debt") val hasDebt: Boolean = false,
) {
    fun toDomain() = PaymentSummary(totalAmount, paid, pendingConfirmation, balanceDue, hasDebt)
}

@Serializable
data class DebtsDto(
    @SerialName("total_due") val totalDue: String = "0.00",
    val debts: List<DebtDto> = emptyList(),
)

@Serializable
data class DebtDto(
    @SerialName("request_id") val requestId: String,
    val category: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("total_amount") val totalAmount: String = "0.00",
    val paid: String = "0.00",
    @SerialName("pending_confirmation") val pendingConfirmation: String = "0.00",
    @SerialName("balance_due") val balanceDue: String = "0.00",
    @SerialName("has_debt") val hasDebt: Boolean = true,
    @SerialName("reminders_sent") val remindersSent: Int = 0,
    @SerialName("last_reminder_at") val lastReminderAt: String? = null,
    @SerialName("next_reminder_at") val nextReminderAt: String? = null,
) {
    fun toDomain() = Debt(
        requestId = requestId,
        category = category.orEmpty(),
        completedAt = parseApiInstant(completedAt),
        summary = PaymentSummary(totalAmount, paid, pendingConfirmation, balanceDue, hasDebt),
        remindersSent = remindersSent,
        lastReminderAt = parseApiInstant(lastReminderAt),
        nextReminderAt = parseApiInstant(nextReminderAt),
    )
}
