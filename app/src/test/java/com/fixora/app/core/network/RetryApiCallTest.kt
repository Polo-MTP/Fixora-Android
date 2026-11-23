package com.fixora.app.core.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

class RetryApiCallTest {

    private val json = NetworkModule.provideJson()

    @Test
    fun retriesOnNoConnection_withSameClientUuid() = runTest {
        val clientUuid = newClientUuid()
        val sent = mutableListOf<String>()

        val result = retryApiCall(json) {
            sent += clientUuid
            if (sent.size < 3) throw IOException("sin red")
            "creada"
        }

        assertEquals(ApiResult.Success("creada"), result)
        assertEquals(listOf(clientUuid, clientUuid, clientUuid), sent)
    }

    @Test
    fun givesUpAfterAttempts() = runTest {
        var calls = 0
        val result = retryApiCall(json, attempts = 3) {
            calls++
            throw IOException("sin red")
        }

        assertEquals(ApiResult.Failure(ApiError.NoConnection), result)
        assertEquals(3, calls)
    }

    @Test
    fun doesNotRetryApiErrors() = runTest {
        var calls = 0
        val result = retryApiCall(json) {
            calls++
            throw HttpException(Response.error<Unit>(409, """{"error":"DUPLICATE"}""".toResponseBody()))
        }

        assertEquals(ApiResult.Failure(ApiError.Http(409, "DUPLICATE", null)), result)
        assertEquals(1, calls)
    }
}
