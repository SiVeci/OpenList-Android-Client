package io.openlist.client.data.repository

import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.network.SystemDocumentHttpClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class SystemDocumentReadCoordinatorTest {
    @Test
    fun `accepts exact 206 bytes without materialization`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 2-4/6").setBody("cde"))
            server.start()
            val coordinator = SystemDocumentReadCoordinator(SystemDocumentHttpClient(), mockk(relaxed = true))

            val result = coordinator.read(coordinator.open(source(server.url("file").toString(), 6)), 2, 3)

            assertTrue(result is ApiResult.Success)
            assertArrayEquals("cde".encodeToByteArray(), (result as ApiResult.Success).data)
        }
    }

    @Test
    fun `rejects malformed 206 range instead of returning wrong bytes`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-2/6").setBody("abc"))
            server.start()
            val coordinator = SystemDocumentReadCoordinator(SystemDocumentHttpClient(), mockk(relaxed = true))

            val result = coordinator.read(coordinator.open(source(server.url("file").toString(), 6)), 2, 3)

            assertTrue(result is ApiResult.Failure)
        }
    }

    @Test
    fun `reads partial range at EOF without treating it as malformed`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 4-5/6").setBody("ef"))
            server.start()
            val coordinator = SystemDocumentReadCoordinator(SystemDocumentHttpClient(), mockk(relaxed = true))

            val result = coordinator.read(coordinator.open(source(server.url("file").toString(), 6)), 4, 8)

            assertTrue(result is ApiResult.Success)
            assertArrayEquals("ef".encodeToByteArray(), (result as ApiResult.Success).data)
        }
    }

    @Test
    fun `returns empty bytes beyond EOF without making a request`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val coordinator = SystemDocumentReadCoordinator(SystemDocumentHttpClient(), mockk(relaxed = true))

            val result = coordinator.read(coordinator.open(source(server.url("file").toString(), 6)), 6, 1)

            assertTrue(result is ApiResult.Success)
            assertEquals(0, (result as ApiResult.Success).data.size)
            assertEquals(null, server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS))
        }
    }

    @Test
    fun `200 fallback materializes privately then removes cache on close`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("abcdef"))
            server.start()
            val manager = mockk<SystemDocumentSpaceManager>()
            val reservation = SystemDocumentSpaceReservation("fixture", 6)
            val cache = File.createTempFile("v14-read", ".part").apply { delete() }
            coEvery { manager.reserveRead(6) } returns reservation
            coEvery { manager.releaseReadReservation(reservation) } returns Unit
            every { manager.newReadCacheFile() } returns cache
            val coordinator = SystemDocumentReadCoordinator(SystemDocumentHttpClient(), manager)
            val session = coordinator.open(source(server.url("file").toString(), 6))

            val result = coordinator.read(session, 2, 3)

            assertTrue(result is ApiResult.Success)
            assertArrayEquals("cde".encodeToByteArray(), (result as ApiResult.Success).data)
            assertTrue(cache.exists())
            coordinator.close(session)
            assertTrue(!cache.exists())
            coVerify(exactly = 1) { manager.releaseReadReservation(reservation) }
        }
    }

    @Test
    fun `authentication and not found responses never return file bytes`() = runTest {
        listOf(401, 404).forEach { status ->
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(status).setBody("not a file"))
                server.start()
                val coordinator = SystemDocumentReadCoordinator(SystemDocumentHttpClient(), mockk(relaxed = true))

                val result = coordinator.read(coordinator.open(source(server.url("file").toString(), 6)), 0, 3)

                assertTrue("HTTP $status must fail rather than return response body", result is ApiResult.Failure)
            }
        }
    }

    @Test
    fun `network timeout fails without materializing a response body`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.start()
            val client = OkHttpClient.Builder().readTimeout(100, TimeUnit.MILLISECONDS).build()
            val coordinator = SystemDocumentReadCoordinator(SystemDocumentHttpClient(client, Unit), mockk(relaxed = true))

            val result = coordinator.read(coordinator.open(source(server.url("file").toString(), 6)), 0, 3)

            assertTrue(result is ApiResult.Failure)
        }
    }

    private fun source(url: String, size: Long) = SystemDocumentReadSource(url, url, null, size)
}
