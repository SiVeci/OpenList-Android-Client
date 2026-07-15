package io.openlist.client.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemWriteTransactionTest {
    @Test
    fun `provider modes accept only declared safe modes`() {
        assertEquals(SystemDocumentOpenMode.READ, SystemDocumentOpenMode.parse("r"))
        assertEquals(SystemDocumentOpenMode.WRITE_TRUNCATE, SystemDocumentOpenMode.parse("wt"))
        assertEquals(SystemDocumentOpenMode.READ_WRITE, SystemDocumentOpenMode.parse("rw"))
        assertNull(SystemDocumentOpenMode.parse("a"))
        assertNull(SystemDocumentOpenMode.parse("w+"))
    }

    @Test
    fun `transaction cannot skip unverified remote stages`() {
        assertTrue(SystemWriteTransactionState.LOCAL_READY.canTransitionTo(SystemWriteTransactionState.LOCAL_WRITING))
        assertTrue(SystemWriteTransactionState.LOCAL_READY.canTransitionTo(SystemWriteTransactionState.REMOTE_STAGING))
        assertFalse(SystemWriteTransactionState.LOCAL_READY.canTransitionTo(SystemWriteTransactionState.TARGET_PROMOTED))
        assertTrue(SystemWriteTransactionState.TARGET_VERIFIED.canTransitionTo(SystemWriteTransactionState.CLEANUP_PENDING))
        assertTrue(SystemWriteTransactionState.CLEANUP_PENDING.canTransitionTo(SystemWriteTransactionState.CONTENT_COMMITTED))
        assertTrue(SystemWriteTransactionState.FAILED_DRAFT.canTransitionTo(SystemWriteTransactionState.LOCAL_READY))
        assertFalse(SystemWriteTransactionState.CLEANED.canTransitionTo(SystemWriteTransactionState.FAILED_DRAFT))
    }
}
