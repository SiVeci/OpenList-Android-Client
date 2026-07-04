package io.openlist.client.feature.files

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.designsystem.components.ExpiryOption
import io.openlist.client.core.designsystem.components.toEpochMillis
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.ShareRepository
import io.openlist.client.core.model.Share
import io.openlist.client.core.model.ShareWriteRequest

/**
 * Share-creation sheet state, shared by [FileListViewModel] and
 * [FileDetailViewModel] (v0.3_EXECUTION_PLAN.md §14 — "文件菜单/详情页'分享'").
 * [createdShare]/[shareUrl] non-null switches the sheet into its post-create
 * success state (link + copy + system share), per PRD §6.5.
 */
data class ShareCreateState(
    val targetPath: String,
    val name: String = "",
    val password: String = "",
    val expiryOption: ExpiryOption = ExpiryOption.NEVER,
    val customExpiryMillis: Long? = null,
    val enabled: Boolean = true,
    val submitting: Boolean = false,
    val errorMessage: String? = null,
    val createdShare: Share? = null,
    val shareUrl: String? = null,
)

/** Submits [state] as a new share and, on success, resolves the share URL
 * from the current instance's base URL (never hardcoded, §23.2). */
suspend fun submitShareCreate(
    shareRepository: ShareRepository,
    instanceRepository: InstanceRepository,
    instanceId: String,
    state: ShareCreateState,
): ApiResult<ShareCreateState> {
    val request = ShareWriteRequest(
        paths = listOf(state.targetPath),
        name = state.name.trim().ifBlank { null },
        password = state.password.trim().ifBlank { null },
        expiresAt = state.expiryOption.toEpochMillis(state.customExpiryMillis),
        disabled = !state.enabled,
    )
    return when (val result = shareRepository.createShare(instanceId, request)) {
        is ApiResult.Success -> {
            val instance = instanceRepository.getById(instanceId)
            val url = instance?.let { shareRepository.buildShareUrl(it.baseUrl, result.data.id) }
            ApiResult.Success(state.copy(createdShare = result.data, shareUrl = url, submitting = false))
        }
        is ApiResult.Failure -> result
    }
}
