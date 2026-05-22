package mlid.enghub.hub.resource.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterFileRequest(
    val bucket: String,
    val reportId: String,
    val reportCategory: String,
    val objectKey: String,
    val filename: String,
    val contentType: String,
    val fileSize: Long,
    val checksum: String? = null,
    val uploaderId: String,
    val tags: Map<String, String>? = null,
)
