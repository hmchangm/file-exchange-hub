package tw.brandy.ironman.hub.resource.dto

import kotlinx.serialization.Serializable

@Serializable
data class PagedFilesResponse(
    val files: List<FileMetadataDto>,
    val total: Long,
    val page: Int,
    val size: Int
)
