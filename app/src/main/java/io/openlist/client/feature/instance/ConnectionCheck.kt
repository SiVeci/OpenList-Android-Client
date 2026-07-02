package io.openlist.client.feature.instance

/** Ephemeral (not persisted) connection-test state, shared by the instance list's
 * per-row "test connection" action and the add-instance screen's test button. */
sealed class ConnectionCheck {
    data object Testing : ConnectionCheck()
    data object Reachable : ConnectionCheck()
    data class Unreachable(val message: String) : ConnectionCheck()
}
