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
    fun `still redacts authorization and token values as before`() {
        // Pre-existing behavior (not part of this Sprint's change): the
        // combined pattern is "<key>\s*[:=]\s*\S+", so it only ever redacts
        // one whitespace-delimited token after the separator. "Authorization:
        // Bearer" is treated as the key/separator pair here, so "Bearer" is
        // what gets redacted and "abc123" (a second word) is untouched --
        // this test documents that existing shape rather than asserting an
        // unrelated improvement to it. token=xyz789 has no embedded space, so
        // its whole value is correctly redacted.
        val message = "Authorization: Bearer abc123 token=xyz789"

        val result = SafeLogger.redact(message)

        assertFalse(result.contains("Bearer"))
        assertFalse(result.contains("xyz789"))
    }

    @Test
    fun `case insensitive SIGN is still redacted`() {
        val message = "?SIGN=AbCdEf123"

        val result = SafeLogger.redact(message)

        assertFalse(result.contains("AbCdEf123"))
    }
}
