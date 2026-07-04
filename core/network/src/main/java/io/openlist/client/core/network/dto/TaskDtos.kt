package io.openlist.client.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One backend task. Source: server/handles/task.go TaskInfo. `state` is the
 * raw numeric `tache.State` — mapped to `UnifiedTaskStatus` by a
 * `TaskStateMapper` (Sprint 5), exact enum values pending real-device
 * verification (v0.3_EXECUTION_PLAN.md V-02).
 */
@Serializable
data class TaskInfoDto(
    val id: String = "",
    val name: String = "",
    val creator: String = "",
    @SerialName("creator_role") val creatorRole: Int = 0,
    val state: Int = 0,
    val status: String = "",
    val progress: Double = 0.0,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("total_bytes") val totalBytes: Long = 0,
    val error: String = "",
)
