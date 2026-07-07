package io.openlist.client.feature.instance

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.Instance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddInstanceViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `testConnection records result and elapsed time`() = runTest {
        val repository = FakeInstanceRepository()
        val viewModel = AddInstanceViewModel(repository)

        viewModel.onUrlChange("https://example.com")
        viewModel.testConnection()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ConnectionCheck.Reachable, state.testResult)
        assertNotNull(state.testElapsedMillis)
        assertEquals(listOf("https://example.com"), repository.testedBaseUrls)
    }

    @Test
    fun `url change clears previous test result and elapsed time`() = runTest {
        val viewModel = AddInstanceViewModel(FakeInstanceRepository())

        viewModel.onUrlChange("https://example.com")
        viewModel.testConnection()
        advanceUntilIdle()

        viewModel.onUrlChange("https://other.example.com")

        val state = viewModel.uiState.value
        assertNull(state.testResult)
        assertNull(state.testElapsedMillis)
    }

    private class FakeInstanceRepository : InstanceRepository {
        val testedBaseUrls = mutableListOf<String>()
        private val instances = MutableStateFlow(emptyList<Instance>())

        override fun observeAll(): Flow<List<Instance>> = instances

        override suspend fun getById(id: String): Instance? = instances.value.firstOrNull { it.id == id }

        override suspend fun getCurrent(): Instance? = instances.value.firstOrNull { it.isCurrent }

        override suspend fun addInstance(rawUrl: String, name: String?, note: String?): ApiResult<Instance> {
            val instance = Instance(
                id = "instance-1",
                name = name ?: "example.com",
                baseUrl = rawUrl,
                createdAt = 0L,
                updatedAt = 0L,
                lastUsedAt = 0L,
                isCurrent = true,
                note = note,
            )
            instances.value = listOf(instance)
            return ApiResult.Success(instance)
        }

        override suspend fun setCurrent(id: String) = Unit

        override suspend fun delete(id: String) = Unit

        override suspend fun testConnection(baseUrl: String): ApiResult<Unit> {
            testedBaseUrls += baseUrl
            return ApiResult.Success(Unit)
        }
    }
}
