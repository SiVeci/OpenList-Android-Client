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

    /** v1.1 DEC-H3: no instance -> add-instance; otherwise -> home workspace. */
    suspend fun resolveStartRoute(): String {
        val all = instanceRepository.observeAll().first()
        if (all.isEmpty()) return Routes.ADD_INSTANCE
        return Routes.INSTANCE_LIST
    }
}
