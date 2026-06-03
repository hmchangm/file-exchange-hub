package mlid.enghub.statusgui.routing

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import mlid.enghub.statusgui.FakeFileQueryRepository
import mlid.enghub.statusgui.repository.PagedResult
import mlid.enghub.statusgui.sampleFileRow
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class MissingFilesRoutesTest {

    @Test
    fun `GET missing without consumerId shows empty prompt`() = testApplication {
        val repo = FakeFileQueryRepository()
        application { missingFilesRoutes(repo) }
        val response = client.get("/missing")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "Consumer ID")
        assertContains(response.bodyAsText(), "Missing Files")
    }

    @Test
    fun `GET missing with consumerId shows results`() = testApplication {
        val repo = FakeFileQueryRepository()
        repo.findMissingResult = PagedResult(listOf(sampleFileRow(filename = "missing_report.csv")), 1)
        application { missingFilesRoutes(repo) }
        val body = client.get("/missing?consumerId=consumer-a").bodyAsText()
        assertContains(body, "missing_report.csv")
    }

    @Test
    fun `GET missing with consumerId shows no results message`() = testApplication {
        val repo = FakeFileQueryRepository()
        repo.findMissingResult = PagedResult(emptyList(), 0)
        application { missingFilesRoutes(repo) }
        val response = client.get("/missing?consumerId=consumer-a")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "No missing files")
    }

    @Test
    fun `GET missing shows pagination when results exceed page size`() = testApplication {
        val repo = FakeFileQueryRepository()
        repo.findMissingResult = PagedResult(listOf(sampleFileRow()), 50)
        application { missingFilesRoutes(repo) }
        val body = client.get("/missing?consumerId=consumer-a").bodyAsText()
        assertContains(body, "Next")
        assertContains(body, "50")
    }
}
