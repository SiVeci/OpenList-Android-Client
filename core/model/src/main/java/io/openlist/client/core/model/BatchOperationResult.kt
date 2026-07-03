package io.openlist.client.core.model

/**
 * Aggregated outcome of a client-side-batched write (remove/move/copy):
 * the backend takes one name per item and aborts on first error with no
 * per-item result, so v0.2 sends items one at a time and aggregates here
 * (v0.2_EXECUTION_PLAN.md decision C).
 */
data class BatchOperationResult(
    val total: Int,
    val successCount: Int,
    val failedCount: Int,
    val failedItems: List<BatchOperationFailure>,
)

data class BatchOperationFailure(
    val path: String,
    val reason: String,
)
