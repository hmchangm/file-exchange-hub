package mlid.enghub.hub.resource

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.jupiter.api.Test
import mlid.enghub.hub.MariaDbTestResource
import mlid.enghub.hub.MinioTestResource
import mlid.enghub.hub.NatsTestResource

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
}
