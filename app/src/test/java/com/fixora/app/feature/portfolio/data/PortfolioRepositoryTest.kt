package com.fixora.app.feature.portfolio.data

import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.NetworkModule
import com.fixora.app.feature.portfolio.domain.CertificateStatus
import com.fixora.app.feature.portfolio.domain.PortfolioRules
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit

class PortfolioRepositoryTest {

    private val json = NetworkModule.provideJson()
    private lateinit var server: MockWebServer
    private lateinit var repository: PortfolioRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<PortfolioApi>()
        repository = PortfolioRepository(api, json)
    }

    @After
    fun tearDown() = server.close()

    private fun respond(data: String, code: Int = 200) = server.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json")
            .body("""{"success":true,"message":"ok","data":$data}""").build(),
    )

    private fun take(): RecordedRequest = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))

    private fun RecordedRequest.jsonBody(): JsonObject = json.parseToJsonElement(checkNotNull(body).utf8()).jsonObject

    @Test
    fun worksKeepOnlyPhotosWithLink() = runBlocking {
        respond("""[{"id":"5","branch_id":"2","title":"Tubería","description":null,
            "media":[{"id":"1","file_key":"photos/9/a.jpg","url":"https://f/a"},{"id":"2","file_key":"photos/9/b.jpg","url":""}]}]""")

        val works = (repository.works("2") as ApiResult.Success).data

        assertEquals("/api/branches/2/portfolio", take().url.encodedPath)
        assertEquals("", works.single().description)
        assertEquals(listOf("https://f/a"), works.single().photoUrls)
    }

    @Test
    fun addWorkUploadsEachPhotoAndSendsItsKeys() = runBlocking {
        respond("""{"file_key":"photos/9/uno.jpg"}""")
        respond("""{"file_key":"photos/9/dos.jpg"}""")
        respond("""{"id":"7","title":"Calentador","media":[]}""", code = 201)

        val result = repository.addWork("2", "  Calentador ", "  ", listOf(byteArrayOf(1), byteArrayOf(2)))

        assertTrue(result is ApiResult.Success)
        repeat(2) {
            val upload = take()
            assertEquals("/api/files/upload", upload.url.encodedPath)
            assertTrue(checkNotNull(upload.body).utf8().contains("photos"))
        }
        val work = take()
        assertEquals("/api/branches/2/portfolio", work.url.encodedPath)
        assertEquals(JsonPrimitive("Calentador"), work.jsonBody()["title"])
        assertFalse("una descripción vacía no se manda", "description" in work.jsonBody())
        assertEquals(
            JsonArray(listOf("photos/9/uno.jpg", "photos/9/dos.jpg").map { JsonObject(mapOf("file_key" to JsonPrimitive(it))) }),
            work.jsonBody()["media"],
        )
    }

    @Test
    fun addWorkWithoutPhotosOnlyRegistersTheWork() = runBlocking {
        respond("""{"id":"8","title":"Revisión","media":[]}""", code = 201)

        repository.addWork("2", "Revisión", "", emptyList())

        assertEquals("/api/branches/2/portfolio", take().url.encodedPath)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun addPhotosUploadsThemAndReturnsTheUpdatedWork() = runBlocking {
        respond("""{"file_key":"photos/9/tres.jpg"}""")
        respond("""{"id":"7","title":"Calentador","media":[{"id":"1","url":"https://f/1"},{"id":"3","url":"https://f/3"}]}""", code = 201)

        val work = (repository.addPhotos("7", listOf(byteArrayOf(3))) as ApiResult.Success).data

        assertEquals("/api/files/upload", take().url.encodedPath)
        val add = take()
        assertEquals("/api/portfolio/7/media", add.url.encodedPath)
        assertEquals(
            JsonArray(listOf(JsonObject(mapOf("file_key" to JsonPrimitive("photos/9/tres.jpg"))))),
            add.jsonBody()["media"],
        )
        assertEquals(listOf("https://f/1", "https://f/3"), work.photoUrls)
    }

    @Test
    fun aFailedPhotoUploadDoesNotRegisterTheWork() = runBlocking {
        server.enqueue(MockResponse.Builder().code(400).addHeader("Content-Type", "application/json")
            .body("""{"error":"INVALID_FILE","message":"Tipo de archivo no permitido."}""").build())

        assertTrue(repository.addWork("2", "Calentador", "", listOf(byteArrayOf(1))) is ApiResult.Failure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun certificatesHideRegistrationDocumentsAndReadTheReview() = runBlocking {
        respond("""{"documents":[
            {"id":"1","type_name":"Identificación Oficial (INE / Pasaporte)","mandatory":true,"is_public":false,"upload_date":"2026-11-01"},
            {"id":"2","type_name":"Carta de No Antecedentes Penales","mandatory":false,"is_public":false,"upload_date":"2026-11-01"},
            {"id":"3","type_name":"Certificaciones o Títulos Técnicos","mandatory":false,"is_public":true,"upload_date":"2026-11-02",
             "validation":{"result":"aprobado","comment":null}},
            {"id":"4","type_name":"Certificaciones o Títulos Técnicos","mandatory":false,"is_public":false,"upload_date":"2026-11-18",
             "validation":{"result":"rechazado","comment":"No se lee"}},
            {"id":"5","type_name":"Certificaciones o Títulos Técnicos","mandatory":false,"is_public":false,"upload_date":"2026-11-18"}]}""")

        val certificates = (repository.certificates() as ApiResult.Success).data

        assertEquals("/api/supplier-applications/me", take().url.encodedPath)
        assertEquals(listOf("5", "4", "3"), certificates.map { it.id })
        assertEquals(
            listOf(CertificateStatus.Pending, CertificateStatus.Rejected, CertificateStatus.Approved),
            certificates.map { it.status },
        )
        assertEquals("No se lee", certificates[1].comment)
        assertTrue(certificates[2].isPublic)
    }

    @Test
    fun uploadCertificateSendsTheFileWithItsType() = runBlocking {
        respond("""{"id":"9","type_name":"Certificaciones o Títulos Técnicos"}""", code = 201)

        repository.uploadCertificate(PickedDocument(byteArrayOf(37, 80, 68, 70), "application/pdf", "dc3.pdf"))

        val request = take()
        assertEquals("/api/supplier-documents", request.url.encodedPath)
        val body = checkNotNull(request.body).utf8()
        assertTrue(body.contains("filename=\"dc3.pdf\""))
        assertTrue(body.contains("Content-Type: application/pdf"))
    }

    @Test
    fun visibilitySendsTheFlag() = runBlocking {
        respond("""{"id":"3","type_name":"Certificaciones o Títulos Técnicos","is_public":true}""")

        repository.setVisibility("3", isPublic = true)

        val request = take()
        assertEquals("PATCH", request.method)
        assertEquals("/api/supplier-documents/3/visibility", request.url.encodedPath)
        assertEquals(JsonObject(mapOf("is_public" to JsonPrimitive(true))), request.jsonBody())
    }

    @Test
    fun onlyOptionalPublishableDocumentsAreCertificates() {
        assertTrue(PortfolioRules.isCertificate("Certificaciones o Títulos Técnicos", mandatory = false))
        assertTrue(PortfolioRules.isCertificate("Póliza de Seguro de Responsabilidad Civil", mandatory = false))
        assertFalse(PortfolioRules.isCertificate("Certificaciones o Títulos Técnicos", mandatory = true))
        assertFalse(PortfolioRules.isCertificate("Comprobante de Domicilio", mandatory = false))
        assertFalse(PortfolioRules.isCertificate("Carta de No Antecedentes Penales", mandatory = false))
    }
}
