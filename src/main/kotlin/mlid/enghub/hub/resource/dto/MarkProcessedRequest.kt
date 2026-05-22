package mlid.enghub.hub.resource.dto

import kotlinx.serialization.Serializable

@Serializable
data class MarkProcessedRequest(
    val consumerId: String,
    val note: String? = null,
)
