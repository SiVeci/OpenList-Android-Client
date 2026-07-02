package io.openlist.client.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.domain.InstanceRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val instanceRepository: InstanceRepository,
) : ViewModel() {

    /** v0.1_PRD §6.1 steps 1-3: no instance -> add-instance; otherwise -> the most
     * recently used instance's Login route, which itself resolves steps 4-6
     * (valid session / invalidated / guest) once it loads. */
    suspend fun resolveStartRoute(): String {
        val all = instanceRepository.observeAll().first()
        if (all.isEmpty()) return Routes.ADD_INSTANCE
        val target = instanceRepository.getCurrent() ?: all.first()
        return Routes.login(target.id)
    }
}
