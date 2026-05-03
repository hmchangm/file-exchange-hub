package tw.brandy.ironman.hub.resource

import io.smallrye.mutiny.Uni
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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
    fun register(request: RegisterFileRequest): Uni<Response> =
        registrationService.register(request)
            .map { Response.status(Response.Status.CREATED).entity(it).build() }
            .onFailure(ObjectNotFoundException::class.java).recoverWithItem { e ->
                Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse(e.message ?: "Not found", "OBJECT_NOT_FOUND")).build()
            }

    @GET
    @Path("/{id}")
    fun getById(@PathParam("id") id: String): Uni<Response> =
        metadataRepository.findById(id).map { metadata ->
            if (metadata == null) {
                Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse("File not found", "FILE_NOT_FOUND")).build()
            } else {
                val tags = metadata.tags?.let { Json.decodeFromString<Map<String, String>>(it) }
                Response.ok(FileMetadataDto.from(metadata, tags)).build()
            }
        }

    @GET
    fun search(
        @QueryParam("uploaderId") uploaderId: String?,
        @QueryParam("bucket") bucket: String?,
        @QueryParam("page") @DefaultValue("0") page: Int,
        @QueryParam("size") @DefaultValue("20") size: Int
    ): Uni<Response> =
        metadataRepository.search(uploaderId, bucket, page, size).map { (files, total) ->
            val dtos = files.map { m ->
                FileMetadataDto.from(m, m.tags?.let { Json.decodeFromString<Map<String, String>>(it) })
            }
            Response.ok(PagedFilesResponse(dtos, total, page, size)).build()
        }

    @GET
    @Path("/missing")
    fun missing(
        @QueryParam("consumerId") consumerId: String,
        @QueryParam("bucket") bucket: String?,
        @QueryParam("since") since: String?,
        @QueryParam("page") @DefaultValue("0") page: Int,
        @QueryParam("size") @DefaultValue("20") size: Int
    ): Uni<Response> {
        val sinceInstant = since?.let { Instant.parse(it) } ?: Instant.now().minusSeconds(86400)
        return metadataRepository.findMissing(consumerId, bucket, sinceInstant, page, size).map { (files, total) ->
            val dtos = files.map { m ->
                FileMetadataDto.from(m, m.tags?.let { Json.decodeFromString<Map<String, String>>(it) })
            }
            Response.ok(PagedFilesResponse(dtos, total, page, size)).build()
        }
    }

    @PUT
    @Path("/{id}/delivery")
    fun markProcessed(@PathParam("id") id: String, request: MarkProcessedRequest): Uni<Response> =
        metadataRepository.findById(id).flatMap { metadata ->
            if (metadata == null) {
                Uni.createFrom().item(
                    Response.status(Response.Status.NOT_FOUND)
                        .entity(ErrorResponse("File not found", "FILE_NOT_FOUND")).build()
                )
            } else {
                val delivery = FileDelivery(
                    fileId = id,
                    consumerId = request.consumerId,
                    note = request.note
                )
                deliveryRepository.insertIgnore(delivery).map { Response.ok().build() }
            }
        }
}
