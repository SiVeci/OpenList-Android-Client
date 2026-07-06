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
        // v1.0 S7-T2 security audit: `otp_code`/`otpCode` (2FA login, DEC-601)
        // was missing from this list entirely, so OTP values were never
        // redacted even when the JSON-body gap below was also fixed.
        Regex("(?i)otp"),
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

    /**
     * Redacts values of sensitive header/key names inside free-form log text
     * AND JSON request/response bodies (the latter matters because
     * [io.openlist.client.core.network.OpenListClientFactory] wires this
     * through `HttpLoggingInterceptor.Level.BODY` when the user's debug-log
     * setting is on — S7-T2 security audit found that JSON's quoted-key form
     * `"password":"value"` was falling through the original key-then-separator
     * regex entirely, since a `"` sat between the key and the `:`/`=`).
     */
    fun redact(message: String): String {
        var result = message
        for (pattern in sensitiveKeyPatterns) {
            // `[A-Za-z0-9_]*` around the key lets real API field names like
            // "otp_code"/"access_token" match on their sensitive substring
            // without requiring an exact key-name equality; this is safe for
            // the word-boundary "sign" pattern too since `\b` never matches
            // in the middle of a single contiguous word (see SafeLoggerTest's
            // "designation"/"assignment" case).
            val fullRegex = Regex(
                "\"?[A-Za-z0-9_]*(?:${pattern.pattern})[A-Za-z0-9_]*\"?\\s*[:=]\\s*" +
                    "(\"(?:[^\"\\\\]|\\\\.)*\"|\\S+(?:\\s+\\S+)?)",
                RegexOption.IGNORE_CASE,
            )
            result = result.replace(fullRegex) { match ->
                val value = match.groupValues[1]
                "${match.value.removeSuffix(value)}$REDACTED"
            }
        }
        return result
    }
}
