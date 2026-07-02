package io.openlist.client.core.common

import android.util.Log
import io.openlist.client.BuildConfig

/**
 * Every log line in the app must go through here, never android.util.Log directly.
 * §9.1/§14 security requirement: Token/Authorization/Cookie/signed-url values must
 * never reach logcat or crash reports, so redaction happens centrally in [redact]
 * rather than being left to each call site to remember.
 */
object SafeLogger {
    private const val REDACTED = "[REDACTED]"

    private val sensitiveKeyPatterns = listOf(
        Regex("(?i)authorization"),
        Regex("(?i)token"),
        Regex("(?i)cookie"),
        Regex("(?i)set-cookie"),
        Regex("(?i)password"),
    )

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, redact(message))
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, redact(message), throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, redact(message), throwable)
    }

    /** Redacts values of sensitive header/key names inside free-form log text. */
    fun redact(message: String): String {
        var result = message
        for (pattern in sensitiveKeyPatterns) {
            result = result.replace(Regex("${pattern.pattern}\\s*[:=]\\s*\\S+", RegexOption.IGNORE_CASE)) { match ->
                val separator = if (match.value.contains(':')) ':' else '='
                "${match.value.substringBefore(separator)}$separator $REDACTED"
            }
        }
        return result
    }
}
