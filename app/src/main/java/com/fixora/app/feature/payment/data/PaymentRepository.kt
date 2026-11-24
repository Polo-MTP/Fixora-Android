package com.fixora.app.feature.payment.data

import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.safeApiCall
import com.fixora.app.feature.payment.domain.AdjustmentReason
import com.fixora.app.feature.payment.domain.Debts
import com.fixora.app.feature.payment.domain.MoneyRules
import com.fixora.app.feature.payment.domain.PaymentMethod
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

/**
 * Cobro de un servicio: el técnico propone ajustes y confirma lo que recibe (ANDROID-25), el
 * cliente aprueba los ajustes y registra su pago (ANDROID-24). Interfaz para probar sin red.
 */
interface PaymentRepository {
    /** [clientUuid] es el mismo en cada reintento para que la API no lo duplique. */
    suspend fun proposeAdjustment(
        requestId: String,
        amountCents: Long,
        reason: AdjustmentReason,
        description: String?,
        clientUuid: String,
    ): ApiResult<Unit>

    suspend fun respondAdjustment(requestId: String, adjustmentId: String, approve: Boolean): ApiResult<Unit>

    suspend fun registerPayment(
        requestId: String,
        method: PaymentMethod,
        amountCents: Long,
        reference: String?,
        clientUuid: String,
    ): ApiResult<Unit>

    /** El proveedor confirma que recibió el pago o dice que no le llegó (con motivo opcional). */
    suspend fun respondPayment(requestId: String, paymentId: String, received: Boolean, reason: String?): ApiResult<Unit>

    suspend fun debts(): ApiResult<Debts>

    suspend fun sendReminder(requestId: String): ApiResult<Unit>
}

class RemotePaymentRepository(
    private val api: PaymentApi,
    private val json: Json,
) : PaymentRepository {

    override suspend fun proposeAdjustment(
        requestId: String,
        amountCents: Long,
        reason: AdjustmentReason,
        description: String?,
        clientUuid: String,
    ) = safeApiCall(json) {
        api.proposeAdjustment(
            requestId,
            ProposeAdjustmentBody(
                amount = MoneyRules.toAmount(amountCents),
                reason = reason.apiValue,
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                clientUuid = clientUuid,
            ),
        )
        Unit
    }

    override suspend fun respondAdjustment(requestId: String, adjustmentId: String, approve: Boolean) = safeApiCall(json) {
        if (approve) api.approveAdjustment(requestId, adjustmentId) else api.rejectAdjustment(requestId, adjustmentId)
    }

    override suspend fun registerPayment(
        requestId: String,
        method: PaymentMethod,
        amountCents: Long,
        reference: String?,
        clientUuid: String,
    ) = safeApiCall(json) {
        api.registerPayment(
            requestId,
            RegisterPaymentBody(
                method = method.apiValue,
                amount = MoneyRules.toAmount(amountCents),
                // La referencia solo aplica a las transferencias.
                reference = reference?.trim()?.takeIf { it.isNotEmpty() && method == PaymentMethod.Transfer },
                clientUuid = clientUuid,
            ),
        )
        Unit
    }

    override suspend fun respondPayment(requestId: String, paymentId: String, received: Boolean, reason: String?) =
        safeApiCall(json) {
            if (received) {
                api.confirmPayment(requestId, paymentId)
            } else {
                api.rejectPayment(requestId, paymentId, RejectPaymentBody(reason?.trim()?.takeIf { it.isNotEmpty() }))
            }
        }

    override suspend fun debts() = safeApiCall(json) {
        val data = api.debts().data
        Debts(totalDue = data.totalDue, debts = data.debts.map { it.toDomain() })
    }

    override suspend fun sendReminder(requestId: String) = safeApiCall(json) { api.sendReminder(requestId) }
}

@Module
@InstallIn(SingletonComponent::class)
object PaymentModule {
    @Provides
    @Singleton
    fun providePaymentApi(retrofit: Retrofit): PaymentApi = retrofit.create()

    @Provides
    fun providePaymentRepository(api: PaymentApi, json: Json): PaymentRepository = RemotePaymentRepository(api, json)
}
