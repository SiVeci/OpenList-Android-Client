package io.openlist.client.core.common

import android.util.Log

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
        // v0.4 S5-T1: signed /d//p/ media URLs carry a `sign` query parameter
        // that is itself a bearer credential (V-401/V-402) — must be redacted
        // like any other sensitive key. `\b` word boundaries are load-bearing
        // here: a bare "(?i)sign" would also match the "sign" substring inside
        // unrelated keys/words such as "designation" or "assignment".
        Regex("(?i)\\bsign\\b"),
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
