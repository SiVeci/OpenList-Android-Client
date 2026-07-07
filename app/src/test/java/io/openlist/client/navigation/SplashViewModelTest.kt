package io.openlist.client.navigation

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.Instance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SplashViewModelTest {

    @Test
    fun `routes to add instance when repository is empty`() = runTest {
        val viewModel = SplashViewModel(FakeInstanceRepository(emptyList()))

        assertEquals(Routes.ADD_INSTANCE, viewModel.resolveStartRoute())
    }

    @Test
    fun `routes to home workspace when any instance exists`() = runTest {
        val viewModel = SplashViewModel(FakeInstanceRepository(listOf(instance("inst-1"))))

        assertEquals(Routes.INSTANCE_LIST, viewModel.resolveStartRoute())
    }

    private class FakeInstanceRepository(
        private val instances: List<Instance>,
    ) : InstanceRepository {
        override fun observeAll(): Flow<List<Instance>> = flowOf(instances)
        override suspend fun getById(id: String): Instance? = instances.firstOrNull { it.id == id }
        override suspend fun getCurrent(): Instance? = instances.firstOrNull { it.isCurrent }
        override suspend fun addInstance(rawUrl: String, name: String?, note: String?): ApiResult<Instance> =
            error("not used")

        override suspend fun setCurrent(id: String) = error("not used")
        override suspend fun delete(id: String) = error("not used")
        override suspend fun testConnection(baseUrl: String): ApiResult<Unit> = error("not used")
    }
}

private fun instance(id: String) = Instance(
    id = id,
    name = id,
    baseUrl = "https://$id.example.com",
    note = null,
    isCurrent = true,
    createdAt = 0L,
    updatedAt = 0L,
    lastUsedAt = 0L,
)
