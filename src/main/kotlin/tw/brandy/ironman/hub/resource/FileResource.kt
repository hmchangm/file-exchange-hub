package tw.brandy.ironman.hub.resource

import jakarta.transaction.Transactional
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import tw.brandy.ironman.hub.domain.FileDelivery
import tw.brandy.ironman.hub.repository.FileDeliveryRepository
import tw.brandy.ironman.hub.repository.FileMetadataRepository
import tw.brandy.ironman.hub.resource.dto.*
import tw.brandy.ironman.hub.service.FileRegistrationService
import tw.brandy.ironman.hub.service.ObjectNotFoundException
import java.time.Instant

@Path("/api/files")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class FileResource(
    private val registrationService: FileRegistrationService,
    private val metadataRepository: FileMetadataRepository,
    private val deliveryRepository: FileDeliveryRepository
) {

    @POST
    @Path("/register")
    fun register(request: RegisterFileRequest): Response = try {
        val response = registrationService.register(request)
        Response.status(Response.Status.CREATED).entity(response).build()
    } catch (e: ObjectNotFoundException) {
        Response.status(Response.Status.NOT_FOUND)
            .entity(ErrorResponse(e.message ?: "Not found", "OBJECT_NOT_FOUND")).build()
    }

    @GET
    @Path("/{id}")
    fun getById(@PathParam("id") id: String): Response {
        val metadata = metadataRepository.findByIdOrNull(id)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse("File not found", "FILE_NOT_FOUND")).build()
        val tags = metadata.tags?.let { Json.decodeFromString<Map<String, String>>(it) }
        return Response.ok(FileMetadataDto.from(metadata, tags)).build()
    }

    @GET
    fun search(
        @QueryParam("uploaderId") uploaderId: String?,
        @QueryParam("bucket") bucket: String?,
        @QueryParam("page") @DefaultValue("0") page: Int,
        @QueryParam("size") @DefaultValue("20") size: Int
    ): Response {
        val (files, total) = metadataRepository.search(uploaderId, bucket, page, size)
        val dtos = files.map { m ->
            val tags = m.tags?.let { Json.decodeFromString<Map<String, String>>(it) }
            FileMetadataDto.from(m, tags)
        }
        return Response.ok(PagedFilesResponse(dtos, total, page, size)).build()
    }

    @GET
    @Path("/missing")
    fun missing(
        @QueryParam("consumerId") consumerId: String,
        @QueryParam("bucket") bucket: String?,
        @QueryParam("since") since: String?,
        @QueryParam("page") @DefaultValue("0") page: Int,
        @QueryParam("size") @DefaultValue("20") size: Int
    ): Response {
        val sinceInstant = since?.let { Instant.parse(it) } ?: Instant.now().minusSeconds(86400)
        val (files, total) = metadataRepository.findMissing(consumerId, bucket, sinceInstant, page, size)
        val dtos = files.map { m ->
            val tags = m.tags?.let { Json.decodeFromString<Map<String, String>>(it) }
            FileMetadataDto.from(m, tags)
        }
        return Response.ok(PagedFilesResponse(dtos, total, page, size)).build()
    }

    @PUT
    @Path("/{id}/delivery")
    @Transactional
    fun markProcessed(@PathParam("id") id: String, request: MarkProcessedRequest): Response {
        metadataRepository.findByIdOrNull(id)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse("File not found", "FILE_NOT_FOUND")).build()

        val existing = deliveryRepository.findByFileIdAndConsumerId(id, request.consumerId)
        if (existing == null) {
            val delivery = FileDelivery().apply {
                fileId = id
                consumerId = request.consumerId
                note = request.note
            }
            deliveryRepository.persist(delivery)
        }
        return Response.ok().build()
    }
}
