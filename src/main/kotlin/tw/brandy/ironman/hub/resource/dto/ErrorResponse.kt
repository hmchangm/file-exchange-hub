package tw.brandy.ironman.hub.resource.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String, val code: String)
