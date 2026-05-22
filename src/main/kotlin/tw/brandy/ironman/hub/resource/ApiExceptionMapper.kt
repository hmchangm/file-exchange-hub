package tw.brandy.ironman.hub.resource

import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import tw.brandy.ironman.hub.resource.dto.ErrorResponse
import tw.brandy.ironman.hub.service.ObjectNotFoundException

@Provider
class ObjectNotFoundExceptionMapper : ExceptionMapper<ObjectNotFoundException> {
    override fun toResponse(exception: ObjectNotFoundException): Response =
        Response
            .status(Response.Status.NOT_FOUND)
            .entity(ErrorResponse(exception.message ?: "Not found", "OBJECT_NOT_FOUND"))
            .build()
}

@Provider
class FileNotFoundExceptionMapper : ExceptionMapper<FileNotFoundException> {
    override fun toResponse(exception: FileNotFoundException): Response =
        Response
            .status(Response.Status.NOT_FOUND)
            .entity(ErrorResponse(exception.message ?: "File not found", "FILE_NOT_FOUND"))
            .build()
}
