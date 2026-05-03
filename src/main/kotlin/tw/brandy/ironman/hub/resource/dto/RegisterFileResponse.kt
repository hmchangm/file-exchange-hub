package tw.brandy.ironman.hub.resource.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterFileResponse(
    val id: String,
    val status: String,
    val eventPublished: Boolean,
    val registeredAt: String
)
