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
    /** LDAP (or another alternate login method) isn't enabled on this
     * instance, or this account isn't permitted to use it (v1.0_PRD §12.1;
     * v1.0_EXECUTION_PLAN.md V-601 — both are plain HTTP 403s from the
     * server, distinguished only by message text, so this is mapped at the
     * Repository layer rather than by the shared code→error table). */
    data object AuthMethodUnavailable : DomainError()
    /** A submitted 2FA/OTP code was wrong or expired (v1.0_PRD §12.1;
     * V-602 — the server's signal is envelope `code=402` with the *same*
     * message regardless of whether this is the first missing-code prompt or
     * a wrong resubmission; the Repository tells them apart by whether an
     * `otpCode` was sent, mapping a resubmission failure to this case
     * instead of re-issuing a "needs OTP" prompt). */
    data object OtpInvalid : DomainError()
    /** An upload task can't be retried: not in a retryable (FAILED) state, or
     * its SAF `content://` grant is no longer readable — v1.0_PRD §12.1. */
    data object UploadRetryUnavailable : DomainError()
    /** A local download task can't be cancelled: not in a cancellable
     * (ENQUEUED/RUNNING) state — v1.0_PRD §12.1. */
    data object DownloadCancelUnavailable : DomainError()
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
    DomainError.AuthMethodUnavailable -> "该实例未启用此登录方式，或当前账号不允许使用"
    DomainError.OtpInvalid -> "两步验证码错误或已过期，请重新输入"
    DomainError.UploadRetryUnavailable -> "该任务当前无法重试，文件可能已不可访问，请重新选择上传"
    DomainError.DownloadCancelUnavailable -> "该任务当前无法取消"
    is DomainError.OpenListError -> message.ifBlank { "请求失败${code?.let { " ($it)" } ?: ""}" }
    is DomainError.Unknown -> "出现未知错误，请重试"
}
