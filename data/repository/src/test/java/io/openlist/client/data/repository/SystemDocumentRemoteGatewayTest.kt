package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.openlist.client.core.auth.TokenProvider
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.FileListResult
import io.openlist.client.core.domain.FilesRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.FileNode
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.SystemDocumentHttpClient
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemDocumentRemoteGatewayTest {
    private val filesRepository = mockk<FilesRepository>()
    private val gateway = SystemDocumentRemoteGateway(
        filesRepository = filesRepository,
        instanceRepository = mockk<InstanceRepository>(),
        tokenProvider = mockk<TokenProvider>(),
        clientFactory = mockk<OpenListClientFactory>(),
        instanceContext = InstanceContext(),
        httpClient = mockk<SystemDocumentHttpClient>(),
    )

    @Test
    fun `fs get server error is absent only after forced fresh parent listing confirms it`() = runTest {
        coEvery { filesRepository.getFile(INSTANCE_ID, TARGET_PATH) } returns ApiResult.Failure(DomainError.ServerError)
        coEvery { filesRepository.listDirectory(INSTANCE_ID, "/", forceRefresh = true) } returns flowOf(
            FileListResult.Fresh(nodes = emptyList(), capability = io.openlist.client.core.model.DirectoryCapability.UNKNOWN),
        )

        val result = gateway.findObject(INSTANCE_ID, TARGET_PATH)

        assertNull((result as ApiResult.Success).data)
        coVerify(exactly = 1) { filesRepository.listDirectory(INSTANCE_ID, "/", forceRefresh = true) }
    }

    @Test
    fun `fs get server error stays an error when the fresh listing still contains target`() = runTest {
        coEvery { filesRepository.getFile(INSTANCE_ID, TARGET_PATH) } returns ApiResult.Failure(DomainError.ServerError)
        coEvery { filesRepository.listDirectory(INSTANCE_ID, "/", forceRefresh = true) } returns flowOf(
            FileListResult.Fresh(nodes = listOf(node(TARGET_PATH)), capability = io.openlist.client.core.model.DirectoryCapability.UNKNOWN),
        )

        val result = gateway.findObject(INSTANCE_ID, TARGET_PATH)

        assertEquals(DomainError.ServerError, (result as ApiResult.Failure).error)
    }

    @Test
    fun `not found does not need a second listing`() = runTest {
        coEvery { filesRepository.getFile(INSTANCE_ID, TARGET_PATH) } returns ApiResult.Failure(DomainError.NotFound)

        val result = gateway.findObject(INSTANCE_ID, TARGET_PATH)

        assertNull((result as ApiResult.Success).data)
        coVerify(exactly = 0) { filesRepository.listDirectory(any(), any(), any()) }
    }

    private fun node(path: String) = FileNode(
        name = path.substringAfterLast('/'),
        path = path,
        isDir = false,
        size = 1,
        modifiedAt = null,
        sign = "",
        thumb = "",
        type = 0,
    )

    private companion object {
        const val INSTANCE_ID = "fixture"
        const val TARGET_PATH = "/target.bin"
    }
}
