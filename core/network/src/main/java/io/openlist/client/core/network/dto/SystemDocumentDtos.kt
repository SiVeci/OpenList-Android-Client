package io.openlist.client.core.network.dto

import kotlinx.serialization.Serializable

/** Typed fs/move-copy payload used only by the v1.4 strong-completion path. */
@Serializable
data class FsMutationTaskResponse(
    val message: String? = null,
    val tasks: List<TaskInfoDto> = emptyList(),
)
