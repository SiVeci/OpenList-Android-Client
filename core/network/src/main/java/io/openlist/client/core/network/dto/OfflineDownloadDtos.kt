package io.openlist.client.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * POST /api/fs/add_offline_download request. Source:
 * server/handles/offline_download.go AddOfflineDownloadReq. [deletePolicy]
 * defaults to blank so the backend applies its own default — the valid
 * enum values are pending real-device verification (v0.3_EXECUTION_PLAN.md V-05).
 */
@Serializable
data class AddOfflineDownloadReq(
    val urls: List<String>,
    val path: String,
    val tool: String = "",
    @SerialName("delete_policy") val deletePolicy: String = "",
)

/** data payload of POST /api/fs/add_offline_download. Source: offline_download.go AddOfflineDownload. */
@Serializable
data class AddOfflineDownloadResp(
    val tasks: List<TaskInfoDto> = emptyList(),
)
