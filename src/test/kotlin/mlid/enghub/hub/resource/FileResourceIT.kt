package mlid.enghub.hub.resource

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import mlid.enghub.hub.MariaDbTestResource
import mlid.enghub.hub.MinioTestResource
import mlid.enghub.hub.NatsTestResource
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

@QuarkusTest
@QuarkusTestResource(MariaDbTestResource::class)
@QuarkusTestResource(MinioTestResource::class)
@QuarkusTestResource(NatsTestResource::class)
class FileResourceIT {
    private val validBody =
        """
        {
          "bucket": "incoming",
          "reportId": "WXG",
          "reportCategory": "AVI",
          "objectKey": "test/sample.pdf",
          "filename": "sample.pdf",
          "contentType": "application/pdf",
          "fileSize": 1024,
          "checksum": "d41d8cd98f00b204e9800998ecf8427e",
          "uploaderId": "test-client"
        }
        """.trimIndent()

    @Test
    fun `register returns 201 with valid payload and existing object`() {
        Given {
            contentType("application/json")
            body(validBody)
        } When {
            post("/api/files/register")
        } Then {
            statusCode(201)
            body("status", equalTo("REGISTERED"))
            body("id", notNullValue())
            body("eventPublished", notNullValue())
        }
    }

    @Test
    fun `register returns 404 when object not in MinIO`() {
        val body = validBody.replace("test/sample.pdf", "nonexistent/file.pdf")
        Given {
            contentType("application/json")
            body(body)
        } When {
            post("/api/files/register")
        } Then {
            statusCode(404)
            body("code", equalTo("OBJECT_NOT_FOUND"))
        }
    }

    @Test
    fun `register succeeds with null checksum bypass`() {
        val body =
            validBody.replace(
                """"checksum": "d41d8cd98f00b204e9800998ecf8427e",""",
                "",
            )
        Given {
            contentType("application/json")
            body(body)
        } When {
            post("/api/files/register")
        } Then {
            statusCode(201)
            body("status", equalTo("REGISTERED"))
        }
    }

    @Test
    fun `get by id returns 404 for unknown id`() {
        When {
            get("/api/files/nonexistent-id")
        } Then {
            statusCode(404)
        }
    }

    @Test
    fun `mark processed is idempotent`() {
        val id =
            Given {
                contentType("application/json")
                body(validBody)
            } When {
                post("/api/files/register")
            } Then {
                statusCode(201)
            } Extract {
                path<String>("id")
            }

        val deliveryBody = """{"consumerId": "consumer-A"}"""
        repeat(2) {
            Given {
                contentType("application/json")
                body(deliveryBody)
            } When {
                put("/api/files/$id/delivery")
            } Then {
                statusCode(200)
            }
        }
    }

    @Test
    fun `missing returns 200 with files and total`() {
        When {
            get("/api/files/missing?consumerId=consumer-B")
        } Then {
            statusCode(200)
            body("files", notNullValue())
            body("total", notNullValue())
        }
    }

    @Test
    fun `search paginates registered files`() {
        // Register two files with a distinct uploader so this test is isolated
        listOf("search/one.pdf", "search/two.pdf").forEach { key ->
            val body =
                validBody
                    .replace("test/sample.pdf", key)
                    .replace("test-client", "search-client")
            Given {
                contentType("application/json")
                body(body)
            } When {
                post("/api/files/register")
            } Then {
                statusCode(201)
            }
        }

        val keyPage0 =
            When {
                get("/api/files?uploaderId=search-client&page=0&size=1")
            } Then {
                statusCode(200)
                body("files.size()", equalTo(1))
                body("total", equalTo(2))
            } Extract {
                path<String>("files[0].objectKey")
            }

        val keyPage1 =
            When {
                get("/api/files?uploaderId=search-client&page=1&size=1")
            } Then {
                statusCode(200)
                body("files.size()", equalTo(1))
            } Extract {
                path<String>("files[0].objectKey")
            }

        assertNotEquals(keyPage0, keyPage1, "page 0 and page 1 must return different rows")
        assertEquals(setOf("search/one.pdf", "search/two.pdf"), setOf(keyPage0, keyPage1))
    }
}
