package com.fixora.app.feature.auth.ui.forgot

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.NetworkModule
import com.fixora.app.feature.auth.data.AuthApi
import com.fixora.app.feature.auth.data.EmailRequest
import com.fixora.app.feature.auth.data.LoginRequest
import com.fixora.app.feature.auth.data.LoginResponse
import com.fixora.app.feature.auth.data.MessageResponse
import com.fixora.app.feature.auth.data.PasswordRecoveryRepository
import com.fixora.app.feature.auth.data.RegisterRequest
import com.fixora.app.feature.auth.data.RegisterResponse
import com.fixora.app.feature.auth.data.ResetPasswordRequest
import com.fixora.app.feature.auth.data.VerifyEmailRequest
import com.fixora.app.feature.auth.domain.FieldError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {

    /** API falsa: guarda lo que recibe y responde con error si se le indica. */
    private class FakeAuthApi : AuthApi {
        val codeRequests = mutableListOf<EmailRequest>()
        val resets = mutableListOf<ResetPasswordRequest>()
        var forgotError: HttpException? = null
        var resetError: HttpException? = null

        override suspend fun forgotPassword(body: EmailRequest): MessageResponse {
            codeRequests += body
            forgotError?.let { throw it }
            return MessageResponse("ok")
        }

        override suspend fun resetPassword(body: ResetPasswordRequest): MessageResponse {
            resets += body
            resetError?.let { throw it }
            return MessageResponse("ok")
        }

        override suspend fun login(body: LoginRequest): LoginResponse = error("no se usa")
        override suspend fun register(body: RegisterRequest): RegisterResponse = error("no se usa")
        override suspend fun verifyEmail(body: VerifyEmailRequest): MessageResponse = error("no se usa")
        override suspend fun resendVerification(body: EmailRequest): MessageResponse = error("no se usa")
    }

    private fun httpError(status: Int, code: String, message: String) = HttpException(
        Response.error<Any>(
            status,
            """{"error":"$code","message":"$message"}""".toResponseBody("application/json".toMediaType()),
        ),
    )

    private lateinit var api: FakeAuthApi
    private lateinit var viewModel: ForgotPasswordViewModel
    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        api = FakeAuthApi()
        viewModel = ForgotPasswordViewModel(PasswordRecoveryRepository(api, NetworkModule.provideJson()))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun goToReset() {
        viewModel.onEmailChange("Ana@Correo.com")
        viewModel.requestCode()
    }

    private fun fillReset(code: String = "123456", password: String = "Nueva123", confirm: String = password) {
        viewModel.onCodeChange(code)
        viewModel.onPasswordChange(password)
        viewModel.onConfirmPasswordChange(confirm)
    }

    @Test
    fun invalidEmailDoesNotCallTheApi() {
        viewModel.onEmailChange("ana@")
        viewModel.requestCode()

        assertTrue(api.codeRequests.isEmpty())
        assertEquals(ForgotPasswordStep.Email, state.step)
        assertEquals(FieldError.EmailInvalid, state.emailError)
    }

    @Test
    fun validEmailRequestsTheCodeAndMovesToReset() {
        goToReset()

        assertEquals(listOf(EmailRequest("ana@correo.com")), api.codeRequests)
        assertEquals(ForgotPasswordStep.Reset, state.step)
        assertEquals(1, state.codeRound)
        assertFalse(state.isLoading)
    }

    @Test
    fun emailFailureStaysOnTheFirstStep() {
        api.forgotError = httpError(503, PasswordRecoveryRepository.RECOVERY_EMAIL_FAILED, "No se pudo enviar")
        goToReset()

        assertEquals(ForgotPasswordStep.Email, state.step)
        assertEquals(PasswordRecoveryRepository.RECOVERY_EMAIL_FAILED, (state.error as ApiError.Http).code)
    }

    @Test
    fun weakOrMismatchedPasswordDoesNotCallTheApi() {
        goToReset()

        fillReset(password = "corta")
        viewModel.resetPassword()
        assertEquals(FieldError.PasswordWeak, state.passwordError)

        fillReset(password = "Nueva123", confirm = "Nueva124")
        viewModel.resetPassword()
        assertEquals(FieldError.PasswordMismatch, state.confirmPasswordError)

        assertTrue(api.resets.isEmpty())
        assertEquals(ForgotPasswordStep.Reset, state.step)
    }

    @Test
    fun validResetSendsCodeAndPasswordAndFinishes() {
        goToReset()
        fillReset()
        viewModel.resetPassword()

        assertEquals(listOf(ResetPasswordRequest("ana@correo.com", "123456", "Nueva123")), api.resets)
        assertEquals(ForgotPasswordStep.Done, state.step)
        assertNull(state.error)
    }

    @Test
    fun wrongCodeKeepsItToFixAndShowsTheError() {
        goToReset()
        api.resetError = httpError(400, "INVALID_RECOVERY_CODE", "El código de recuperación es incorrecto.")
        fillReset()
        viewModel.resetPassword()

        assertEquals(ForgotPasswordStep.Reset, state.step)
        assertEquals("123456", state.code)
        assertEquals("El código de recuperación es incorrecto.", (state.error as ApiError.Http).message)
    }

    @Test
    fun expiredCodeIsClearedToAskForAnother() {
        goToReset()
        api.resetError = httpError(400, PasswordRecoveryRepository.RECOVERY_CODE_EXPIRED, "El código expiró.")
        fillReset()
        viewModel.resetPassword()

        assertEquals("", state.code)
        assertEquals(ForgotPasswordStep.Reset, state.step)
    }

    @Test
    fun resendAsksForANewCodeAndRestartsTheCountdown() {
        goToReset()
        viewModel.onCodeChange("111")
        viewModel.resendCode()

        assertEquals(2, api.codeRequests.size)
        assertEquals(2, state.codeRound)
        assertEquals("", state.code)
        assertTrue(state.codeResent)
    }

    @Test
    fun backFromResetReturnsToEmailAndThenExits() {
        goToReset()

        assertTrue(viewModel.back())
        assertEquals(ForgotPasswordStep.Email, state.step)
        assertEquals("Ana@Correo.com", state.email.value)
        assertFalse(viewModel.back())
    }
}
