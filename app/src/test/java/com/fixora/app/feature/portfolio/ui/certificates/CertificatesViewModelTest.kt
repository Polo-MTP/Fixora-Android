package com.fixora.app.feature.portfolio.ui.certificates

import com.fixora.app.core.network.DataResponse
import com.fixora.app.core.network.NetworkModule
import com.fixora.app.feature.portfolio.data.ApplicationDto
import com.fixora.app.feature.portfolio.data.DocumentDto
import com.fixora.app.feature.portfolio.data.DocumentProblem
import com.fixora.app.feature.portfolio.data.DocumentReadResult
import com.fixora.app.feature.portfolio.data.MediaListRequest
import com.fixora.app.feature.portfolio.data.PickedDocument
import com.fixora.app.feature.portfolio.data.PortfolioApi
import com.fixora.app.feature.portfolio.data.PortfolioRepository
import com.fixora.app.feature.portfolio.data.UploadedDto
import com.fixora.app.feature.portfolio.data.ValidationDto
import com.fixora.app.feature.portfolio.data.VisibilityRequest
import com.fixora.app.feature.portfolio.data.WorkDto
import com.fixora.app.feature.portfolio.data.WorkRequest
import com.fixora.app.feature.portfolio.domain.CertificateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CertificatesViewModelTest {

    /** API falsa: guarda sus documentos y lo que recibe. */
    private class FakeApi : PortfolioApi {
        val documents = mutableListOf(
            DocumentDto("1", "Identificación Oficial (INE / Pasaporte)", mandatory = true),
            DocumentDto("2", "Certificaciones o Títulos Técnicos", validation = ValidationDto("aprobado")),
            DocumentDto("3", "Certificaciones o Títulos Técnicos", validation = ValidationDto("pendiente")),
        )
        var uploads = 0
        val visibility = mutableListOf<Pair<String, Boolean>>()

        override suspend fun getMyApplication() = DataResponse(ApplicationDto(documents.toList()))
        override suspend fun uploadCertificate(file: MultipartBody.Part): DataResponse<DocumentDto> {
            uploads++
            val doc = DocumentDto("${documents.size + 1}", "Certificaciones o Títulos Técnicos", uploadDate = "2026-11-18")
            documents += doc
            return DataResponse(doc)
        }
        override suspend fun setVisibility(documentId: String, body: VisibilityRequest): DataResponse<DocumentDto> {
            visibility += documentId to body.isPublic
            return DataResponse(documents.first { it.id == documentId })
        }

        override suspend fun getWorks(branchId: String): DataResponse<List<WorkDto>> = error("no se usa")
        override suspend fun addWork(branchId: String, body: WorkRequest): DataResponse<WorkDto> = error("no se usa")
        override suspend fun deleteWork(workId: String) = error("no se usa")
        override suspend fun addMedia(workId: String, body: MediaListRequest): DataResponse<WorkDto> = error("no se usa")
        override suspend fun uploadFile(category: RequestBody, file: MultipartBody.Part): DataResponse<UploadedDto> = error("no se usa")
    }

    private lateinit var api: FakeApi
    private var nextRead: DocumentReadResult = DocumentReadResult.Ready(PickedDocument(byteArrayOf(1), "application/pdf", "dc3.pdf"))
    private lateinit var viewModel: CertificatesViewModel
    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        api = FakeApi()
        viewModel = CertificatesViewModel(PortfolioRepository(api, NetworkModule.provideJson())) { error("se usa onFileSelected") }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun showsOnlyCertificatesWithTheirReview() {
        assertEquals(listOf("2", "3"), state.certificates.map { it.id }.sorted())
        assertFalse(state.isLoading)
    }

    @Test
    fun aRejectedFileIsNotUploaded() {
        nextRead = DocumentReadResult.Rejected(DocumentProblem.TooLarge)

        viewModel.onFilePickedForTest()

        assertEquals(DocumentProblem.TooLarge, state.fileProblem)
        assertEquals(0, api.uploads)
        assertFalse(state.isUploading)
    }

    @Test
    fun uploadingReloadsAndShowsItAsPending() {
        viewModel.onFilePickedForTest()

        assertEquals(1, api.uploads)
        assertTrue(state.justUploaded)
        assertEquals(CertificateStatus.Pending, state.certificates.first { it.id == "4" }.status)
    }

    @Test
    fun onlyApprovedCertificatesCanBeShown() {
        val approved = state.certificates.first { it.id == "2" }
        val pending = state.certificates.first { it.id == "3" }

        viewModel.setVisibility(pending, isPublic = true)
        viewModel.setVisibility(approved, isPublic = true)

        assertEquals(listOf("2" to true), api.visibility)
        assertTrue(state.certificates.first { it.id == "2" }.isPublic)
        assertNull(state.togglingId)
    }

    // El selector real entrega una Uri, que no se puede crear en pruebas locales: se pasa la lectura.
    private fun CertificatesViewModel.onFilePickedForTest() = onFileSelected { nextRead }
}
