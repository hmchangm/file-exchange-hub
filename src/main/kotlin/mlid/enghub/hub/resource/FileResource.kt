package mlid.enghub.hub.resource

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jboss.resteasy.reactive.ResponseStatus
import org.jboss.resteasy.reactive.RestResponse
import mlid.enghub.hub.domain.FileDelivery
import mlid.enghub.hub.repository.FileDeliveryRepository
import mlid.enghub.hub.repository.FileMetadataRepository
import mlid.enghub.hub.resource.dto.FileMetadataDto
import mlid.enghub.hub.resource.dto.MarkProcessedRequest
import mlid.enghub.hub.resource.dto.PagedFilesResponse
import mlid.enghub.hub.resource.dto.RegisterFileRequest
import mlid.enghub.hub.resource.dto.RegisterFileResponse
import mlid.enghub.hub.service.FileRegistrationService
import java.time.Instant

@Path("/api/files")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class FileResource(
    private val registrationService: FileRegistrationService,
    private val metadataRepository: FileMetadataRepository,
    private val deliveryRepository: FileDeliveryRepository,
) {
    @POST
    @Path("/register")
    @ResponseStatus(201)
    suspend fun register(request: RegisterFileRequest): RegisterFileResponse = registrationService.register(request)

    @GET
    @Path("/{id}")
    suspend fun getById(
        @PathParam("id") id: String,
    ): FileMetadataDto {
        val metadata =
            metadataRepository.findById(id)
                ?: throw FileNotFoundException()
        val tags = metadata.tags?.let { Json.decodeFromString<Map<String, String>>(it) }
        return FileMetadataDto.from(metadata, tags)
    }

    @GET
    suspend fun search(
        @QueryParam("uploaderId") uploaderId: String?,
        @QueryParam("bucket") bucket: String?,
        @QueryParam("page") @DefaultValue("0") page: Int,
        @QueryParam("size") @DefaultValue("20") size: Int,
    ): PagedFilesResponse {
        val (files, total) = metadataRepository.search(uploaderId, bucket, page, size)
        val dtos =
            files.map { m ->
                FileMetadataDto.from(m, m.tags?.let { Json.decodeFromString<Map<String, String>>(it) })
            }
        return PagedFilesResponse(dtos, total, page, size)
    }

    @GET
    @Path("/missing")
    suspend fun missing(
        @QueryParam("consumerId") consumerId: String,
        @QueryParam("bucket") bucket: String?,
        @QueryParam("since") since: String?,
        @QueryParam("page") @DefaultValue("0") page: Int,
        @QueryParam("size") @DefaultValue("20") size: Int,
    ): PagedFilesResponse {
        val sinceInstant = since?.let { Instant.parse(it) } ?: Instant.now().minusSeconds(86400)
        val (files, total) = metadataRepository.findMissing(consumerId, bucket, sinceInstant, page, size)
        val dtos =
            files.map { m ->
                FileMetadataDto.from(m, m.tags?.let { Json.decodeFromString<Map<String, String>>(it) })
            }
        return PagedFilesResponse(dtos, total, page, size)
    }

    @PUT
    @Path("/{id}/delivery")
    suspend fun markProcessed(
        @PathParam("id") id: String,
        request: MarkProcessedRequest,
    ): RestResponse<Void> {
        if (metadataRepository.findById(id) == null) {
            throw FileNotFoundException()
        }
        val delivery =
            FileDelivery(
                fileId = id,
                consumerId = request.consumerId,
                note = request.note,
            )
        deliveryRepository.insertIgnore(delivery)
        return RestResponse.ok()
    }
}

class FileNotFoundException : RuntimeException("File not found")
