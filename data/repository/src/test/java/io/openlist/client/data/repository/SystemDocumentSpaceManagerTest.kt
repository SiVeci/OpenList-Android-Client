package io.openlist.client.data.repository

import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.openlist.client.core.database.dao.SystemWriteTransactionDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SystemDocumentSpaceManagerTest {
    private val transactionDao = mockk<SystemWriteTransactionDao>()
    private val context = mockk<Context>(relaxed = true)

    @Test
    fun `all active reservations and 1 GiB safety margin are enforced`() = runTest {
        coEvery { transactionDao.totalActiveReservations() } returns GIB
        val manager = SystemDocumentSpaceManager(
            volume = FixedVolume(total = 10 * GIB, available = 5 * GIB),
            transactionDao = transactionDao,
            context = context,
        )

        val first = manager.reserveRead(3 * GIB)
        assertNotNull(first)
        assertNull(manager.reserveRead(1L))

        manager.releaseReadReservation(first!!)
        assertNotNull(manager.reserveRead(3 * GIB))
    }

    @Test
    fun `unknown size growth is denied before crossing reserve`() = runTest {
        coEvery { transactionDao.totalActiveReservations() } returns 0L
        val manager = SystemDocumentSpaceManager(
            volume = FixedVolume(total = 20 * GIB, available = 4 * GIB),
            transactionDao = transactionDao,
            context = context,
        )
        val reservation = manager.reserveRead(GIB)!!

        assertFalse(manager.growReadReservation(reservation, GIB + 1L))
        assertTrue(manager.growReadReservation(reservation, GIB))
    }

    @Test
    fun `startup cleanup is limited to read cache namespace`() {
        val cacheRoot = createTempDir(prefix = "v14-cache")
        val readCache = File(cacheRoot, "system-documents-read").apply { mkdirs() }
        File(readCache, "orphan.part").writeText("temporary")
        val unrelated = File(cacheRoot, "unrelated-draft").apply { writeText("keep") }
        every { context.cacheDir } returns cacheRoot
        val manager = SystemDocumentSpaceManager(FixedVolume(20 * GIB, 10 * GIB), transactionDao, context)

        manager.cleanOrphanedReadCache()

        assertTrue(readCache.listFiles().isNullOrEmpty())
        assertTrue(unrelated.exists())
        cacheRoot.deleteRecursively()
    }

    private class FixedVolume(private val total: Long, private val available: Long) : SystemDocumentVolume {
        override fun snapshot() = SystemDocumentVolumeSnapshot(total, available)
    }

    private companion object {
        const val GIB = SystemDocumentSpaceManager.GIB
    }
}
