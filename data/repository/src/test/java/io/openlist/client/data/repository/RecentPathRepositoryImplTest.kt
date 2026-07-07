package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.openlist.client.core.database.dao.RecentPathDao
import io.openlist.client.core.database.entity.RecentPathEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecentPathRepositoryImplTest {

    private val recentPathDao = mockk<RecentPathDao>(relaxed = true)
    private lateinit var repository: RecentPathRepositoryImpl

    @Before
    fun setUp() {
        repository = RecentPathRepositoryImpl(recentPathDao)
    }

    @Test
    fun `recordPath normalizes path and trims per instance`() = runTest {
        val entrySlot = slot<RecentPathEntity>()
        coEvery { recentPathDao.upsert(capture(entrySlot)) } returns Unit
        coEvery { recentPathDao.trimToLimit(INSTANCE_ID, 50) } returns Unit
        val before = System.currentTimeMillis()

        repository.recordPath(INSTANCE_ID, "/docs/", displayName = "Docs")

        val entry = entrySlot.captured
        assertEquals(INSTANCE_ID, entry.instanceId)
        assertEquals("/docs", entry.path)
        assertEquals("Docs", entry.displayName)
        assertTrue(entry.visitedAt >= before)
        coVerify(exactly = 1) { recentPathDao.trimToLimit(INSTANCE_ID, 50) }
    }

    @Test
    fun `recordPath derives root display name when none is supplied`() = runTest {
        val entrySlot = slot<RecentPathEntity>()
        coEvery { recentPathDao.upsert(capture(entrySlot)) } returns Unit

        repository.recordPath(INSTANCE_ID, "/", displayName = null)

        assertEquals("/", entrySlot.captured.path)
        assertEquals("根目录", entrySlot.captured.displayName)
    }

    @Test
    fun `observeAll maps entities to domain models without exposing Entity`() = runTest {
        every { recentPathDao.observeAll() } returns flowOf(
            listOf(
                RecentPathEntity(
                    instanceId = "i1",
                    path = "/a",
                    displayName = "a",
                    visitedAt = 10,
                ),
                RecentPathEntity(
                    instanceId = "i2",
                    path = "/b",
                    displayName = "b",
                    visitedAt = 20,
                ),
            ),
        )

        val recents = repository.observeAll().first()

        assertEquals(listOf("i1", "i2"), recents.map { it.instanceId })
        assertEquals(listOf("/a", "/b"), recents.map { it.path })
        assertEquals(listOf("a", "b"), recents.map { it.displayName })
        assertEquals(listOf(10L, 20L), recents.map { it.visitedAt })
    }

    @Test
    fun `deleteByInstanceId delegates to dao`() = runTest {
        repository.deleteByInstanceId(INSTANCE_ID)

        coVerify(exactly = 1) { recentPathDao.deleteByInstanceId(INSTANCE_ID) }
    }

    private companion object {
        const val INSTANCE_ID = "instance-1"
    }
}
