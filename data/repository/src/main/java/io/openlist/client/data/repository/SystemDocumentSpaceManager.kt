package io.openlist.client.data.repository

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import io.openlist.client.core.database.dao.SystemWriteTransactionDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SystemDocumentVolumeSnapshot(
    val totalBytes: Long,
    val availableBytes: Long,
)

interface SystemDocumentVolume {
    fun snapshot(): SystemDocumentVolumeSnapshot?
}

@Singleton
class AndroidSystemDocumentVolume @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemDocumentVolume {
    override fun snapshot(): SystemDocumentVolumeSnapshot? = runCatching {
        StatFs(context.filesDir.absolutePath).let { stat ->
            SystemDocumentVolumeSnapshot(stat.totalBytes, stat.availableBytes)
        }
    }.getOrNull()
}

/**
 * Controls only private read-cache allocations in P3.  P4 reuses the same
 * formula for no-backup drafts; neither path ever touches shared storage.
 */
@Singleton
class SystemDocumentSpaceManager @Inject constructor(
    private val volume: SystemDocumentVolume,
    private val transactionDao: SystemWriteTransactionDao,
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private val readReservations = mutableMapOf<String, Long>()
    private val draftReservations = mutableMapOf<String, Long>()

    suspend fun reserveRead(initialBytes: Long): SystemDocumentSpaceReservation? {
        if (initialBytes < 0) return null
        return mutex.withLock {
            if (!canAllocateLocked(initialBytes)) return@withLock null
            val id = UUID.randomUUID().toString()
            readReservations[id] = initialBytes
            SystemDocumentSpaceReservation(id, initialBytes)
        }
    }

    /** For unknown-size materialization, reserve every observed growth increment. */
    suspend fun growReadReservation(reservation: SystemDocumentSpaceReservation, additionalBytes: Long): Boolean {
        if (additionalBytes < 0) return false
        return mutex.withLock {
            val current = readReservations[reservation.id] ?: return@withLock false
            if (!canAllocateLocked(additionalBytes)) return@withLock false
            readReservations[reservation.id] = current + additionalBytes
            reservation.updateReservedBytes(current + additionalBytes)
            true
        }
    }

    suspend fun releaseReadReservation(reservation: SystemDocumentSpaceReservation) {
        mutex.withLock { readReservations.remove(reservation.id) }
    }

    /**
     * Draft reservations are mirrored to Room by WriteCoordinator immediately
     * after acquisition.  The in-memory entry closes the race before that
     * durable journal row exists; after a process restart Room is authoritative.
     */
    suspend fun reserveDraft(transactionId: String, initialBytes: Long): Boolean {
        if (initialBytes < 0) return false
        return mutex.withLock {
            if (draftReservations.containsKey(transactionId) || !canAllocateLocked(initialBytes)) return@withLock false
            draftReservations[transactionId] = initialBytes
            true
        }
    }

    suspend fun confirmDraftReservation(transactionId: String) {
        mutex.withLock { draftReservations.remove(transactionId) }
    }

    suspend fun growDraftReservation(transactionId: String, additionalBytes: Long): Boolean {
        if (additionalBytes < 0) return false
        return mutex.withLock {
            if (!canAllocateLocked(additionalBytes)) return@withLock false
            // Once persisted, the existing reservation is already included by
            // transactionDao.totalActiveReservations().  Keep only the growth
            // delta in memory until the DAO update completes.
            draftReservations[transactionId] = (draftReservations[transactionId] ?: 0L) + additionalBytes
            true
        }
    }

    suspend fun releaseDraftReservation(transactionId: String) {
        mutex.withLock { draftReservations.remove(transactionId) }
    }

    /** Rechecks the same volume safety invariant before a user retries a draft. */
    suspend fun canKeepExistingDraft(): Boolean = mutex.withLock { canAllocateLocked(0L) }

    suspend fun activeReadReservationBytes(): Long = mutex.withLock { readReservations.values.sum() }

    /** P3 startup cleanup is restricted to its own cache namespace. */
    fun cleanOrphanedReadCache() {
        readCacheDirectory().listFiles()?.forEach { it.deleteRecursively() }
    }

    fun newReadCacheFile(): File {
        val directory = readCacheDirectory()
        check(directory.exists() || directory.mkdirs()) { "无法创建私有读取缓存目录" }
        return File(directory, "${UUID.randomUUID()}.part")
    }

    fun draftFile(transactionId: String): File {
        require(UUID.fromString(transactionId).toString() == transactionId) { "非法事务标识" }
        val directory = File(context.noBackupFilesDir, "system-documents-drafts")
        check(directory.exists() || directory.mkdirs()) { "无法创建私有草稿目录" }
        val file = File(directory, "$transactionId.draft")
        check(file.canonicalFile.parentFile == directory.canonicalFile) { "草稿路径越界" }
        return file
    }

    fun deleteDraftFile(transactionId: String) {
        draftFile(transactionId).delete()
    }

    private suspend fun canAllocateLocked(additionalBytes: Long): Boolean {
        val snapshot = volume.snapshot() ?: return false
        if (snapshot.totalBytes <= 0 || snapshot.availableBytes < 0) return false
        val durableReservations = transactionDao.totalActiveReservations().coerceAtLeast(0L)
        val safetyReserve = maxOf(snapshot.totalBytes / 10L, GIB)
        return snapshot.availableBytes - durableReservations - readReservations.values.sum() - draftReservations.values.sum() - additionalBytes >= safetyReserve
    }

    private fun readCacheDirectory(): File = File(context.cacheDir, "system-documents-read")

    companion object {
        const val GIB: Long = 1024L * 1024L * 1024L
    }
}

class SystemDocumentSpaceReservation internal constructor(
    internal val id: String,
    initialReservedBytes: Long,
) {
    var reservedBytes: Long = initialReservedBytes
        private set

    internal fun updateReservedBytes(value: Long) {
        reservedBytes = value
    }
}
