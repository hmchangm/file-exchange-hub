package mlid.enghub.statusgui.routing

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import mlid.enghub.statusgui.FakeFileQueryRepository
import mlid.enghub.statusgui.repository.PagedResult
import mlid.enghub.statusgui.sampleDeliveryRow
import mlid.enghub.statusgui.sampleFileRow
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class FileSearchRoutesTest {

    @Test
    fun `GET files renders search form and results`() = testApplication {
        val repo = FakeFileQueryRepository()
        repo.searchResult = PagedResult(listOf(sampleFileRow()), 1)
        application { fileSearchRoutes(repo) }
        val response = client.get("/files")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "report_Q1.csv")
        assertContains(body, "finance")
        assertContains(body, "uploader-01")
    }

    @Test
    fun `GET files shows empty message when no results`() = testApplication {
        val repo = FakeFileQueryRepository()
        repo.searchResult = PagedResult(emptyList(), 0)
        application { fileSearchRoutes(repo) }
        val response = client.get("/files")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "No files found")
    }

    @Test
    fun `GET files shows FAILED status badge`() = testApplication {
        val repo = FakeFileQueryRepository()
        repo.searchResult = PagedResult(listOf(sampleFileRow(status = "FAILED")), 1)
        application { fileSearchRoutes(repo) }
        val body = client.get("/files").bodyAsText()
        assertContains(body, "badge-failed")
    }

    @Test
    fun `GET files shows pagination when more than one page`() = testApplication {
        val repo = FakeFileQueryRepository()
        repo.searchResult = PagedResult(listOf(sampleFileRow()), 45)
        application { fileSearchRoutes(repo) }
        val body = client.get("/files").bodyAsText()
        assertContains(body, "Next")
        assertContains(body, "45")
    }

    @Test
    fun `GET files id returns detail page`() = testApplication {
        val repo = FakeFileQueryRepository()
        repo.findByIdResult = sampleFileRow()
        repo.findDeliveriesResult = listOf(sampleDeliveryRow())
        application { fileSearchRoutes(repo) }
        val body = client.get("/files/test-id-001").bodyAsText()
        assertContains(body, "test-id-001")
        assertContains(body, "consumer-reporting")
        assertContains(body, "finance/report_Q1.csv")
    }

    @Test
    fun `GET files id returns 404 for unknown file`() = testApplication {
        val repo = FakeFileQueryRepository()
        repo.findByIdResult = null
        application { fileSearchRoutes(repo) }
        val response = client.get("/files/no-such-id")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertContains(response.bodyAsText(), "File not found")
    }
}
