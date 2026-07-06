package io.openlist.client.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [SafeLogger.redact]'s `sign` query-parameter redaction (v0.4_EXECUTION_PLAN.md
 * §11 S5-T1, PRD §10.4): signed `/d//p/` media URLs carry a `sign` value that
 * is itself a bearer credential and must never reach logcat, but the match
 * must use a word boundary so it doesn't also clobber unrelated words that
 * merely contain "sign" as a substring (e.g. "designation", "assignment").
 */
class SafeLoggerTest {

    @Test
    fun `redacts a sign query parameter inside a full url`() {
        val message = "Fetching https://example.com/d/movie.mp4?sign=AbCdEf123"

        val result = SafeLogger.redact(message)

        assertFalse(result.contains("AbCdEf123"))
        assertTrue(result.contains("[REDACTED]"))
    }

    @Test
    fun `does not redact words that merely contain sign as a substring`() {
        val designation = "designation=abc"
        val assignment = "assignment=1"

        assertEquals(designation, SafeLogger.redact(designation))
        assertEquals(assignment, SafeLogger.redact(assignment))
    }

    @Test
    fun `redacts sign with colon separator too`() {
        val message = "params sign: AbCdEf123 done"

        val result = SafeLogger.redact(message)

        assertFalse(result.contains("AbCdEf123"))
    }

    @Test
    fun `redacts a two-word bearer-scheme authorization value in full`() {
        // S7-T2 security audit: the original pattern only redacted the first
        // whitespace-delimited token after the separator, so "Authorization:
        // Bearer abc123" left "abc123" (the actual credential) untouched.
        // The value capture now spans up to two tokens so the whole
        // "Bearer abc123" credential is redacted, not just the scheme word.
        val message = "Authorization: Bearer abc123 token=xyz789"

        val result = SafeLogger.redact(message)

        assertFalse(result.contains("Bearer"))
        assertFalse(result.contains("abc123"))
        assertFalse(result.contains("xyz789"))
    }

    @Test
    fun `case insensitive SIGN is still redacted`() {
        val message = "?SIGN=AbCdEf123"

        val result = SafeLogger.redact(message)

        assertFalse(result.contains("AbCdEf123"))
    }

    @Test
    fun `redacts JSON quoted-key request and response bodies`() {
        // S7-T2 confirmed leak: HttpLoggingInterceptor.Level.BODY logs the raw
        // JSON request/response when the user's debug-log setting is on, and
        // the pre-fix regex required the key text to sit directly next to the
        // separator -- a quote between "password" and ':' made the whole
        // match fail, so compact JSON bodies passed through unredacted.
        val message = """{"username":"alice","password":"hunter2","otp_code":"123456"}"""

        val result = SafeLogger.redact(message)

        assertFalse(result.contains("hunter2"))
        assertFalse(result.contains("123456"))
        assertTrue(result.contains("alice")) // non-sensitive field left intact
    }

    @Test
    fun `redacts a JSON token field nested in a response body`() {
        val message = """{"data":{"token":"abcdef123456","otp":false}}"""

        val result = SafeLogger.redact(message)

        assertFalse(result.contains("abcdef123456"))
    }
}
