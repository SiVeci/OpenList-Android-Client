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
    is DomainError.OpenListError -> message.ifBlank { "请求失败${code?.let { " ($it)" } ?: ""}" }
    is DomainError.Unknown -> "出现未知错误，请重试"
}
