package io.openlist.client.data.repository

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.openlist.client.core.database.dao.AdminCacheDao
import io.openlist.client.core.database.dao.DownloadTaskDao
import io.openlist.client.core.database.dao.FileCacheDao
import io.openlist.client.core.database.dao.InstanceDao
import io.openlist.client.core.database.dao.PreviewCacheDao
import io.openlist.client.core.database.dao.RecentPathDao
import io.openlist.client.core.database.dao.RemoteTaskDao
import io.openlist.client.core.database.dao.SearchHistoryDao
import io.openlist.client.core.database.dao.SessionDao
import io.openlist.client.core.database.dao.ShareDao
import io.openlist.client.core.database.dao.UploadTaskDao
import io.openlist.client.core.network.OpenListClientFactory
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.file.Files

class InstanceRepositoryImplTest {

    private val instanceDao = mockk<InstanceDao>(relaxed = true)
    private val sessionDao = mockk<SessionDao>(relaxed = true)
    private val fileCacheDao = mockk<FileCacheDao>(relaxed = true)
    private val downloadTaskDao = mockk<DownloadTaskDao>(relaxed = true)
    private val uploadTaskDao = mockk<UploadTaskDao>(relaxed = true)
    private val shareDao = mockk<ShareDao>(relaxed = true)
    private val searchHistoryDao = mockk<SearchHistoryDao>(relaxed = true)
    private val remoteTaskDao = mockk<RemoteTaskDao>(relaxed = true)
    private val previewCacheDao = mockk<PreviewCacheDao>(relaxed = true)
    private val adminCacheDao = mockk<AdminCacheDao>(relaxed = true)
    private val recentPathDao = mockk<RecentPathDao>(relaxed = true)
    private val clientFactory = mockk<OpenListClientFactory>(relaxed = true)
    private val context = mockk<Context>()

    @Test
    fun `delete clears recent paths for the instance`() = runTest {
        val cacheDir = Files.createTempDirectory("openlist-instance-delete").toFile()
        every { context.cacheDir } returns cacheDir
        coEvery { recentPathDao.deleteByInstanceId(INSTANCE_ID) } returns Unit
        val repository = InstanceRepositoryImpl(
            dao = instanceDao,
            sessionDao = sessionDao,
            fileCacheDao = fileCacheDao,
            downloadTaskDao = downloadTaskDao,
            uploadTaskDao = uploadTaskDao,
            shareDao = shareDao,
            searchHistoryDao = searchHistoryDao,
            remoteTaskDao = remoteTaskDao,
            previewCacheDao = previewCacheDao,
            adminCacheDao = adminCacheDao,
            recentPathDao = recentPathDao,
            clientFactory = clientFactory,
            context = context,
        )

        repository.delete(INSTANCE_ID)

        coVerify(exactly = 1) { recentPathDao.deleteByInstanceId(INSTANCE_ID) }
        coVerify(exactly = 1) { instanceDao.deleteById(INSTANCE_ID) }
    }

    private companion object {
        const val INSTANCE_ID = "instance-1"
    }
}
