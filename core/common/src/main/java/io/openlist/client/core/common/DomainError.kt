package io.openlist.client.core.common

/** Unified error taxonomy (v0.1_PRD §11.1). ErrorInterceptor/Repository map all
 * network, database and codec failures into one of these before reaching UI. */
sealed class DomainError {
    data object NetworkUnavailable : DomainError()
    data object Timeout : DomainError()
    data object Unauthorized : DomainError()
    data object Forbidden : DomainError()
    data object NotFound : DomainError()
    data object ServerError : DomainError()
    data object InvalidInstance : DomainError()
    data object CertificateError : DomainError()
    data object PathEncodeError : DomainError()
    /** The instance has no search index built/enabled (v0.3_EXECUTION_PLAN.md §19, V-04). */
    data object SearchNotAvailable : DomainError()
    /** A share id no longer resolves (deleted, or never existed). */
    data object ShareNotFound : DomainError()
    /** File exceeds the preview size cap (v0.4_EXECUTION_PLAN.md §11, S1-T4). */
    data object PreviewTooLarge : DomainError()
    /** No in-app player/renderer can handle this media format. */
    data object MediaUnsupported : DomainError()
    /** A previously-resolved MediaSource/PreviewUrl's URL is no longer valid
     * (core:common has no dependency on core:model, so this is deliberately
     * not a KDoc `[...]` link to those types). */
    data object MediaSourceExpired : DomainError()
    /** No installed app (and no web fallback) can open this file externally. */
    data object ExternalOpenUnavailable : DomainError()
    /** Current session is not an admin, or the server rejected an
     * admin API call with 403 (v0.5_EXECUTION_PLAN.md §10.6/PRD §14.1).
     * Distinct from [Forbidden] so admin-gate UI can show copy specific to
     * "not an admin" rather than the generic permission-denied message. */
    data object AdminAccessDenied : DomainError()
    /** An admin API endpoint is missing/incompatible on this backend
     * version (PRD §16.4.4 "后端版本差异导致某个 admin 接口不可用"). */
    data object AdminApiUnavailable : DomainError()
    data class OpenListError(val code: Int?, val message: String) : DomainError()
    data class Unknown(val throwable: Throwable?) : DomainError()
}

/** UI-facing copy for each error (v0.1_PRD §11.2). Screens must not branch on
 * DomainError subtype themselves — display this string, add a retry affordance
 * where the table calls for one. */
fun DomainError.toUserMessage(): String = when (this) {
    DomainError.NetworkUnavailable -> "网络不可达，请检查网络连接"
    DomainError.Timeout -> "连接超时，请重试"
    DomainError.Unauthorized -> "登录已失效，请重新登录"
    DomainError.Forbidden -> "没有权限执行此操作"
    DomainError.NotFound -> "文件或目录不存在"
    DomainError.ServerError -> "服务器出现错误，请稍后重试"
    DomainError.InvalidInstance -> "实例地址无效，请检查后重试"
    DomainError.CertificateError -> "证书不可信，请检查实例地址"
    DomainError.PathEncodeError -> "路径解析失败"
    DomainError.SearchNotAvailable -> "该实例未启用搜索索引"
    DomainError.ShareNotFound -> "分享不存在或已被删除"
    DomainError.PreviewTooLarge -> "文件过大，无法预览"
    DomainError.MediaUnsupported -> "该格式暂不支持播放"
    DomainError.MediaSourceExpired -> "播放地址已失效，请重试"
    DomainError.ExternalOpenUnavailable -> "没有可处理该文件的应用"
    DomainError.AdminAccessDenied -> "当前账号不是管理员或无管理权限"
    DomainError.AdminApiUnavailable -> "当前实例不支持该管理能力"
    is DomainError.OpenListError -> message.ifBlank { "请求失败${code?.let { " ($it)" } ?: ""}" }
    is DomainError.Unknown -> "出现未知错误，请重试"
}
